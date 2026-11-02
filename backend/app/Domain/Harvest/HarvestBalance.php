<?php

namespace App\Domain\Harvest;

use App\Domain\Contracts\DomainFailure;
use Illuminate\Support\Facades\DB;

final class HarvestBalance
{
    public function publicRow(object $item, object $harvest): array
    {
        $active = $item->active && $harvest->record_state === 'active';

        return ['id' => $item->id, 'harvest_id' => $harvest->id, 'planting_group_id' => $harvest->planting_group_id,
            'version' => $item->balance_version, 'harvest_version' => $harvest->version, 'active' => (bool) $active,
            'weight_kg' => $item->weight_kg, 'sold_weight_kg' => $item->sold_weight_kg,
            'available_weight_kg' => $active ? bcsub($item->weight_kg, $item->sold_weight_kg, 6) : '0.000000'];
    }

    public function assertWeight(object $item, string $newWeight): void
    {
        if (bccomp($newWeight, $item->sold_weight_kg, 6) < 0) {
            throw new DomainFailure('harvest_in_use', 'Bobot panen tidak boleh lebih kecil dari bobot yang sudah digunakan penjualan. Selesaikan transaksi terkait melalui pengelola sebelum koreksi atau pembatalan.');
        }
    }

    /** Caller is an authorized ledger transaction. No public balance mutation endpoint. */
    public function publish(string $greenhouseId, object $item, object $harvest): void
    {
        if (DB::transactionLevel() < 1) {
            throw new \LogicException('Harvest balance feed requires a transaction.');
        }
        $body = $this->publicRow($item, $harvest);
        DB::table('change_feed')->insert(['greenhouse_id' => $greenhouseId, 'audience' => 'all', 'entity_type' => 'harvest_balance',
            'entity_id' => $item->id, 'version' => $body['version'], 'tombstone' => ! $body['active'], 'body' => json_encode($body, JSON_THROW_ON_ERROR), 'committed_at' => now()]);
    }
}
