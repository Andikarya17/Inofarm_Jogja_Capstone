<?php

namespace Tests\Feature;

use App\Domain\Finance\ConsumerPriceService;
use Illuminate\Support\Facades\DB;
use Tests\FinanceTestCase;

class FinanceConsumerPriceTest extends FinanceTestCase
{
    private function create(string $name, string $quality = 'saleable', string $price = '25000.000000')
    {
        return $this->postJson('/api/v2/consumer-prices', $this->operation('consumer_price', ['variety_name' => $name, 'quality' => $quality, 'price_per_kg' => $price]));
    }

    public function test_manager_creates_and_lists_price(): void
    {
        $this->asUser($this->account());
        $row = $this->create('Golden')->assertCreated()->json('data');

        $this->assertSame(['id', 'variety_name', 'quality', 'price_per_kg', 'version', 'record_state', 'updated_at'], array_keys($row));
        $this->assertSame('25000.000000', $row['price_per_kg']);
        $list = $this->getJson('/api/v2/consumer-prices')->assertOk()->json('data');
        $this->assertCount(1, $list);
        $this->assertSame($row['id'], $list[0]['id']);
    }

    public function test_variety_key_normalized(): void
    {
        $this->assertSame('golden melon', ConsumerPriceService::key("  GOLDEN   Melon "));
        $this->asUser($this->account());
        $this->create('Golden')->assertCreated();
        $this->create(' GOLDEN ')->assertStatus(409)->assertJsonPath('code', 'duplicate');
        $this->create(' GOLDEN ', 'defective')->assertCreated();
    }

    public function test_update_bumps_version_and_audits(): void
    {
        $this->asUser($this->account());
        $row = $this->create('Golden')->assertCreated()->json('data');
        $payload = ['variety_name' => 'Golden', 'quality' => 'saleable', 'price_per_kg' => '27500.500000'];
        $saved = $this->patchJson('/api/v2/consumer-prices/'.$row['id'], $this->operation('consumer_price', $payload, 'update', $row['id'], 1))->assertOk()->json('data');

        $this->assertSame(2, $saved['version']);
        $this->assertSame('27500.500000', $saved['price_per_kg']);
        $this->assertSame(2, DB::table('audit_logs')->where('entity_type', 'consumer_price')->where('entity_id', $row['id'])->count());
        $this->patchJson('/api/v2/consumer-prices/'.$row['id'], $this->operation('consumer_price', $payload, 'update', $row['id'], 1))->assertStatus(409);
        $this->assertSame(0, DB::table('change_feed')->where('entity_type', 'consumer_price')->count());
    }

    public function test_deactivate_then_create_same_key_allowed(): void
    {
        $this->asUser($this->account());
        $row = $this->create('Golden')->assertCreated()->json('data');
        $off = $this->patchJson('/api/v2/consumer-prices/'.$row['id'], $this->operation('consumer_price',
            ['variety_name' => 'Golden', 'quality' => 'saleable', 'price_per_kg' => '25000.000000', 'record_state' => 'inactive'], 'update', $row['id'], 1))->assertOk()->json('data');

        $this->assertSame('inactive', $off['record_state']);
        $this->assertCount(0, $this->getJson('/api/v2/consumer-prices')->json('data'));
        $this->assertCount(1, $this->getJson('/api/v2/consumer-prices?all=1')->json('data'));
        $this->create('golden')->assertCreated();
    }

    public function test_worker_forbidden(): void
    {
        $this->asUser($this->account('worker'));
        $this->getJson('/api/v2/consumer-prices')->assertForbidden();
        $this->create('Golden')->assertForbidden();
    }

    public function test_negative_price_rejected(): void
    {
        $this->asUser($this->account());
        $this->create('Golden', 'saleable', '-1')->assertUnprocessable();
        $this->create('Golden', 'saleable', '-1.000000')->assertUnprocessable();
        $this->create('Golden', 'grade_a')->assertUnprocessable();
    }
}
