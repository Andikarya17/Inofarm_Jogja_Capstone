<?php

namespace Tests\Feature;

use Illuminate\Support\Str;
use Tests\FinanceTestCase;

class FinanceIncomeReportTest extends FinanceTestCase
{
    private function harvest(array $ctx, string $kg = '2000'): string
    {
        $item = (string) Str::uuid();
        $this->postJson('/api/v2/harvests', $this->operation('harvest', ['planting_group_id' => $ctx['group']['id'], 'occurred_at' => '2026-10-29T12:00:00+07:00', 'notes' => '',
            'items' => [['id' => $item, 'quality' => 'saleable', 'fruit_count' => null, 'weight_kg' => $kg]]]))->assertCreated();

        return $item;
    }

    private function sale(array $ctx, string $item, string $paidAt, string $kg, string $price, ?string $type): array
    {
        $payload = ['season_id' => $ctx['season']['id'], 'paid_at' => $paidAt, 'payment_status' => 'paid', 'customer_name' => '', 'notes' => '',
            'items' => [['id' => (string) Str::uuid(), 'harvest_item_id' => $item, 'quantity_kg' => $kg, 'price_per_kg' => $price]]];
        if ($type !== null) {
            $payload['customer_type'] = $type;
        }
        $op = $this->operation('sale', $payload);
        $this->postJson('/api/v2/sales', $op)->assertCreated();

        return $op;
    }

    private function income(string $granularity, string $from, string $until): array
    {
        return $this->getJson('/api/v2/reports/income?'.http_build_query(compact('granularity', 'from', 'until')))->assertOk()->json('data');
    }

    public function test_income_month_buckets_split_by_type(): void
    {
        $ctx = $this->context($this->account());
        $item = $this->harvest($ctx);
        $this->sale($ctx, $item, '2026-11-05T09:00:00+07:00', '180', '35000', 'consumer');
        $this->sale($ctx, $item, '2026-11-18T15:40:00+07:00', '728', '25000', 'partner');

        $nov = collect($this->income('month', '2026-10-01', '2026-11-30')['buckets'])->firstWhere('key', '2026-11');

        $this->assertSame('6300000.00', $nov['by_type']['consumer']['receipts']);
        $this->assertSame('18200000.00', $nov['by_type']['partner']['receipts']);
        $this->assertSame('24500000.00', $nov['receipts']);
        $this->assertSame('908.000000', $nov['weight_kg']);
        $this->assertSame('Nov', $nov['label']);
    }

    public function test_income_counts_untyped_sales_as_unspecified(): void
    {
        $ctx = $this->context($this->account());
        $this->sale($ctx, $this->harvest($ctx), '2026-11-05T09:00:00+07:00', '10', '30000', null);

        $nov = collect($this->income('month', '2026-11-01', '2026-11-30')['buckets'])->firstWhere('key', '2026-11');

        $this->assertSame('300000.00', $nov['by_type']['unspecified']['receipts']);
        $this->assertSame('0.00', $nov['by_type']['partner']['receipts']);
    }

    public function test_income_excludes_cancelled_sales(): void
    {
        $ctx = $this->context($this->account());
        $op = $this->sale($ctx, $this->harvest($ctx), '2026-11-05T09:00:00+07:00', '10', '30000', 'consumer');
        $this->postJson('/api/v2/sales/'.$op['entity_id'].'/cancel', $this->operation('sale', ['reason' => 'Batal sintetis'], 'cancel', $op['entity_id'], 1))->assertOk();

        $this->assertSame('0.00', $this->income('month', '2026-11-01', '2026-11-30')['totals']['receipts']);
    }

    public function test_week_bucket_starts_monday_wib(): void
    {
        $ctx = $this->context($this->account());
        $item = $this->harvest($ctx);
        $this->sale($ctx, $item, '2026-11-23T00:30:00+07:00', '1', '100', 'consumer');
        $this->sale($ctx, $item, '2026-11-22T23:59:00+07:00', '1', '200', 'consumer');

        $weeks = collect($this->income('week', '2026-11-16', '2026-11-29')['buckets'])->keyBy('key');

        $this->assertSame(['2026-11-16', '2026-11-23'], $weeks->keys()->all());
        $this->assertSame('200.00', $weeks['2026-11-16']['receipts']);
        $this->assertSame('100.00', $weeks['2026-11-23']['receipts']);
        $this->assertSame('23 Nov', $weeks['2026-11-23']['label']);
    }

    public function test_income_rejects_more_than_104_buckets(): void
    {
        $this->context($this->account());
        $this->getJson('/api/v2/reports/income?granularity=week&from=2024-01-01&until=2026-11-30')->assertUnprocessable();
    }

    public function test_income_by_type_has_retail(): void
    {
        $ctx = $this->context($this->account());
        $this->sale($ctx, $this->harvest($ctx), '2026-11-05T09:00:00+07:00', '10', '30000', 'retail');

        $report = $this->income('month', '2026-11-01', '2026-11-30');
        $nov = collect($report['buckets'])->firstWhere('key', '2026-11');

        $this->assertSame('300000.00', $nov['by_type']['retail']['receipts']);
        $this->assertSame('300000.00', $report['totals']['by_type']['retail']['receipts']);
    }

    public function test_income_worker_403(): void
    {
        $this->context($this->account());
        $this->asUser($this->account('worker'))->getJson('/api/v2/reports/income?granularity=month&from=2026-11-01&until=2026-11-30')->assertForbidden();
    }
}
