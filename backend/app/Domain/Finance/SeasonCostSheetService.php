<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\DomainFailure;
use App\Domain\Identity\EntityIdentity;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Database\UniqueConstraintViolationException;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

/**
 * One standard cost sheet per season number for the whole farm: every greenhouse season with sequence n
 * uses sheet n (user decision 21 Nov 2026). Sheets have no greenhouse, so they never enter the change feed.
 */
final class SeasonCostSheetService
{
    public function __construct(private GreenhouseAccess $access, private MutationLedger $ledger) {}

    /** Returns sheet n, creating it from sheet n-1 (or the required lines with blank amounts). Caller owns a transaction. */
    public function ensureFor(int $sequence, User $actor): object
    {
        $existing = DB::table('season_cost_sheets')->where('sequence', $sequence)->first();
        if ($existing) {
            return $existing;
        }
        // Concurrent first reads can both miss the lookup; the unique sequence makes the loser fail, so it rolls back its own attempt and reads the winner's sheet.
        try {
            return DB::transaction(fn () => $this->createSheet($sequence, $actor));
        } catch (UniqueConstraintViolationException) {
            // A locking read sees the winner's committed row even inside an outer REPEATABLE READ snapshot (ScopedRead).
            return DB::table('season_cost_sheets')->where('sequence', $sequence)->lockForUpdate()->first() ?? throw new \RuntimeException('Lembar biaya standar tidak ditemukan.');
        }
    }

    private function createSheet(int $sequence, User $actor): object
    {
        $id = (string) Str::uuid();
        app(EntityIdentity::class)->claim('season_cost_sheet', $id);
        $now = now();
        DB::table('season_cost_sheets')->insert(['id' => $id, 'sequence' => $sequence, 'record_state' => 'active', 'version' => 1, 'created_at' => $now, 'updated_at' => $now]);
        $previous = DB::table('season_cost_sheets')->where('sequence', '<', $sequence)->orderByDesc('sequence')->first();
        $source = $previous
            ? DB::table('season_cost_lines')->where('sheet_id', $previous->id)->where('active', true)->orderBy('sort_order')->get()
                ->map(fn ($line) => ['name' => $line->name, 'cost_class' => $line->cost_class, 'amount' => $line->amount, 'is_required' => (bool) $line->is_required])->all()
            : array_map(fn ($line) => [...$line, 'amount' => null, 'is_required' => true], StandardCostCatalog::REQUIRED);
        foreach (array_values($source) as $order => $line) {
            $lineId = (string) Str::uuid();
            app(EntityIdentity::class)->claim('season_cost_line', $lineId);
            DB::table('season_cost_lines')->insert(['id' => $lineId, 'sheet_id' => $id, 'name' => $line['name'], 'cost_class' => $line['cost_class'],
                'amount' => $line['amount'], 'is_required' => $line['is_required'], 'sort_order' => $order, 'active' => true]);
        }
        $sheet = DB::table('season_cost_sheets')->where('id', $id)->first();
        DB::table('audit_logs')->insert(['actor_id' => $actor->id, 'operation_id' => (string) Str::uuid(), 'entity_type' => 'season_cost_sheet',
            'entity_id' => $id, 'action' => 'create', 'version' => 1, 'reason' => null,
            'changes' => json_encode(['before' => null, 'after' => $this->publicSheet($sheet)], JSON_THROW_ON_ERROR), 'server_at' => $now]);

        return $sheet;
    }

    /** Every sequence that has at least one greenhouse season gets its sheet; returns them newest first. */
    public function all(User $actor): array
    {
        $this->access->manager($actor);
        foreach (DB::table('seasons')->whereNotNull('sequence')->distinct()->orderBy('sequence')->pluck('sequence') as $sequence) {
            $this->ensureFor((int) $sequence, $actor);
        }

        return DB::table('season_cost_sheets')->orderByDesc('sequence')->get()->map(fn ($sheet) => $this->publicSheet($sheet))->all();
    }

    public function publicSheet(object $sheet): array
    {
        $lines = DB::table('season_cost_lines')->where('sheet_id', $sheet->id)->where('active', true)->orderBy('sort_order')->get()
            ->map(fn ($line) => ['id' => $line->id, 'name' => $line->name, 'cost_class' => $line->cost_class,
                'amount' => $line->amount === null ? null : (string) $line->amount, 'is_required' => (bool) $line->is_required, 'sort_order' => (int) $line->sort_order])->all();
        $seasons = DB::table('seasons')->join('greenhouses', 'greenhouses.id', '=', 'seasons.greenhouse_id')
            ->where('seasons.sequence', $sheet->sequence)->where('greenhouses.active', true)->orderBy('greenhouses.name')
            ->get(['greenhouses.name', 'seasons.status']);
        $closed = $seasons->where('status', 'closed')->pluck('name')->values()->all();

        return ['id' => $sheet->id, 'sequence' => (int) $sheet->sequence, 'version' => (int) $sheet->version, 'lines' => $lines,
            'totals' => self::totals($lines), 'greenhouse_count' => $seasons->count(),
            'open_greenhouse_names' => $seasons->where('status', 'open')->pluck('name')->values()->all(),
            // Editing is refused while an active greenhouse has closed this season number; inactive greenhouses do not lock it.
            'locked' => $closed !== [], 'closed_greenhouse_names' => $closed,
            'updated_at' => CarbonImmutable::parse($sheet->updated_at, 'UTC')->toISOString()];
    }

