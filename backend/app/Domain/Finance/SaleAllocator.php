<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\DomainFailure;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;
use Ramsey\Uuid\Uuid;

/**
 * Takes each sale line (variety group + grade) from harvest stock, oldest harvest first (25 Nov 2026). A line converted
 * from a legacy `items` payload keeps its fixed harvest item instead. Caller holds the MutationLedger transaction.
 */
final class SaleAllocator
{
    private const NAMESPACE = '0b8e5f3a-7c21-5d94-a6e2-4f19c3b8d057';

    /** Deterministic allocation id, so reallocating a line onto the same harvest item reuses its row. */
    public static function allocationId(string $lineId, string $harvestItemId): string
    {
        return Uuid::uuid5(self::NAMESPACE, $lineId.'|'.$harvestItemId)->toString();
    }

    /**
     * $lines: [{id, planting_group_id, grade_id, quantity_kg, fixed?: {harvest_item_id, allocation_id}}].
     * $oldAllocations: this sale's sale_items; their active kg is available again to this transaction.
     * Returns [{id, sale_line_id, harvest_item_id, harvest_id, harvest_version, grade_id, grade_name, quantity_kg}].
     */
    public static function allocate(string $seasonId, array $lines, Collection $oldAllocations): array
    {
        $groups = DB::table('planting_groups')->whereIn('id', array_unique(array_column($lines, 'planting_group_id')))->pluck('season_id', 'id');
        foreach ($lines as $line) {
            abort_unless(($groups[$line['planting_group_id']] ?? null) === $seasonId, 422, 'Seluruh baris harus berasal dari kelompok tanam di musim penjualan yang sama.');
        }
        $credit = [];
        foreach ($oldAllocations as $old) {
            if ($old->active) {
                $credit[$old->harvest_item_id] = bcadd($credit[$old->harvest_item_id] ?? '0', $old->quantity_kg, 6);
            }
        }
        $used = [];
        $allocations = [];
        $short = [];
        usort($lines, fn ($a, $b) => strcmp($a['id'], $b['id']));
        foreach ($lines as $line) {
            $query = DB::table('harvest_items as hi')->join('harvests as h', 'h.id', '=', 'hi.harvest_id')
                ->select('hi.*', 'h.version as harvest_version', 'h.record_state as harvest_state', 'h.planting_group_id')
                ->where('h.planting_group_id', $line['planting_group_id']);
            if (isset($line['fixed'])) {
                $candidates = $query->where('hi.id', $line['fixed']['harvest_item_id'])->lockForUpdate()->get();
                abort_unless($candidates->count() === 1, 422, 'Sumber panen tidak tersedia.');
                abort_unless($candidates[0]->active && $candidates[0]->harvest_state === 'active', 422, 'Sumber panen baru harus aktif.');
            } else {
                $candidates = $query->where('hi.grade_id', $line['grade_id'])->where('hi.active', true)->where('h.record_state', 'active')
                    ->orderBy('h.occurred_at')->orderBy('h.id')->orderBy('hi.id')->lockForUpdate()->get();
            }
            $remaining = $line['quantity_kg'];
            $total = '0.000000';
            foreach ($candidates as $item) {
                $free = bcsub(bcadd(bcsub($item->weight_kg, $item->sold_weight_kg, 6), $credit[$item->id] ?? '0', 6), $used[$item->id] ?? '0', 6);
                $total = bcadd($total, bccomp($free, '0', 6) > 0 ? $free : '0', 6);
                if (bccomp($remaining, '0', 6) <= 0 || bccomp($free, '0', 6) <= 0) {
                    continue;
                }
                $take = bccomp($free, $remaining, 6) < 0 ? $free : $remaining;
                $used[$item->id] = bcadd($used[$item->id] ?? '0', $take, 6);
                $remaining = bcsub($remaining, $take, 6);
                $allocations[] = ['id' => $line['fixed']['allocation_id'] ?? self::allocationId($line['id'], $item->id), 'sale_line_id' => $line['id'],
                    'harvest_item_id' => $item->id, 'harvest_id' => $item->harvest_id, 'harvest_version' => $item->harvest_version,
                    'grade_id' => $item->grade_id, 'grade_name' => $item->grade_name, 'quantity_kg' => bcadd($take, '0', 6)];
            }
            if (bccomp($remaining, '0', 6) > 0) {
                $short[] = ['line_id' => $line['id'], 'available_kg' => $total];
            }
        }
        if ($short !== []) {
            $message = 'Bobot penjualan melebihi panen tersedia. Tinjau saldo terbaru sebelum mengajukan kembali.';
            // A legacy fixed source keeps its old 409 so queued operations from older apps are handled as before.
            throw isset($lines[0]['fixed']) ? new DomainFailure('harvest_exceeded', $message) : new DomainFailure('harvest_exceeded', $message, 422, reasons: $short);
        }

        return $allocations;
    }
}
