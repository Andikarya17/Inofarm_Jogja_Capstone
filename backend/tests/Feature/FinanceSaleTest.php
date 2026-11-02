<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Illuminate\Database\Events\QueryExecuted;
use Tests\FinanceTestCase;

class FinanceSaleTest extends FinanceTestCase
{
    private function prepared(): array
    {
        $actor = $this->account();$ctx = $this->context($actor);$grade = $this->createMaster('grades', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Grade sintetis']);
        $harvest = $this->operation('harvest', ['planting_group_id' => $ctx['group']['id'], 'occurred_at' => '2026-10-30T08:00:00+07:00', 'notes' => 'Panen sumber sintetis',
            'items' => [['id' => (string) Str::uuid(), 'grade_id' => $grade['id'], 'fruit_count' => 3, 'weight_kg' => '5']]]);
        $this->postJson('/api/v2/harvests', $harvest)->assertCreated();
        return [...$ctx, 'actor' => $actor, 'harvest' => $harvest, 'source' => $harvest['payload']['items'][0]['id']];
    }
    private function sale(array $ctx, string $quantity = '3', string $price = '10000'): array
    {
        return $this->operation('sale', ['season_id' => $ctx['season']['id'], 'paid_at' => '2026-10-30T09:00:00+07:00', 'payment_status' => 'paid', 'customer_name' => '', 'notes' => 'Lunas sintetis',
            'items' => [['id' => (string) Str::uuid(), 'harvest_item_id' => $ctx['source'], 'quantity_kg' => $quantity, 'price_per_kg' => $price]]]);
    }
    public function test_paid_sale_replay_oversell_cancel_and_harvest_source_guard(): void
    {
        $ctx = $this->prepared();$op = $this->sale($ctx);
        $receipt = $this->postJson('/api/v2/sales', $op)->assertCreated()->assertJsonPath('data.total_amount', '30000.00')->assertJsonPath('data.total_weight_kg', '3.000000')->json();
        $this->postJson('/api/v2/sales', $op)->assertCreated()->assertExactJson($receipt);
        $this->assertDatabaseHas('harvest_items', ['id' => $ctx['source'], 'sold_weight_kg' => '3.000000', 'balance_version' => 2]);
        $this->getJson('/api/v2/sales/'.$op['entity_id'])->assertExactJson($receipt['data']);
        $this->postJson('/api/v2/sales', $this->sale($ctx))->assertConflict()->assertJsonPath('code', 'harvest_exceeded');
        $correction = $this->operation('harvest', [...$ctx['harvest']['payload'], 'reason' => 'Koreksi sintetis', 'reference_policy' => 'preserve'], 'update', $ctx['harvest']['entity_id'], 1);
        $correction['payload']['items'][0]['weight_kg'] = '2';
        $this->patchJson('/api/v2/harvests/'.$ctx['harvest']['entity_id'], $correction)->assertConflict()->assertJsonPath('code', 'harvest_in_use');
        $cancel = $this->operation('sale', ['reason' => 'Salah catat sintetis'], 'cancel', $op['entity_id'], 1);
        $this->postJson('/api/v2/sales/'.$op['entity_id'].'/cancel', $cancel)->assertOk()->assertJsonPath('data.record_state', 'cancelled')->assertJsonCount(1, 'data.items');
        $this->assertDatabaseHas('harvest_items', ['id' => $ctx['source'], 'sold_weight_kg' => '0.000000', 'balance_version' => 3]);
        $this->patchJson('/api/v2/harvests/'.$ctx['harvest']['entity_id'], $correction)->assertOk();
        $this->postJson('/api/v2/sales', $op)->assertExactJson($receipt);
        $this->getJson('/api/v2/sales/'.$op['entity_id'].'/audit')->assertJsonCount(2, 'data');
        $this->assertSame(2, DB::table('sync_operations')->where('entity_id', $op['entity_id'])->count());
    }
    public function test_sale_update_changes_only_own_usage_and_rejects_stale_version(): void
    {
        $ctx = $this->prepared();$op = $this->sale($ctx);$this->postJson('/api/v2/sales', $op)->assertCreated();
        $update = $this->operation('sale', [...$op['payload'], 'reason' => 'Koreksi bobot'], 'update', $op['entity_id'], 1);
        $update['payload']['items'][0]['quantity_kg'] = '4';$update['payload']['items'][0]['price_per_kg'] = '0.00125';
        $this->patchJson('/api/v2/sales/'.$op['entity_id'], $update)->assertOk()->assertJsonPath('data.total_amount', '0.01');
        $this->assertDatabaseHas('harvest_items', ['id' => $ctx['source'], 'sold_weight_kg' => '4.000000']);
        $stale = $update;$stale['operation_id'] = (string) Str::uuid();
        $this->patchJson('/api/v2/sales/'.$op['entity_id'], $stale)->assertConflict()->assertJsonPath('code', 'version_conflict');
        $this->assertDatabaseCount('sale_items', 1);$this->assertDatabaseCount('sales', 1);
    }
    public function test_financial_scope_unknown_totals_and_balance_corruption_are_rejected(): void
    {
        $ctx = $this->prepared();$op = $this->sale($ctx);$this->postJson('/api/v2/sales', $op)->assertCreated();
        $worker = $this->account('worker');$this->createMaster('greenhouse_assignments', ['greenhouse_id' => $ctx['gh']['id'], 'user_id' => $worker->public_id, 'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);
        $this->asUser($worker)->getJson('/api/v2/sales')->assertForbidden();$this->getJson('/api/v2/sales/'.$op['entity_id'])->assertForbidden();
        $this->postJson('/api/v2/sales', $op)->assertForbidden();
        $this->assertEmpty(array_filter($this->getJson('/api/v2/sync/changes')->assertOk()->json('data'), fn ($row) => $row['entity_type'] === 'sale'));
        $this->asUser($ctx['actor']);$invalid = $this->sale($ctx, '1');$invalid['payload']['total_amount'] = '1.00';
        $this->postJson('/api/v2/sales', $invalid)->assertUnprocessable();
        DB::table('harvest_items')->where('id', $ctx['source'])->update(['sold_weight_kg' => '0']);
        $this->postJson('/api/v2/sales', $this->sale($ctx, '1'))->assertStatus(500)->assertJsonPath('code', 'harvest_balance_inconsistent');
        $this->assertDatabaseCount('sales', 1);
        $this->getJson('/api/v2/harvests/'.$ctx['harvest']['entity_id'].'/availability')->assertStatus(500)->assertJsonPath('code', 'harvest_balance_inconsistent');
    }
    public function test_closed_replay_parent_identity_and_duplicate_sources_are_not_silently_reassigned(): void
    {
        $ctx = $this->prepared();$op = $this->sale($ctx, '1');$receipt = $this->postJson('/api/v2/sales', $op)->assertCreated()->json();
        $bad = $this->sale($ctx, '1');$bad['payload']['items'][] = [...$bad['payload']['items'][0], 'id' => (string) Str::uuid()];
        $this->postJson('/api/v2/sales', $bad)->assertUnprocessable();
        $otherSeason = $this->createMaster('seasons', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Musim lain', 'start_date' => '2026-10-01', 'end_date' => null]);
        $bad = $this->sale($ctx, '1');$bad['payload']['season_id'] = $otherSeason['id'];$this->postJson('/api/v2/sales', $bad)->assertUnprocessable();
        $this->postJson('/api/v2/seasons/'.$ctx['season']['id'].'/close', $this->operation('seasons', ['reason' => 'Tutup sintetis'], 'close', $ctx['season']['id'], 1))->assertOk();
        $this->postJson('/api/v2/sales', $op)->assertExactJson($receipt);
        $this->postJson('/api/v2/sales', $this->sale($ctx, '1'))->assertConflict()->assertJsonPath('code', 'season_closed');
        $this->assertDatabaseCount('sales', 1);
    }
    public function test_fault_after_receipt_rolls_back_sale_children_counter_identity_and_all_feed_rows(): void
    {
        $ctx = $this->prepared();$op = $this->sale($ctx);$feedCount = DB::table('change_feed')->count();$injected = false;
        DB::listen(function (QueryExecuted $event) use (&$injected) {
            if (!$injected && str_starts_with($event->sql, 'insert into `sync_operations`')) {$injected = true;throw new \RuntimeException('Synthetic sale post-receipt fault');}
        });
        $this->postJson('/api/v2/sales', $op)->assertStatus(500);$this->assertTrue($injected);
        $this->assertDatabaseCount('sales', 0);$this->assertDatabaseCount('sale_items', 0);
        $this->assertDatabaseHas('harvest_items', ['id' => $ctx['source'], 'sold_weight_kg' => '0.000000', 'balance_version' => 1]);
        foreach ([$op['entity_id'], $op['payload']['items'][0]['id']] as $id) {$this->assertDatabaseMissing('entity_identities', ['entity_id' => $id]);}
        $this->assertSame($feedCount, DB::table('change_feed')->count());
        foreach (['audit_logs', 'sync_operations'] as $table) {$this->assertSame(0, DB::table($table)->where('entity_id', $op['entity_id'])->count());}
    }
}