    /** What a season report depends on: the sheet's own inputs, not other greenhouses' names or statuses. */
    public static function reportSource(array $public): array
    {
        return ['id' => $public['id'], 'sequence' => $public['sequence'], 'version' => $public['version'], 'lines' => $public['lines'], 'totals' => $public['totals']];
    }

    /** FC and VC per greenhouse; a class is null while one of its lines has no amount (blank is not zero). */
    public static function totals(array $lines): array
    {
        $sum = ['FC' => '0.00', 'VC' => '0.00'];
        foreach ($lines as $line) {
            if ($sum[$line['cost_class']] === null) {
                continue;
            }
            $sum[$line['cost_class']] = $line['amount'] === null ? null : bcadd($sum[$line['cost_class']], $line['amount'], 2);
        }
        $tc = $sum['FC'] !== null && $sum['VC'] !== null ? bcadd($sum['FC'], $sum['VC'], 2) : null;

        return ['FC' => $sum['FC'], 'VC' => $sum['VC'], 'TC' => $tc, 'complete' => $tc !== null];
    }

    public function mutate(User $actor, array $op): array
    {
        return $this->ledger->run($actor, $op, function (User $current) {
            $this->access->manager($current);
        }, function (User $current) use ($op) {
            $sheet = DB::table('season_cost_sheets')->where('id', $op['entity_id'])->lockForUpdate()->first() ?? abort(404);
            $before = $this->publicSheet($sheet);
            $this->ledger->version($sheet, $op['base_version'], $before);
            if ($before['locked']) {
                throw new DomainFailure('season_closed', 'Ada greenhouse yang sudah menutup musim ini. Buka kembali musimnya sebelum mengubah biaya standar.');
            }
            $this->replaceLines($sheet->id, $op['payload']['lines']);
            DB::table('season_cost_sheets')->where('id', $sheet->id)->update(['version' => $sheet->version + 1, 'updated_at' => now()]);

            return ['before' => $before, 'after' => $this->publicSheet(DB::table('season_cost_sheets')->where('id', $sheet->id)->first()), 'greenhouse_id' => null];
        });
    }

    private function replaceLines(string $sheetId, array $lines): void
    {
        $current = DB::table('season_cost_lines')->where('sheet_id', $sheetId)->where('active', true)->get()->keyBy('id');
        $errors = [];
        $names = [];
        $kept = [];
        foreach ($lines as $index => $line) {
            $key = mb_strtolower(trim($line['name']));
            if (isset($names[$key])) {
                $errors["lines.$index.name"] = 'Nama biaya sudah dipakai di lembar ini.';
            }
            $names[$key] = true;
            $old = $current[$line['id']] ?? null;
            if ($old && (bool) $old->is_required && ($old->name !== $line['name'] || $old->cost_class !== $line['cost_class'] || ! $line['is_required'])) {
                $errors["lines.$index.name"] = 'Baris wajib tidak dapat diganti nama atau jenisnya.';
            }
            if (! $old && $line['is_required']) {
                $errors["lines.$index.is_required"] = 'Baris tambahan tidak dapat dijadikan wajib.';
            }
            $kept[$line['id']] = true;
        }
        foreach ($current as $id => $old) {
            if ($old->is_required && ! isset($kept[$id])) {
                $errors['lines'] = 'Baris wajib "'.$old->name.'" tidak dapat dihapus.';
            }
        }
        if ($errors) {
            throw ValidationException::withMessages($errors);
        }
        foreach ($current as $id => $old) {
            if (! isset($kept[$id])) {
                DB::table('season_cost_lines')->where('id', $id)->update(['active' => false]);
            }
        }
        foreach (array_values($lines) as $order => $line) {
            $values = ['name' => trim($line['name']), 'cost_class' => $line['cost_class'], 'amount' => $line['amount'], 'sort_order' => $order];
            if (isset($current[$line['id']])) {
                DB::table('season_cost_lines')->where('id', $line['id'])->update($values);
            } else {
                app(EntityIdentity::class)->claim('season_cost_line', $line['id']);
                DB::table('season_cost_lines')->insert(['id' => $line['id'], 'sheet_id' => $sheetId, ...$values, 'is_required' => false, 'active' => true]);
            }
        }
    }
}
