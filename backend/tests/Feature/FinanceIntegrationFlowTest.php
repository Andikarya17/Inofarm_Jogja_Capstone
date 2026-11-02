<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;

class FinanceIntegrationFlowTest extends FinanceTestCase
{
    public function test_worker_records_feed_manager_finance_and_frozen_export_with_current_role_checks(): void
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $grade = $this->createMaster('grades', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Grade integrasi']);
        $category = $this->createMaster('expense_categories', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Biaya integrasi']);
        $email = Str::uuid().'@example.invalid';
        $account = $this->operation('users', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Pekerja integrasi', 'email' => $email, 'role' => 'worker', 'password' => 'integration-only-password']);
        $this->postJson('/api/v2/users', $account)->assertCreated();
        $worker = User::where('public_id', $account['entity_id'])->firstOrFail();
        $login = $this->postJson('/api/v2/auth/login', ['email' => $email, 'password' => 'integration-only-password'])->assertOk()->json();
        $this->app['auth']->forgetGuards();
        $this->withToken($login['token']);
        $this->getJson('/api/v2/sync/status')->assertOk()->assertJsonPath('user.role', 'worker');

        $activity = $this->operation('activity', ['planting_group_id' => $ctx['group']['id'], 'activity_type' => 'general', 'occurred_at' => '2026-11-02T08:00:00+07:00', 'work_state' => 'completed', 'materials' => [], 'notes' => 'Integrasi worker manual']);
        $receipt = $this->postJson('/api/v2/sync/operations', $activity)->assertCreated()->json();
        $this->postJson('/api/v2/sync/operations', $activity)->assertCreated()->assertExactJson($receipt);
        $item = (string) Str::uuid();
        $harvest = $this->operation('harvest', ['planting_group_id' => $ctx['group']['id'], 'occurred_at' => '2026-11-02T09:00:00+07:00', 'notes' => 'Panen integrasi', 'items' => [['id' => $item, 'grade_id' => $grade['id'], 'fruit_count' => 2, 'weight_kg' => '4']]]);
        $this->postJson('/api/v2/harvests', $harvest)->assertCreated()->assertJsonPath('data.recorded_by', $worker->public_id);
        $payment = $this->operation('expense', ['greenhouse_id' => $ctx['gh']['id'], 'category_id' => $category['id'], 'amount' => '80.00', 'paid_at' => '2026-11-02T08:00:00+07:00', 'payment_kind' => 'operating', 'notes' => 'Kas aktual integrasi']);
        $this->postJson('/api/v2/expenses', $payment)->assertForbidden();

        $this->asUser($manager)->getJson('/api/v2/activities/'.$activity['entity_id'])->assertOk()->assertJsonPath('recorded_by', $worker->public_id);
        $this->postJson('/api/v2/expenses', $payment)->assertCreated();
        $allocation = $this->operation('cost_allocation', ['expense_id' => $payment['entity_id'], 'season_id' => $ctx['season']['id'], 'amount' => '80.00', 'cost_class' => 'FC', 'allocation_basis' => 'Satu sumber dialokasikan manual']);
        $this->postJson('/api/v2/cost-allocations', $allocation)->assertCreated();
        $sale = $this->operation('sale', ['season_id' => $ctx['season']['id'], 'paid_at' => '2026-11-02T10:00:00+07:00', 'payment_status' => 'paid', 'customer_name' => 'Pembeli sintetis', 'notes' => 'Lunas integrasi', 'items' => [['id' => (string) Str::uuid(), 'harvest_item_id' => $item, 'quantity_kg' => '3', 'price_per_kg' => '50']]]);
        $this->postJson('/api/v2/sales', $sale)->assertCreated()->assertJsonPath('data.total_amount', '150.00');

        $input = ['season_id' => $ctx['season']['id'], 'coverage' => ['costs' => 'complete', 'sales' => 'complete'], 'known_pending_count' => 0, 'requested_state' => 'provisional', 'review_note' => 'Integrasi bukan UAT mitra'];
        $preview = $this->postJson('/api/v2/season-reports/preview', $input)->assertOk()->json();
        foreach (['TC' => '80.00', 'TR' => '150.00', 'L' => '70.00'] as $key => $value) {
            $this->assertSame($value, $preview['metrics'][$key]['value']);
        }
        $report = $this->operation('season_report', [...$input, 'expected_sources_hash' => $preview['sources_hash'], 'expected_formula_version' => $preview['formula_version']]);
        $saved = $this->postJson('/api/v2/season-reports', $report)->assertCreated()->json('data');
        $path = '/api/v2/report-exports/'.$report['entity_id'].'/csv?expected_snapshot_hash='.$saved['snapshot_hash'];
        $csv = $this->get($path)->assertOk()->assertHeader('X-Report-Snapshot-Hash', $saved['snapshot_hash'])->getContent();
        $cancel = $this->operation('sale', ['reason' => 'Koreksi setelah snapshot integrasi'], 'cancel', $sale['entity_id'], 1);
        $this->postJson('/api/v2/sales/'.$sale['entity_id'].'/cancel', $cancel)->assertOk();
        $this->postJson('/api/v2/season-reports/preview', $input)->assertOk()->assertJsonPath('metrics.TR.value', '0.00');
        $this->assertSame($csv, $this->get($path)->assertOk()->getContent());
        $this->assertDatabaseHas('harvest_items', ['id' => $item, 'sold_weight_kg' => '0.000000']);
        foreach (['activities', 'sync_operations', 'audit_logs'] as $table) {
            $column = $table === 'activities' ? 'id' : 'entity_id';
            $this->assertSame(1, DB::table($table)->where($column, $activity['entity_id'])->count());
        }
        $this->asUser($worker)->getJson('/api/v2/reports/'.$report['entity_id'])->assertForbidden();
        $this->getJson($path)->assertForbidden();
        $feed = $this->getJson('/api/v2/sync/changes')->assertOk()->json('data');
        foreach ($feed as $row) {
            $this->assertNotContains($row['entity_type'], ['expense', 'cost_allocation', 'sale', 'season_report', 'users']);
        }
    }
}
