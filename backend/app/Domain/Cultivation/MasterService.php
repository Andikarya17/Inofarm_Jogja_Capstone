<?php

namespace App\Domain\Cultivation;

use App\Domain\Contracts\DomainFailure;
use App\Domain\Harvest\HarvestProgress;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Identity\EntityIdentity;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

final class MasterService
{
    public const TABLES = ['varieties', 'units', 'materials', 'grades', 'expense_categories', 'seasons', 'planting_groups', 'greenhouses', 'greenhouse_assignments'];

    public function __construct(private GreenhouseAccess $access, private MutationLedger $ledger, private RunningPeriod $period) {}

    public function scope(User $actor, string $table)
    {
        $query = DB::table($table);
        if ($table === 'greenhouses') {
            return $this->access->query($actor);
        }
        if ($table === 'planting_groups') {
            return $query->whereIn('season_id', DB::table('seasons')->select('id')->whereIn('greenhouse_id', $this->access->ids($actor)));
        }
        if ($table === 'greenhouse_assignments') {
            $this->access->manager($actor);
        }
        if ($table === 'expense_categories') {
            $this->access->manager($actor);
        }

        return $query->whereIn('greenhouse_id', $this->access->ids($actor));
    }

    public function greenhouse(string $table, object|array $row): string
    {
        $row = (array) $row;
        if ($table === 'greenhouses') {
            return $row['id'];
        }
        if ($table === 'planting_groups') {
            return DB::table('seasons')->where('id', $row['season_id'])->value('greenhouse_id') ?? abort(404);
        }

        return $row['greenhouse_id'];
    }

    public function publicRow(string $table, object $row): array
    {
        $value = (array) $row;
        if (array_key_exists('active', $value)) {
            $value['active'] = (bool) $value['active'];
        }
        if ($table === 'greenhouses') {
            $value['running_period_reviewed'] = (bool) $value['running_period_reviewed'];
        }
        if ($table === 'planting_groups') {
            $value['harvest_target'] = HarvestProgress::decode($row->harvest_target);
            $value['harvest_completion'] = HarvestProgress::decode($row->harvest_completion);
        }
        if ($table === 'greenhouse_assignments') {
            $user = User::findOrFail($row->user_id);
            $value['user_id'] = $user->public_id;
            $value['user_name'] = $user->name;
        }
        unset($value['created_by']);
        foreach (['created_at', 'updated_at', 'effective_from', 'effective_until'] as $key) {
            if (isset($value[$key])) {
                $value[$key] = CarbonImmutable::parse($value[$key], 'UTC')->toISOString();
            }
        }

        return $value;
    }

