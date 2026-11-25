<?php

namespace Tests\Feature;

use App\Domain\Finance\SaleLineMigration;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;

/** Migration 24: every sale item gets one sale line; retail buyers are accepted. */
class FinanceSaleLinesTest extends FinanceTestCase
{
    private function saleWithTwoItems(): string
    {
        $ctx = $this->context($this->account());
        $item = (string) Str::uuid();
        $second = (string) Str::uuid();
        $this->postJson('/api/v2/harvests', $this->operation('harvest', ['planting_group_id' => $ctx['group']['id'], 'occurred_at' => '2026-10-29T12:00:00+07:00', 'notes' => '',
            'items' => [['id' => $item, 'quality' => 'saleable', 'fruit_count' => null, 'weight_kg' => '200'], ['id' => $second, 'quality' => 'defective', 'fruit_count' => null, 'weight_kg' => '20']]]))->assertCreated();
        $sale = $this->postJson('/api/v2/sales', $this->operation('sale', ['season_id' => $ctx['season']['id'], 'paid_at' => '2026-11-18T15:40:00+07:00',
            'payment_status' => 'paid', 'customer_name' => 'Budi', 'notes' => '',
            'items' => [
                ['id' => (string) Str::uuid(), 'harvest_item_id' => $item, 'quantity_kg' => '3.333', 'price_per_kg' => '30001'],
                ['id' => (string) Str::uuid(), 'harvest_item_id' => $second, 'quantity_kg' => '1.5', 'price_per_kg' => '25000'],
            ]]))->assertCreated()->json('data');

        return $sale['id'];
    }

    private function wipeLines(): void
    {
        DB::table('sale_items')->update(['sale_line_id' => null]);
        DB::table('sale_lines')->delete();
    }

    public function test_backfill_creates_one_line_per_item_and_keeps_totals(): void
    {
        $saleId = $this->saleWithTwoItems();
        $this->wipeLines();

        $this->assertSame(2, SaleLineMigration::backfill());

        $this->assertSame(2, DB::table('sale_lines')->where('sale_id', $saleId)->count());
        $this->assertSame(0, DB::table('sale_items')->whereNull('sale_line_id')->count());
        $sum = DB::table('sale_lines')->where('sale_id', $saleId)->sum('exact_amount');
        $this->assertSame(0, bccomp((string) $sum, (string) DB::table('sales')->where('id', $saleId)->value('exact_total'), 6));
        $line = DB::table('sale_lines')->where('sale_id', $saleId)->orderBy('quantity_kg')->first();
        $this->assertNotEmpty($line->variety_name);
        $this->assertTrue((bool) $line->active);
    }

    public function test_backfill_is_idempotent(): void
    {
        $this->saleWithTwoItems();
        $this->wipeLines();
        SaleLineMigration::backfill();
        $this->assertSame(0, SaleLineMigration::backfill());
    }

    public function test_customers_accept_retail(): void
    {
        $this->context($this->account());
        $this->postJson('/api/v2/customers/import', ['dry_run' => false, 'type' => 'retail', 'rows' => [['row' => 2, 'name' => 'Toko Sari', 'whatsapp' => '081234567890']]])->assertOk();
        $this->assertSame(1, DB::table('customers')->where('type', 'retail')->count());
    }
}
