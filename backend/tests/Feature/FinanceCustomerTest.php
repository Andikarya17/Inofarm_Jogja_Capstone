<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;

class FinanceCustomerTest extends FinanceTestCase
{
    private function import(array $rows, bool $dryRun = false)
    {
        return $this->postJson('/api/v2/customers/import', ['dry_run' => $dryRun, 'type' => 'consumer', 'rows' => $rows]);
    }

    private function saleWithBuyer(array $ctx, array $customer): array
    {
        $item = (string) Str::uuid();
        $this->postJson('/api/v2/harvests', $this->operation('harvest', ['planting_group_id' => $ctx['group']['id'], 'occurred_at' => '2026-10-29T12:00:00+07:00', 'notes' => '',
            'items' => [['id' => $item, 'quality' => 'saleable', 'fruit_count' => null, 'weight_kg' => '100']]]))->assertCreated();
        $op = $this->operation('sale', ['season_id' => $ctx['season']['id'], 'paid_at' => '2026-11-05T09:00:00+07:00', 'payment_status' => 'paid',
            'customer_name' => '', 'notes' => '', 'customer' => $customer,
            'items' => [['id' => (string) Str::uuid(), 'harvest_item_id' => $item, 'quantity_kg' => '3.2', 'price_per_kg' => '30000']]]);

        return $this->postJson('/api/v2/sales', $op)->assertCreated()->json('data');
    }

    public function test_import_dedupes_whatsapp_formats(): void
    {
        $this->context($this->account());
        $result = $this->import([['row' => 2, 'name' => 'Pembeli A', 'whatsapp' => '0812-3456 7890'],
            ['row' => 3, 'name' => 'Pembeli A lagi', 'whatsapp' => '+62 812 3456 7890'], ['row' => 4, 'name' => 'Pembeli A ketiga', 'whatsapp' => '6281234567890']])
            ->assertOk()->json();

        $this->assertSame(['created' => 1, 'existing' => 2, 'invalid' => 0], $result['summary']);
        $this->assertSame(['created', 'existing', 'existing'], array_column($result['rows'], 'status'));
        $this->assertSame('6281234567890', DB::table('customers')->value('whatsapp'));
    }

    public function test_import_dry_run_writes_nothing(): void
    {
        $this->context($this->account());
        $this->import([['row' => 2, 'name' => 'Pembeli A', 'whatsapp' => '081234567890']], true)->assertOk()->assertJsonPath('summary.created', 1);
        $this->assertSame(0, DB::table('customers')->count());
    }

    public function test_import_blank_name_invalid(): void
    {
        $this->context($this->account());
        $result = $this->import([['row' => 2, 'name' => ' ', 'whatsapp' => '081234567890']])->assertOk()->json();
        $this->assertSame('invalid', $result['rows'][0]['status']);
        $this->assertSame(0, DB::table('customers')->count());
    }

    public function test_import_without_number_creates_customer(): void
    {
        $this->context($this->account());
        $this->import([['row' => 2, 'name' => 'Tanpa nomor', 'whatsapp' => null], ['row' => 3, 'name' => 'Nomor rusak', 'whatsapp' => '0812-34']])
            ->assertOk()->assertJsonPath('summary.created', 1)->assertJsonPath('summary.invalid', 1);
    }

    public function test_sale_creates_new_customer_inline(): void
    {
        $ctx = $this->context($this->account());
        $id = (string) Str::uuid();
        $sale = $this->saleWithBuyer($ctx, ['id' => $id, 'type' => 'consumer', 'name' => 'Pembeli baru', 'whatsapp' => '0812 1111 2222']);

        $this->assertSame($id, $sale['customer_id']);
        $this->assertSame('consumer', $sale['customer_type']);
        $this->assertSame('Pembeli baru', $sale['customer_name']);
        $this->assertSame('6281211112222', DB::table('customers')->where('id', $id)->value('whatsapp'));
    }

    public function test_sale_reuses_existing_customer(): void
    {
        $ctx = $this->context($this->account());
        $existing = $this->import([['row' => 2, 'name' => 'Pembeli lama', 'whatsapp' => '081233334444']])->json('rows.0.customer_id');
        $sale = $this->saleWithBuyer($ctx, ['id' => $existing, 'type' => 'consumer', 'name' => 'Nama dari HP', 'whatsapp' => null]);

        $this->assertSame($existing, $sale['customer_id']);
        $this->assertSame('Pembeli lama', $sale['customer_name']);
        $this->assertSame(1, DB::table('customers')->count());
    }

    public function test_customer_search_by_name_and_digits(): void
    {
        $ctx = $this->context($this->account());
        $this->import([['row' => 2, 'name' => 'Bu Sari', 'whatsapp' => '081233334444'], ['row' => 3, 'name' => 'Pak Joko', 'whatsapp' => '081255556666']]);
        $this->saleWithBuyer($ctx, ['id' => $this->getJson('/api/v2/customers?q=sari')->json('data.0.id'), 'type' => 'consumer', 'name' => 'x', 'whatsapp' => null]);

        $byName = $this->getJson('/api/v2/customers?type=consumer&q=sari')->assertOk()->json('data');
        $this->assertSame(['Bu Sari'], array_column($byName, 'name'));
        $this->assertSame(1, $byName[0]['purchase_count']);
        $this->assertSame('3.200000', $byName[0]['total_kg']);
        $this->assertSame(['Pak Joko'], array_column($this->getJson('/api/v2/customers?q=5555')->json('data'), 'name'));
    }

    public function test_worker_cannot_read_customers(): void
    {
        $this->context($this->account());
        $this->asUser($this->account('worker'));
        $this->getJson('/api/v2/customers')->assertForbidden();
        $this->import([['row' => 2, 'name' => 'x', 'whatsapp' => null]])->assertForbidden();
    }
}
