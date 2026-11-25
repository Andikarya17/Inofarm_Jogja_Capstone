<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\DomainFailure;
use App\Domain\Cultivation\MasterService;
use App\Domain\Harvest\HarvestBalance;
use App\Domain\Harvest\QualityGradeCatalog;
use App\Domain\Identity\EntityIdentity;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;

final class SaleService
{
    public function __construct(private GreenhouseAccess $access, private MasterService $masters, private MutationLedger $ledger, private HarvestBalance $balances) {}

    public function scope(User $actor)
    {
        $this->access->manager($actor);
        return DB::table('sales')->whereIn('greenhouse_id', $this->access->ids($actor));
    }

    public function publicRow(object $row): array
    {
        $data = (array) $row;$data['recorded_by'] = User::findOrFail($row->recorded_by)->public_id;
        foreach (['paid_at', 'created_at', 'updated_at'] as $key) {$data[$key] = CarbonImmutable::parse($data[$key], 'UTC')->toISOString();}
        $data['items'] = DB::table('sale_items')->where('sale_id', $row->id)->where('active', true)->orderBy('id')->get()->map(fn ($line) => [...(array) $line, 'active' => (bool) $line->active])->all();
        $allocations = DB::table('sale_items as si')->join('harvests as h', 'h.id', '=', 'si.harvest_id')->where('si.sale_id', $row->id)->where('si.active', true)
            ->orderBy('h.occurred_at')->orderBy('h.id')->orderBy('si.harvest_item_id')->select('si.sale_line_id', 'si.harvest_item_id', 'si.harvest_id', 'si.quantity_kg')->get()->groupBy('sale_line_id');
        $qualities = array_flip(array_map('mb_strtolower', QualityGradeCatalog::NAMES));
        $data['lines'] = DB::table('sale_lines')->where('sale_id', $row->id)->where('active', true)->orderBy('id')->get()->map(fn ($line) => [
            'id' => $line->id, 'planting_group_id' => $line->planting_group_id, 'grade_id' => $line->grade_id, 'grade_name' => $line->grade_name,
            'variety_name' => $line->variety_name, 'quality' => $qualities[mb_strtolower(trim($line->grade_name))] ?? null, 'buyer_grade' => $line->buyer_grade,
            'quantity_kg' => $line->quantity_kg, 'fruit_count' => $line->fruit_count === null ? null : (int) $line->fruit_count, 'price_per_kg' => $line->price_per_kg,
            'exact_amount' => $line->exact_amount,
            'allocations' => ($allocations[$line->id] ?? collect())->map(fn ($a) => ['harvest_item_id' => $a->harvest_item_id, 'harvest_id' => $a->harvest_id, 'quantity_kg' => $a->quantity_kg])->values()->all(),
        ])->all();
        return $data;
    }

