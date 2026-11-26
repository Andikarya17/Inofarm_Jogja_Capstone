<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;

class FinanceSeasonOverviewTest extends FinanceTestCase
{
    private function harvest(array $ctx, string $kg = '2000'): string
    {
        $item = (string) Str::uuid();
        $this->postJson('/api/v2/harvests', $this->operation('harvest', ['planting_group_id' => $ctx['group']['id'], 'occurred_at' => '2026-10-29T12:00:00+07:00', 'notes' => '',
            'items' => [['id' => $item, 'quality' => 'saleable', 'fruit_count' => null, 'weight_kg' => $kg]]]))->assertCreated();

        return $item;
    }

    private function sale(array $ctx, string $paidAt, string $kg, string $price, ?string $type): void
    {
        $payload = ['season_id' => $ctx['season']['id'], 'paid_at' => $paidAt, 'payment_status' => 'paid', 'customer_name' => '', 'notes' => '',
            'items' => [['id' => (string) Str::uuid(), 'harvest_item_id' => $this->harvest($ctx), 'quantity_kg' => $kg, 'price_per_kg' => $price]]];
        if ($type !== null) {
            $payload['customer_type'] = $type;
        }
        $this->postJson('/api/v2/sales', $this->operation('sale', $payload))->assertCreated();
    }

    private function overview(string $query = ''): array
    {
        return $this->getJson('/api/v2/reports/season-overview'.$query)->assertOk()->json('data');
    }

    public function test_overview_farm_cost_is_sheet_times_greenhouses(): void
    {
        $manager = $this->account();
        $this->context($manager);
        $this->context($manager);
        $this->standardCosts('7581100.00', '3430000.00');

        $sheet = $this->overview('?sequence=1')['selected']['cost_sheet'];

        $this->assertSame('11011100.00', $sheet['per_greenhouse']['tc']);
        $this->assertSame('22022200.00', $sheet['farm']['tc']);
        $this->assertSame('15162200.00', $sheet['farm']['fc']);
        $this->assertSame('6860000.00', $sheet['farm']['vc']);
    }

    public function test_overview_costs_null_when_line_blank(): void
    {
        $manager = $this->account();
        $this->context($manager);

        $data = $this->overview();

        $this->assertNull($data['selected']['cost_sheet']['farm']['tc']);
        $this->assertNull($data['selected']['cost_sheet']['per_greenhouse']['fc']);
        $this->assertNull($data['sequences'][0]['tc']);
    }

    public function test_overview_sales_by_type_includes_retail(): void
    {
        $ctx = $this->context($this->account());
        $this->sale($ctx, '2026-11-05T09:00:00+07:00', '10', '30000', 'consumer');
        $this->sale($ctx, '2026-11-06T09:00:00+07:00', '20', '25000', 'retail');
        $this->sale($ctx, '2026-11-07T09:00:00+07:00', '30', '20000', 'partner');
        $this->sale($ctx, '2026-11-08T09:00:00+07:00', '5', '10000', null);

        $sales = $this->overview()['selected']['sales'];

        $this->assertSame(['consumer', 'retail', 'partner', 'unspecified'], array_keys($sales['by_type']));
        $this->assertSame(['amount' => '500000.00', 'kg' => '20.000000', 'count' => 1], $sales['by_type']['retail']);
        $this->assertSame('1450000.00', $sales['amount']);
        $this->assertSame('65.000000', $sales['kg']);
        $this->assertSame(4, $sales['count']);
    }

    public function test_overview_counts_only_sequence_sales(): void
    {
        $manager = $this->account();
        $a = $this->context($manager);
        $b = $this->context($manager);
        DB::table('seasons')->where('id', $b['season']['id'])->update(['sequence' => 2]);
        $this->sale($a, '2026-11-05T09:00:00+07:00', '10', '30000', 'consumer');
        $this->sale($b, '2026-11-06T09:00:00+07:00', '5', '20000', 'consumer');

        $one = $this->overview('?sequence=1')['selected'];
        $two = $this->overview('?sequence=2')['selected'];

        $this->assertSame('300000.00', $one['sales']['amount']);
        $this->assertSame('100000.00', $two['sales']['amount']);
        $this->assertCount(1, $two['by_greenhouse']);
        $this->assertSame('100000.00', $two['by_greenhouse'][0]['amount']);
    }

    public function test_overview_weekly_buckets_monday_wib(): void
    {
        $ctx = $this->context($this->account());
        $this->sale($ctx, '2026-11-22T23:30:00+07:00', '1', '200', 'consumer');
        $this->sale($ctx, '2026-11-23T00:30:00+07:00', '1', '100', 'retail');

        $weekly = $this->overview()['selected']['weekly'];

        $this->assertSame('2026-11-16', $weekly[0]['key']);
        $this->assertSame('200.00', $weekly[0]['amount']);
        $this->assertSame('100.00', $weekly[1]['by_type']['retail']['amount']);
        $this->assertSame('2026-11-23', $weekly[1]['start']);
    }

    public function test_sequences_list_has_status_and_totals(): void
    {
        $manager = $this->account();
        $a = $this->context($manager);
        $b = $this->context($manager);
        DB::table('seasons')->where('id', $b['season']['id'])->update(['sequence' => 2, 'status' => 'closed']);
        $this->standardCosts('7581100.00', '3430000.00');
        $this->sale($a, '2026-11-05T09:00:00+07:00', '10', '30000', 'consumer');

        $list = collect($this->overview()['sequences'])->keyBy('sequence');

        $this->assertSame('running', $list[1]['status']);
        $this->assertSame('closed', $list[2]['status']);
        $this->assertSame(1, $list[1]['greenhouse_count']);
        $this->assertSame('11011100.00', $list[1]['tc']);
        $this->assertSame('300000.00', $list[1]['sales_amount']);
        $this->assertSame('10.000000', $list[1]['sales_kg']);
        $this->assertSame(1, $this->overview()['selected']['sequence']);
    }

    public function test_unknown_sequence_404(): void
    {
        $this->context($this->account());
        $this->getJson('/api/v2/reports/season-overview?sequence=9')->assertNotFound();
    }

    public function test_profit_summary_route_removed(): void
    {
        $this->context($this->account());
        $this->getJson('/api/v2/reports/profit-summary')->assertNotFound();
    }

    public function test_worker_forbidden_overview(): void
    {
        $this->context($this->account());
        $this->asUser($this->account('worker'))->getJson('/api/v2/reports/season-overview')->assertForbidden();
    }
}