    public function mutate(User $actor, string $table, array $op): array
    {
        return $this->ledger->run($actor, $op, function (User $current) use ($table, $op) {
            $this->access->manager($current);
            if ($table !== 'greenhouses' || $op['action'] !== 'create') {
                $source = $op['action'] === 'create' ? $op['payload'] : ($this->scope($current, $table)->where('id', $op['entity_id'])->first() ?? abort(404));
                $this->access->require($current, $this->greenhouse($table, $source));
            } elseif (DB::table('greenhouses')->where('id', $op['entity_id'])->exists()) {
                $this->access->require($current, $op['entity_id']);
            }
        }, function (User $current) use ($table, $op) {
            $id = $op['entity_id'];
            $before = null;
            $payload = $op['payload'];
            $action = $op['action'];
            if ($action !== 'create') {
                $row = $this->scope($current, $table)->where('id', $id)->first() ?? abort(404);
                $before = $this->publicRow($table, $row);
                $this->ledger->version($row, $op['base_version'], $before);
                $gh = $this->greenhouse($table, $row);
            } else {
                if (DB::table($table)->where('id', $id)->exists()) {
                    throw new DomainFailure('entity_exists', 'Identitas catatan sudah digunakan.');
                }
                $gh = $table === 'greenhouses' ? $id : $this->greenhouse($table, $payload);
            }
            $context = null;
            if ($table !== 'greenhouses' || $action !== 'create') {
                $context = $this->access->require($current, $gh);
                abort_if(! $context->active && ! ($table === 'greenhouses' && $action === 'update'), 422, 'Greenhouse tidak aktif.');
            }
            $data = $payload;
            unset($data['reason']);
            if (isset($before['dimension'])) {
                abort_if($data['dimension'] !== $before['dimension'], 422, 'Dimensi satuan/bahan tidak dapat diubah; buat master baru.');
            }
            if ($action === 'designate_period') {
                $data = $this->period->designation($context, $data['planting_group_id']);
            } elseif (in_array($action, ['close', 'reopen'], true)) {
                $newStatus = $action === 'close' ? 'closed' : 'open';
                abort_if($before['status'] === $newStatus, 422, 'Status musim sudah sesuai permintaan.');
                if ($action === 'close') {
                    $this->assertSoldOut($id, $payload['reason'] ?? null);
                }
                $data = ['status' => $newStatus];
            } elseif ($table === 'planting_groups') {
                $seasonId = $before['season_id'] ?? $data['season_id'];
                $season = DB::table('seasons')->where('id', $seasonId)->first() ?? abort(404);
                if ($season->status !== 'open') {
                    throw new DomainFailure('season_closed', 'Musim ditutup. Buka kembali dengan alasan sebelum mengubah kelompok.');
                }
                if (in_array($action, ['complete_harvest', 'reopen_harvest'], true)) {
                    $complete = $action === 'complete_harvest';
                    abort_unless($row->active && $context->active, 422, 'Kelompok dan greenhouse harus aktif.');
                    abort_if($complete === ($before['harvest_completion'] !== null), 422, 'Status panen sudah sesuai.');
                    abort_if($complete && ! DB::table('harvests')->where('planting_group_id', $id)->where('record_state', 'active')->exists(),
                        422, 'Catat sedikitnya satu panen sebelum menandai selesai.');
                    $data = ['harvest_completion' => $complete ? json_encode([
                        'reason' => $payload['reason'] ?? null, 'at' => CarbonImmutable::now('UTC')->toISOString(),
                        'by' => $current->public_id, 'by_name' => $current->name,
                    ], JSON_THROW_ON_ERROR) : null];
                } else {
                    $variety = DB::table('varieties')->where('id', $data['variety_id'])->where('greenhouse_id', $gh)
                        ->when(($before['variety_id'] ?? null) !== $data['variety_id'], fn ($q) => $q->where('active', true))->first();
                    abort_unless($variety, 422, 'Varietas harus aktif dan berasal dari greenhouse yang sama.');
                    abort_if(($data['transplant_date'] ?? null) !== null && $data['transplant_date'] < $data['sowing_date'], 422, 'Tanggal pindah tidak boleh sebelum semai.');
                    if (array_key_exists('harvest_target', $data)) {
                        $data['harvest_target'] = HarvestProgress::changeTarget($current, $data['harvest_target'],
                            $before['harvest_target'] ?? null, ($before['version'] ?? 0) + 1);
                    }
                }
            } elseif ($table === 'seasons') {
                if (($before['status'] ?? null) === 'closed') {
                    throw new DomainFailure('season_closed', 'Buka kembali musim sebelum mengoreksi data musim.');
                }
                abort_if(($data['end_date'] ?? null) !== null && $data['end_date'] < $data['start_date'], 422, 'Tanggal akhir tidak boleh sebelum mulai.');
            } elseif ($table === 'greenhouse_assignments') {
                if ($action === 'create') {
                    $user = $this->access->manageableUsers($current)->where('public_id', $data['user_id'])->where('role', 'worker')->where('active', true)->first() ?? abort(422, 'Pilih akun pekerja aktif untuk ditambahkan ke greenhouse ini.');
                    $data['user_id'] = $user->id;
                    abort_if(DB::table($table)->where('user_id', $user->id)->where('greenhouse_id', $gh)->exists(), 422, 'Penugasan sudah ada; perbarui versi yang ada.');
                }
                abort_if(isset($data['effective_until']) && strtotime($data['effective_until']) <= strtotime($data['effective_from']), 422, 'Akhir penugasan harus sesudah awal.');
                foreach (['effective_from', 'effective_until'] as $key) {
                    if (isset($data[$key])) {
                        $data[$key] = CarbonImmutable::parse($data[$key])->utc()->format('Y-m-d H:i:s.u');
                    }
                }
            }
            $this->period->guardMasterChange($table, $action, $id, $context?->running_group_id, $data, $before);
            $data['version'] = $before === null ? 1 : $before['version'] + 1;
            $data['updated_at'] = now();
            if ($action === 'create') {
                $data['id'] = $id;
                $data['created_at'] = now();
                if (in_array($table, ['greenhouses', 'greenhouse_assignments'], true)) {
                    $data['created_by'] = $current->id;
                }
                DB::table($table)->insert($data);
                if ($table === 'greenhouses') {
                    $assignmentId = (string) Str::uuid();
                    app(EntityIdentity::class)->claim('greenhouse_assignments', $assignmentId);
                    DB::table('greenhouse_assignments')->insert(['id' => $assignmentId, 'greenhouse_id' => $id,
                        'user_id' => $current->id, 'created_by' => $current->id, 'effective_from' => now(), 'created_at' => now(), 'updated_at' => now()]);
                    app(SprayMaterialCatalog::class)->ensure($current, $id);
                }
            } else {
                DB::table($table)->where('id', $id)->update($data);
            }
            $after = $this->publicRow($table, DB::table($table)->where('id', $id)->first());

            return ['before' => $before, 'after' => $after, 'greenhouse_id' => $gh,
                'audience' => in_array($table, ['greenhouse_assignments', 'expense_categories'], true) ? 'manager' : 'all'];
        });
    }