    public function mutate(User $actor, array $op): array
    {
        return $this->ledger->run($actor, $op, function (User $current) use ($op) {
            $this->access->manager($current);
            if ($op['action'] === 'create') {$this->masters->scope($current, 'seasons')->where('id', $op['payload']['season_id'])->first() ?? abort(404);}
            else {$this->scope($current)->where('id', $op['entity_id'])->first() ?? abort(404);}
        }, function (User $current) use ($op) {
            $row = $op['action'] === 'create' ? null : ($this->scope($current)->where('id', $op['entity_id'])->lockForUpdate()->first() ?? abort(404));
            $before = $row ? $this->publicRow($row) : null;
            if ($row) {$this->ledger->version($row, $op['base_version'], $before);abort_unless($row->record_state === 'active', 422, 'Penjualan telah dibatalkan.');}
            $p = $op['payload'];$seasonId = $row->season_id ?? $p['season_id'];
            abort_if($row && $op['action'] !== 'cancel' && $p['season_id'] !== $seasonId, 422, 'Musim penjualan tidak dapat dipindah.');
            $season = $this->masters->scope($current, 'seasons')->where('id', $seasonId)->lockForUpdate()->first() ?? abort(404);
            abort_unless($this->access->require($current, $season->greenhouse_id)->active, 422, 'Greenhouse tidak aktif.');
            if ($season->status !== 'open') {throw new DomainFailure('season_closed', 'Musim ditutup. Buka kembali dengan alasan sebelum mengubah penjualan.');}
            $old = DB::table('sale_items')->where('sale_id', $op['entity_id'])->orderBy('id')->lockForUpdate()->get()->keyBy('id');
            $oldLines = DB::table('sale_lines')->where('sale_id', $op['entity_id'])->orderBy('id')->lockForUpdate()->get()->keyBy('id');
            if ($op['action'] === 'cancel') {
                // Lines and allocations stay as they were; the cancelled state alone releases the stock.
                $quote = null;$lines = [];$allocations = [];
            } else {
                abort_unless(isset($p['lines']) xor isset($p['items']), 422, 'Kirim baris penjualan atau rincian lama, tidak keduanya.');
                $lines = isset($p['lines']) ? $this->checkedLines($p['lines']) : $this->legacyLines($p['items']);
                $quote = SaleMath::quote($lines, isset($p['lines']) ? 'lines' : 'items');
                $lines = $quote['items'];
                $allocations = SaleAllocator::allocate($seasonId, $lines, $old);
            }
            // Every harvest item this sale used before or uses now gets its counter recomputed.
            $prior = [];$next = [];
            foreach ($old as $line) {if ($line->active) {$prior[$line->harvest_item_id] = bcadd($prior[$line->harvest_item_id] ?? '0', $line->quantity_kg, 6);}}
            foreach ($allocations as $allocation) {$next[$allocation['harvest_item_id']] = bcadd($next[$allocation['harvest_item_id']] ?? '0', $allocation['quantity_kg'], 6);}
            $sources = collect(array_keys($prior + $next))->sort()->values();
            $sourceRows = DB::table('harvest_items')->whereIn('id', $sources)->orderBy('id')->lockForUpdate()->get()->keyBy('id');
            abort_unless($sourceRows->count() === $sources->count(), 422, 'Sumber panen tidak tersedia.');
            $locked = [];
            foreach ($sourceRows as $source) {
                $harvest = DB::table('harvests')->where('id', $source->harvest_id)->lockForUpdate()->first() ?? abort(422);
                $this->balances->assertConsistent($source);
                $sold = bcadd(bcsub($source->sold_weight_kg, $prior[$source->id] ?? '0', 6), $next[$source->id] ?? '0', 6);
                if (bccomp($sold, '0', 6) < 0 || bccomp($sold, $source->weight_kg, 6) > 0) {throw new DomainFailure('harvest_exceeded', 'Bobot penjualan melebihi panen tersedia. Tinjau saldo terbaru sebelum mengajukan kembali.');}
                $locked[$source->id] = [$source, $harvest, $sold];
            }
            $version = ($row->version ?? 0) + 1;
            if ($quote === null) {$data = ['record_state' => 'cancelled'];}
            else {
                abort_unless(($p['payment_status'] ?? null) === 'paid', 422, 'Model awal mencatat penjualan yang telah lunas.');
                $data = ['season_id' => $seasonId, 'greenhouse_id' => $season->greenhouse_id, 'season_name' => $row->season_name ?? $season->name,
                    'season_version' => $row->season_version ?? $season->version, 'paid_at' => CarbonImmutable::parse($p['paid_at'])->utc()->format('Y-m-d H:i:s.u'),
                    'payment_status' => 'paid', 'customer_name' => $p['customer_name'], 'notes' => $p['notes'], ...collect($quote)->except('items')->all()];
                // Older clients omit these keys; an update from them keeps the stored values instead of clearing them.
                foreach (['payment_method', 'payment_reference', 'customer_type'] as $key) {
                    $data[$key] = array_key_exists($key, $p) ? $p[$key] : ($row->$key ?? null);
                }
                // A named buyer (21 Nov 2026) sets the type and keeps a copy of the name as it was at the sale.
                if (isset($p['customer'])) {
                    [$data['customer_id'], $data['customer_type'], $data['customer_name']] = app(CustomerService::class)->forSale($current, $p['customer']);
                } elseif (array_key_exists('customer', $p)) {
                    // An explicit null unlinks the buyer (correction to "Tidak dicatat"); omitting the key keeps it for older clients.
                    $data['customer_id'] = null;
                }
                // Grades A/B/C are the mitra's own grading (20 Nov 2026); other buyers buy the Layak line ungraded.
                if (collect($lines)->contains(fn ($line) => $line['buyer_grade'] !== null)) {
                    abort_unless(($data['customer_type'] ?? null) === 'partner', 422, 'Grade A, B, C hanya untuk penjualan ke mitra.');
                }
            }
            $data = [...$data, 'version' => $version, 'updated_at' => now()];
            if (!$row) {DB::table('sales')->insert([...$data, 'id' => $op['entity_id'], 'recorded_by' => $current->id, 'recorded_by_name' => $current->name, 'created_at' => now()]);}
            else {DB::table('sales')->where('id', $row->id)->update($data);}
            if ($op['action'] !== 'cancel') {
                DB::table('sale_items')->where('sale_id', $op['entity_id'])->update(['active' => false]);
                DB::table('sale_lines')->where('sale_id', $op['entity_id'])->update(['active' => false]);
                $this->saveLines($op['entity_id'], $lines, $oldLines);
                $this->saveAllocations($op['entity_id'], $allocations, collect($lines)->keyBy('id'), $old);
            }
            foreach ($locked as [$source, $harvest, $sold]) {
                if (bccomp($sold, $source->sold_weight_kg, 6) !== 0) {
                    DB::table('harvest_items')->where('id', $source->id)->update(['sold_weight_kg' => $sold, 'balance_version' => $source->balance_version + 1]);
                    $this->balances->publish($season->greenhouse_id, DB::table('harvest_items')->where('id', $source->id)->first(), $harvest);
                }
            }
            return ['before' => $before, 'after' => $this->publicRow(DB::table('sales')->where('id', $op['entity_id'])->first()), 'greenhouse_id' => $season->greenhouse_id, 'audience' => 'manager'];
        });
    }

