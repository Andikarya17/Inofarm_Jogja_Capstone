<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;

/** Inofarm 20-21 Nov 2026: a mitra grades the fruit A/B/C when buying; each grade is sold per kg from the Layak line. */
class FinanceBuyerGradeTest extends FinanceTestCase
{
    private function setUpSale(string $kg = '200'): array
    {
        $ctx = $this->context($this->account());
        $item = (string) Str::uuid();
        $this->postJson('/api/v2/harvests', $this->operation('harvest', ['planting_group_id' => $ctx['group']['id'], 'occurred_at' => '2026-10-29T12:00:00+07:00', 'notes' => '',
            'items' => [['id' => $item, 'quality' => 'saleable', 'fruit_count' => null, 'weight_kg' => $kg]]]))->assertCreated();

        return [$ctx, $item];
    }

    private function sale(array $ctx, array $lines, string $type = 'partner')
    {
        return $this->postJson('/api/v2/sales', $this->operation('sale', ['season_id' => $ctx['season']['id'], 'paid_at' => '2026-11-18T15:40:00+07:00',
            'payment_status' => 'paid', 'customer_name' => '', 'notes' => '',
            'customer' => ['id' => (string) Str::uuid(), 'type' => $type, 'name' => 'Mitra 1', 'whatsapp' => null],
            'items' => array_map(fn ($line) => ['id' => (string) Str::uuid(), ...$line], $lines)]));
    }

    public function test_partner_sale_three_grades_same_harvest_item(): void
    {
        [$ctx, $item] = $this->setUpSale();
        $data = $this->sale($ctx, [
            ['harvest_item_id' => $item, 'quantity_kg' => '60', 'price_per_kg' => '30000', 'buyer_grade' => 'A'],
            ['harvest_item_id' => $item, 'quantity_kg' => '50', 'price_per_kg' => '24000', 'buyer_grade' => 'B'],
            ['harvest_item_id' => $item, 'quantity_kg' => '25', 'price_per_kg' => '18000', 'buyer_grade' => 'C'],
        ])->assertCreated()->json('data');

        $this->assertSame('3450000.00', $data['total_amount']);
        $this->assertSame(['A', 'B', 'C'], collect($data['items'])->sortBy('buyer_grade')->pluck('buyer_grade')->values()->all());
        $this->assertSame('135.000000', (string) DB::table('harvest_items')->where('id', $item)->value('sold_weight_kg'));
    }

    public function test_buyer_grade_rejected_for_consumer(): void
    {
        [$ctx, $item] = $this->setUpSale();
        $this->sale($ctx, [['harvest_item_id' => $item, 'quantity_kg' => '1', 'price_per_kg' => '30000', 'buyer_grade' => 'A']], 'consumer')->assertUnprocessable();
    }

    public function test_duplicate_grade_for_same_item_rejected(): void
    {
        [$ctx, $item] = $this->setUpSale();
        $this->sale($ctx, [['harvest_item_id' => $item, 'quantity_kg' => '1', 'price_per_kg' => '1', 'buyer_grade' => 'A'],
            ['harvest_item_id' => $item, 'quantity_kg' => '2', 'price_per_kg' => '1', 'buyer_grade' => 'A']])->assertUnprocessable();
    }

    public function test_grade_lines_cannot_exceed_balance(): void
    {
        [$ctx, $item] = $this->setUpSale('100');
        $this->sale($ctx, [['harvest_item_id' => $item, 'quantity_kg' => '60', 'price_per_kg' => '1', 'buyer_grade' => 'A'],
            ['harvest_item_id' => $item, 'quantity_kg' => '50', 'price_per_kg' => '1', 'buyer_grade' => 'B']])->assertStatus(409)->assertJsonPath('code', 'harvest_exceeded');
    }
}
