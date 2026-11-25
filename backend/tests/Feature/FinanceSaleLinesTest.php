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
        // Before migration 24 no line identity existed.
        DB::table('entity_identities')->where('entity_type', 'sale_line')->delete();
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

    /** Records one harvest line and returns [harvest id, harvest item id, grade id]. */
    private function harvest(array $ctx, string $day, string $kg, ?string $groupId = null, string $quality = 'saleable'): array
    {
        $item = (string) Str::uuid();
        $op = $this->operation('harvest', ['planting_group_id' => $groupId ?? $ctx['group']['id'], 'occurred_at' => $day.'T12:00:00+07:00', 'notes' => '',
            'items' => [['id' => $item, 'quality' => $quality, 'fruit_count' => null, 'weight_kg' => $kg]]]);
        $this->postJson('/api/v2/harvests', $op)->assertCreated();

        return [$op['entity_id'], $item, DB::table('harvest_items')->where('id', $item)->value('grade_id')];
    }

    private function line(string $groupId, string $gradeId, string $kg, string $price = '30000', array $extra = []): array
    {
        return ['id' => (string) Str::uuid(), 'planting_group_id' => $groupId, 'grade_id' => $gradeId, 'quantity_kg' => $kg, 'fruit_count' => null,
            'price_per_kg' => $price, 'buyer_grade' => null, ...$extra];
    }

    private function salePayload(array $ctx, array $lines, ?array $customer = null): array
    {
        return ['season_id' => $ctx['season']['id'], 'paid_at' => '2026-11-20T10:00:00+07:00', 'payment_status' => 'paid', 'customer_name' => '', 'notes' => '',
            ...($customer ? ['customer' => $customer] : []), 'lines' => $lines];
    }

    private function threeHarvests(): array
    {
        $ctx = $this->context($this->account());
        $first = $this->harvest($ctx, '2026-11-17', '10');
        $second = $this->harvest($ctx, '2026-11-18', '20');
        $third = $this->harvest($ctx, '2026-11-19', '30');

        return [$ctx, $first, $second, $third];
    }

    private function sold(string $itemId): string
    {
        return (string) DB::table('harvest_items')->where('id', $itemId)->value('sold_weight_kg');
    }

    public function test_line_allocates_fifo_across_three_harvests(): void
    {
        [$ctx, $first, $second, $third] = $this->threeHarvests();
        $data = $this->postJson('/api/v2/sales', $this->operation('sale', $this->salePayload($ctx, [$this->line($ctx['group']['id'], $first[2], '25')])))
            ->assertCreated()->json('data');

        $this->assertSame('10.000000', $this->sold($first[1]));
        $this->assertSame('15.000000', $this->sold($second[1]));
        $this->assertSame('0.000000', $this->sold($third[1]));
        $this->assertCount(1, $data['lines']);
        $this->assertCount(2, $data['lines'][0]['allocations']);
        $this->assertSame([$first[1], $second[1]], array_column($data['lines'][0]['allocations'], 'harvest_item_id'));
        $this->assertSame('saleable', $data['lines'][0]['quality']);
        $this->assertSame('Varietas sintetis', $data['lines'][0]['variety_name']);
        $this->assertCount(2, $data['items']);
        $this->assertSame('25.000000', $data['total_weight_kg']);
    }

    public function test_line_exceeding_stock_rejected_with_available(): void
    {
        [$ctx, $first] = $this->threeHarvests();
        $line = $this->line($ctx['group']['id'], $first[2], '61');
        $this->postJson('/api/v2/sales', $this->operation('sale', $this->salePayload($ctx, [$line])))
            ->assertUnprocessable()->assertJsonPath('code', 'harvest_exceeded')
            ->assertJsonPath('reasons.0.line_id', $line['id'])->assertJsonPath('reasons.0.available_kg', '60.000000');
        $this->assertSame(0, DB::table('sales')->count());
    }

    public function test_update_reallocates_and_releases(): void
    {
        [$ctx, $first, $second] = $this->threeHarvests();
        $op = $this->operation('sale', $this->salePayload($ctx, [$this->line($ctx['group']['id'], $first[2], '25')]));
        $this->postJson('/api/v2/sales', $op)->assertCreated();
        $update = $this->operation('sale', [...$op['payload'], 'reason' => 'Koreksi bobot'], 'update', $op['entity_id'], 1);
        $update['payload']['lines'][0]['quantity_kg'] = '5';
        $data = $this->patchJson('/api/v2/sales/'.$op['entity_id'], $update)->assertOk()->json('data');

        $this->assertSame('5.000000', $this->sold($first[1]));
        $this->assertSame('0.000000', $this->sold($second[1]));
        $this->assertCount(1, $data['lines'][0]['allocations']);
        $this->assertCount(1, $data['items']);
        $this->assertSame(1, DB::table('sale_items')->where('sale_id', $op['entity_id'])->where('active', true)->count());
    }

    public function test_cancelled_harvest_excluded_from_fifo(): void
    {
        [$ctx, $first, $second] = $this->threeHarvests();
        $this->postJson('/api/v2/harvests/'.$first[0].'/cancel', $this->operation('harvest', ['reason' => 'Batal uji'], 'cancel', $first[0], 1))->assertOk();
        $this->postJson('/api/v2/sales', $this->operation('sale', $this->salePayload($ctx, [$this->line($ctx['group']['id'], $first[2], '15')])))->assertCreated();

        $this->assertSame('0.000000', $this->sold($first[1]));
        $this->assertSame('15.000000', $this->sold($second[1]));
    }

    public function test_correcting_backfilled_sale_keeps_total(): void
    {
        $saleId = $this->saleWithTwoItems();
        $this->wipeLines();
        SaleLineMigration::backfill();
        $before = $this->getJson('/api/v2/sales/'.$saleId)->assertOk()->json();
        $lines = array_map(fn ($line) => ['id' => $line['id'], 'planting_group_id' => $line['planting_group_id'], 'grade_id' => $line['grade_id'],
            'quantity_kg' => $line['quantity_kg'], 'fruit_count' => $line['fruit_count'], 'price_per_kg' => '31000', 'buyer_grade' => $line['buyer_grade']], $before['lines']);
        $payload = ['season_id' => $before['season_id'], 'paid_at' => '2026-11-18T15:40:00+07:00', 'payment_status' => 'paid', 'customer_name' => 'Budi', 'notes' => '',
            'lines' => $lines, 'reason' => 'Harga dikoreksi'];
        $after = $this->patchJson('/api/v2/sales/'.$saleId, $this->operation('sale', $payload, 'update', $saleId, 1))->assertOk()->json('data');

        $this->assertSame($before['total_weight_kg'], $after['total_weight_kg']);
        $this->assertSame('149823.00', $after['total_amount']);
        $this->assertSame(0, DB::table('harvest_items')->where(fn ($q) => $q->whereColumn('sold_weight_kg', '>', 'weight_kg')->orWhere('sold_weight_kg', '<', 0))->count());
        $this->assertSame('4.833000', (string) DB::table('harvest_items')->sum('sold_weight_kg'));
        $this->assertSame(collect($before['lines'])->pluck('id')->sort()->values()->all(), collect($after['lines'])->pluck('id')->sort()->values()->all());
    }

    public function test_legacy_items_payload_still_accepted(): void
    {
        $saleId = $this->saleWithTwoItems();
        $data = $this->getJson('/api/v2/sales/'.$saleId)->assertOk()->json();

        $this->assertCount(2, $data['items']);
        $this->assertCount(2, $data['lines']);
        $this->assertSame(2, DB::table('sale_lines')->where('sale_id', $saleId)->where('active', true)->count());
        foreach ($data['items'] as $item) {
            $this->assertSame(SaleLineMigration::lineId($item['id']), $item['sale_line_id']);
        }
        $this->assertSame(['defective', 'saleable'], collect($data['lines'])->pluck('quality')->sort()->values()->all());
    }

    public function test_retail_sale_with_fruit_count(): void
    {
        $ctx = $this->context($this->account());
        $golden = $this->createMaster('varieties', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Golden']);
        $inthanon = $this->createMaster('varieties', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Inthanon']);
        $groups = array_map(fn ($variety) => $this->createMaster('planting_groups', ['season_id' => $ctx['season']['id'], 'variety_id' => $variety['id'],
            'name' => 'Kelompok '.$variety['name'], 'population' => 100, 'sowing_date' => '2026-10-01', 'transplant_date' => '2026-10-10'])['id'], [$golden, $inthanon]);
        $a = $this->harvest($ctx, '2026-11-17', '50', $groups[0]);
        $b = $this->harvest($ctx, '2026-11-17', '20', $groups[1]);
        $data = $this->postJson('/api/v2/sales', $this->operation('sale', $this->salePayload($ctx, [
            $this->line($groups[0], $a[2], '50', '28000', ['fruit_count' => 38]),
            $this->line($groups[1], $b[2], '20', '26000', ['fruit_count' => 16]),
        ], ['id' => (string) Str::uuid(), 'type' => 'retail', 'name' => 'Toko Sari', 'whatsapp' => null])))->assertCreated()->json('data');

        $this->assertSame('1920000.00', $data['total_amount']);
        $this->assertSame('retail', $data['customer_type']);
        $this->assertSame([16, 38], collect($data['lines'])->pluck('fruit_count')->sort()->values()->all());
        $this->assertSame(['Golden', 'Inthanon'], collect($data['lines'])->pluck('variety_name')->sort()->values()->all());
    }

    public function test_duplicate_line_key_rejected(): void
    {
        [$ctx, $first] = $this->threeHarvests();
        $this->postJson('/api/v2/sales', $this->operation('sale', $this->salePayload($ctx, [
            $this->line($ctx['group']['id'], $first[2], '1'), $this->line($ctx['group']['id'], $first[2], '2'),
        ])))->assertUnprocessable()->assertJsonPath('message', 'Baris dengan varietas, kualitas dan grade mitra yang sama tidak boleh berulang dalam satu penjualan.');
        $this->assertSame(0, DB::table('sales')->count());
    }

    public function test_buyer_grade_rejected_for_retail(): void
    {
        [$ctx, $first] = $this->threeHarvests();
        $this->postJson('/api/v2/sales', $this->operation('sale', $this->salePayload($ctx, [$this->line($ctx['group']['id'], $first[2], '1', '30000', ['buyer_grade' => 'A'])],
            ['id' => (string) Str::uuid(), 'type' => 'retail', 'name' => 'Toko Sari', 'whatsapp' => null])))->assertUnprocessable()->assertJsonPath('message', 'Grade A, B, C hanya untuk penjualan ke mitra.');
        $this->assertSame(0, DB::table('sales')->count());
    }

    public function test_fruit_count_zero_rejected(): void
    {
        [$ctx, $first] = $this->threeHarvests();
        $this->postJson('/api/v2/sales', $this->operation('sale', $this->salePayload($ctx, [$this->line($ctx['group']['id'], $first[2], '1', '30000', ['fruit_count' => 0])])))
            ->assertUnprocessable()->assertJsonValidationErrors(['operation.payload.lines.0.fruit_count'], 'field_errors');
    }

    public function test_line_group_outside_sale_season_rejected(): void
    {
        [$ctx, $first] = $this->threeHarvests();
        $otherSeason = $this->createMaster('seasons', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Musim lain', 'start_date' => '2026-10-01', 'end_date' => null]);
        $otherGroup = $this->createMaster('planting_groups', ['season_id' => $otherSeason['id'], 'variety_id' => $ctx['variety']['id'],
            'name' => 'Kelompok lain', 'population' => 100, 'sowing_date' => '2026-10-01', 'transplant_date' => null]);
        $this->harvest($ctx, '2026-11-17', '10', $otherGroup['id']);
        $this->postJson('/api/v2/sales', $this->operation('sale', $this->salePayload($ctx, [$this->line($otherGroup['id'], $first[2], '1')])))
            ->assertUnprocessable()->assertJsonPath('message', 'Seluruh baris harus berasal dari kelompok tanam di musim penjualan yang sama.');
        $this->assertSame(0, DB::table('sales')->count());
    }

    public function test_lines_and_items_together_rejected(): void
    {
        [$ctx, $first] = $this->threeHarvests();
        $payload = $this->salePayload($ctx, [$this->line($ctx['group']['id'], $first[2], '1')]);
        $payload['items'] = [['id' => (string) Str::uuid(), 'harvest_item_id' => $first[1], 'quantity_kg' => '1', 'price_per_kg' => '1']];
        $this->postJson('/api/v2/sales', $this->operation('sale', $payload))->assertUnprocessable()->assertJsonValidationErrors(['operation.payload.items'], 'field_errors');
    }

    public function test_lines_replay_returns_same_receipt(): void
    {
        [$ctx, $first] = $this->threeHarvests();
        $op = $this->operation('sale', $this->salePayload($ctx, [$this->line($ctx['group']['id'], $first[2], '12')]));
        $receipt = $this->postJson('/api/v2/sales', $op)->assertCreated()->json();
        $this->postJson('/api/v2/sales', $op)->assertCreated()->assertExactJson($receipt);
        $this->getJson('/api/v2/sales/'.$op['entity_id'])->assertExactJson($receipt['data']);
        $this->assertSame('12.000000', (string) DB::table('harvest_items')->sum('sold_weight_kg'));
    }
}