    /** New-shape lines: one line per group + grade (+ mitra grade); SaleAllocator picks the stock. */
    private function checkedLines(array $lines): array
    {
        $keys = array_map(fn ($line) => $line['planting_group_id'].'|'.$line['grade_id'].'|'.($line['buyer_grade'] ?? ''), $lines);
        abort_unless(count(array_unique(array_column($lines, 'id'))) === count($lines) && count(array_unique($keys)) === count($lines), 422,
            'Baris dengan varietas, kualitas dan grade mitra yang sama tidak boleh berulang dalam satu penjualan.');

        return array_map(fn ($line) => [...$line, 'fruit_count' => $line['fruit_count'] ?? null, 'buyer_grade' => $line['buyer_grade'] ?? null], $lines);
    }

    /**
     * Legacy `items` (queued by older apps): each item is one line whose id derives from the item id, allocated only to its
     * own harvest item under the item id, so replays and backfilled rows stay identical.
     */
    private function legacyLines(array $items): array
    {
        $pairs = array_map(fn ($item) => $item['harvest_item_id'].'|'.($item['buyer_grade'] ?? ''), $items);
        abort_unless(count(array_unique(array_column($items, 'id'))) === count($items) && count(array_unique($pairs)) === count($items), 422, 'Identitas rincian dan sumber panen tidak boleh berulang dalam satu transaksi.');
        $sources = DB::table('harvest_items as hi')->join('harvests as h', 'h.id', '=', 'hi.harvest_id')->whereIn('hi.id', array_column($items, 'harvest_item_id'))
            ->select('hi.id', 'hi.grade_id', 'h.planting_group_id')->get()->keyBy('id');

        return array_map(function ($item) use ($sources) {
            $source = $sources[$item['harvest_item_id']] ?? abort(422, 'Sumber panen tidak tersedia.');

            return ['id' => SaleLineMigration::lineId($item['id']), 'planting_group_id' => $source->planting_group_id, 'grade_id' => $source->grade_id,
                'quantity_kg' => $item['quantity_kg'], 'fruit_count' => null, 'price_per_kg' => $item['price_per_kg'], 'buyer_grade' => $item['buyer_grade'] ?? null,
                'fixed' => ['harvest_item_id' => $item['harvest_item_id'], 'allocation_id' => $item['id']]];
        }, $items);
    }