    /** Unsold saleable harvest weight (kg) of a season; defective lines are already "gagal jual". */
    public static function unsoldWeight(string $seasonId): string
    {
        return (string) (DB::table('harvest_items as item')->join('harvests as harvest', 'harvest.id', '=', 'item.harvest_id')
            ->join('planting_groups as grp', 'grp.id', '=', 'harvest.planting_group_id')->where('grp.season_id', $seasonId)
            ->where('harvest.record_state', 'active')->where('item.active', true)
            ->where(fn ($q) => $q->whereNull('item.quality')->orWhere('item.quality', '!=', 'defective'))
            ->selectRaw('CAST(COALESCE(SUM(item.weight_kg - item.sold_weight_kg), 0) AS DECIMAL(20,6)) AS kg')->value('kg') ?? '0.000000');
    }

    /** Field practice: a season ends once its fruit is sold out; leftovers need an explicit note (e.g. gagal jual). */
    private function assertSoldOut(string $seasonId, ?string $note): void
    {
        $unsold = self::unsoldWeight($seasonId);
        if (bccomp($unsold, '0', 6) > 0 && trim($note ?? '') === '') {
            throw new DomainFailure('season_unsold_balance', 'Masih ada '.rtrim(rtrim($unsold, '0'), '.').' kg buah belum terjual. Isi catatan, misalnya gagal jual, untuk tetap menutup musim.', 422);
        }
    }

    /** Copies active masters missing (by name) in the target; re-running it copies nothing twice. */
    public function copyMasters(User $actor, string $target, string $source): array
    {
        $this->access->manager($actor);
        $this->access->require($actor, $source);
        $result = [];
        DB::transaction(function () use ($actor, $target, $source, &$result) {
            foreach (['varieties', 'units', 'materials', 'grades', 'expense_categories'] as $table) {
                $existing = DB::table($table)->where('greenhouse_id', $target)->get()
                    ->map(fn ($row) => mb_strtolower(trim($row->name)).'|'.($row->dimension ?? ''))->flip();
                $copied = 0;
                foreach (DB::table($table)->where('greenhouse_id', $source)->where('active', true)->orderBy('name')->get() as $row) {
                    $key = mb_strtolower(trim($row->name)).'|'.($row->dimension ?? '');
                    if ($existing->has($key)) {
                        continue;
                    }
                    $payload = ['greenhouse_id' => $target, 'name' => $row->name] + (isset($row->dimension) ? ['dimension' => $row->dimension] : []);
                    $this->mutate($actor, $table, ['schema_version' => '2', 'sync_epoch' => DB::table('sync_state')->where('id', 1)->value('epoch'),
                        'operation_id' => (string) Str::uuid(), 'entity_id' => (string) Str::uuid(), 'entity_type' => $table,
                        'action' => 'create', 'base_version' => null, 'payload' => $payload]);
                    $existing[$key] = true;
                    $copied++;
                }
                $result[$table] = $copied;
            }
        });

        return ['greenhouse_id' => $target, 'source_greenhouse_id' => $source, 'copied' => $result];
    }
}
