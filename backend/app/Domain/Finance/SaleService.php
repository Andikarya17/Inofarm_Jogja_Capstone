<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\DomainFailure;
use App\Domain\Cultivation\MasterService;
use App\Domain\Harvest\HarvestBalance;
use App\Domain\Identity\EntityIdentity;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
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
            if ($op['action'] !== 'cancel') {
                abort_unless(count(array_unique(array_column($p['items'], 'id'))) === count($p['items']) && count(array_unique(array_column($p['items'], 'harvest_item_id'))) === count($p['items']), 422, 'Identitas rincian dan sumber panen tidak boleh berulang dalam satu transaksi.');
            }
            $quote = $op['action'] === 'cancel' ? null : SaleMath::quote($p['items']);
            $submitted = collect($quote['items'] ?? [])->keyBy('id');
            $sources = $old->pluck('harvest_item_id')->merge($submitted->pluck('harvest_item_id'))->unique()->sort()->values();
            $sourceRows = DB::table('harvest_items')->whereIn('id', $sources)->orderBy('id')->lockForUpdate()->get()->keyBy('id');
            abort_unless($sourceRows->count() === $sources->count(), 422, 'Sumber panen tidak tersedia.');
            $locked = [];
            foreach ($sourceRows as $source) {
                $harvest = DB::table('harvests')->where('id', $source->harvest_id)->lockForUpdate()->first() ?? abort(422);
                $group = $this->masters->scope($current, 'planting_groups')->where('id', $harvest->planting_group_id)->first() ?? abort(404);
                abort_unless($group->season_id === $seasonId, 422, 'Seluruh sumber harus berasal dari musim penjualan yang sama.');
                $this->balances->assertConsistent($source);
                $prior = '0.000000';foreach ($old as $line) {if ($line->active && $line->harvest_item_id === $source->id) {$prior = bcadd($prior, $line->quantity_kg, 6);}}
                $next = '0.000000';foreach ($submitted as $line) {if ($line['harvest_item_id'] === $source->id) {$next = bcadd($next, $line['quantity_kg'], 6);}}
                if (bccomp($next, '0', 6) > 0) {abort_unless($source->active && $harvest->record_state === 'active', 422, 'Sumber panen baru harus aktif.');}
                $sold = bcadd(bcsub($source->sold_weight_kg, $prior, 6), $next, 6);
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
            }
            $data = [...$data, 'version' => $version, 'updated_at' => now()];
            if (!$row) {DB::table('sales')->insert([...$data, 'id' => $op['entity_id'], 'recorded_by' => $current->id, 'recorded_by_name' => $current->name, 'created_at' => now()]);}
            else {DB::table('sales')->where('id', $row->id)->update($data);}
            if ($op['action'] !== 'cancel') {foreach ($old as $line) {DB::table('sale_items')->where('id', $line->id)->update(['active' => false]);}}
            foreach ($submitted as $id => $line) {
                [$source, $harvest] = $locked[$line['harvest_item_id']];$previous = $old->get($id);
                abort_if($previous && $previous->harvest_item_id !== $source->id, 422, 'Sumber rincian penjualan tidak dapat diganti. Gunakan rincian baru.');
                $saved = ['sale_id' => $op['entity_id'], 'harvest_item_id' => $source->id, 'harvest_id' => $harvest->id,
                    'harvest_version' => $previous->harvest_version ?? $harvest->version, 'grade_id' => $source->grade_id,
                    'grade_name' => $previous->grade_name ?? $source->grade_name, 'quantity_kg' => $line['quantity_kg'], 'price_per_kg' => $line['price_per_kg'], 'exact_amount' => $line['exact_amount'], 'active' => true];
                if ($previous) {DB::table('sale_items')->where('id', $id)->update($saved);}
                else {app(EntityIdentity::class)->claim('sale_item', $id);DB::table('sale_items')->insert(['id' => $id, ...$saved]);}
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
}
