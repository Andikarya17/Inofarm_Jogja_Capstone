<?php

namespace App\Domain\Finance;

use Illuminate\Support\Facades\DB;
use Ramsey\Uuid\Uuid;

/** Data step of migration 24, kept here so tests can run it against existing rows. */
final class SaleLineMigration
{
    private const NAMESPACE = '6f0c2a54-3b1e-5d7a-9c48-2e5b8a1d4f73';

    /** Deterministic line id for a legacy sale item, so a repeated backfill never creates a second line. */
    public static function lineId(string $saleItemId): string
    {
        return Uuid::uuid5(self::NAMESPACE, $saleItemId)->toString();
    }

    /** Gives every sale item without a line exactly one sale line. Returns how many lines were created. */
    public static function backfill(): int
    {
        $created = 0;
        $query = DB::table('sale_items as si')
            ->join('harvests as h', 'h.id', '=', 'si.harvest_id')
            ->join('planting_groups as pg', 'pg.id', '=', 'h.planting_group_id')
            ->join('varieties as v', 'v.id', '=', 'pg.variety_id')
            ->whereNull('si.sale_line_id')
            ->orderBy('si.id')
            ->select('si.id', 'si.sale_id', 'si.grade_id', 'si.grade_name', 'si.buyer_grade', 'si.quantity_kg', 'si.price_per_kg', 'si.exact_amount', 'si.active',
                'h.planting_group_id', 'v.name as variety_name');
        $now = now();
        $query->chunkById(500, function ($items) use (&$created, $now) {
            $lines = [];
            foreach ($items as $item) {
                $lines[] = ['id' => self::lineId($item->id), 'sale_id' => $item->sale_id, 'planting_group_id' => $item->planting_group_id, 'grade_id' => $item->grade_id,
                    'grade_name' => $item->grade_name, 'variety_name' => $item->variety_name, 'buyer_grade' => $item->buyer_grade, 'quantity_kg' => $item->quantity_kg,
                    'fruit_count' => null, 'price_per_kg' => $item->price_per_kg, 'exact_amount' => $item->exact_amount, 'active' => $item->active,
                    'created_at' => $now, 'updated_at' => $now];
            }
            DB::table('sale_lines')->insertOrIgnore($lines);
            foreach ($items as $item) {
                DB::table('sale_items')->where('id', $item->id)->update(['sale_line_id' => self::lineId($item->id)]);
            }
            $created += count($lines);
        }, 'si.id', 'id');

        return $created;
    }
}