    /** Upserts lines by id; names are copied at first save and kept while the line keeps its group and grade. */
    private function saveLines(string $saleId, array $lines, Collection $oldLines): void
    {
        foreach ($lines as $line) {
            $previous = $oldLines->get($line['id']);
            $same = $previous && $previous->planting_group_id === $line['planting_group_id'] && $previous->grade_id === $line['grade_id'];
            $names = $same ? ['grade_name' => $previous->grade_name, 'variety_name' => $previous->variety_name] : [
                'grade_name' => DB::table('grades')->where('id', $line['grade_id'])->value('name') ?? abort(422, 'Kualitas tidak tersedia.'),
                'variety_name' => DB::table('planting_groups as pg')->join('varieties as v', 'v.id', '=', 'pg.variety_id')->where('pg.id', $line['planting_group_id'])->value('v.name')];
            $saved = ['sale_id' => $saleId, 'planting_group_id' => $line['planting_group_id'], 'grade_id' => $line['grade_id'], ...$names,
                'buyer_grade' => $line['buyer_grade'], 'quantity_kg' => $line['quantity_kg'], 'fruit_count' => $line['fruit_count'],
                'price_per_kg' => $line['price_per_kg'], 'exact_amount' => $line['exact_amount'], 'active' => true, 'updated_at' => now()];
            if ($previous) {DB::table('sale_lines')->where('id', $line['id'])->update($saved);}
            else {app(EntityIdentity::class)->claim('sale_line', $line['id']);DB::table('sale_lines')->insert(['id' => $line['id'], ...$saved, 'created_at' => now()]);}
        }
    }

    /** Upserts allocation rows (sale_items) by id; an existing row keeps its harvest item and its snapshot of the harvest. */
    private function saveAllocations(string $saleId, array $allocations, Collection $lines, Collection $old): void
    {
        foreach ($allocations as $allocation) {
            $previous = $old->get($allocation['id']);$line = $lines[$allocation['sale_line_id']];
            abort_if($previous && $previous->harvest_item_id !== $allocation['harvest_item_id'], 422, 'Sumber rincian penjualan tidak dapat diganti. Gunakan rincian baru.');
            $saved = ['sale_id' => $saleId, 'sale_line_id' => $allocation['sale_line_id'], 'harvest_item_id' => $allocation['harvest_item_id'], 'harvest_id' => $allocation['harvest_id'],
                'harvest_version' => $previous->harvest_version ?? $allocation['harvest_version'], 'grade_id' => $allocation['grade_id'],
                'grade_name' => $previous->grade_name ?? $allocation['grade_name'], 'quantity_kg' => $allocation['quantity_kg'], 'price_per_kg' => $line['price_per_kg'],
                'exact_amount' => bcmul($allocation['quantity_kg'], $line['price_per_kg'], 12), 'buyer_grade' => $line['buyer_grade'], 'active' => true];
            if ($previous) {DB::table('sale_items')->where('id', $allocation['id'])->update($saved);}
            else {app(EntityIdentity::class)->claim('sale_item', $allocation['id']);DB::table('sale_items')->insert(['id' => $allocation['id'], ...$saved]);}
        }
    }
}
