<?php

namespace Tests\Feature;

use Illuminate\Database\Events\QueryExecuted;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;

class FinanceSeasonReportTest extends FinanceTestCase
{
    private function input(array $ctx): array
    {
        return ['season_id' => $ctx['season']['id'], 'coverage' => ['costs' => 'complete', 'sales' => 'complete'],
            'known_pending_count' => 0, 'requested_state' => 'provisional', 'review_note' => 'Tinjauan sintetis, bukan data usaha mitra.'];
    }

    private function prepared(): array
    {
        $actor = $this->account();
        $ctx = $this->context($actor);
        $category = $this->createMaster('expense_categories', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Sumber sintetis']);
        $payment = $this->operation('expense', ['greenhouse_id' => $ctx['gh']['id'], 'category_id' => $category['id'], 'amount' => '7999990.00',
            'paid_at' => '2026-10-30T08:00:00+07:00', 'payment_kind' => 'operating', 'notes' => 'Kas sumber, jangan dibebankan dua kali']);
        $this->postJson('/api/v2/expenses', $payment)->assertCreated();
        foreach (['FC' => '4999990.00', 'VC' => '3000000.00'] as $class => $amount) {
            $this->postJson('/api/v2/cost-allocations', $this->operation('cost_allocation', ['expense_id' => $payment['entity_id'], 'season_id' => $ctx['season']['id'],
                'amount' => $amount, 'cost_class' => $class, 'allocation_basis' => 'Dasar eksplisit sintetis']))->assertCreated();
        }
        $assetPayment = $this->operation('expense', [...$payment['payload'], 'amount' => '30.00', 'payment_kind' => 'asset_acquisition']);
        $this->postJson('/api/v2/expenses', $assetPayment)->assertCreated();
        $asset = $this->operation('asset', ['expense_id' => $assetPayment['entity_id'], 'name' => 'Aset sintetis', 'cost' => '30.00', 'residual' => '0.00',
            'life_years' => 3, 'in_service_date' => '2026-01-01', 'usage_basis' => 'Manual']);
        $this->postJson('/api/v2/assets', $asset)->assertCreated();
        $depreciation = $this->operation('depreciation_allocation', ['asset_id' => $asset['entity_id'], 'season_id' => $ctx['season']['id'], 'usage_reference' => (string) Str::uuid(),
            'usage_year' => 1, 'period_start' => '2026-10-01', 'period_end' => '2026-11-01', 'amount' => '10.00', 'allocation_basis' => 'Alokasi nominal disepakati sintetis']);
        $this->postJson('/api/v2/depreciation-allocations', $depreciation)->assertCreated();
        $grade = $this->createMaster('grades', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Grade sintetis']);
        $item = (string) Str::uuid();
        $harvest = $this->operation('harvest', ['planting_group_id' => $ctx['group']['id'], 'occurred_at' => '2026-10-30T08:00:00+07:00', 'notes' => '600kg termasuk200kg belum terjual',
            'items' => [['id' => $item, 'grade_id' => $grade['id'], 'fruit_count' => 150, 'weight_kg' => '600']]]);
        $this->postJson('/api/v2/harvests', $harvest)->assertCreated();
        $sale = $this->operation('sale', ['season_id' => $ctx['season']['id'], 'paid_at' => '2026-10-30T09:00:00+07:00', 'payment_status' => 'paid', 'customer_name' => '', 'notes' => 'Lunas sintetis',
            'items' => [['id' => (string) Str::uuid(), 'harvest_item_id' => $item, 'quantity_kg' => '400', 'price_per_kg' => '30000']]]);
        $this->postJson('/api/v2/sales', $sale)->assertCreated();

        return [...$ctx, 'actor' => $actor, 'sale' => $sale, 'depreciation' => $depreciation, 'source' => $item];
    }

    private function report(array $input, array $preview): array
    {
        return $this->operation('season_report', [...$input, 'expected_sources_hash' => $preview['sources_hash'], 'expected_formula_version' => $preview['formula_version']]);
    }

    public function test_synthetic_snapshot_sources_replay_and_independent_totals(): void
    {
        $ctx = $this->prepared();
        $input = $this->input($ctx);
        $preview = $this->postJson('/api/v2/season-reports/preview', $input)->assertOk()->json();
        foreach (['FC' => '5000000.00', 'VC' => '3000000.00', 'TR' => '12000000.00', 'Q' => '400.000000', 'BEP_Q' => '266.666667'] as $key => $value) {
            $this->assertSame($value, $preview['metrics'][$key]['value']);
        }
        $this->assertSame('provisional', $preview['report_state']);
        $this->assertSame(7, $preview['source_count']);
        $op = $this->report($input, $preview);
        $receipt = $this->postJson('/api/v2/season-reports', $op)->assertCreated()->json();
        $this->postJson('/api/v2/season-reports', $op)->assertCreated()->assertExactJson($receipt);
        $this->getJson('/api/v2/reports/'.$op['entity_id'])->assertExactJson($receipt['data']);
        $page = $this->getJson('/api/v2/reports/'.$op['entity_id'].'/sources?limit=2')->assertOk()->assertJsonCount(2, 'data')->json();
        $this->assertSame('2', $page['next_cursor']);
        $this->assertSame($receipt['data']['snapshot_hash'], $page['snapshot_hash']);
        foreach (['sync_operations', 'audit_logs', 'change_feed'] as $table) {
            $this->assertSame(1, DB::table($table)->where('entity_id', $op['entity_id'])->count());
        }
        $this->assertDatabaseCount('report_snapshots', 1);
        file_put_contents(base_path('../tmp/fin12-public.json'), json_encode(['receipt' => $receipt, 'full' => json_decode(DB::table('report_snapshots')->value('content'), true)], JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR));
    }

    public function test_source_changes_require_new_review_and_prior_snapshot_never_recalculates(): void
    {
        $ctx = $this->prepared();
        $input = $this->input($ctx);
        $preview = $this->postJson('/api/v2/season-reports/preview', $input)->assertOk()->json();
        $op = $this->report($input, $preview);
        $old = $this->postJson('/api/v2/season-reports', $op)->assertCreated()->json();
        $this->postJson('/api/v2/sales/'.$ctx['sale']['entity_id'].'/cancel', $this->operation('sale', ['reason' => 'Pembatalan sintetis'], 'cancel', $ctx['sale']['entity_id'], 1))->assertOk();
        $this->postJson('/api/v2/season-reports', $this->report($input, $preview))->assertConflict()->assertJsonPath('code', 'report_sources_changed');
        $this->getJson('/api/v2/season-reports/preview/sources?'.http_build_query(['season_id' => $ctx['season']['id'], 'sources_hash' => $preview['sources_hash']]))->assertConflict();
        $fresh = $this->postJson('/api/v2/season-reports/preview', $input)->assertOk()->json();
        $this->assertSame('0.00', $fresh['metrics']['TR']['value']);
        $this->assertSame('0.000000', $fresh['metrics']['Q']['value']);
        $this->assertNull($fresh['metrics']['P']['value']);
        $this->getJson('/api/v2/reports/'.$op['entity_id'])->assertExactJson($old['data']);
        $this->postJson('/api/v2/season-reports', $op)->assertExactJson($old);
        $frozen = $this->getJson('/api/v2/reports/'.$op['entity_id'].'/sources?limit=100')->assertOk()->json('data');
        $this->assertSame('active', collect($frozen)->firstWhere('kind', 'sale')['data']['record_state']);
    }

    public function test_final_review_closure_known_zero_and_field_validation(): void
    {
        $actor = $this->account();
        $ctx = $this->context($actor);
        $input = $this->input($ctx);
        $unknown = $this->postJson('/api/v2/season-reports/preview', [...$input, 'coverage' => ['costs' => 'unknown', 'sales' => 'unknown'], 'known_pending_count' => null])->assertOk()->json();
        $this->assertNull($unknown['metrics']['TC']['value']);
        $this->assertNull($unknown['metrics']['TR']['value']);
        $zero = $this->postJson('/api/v2/season-reports/preview', $input)->assertOk()->json();
        $this->assertSame('0.00', $zero['metrics']['TC']['value']);
        $this->assertNull($zero['metrics']['RC']['value']);
        $final = [...$input, 'requested_state' => 'reviewed_final'];
        $this->postJson('/api/v2/season-reports/preview', $final)->assertConflict()->assertJsonPath('code', 'report_review_incomplete');
        $this->postJson('/api/v2/seasons/'.$ctx['season']['id'].'/close', $this->operation('seasons', ['reason' => 'Tutup sintetis'], 'close', $ctx['season']['id'], 1))->assertOk();
        $this->postJson('/api/v2/season-reports/preview', [...$final, 'known_pending_count' => null])->assertConflict();
        $closed = $this->postJson('/api/v2/season-reports/preview', $final)->assertOk()->assertJsonPath('report_state', 'reviewed_final')->json();
        $this->postJson('/api/v2/season-reports', $this->report($final, $closed))->assertCreated()->assertJsonPath('data.metrics.RC.status', 'unavailable');
        $this->postJson('/api/v2/season-reports/preview', [...$input, 'known_pending_count' => '0'])->assertUnprocessable();
        $this->postJson('/api/v2/season-reports/preview', [...$input, 'coverage' => ['costs' => 'complete', 'sales' => 'complete', 'actor' => 'manager']])->assertUnprocessable();
        $bad = $this->report($input, $closed);
        $bad['payload']['expected_formula_version'] = 'old-formula';
        $this->postJson('/api/v2/season-reports', $bad)->assertConflict()->assertJsonPath('code', 'report_formula_changed');
    }

    public function test_current_scope_role_immutable_routes_and_checksum_failure(): void
    {
        $ctx = $this->prepared();
        $input = $this->input($ctx);
        $preview = $this->postJson('/api/v2/season-reports/preview', $input)->assertOk()->json();
        $op = $this->report($input, $preview);
        $this->postJson('/api/v2/season-reports', $op)->assertCreated();
        $this->patchJson('/api/v2/reports/'.$op['entity_id'], [])->assertStatus(405);
        $this->asUser($this->account('worker'))->getJson('/api/v2/season-reports')->assertForbidden();
        $this->getJson('/api/v2/reports/'.$op['entity_id'])->assertForbidden();
        $this->postJson('/api/v2/season-reports', $op)->assertForbidden();
        $this->assertEmpty(array_filter($this->getJson('/api/v2/sync/changes')->assertOk()->json('data'), fn ($r) => $r['entity_type'] === 'season_report'));
        $this->asUser($this->account())->getJson('/api/v2/reports/'.$op['entity_id'])->assertOk();
        $this->asUser($ctx['actor']);
        DB::table('greenhouse_assignments')->where('user_id', $ctx['actor']->id)->update(['active' => false]);
        $this->postJson('/api/v2/season-reports', $op)->assertCreated();
        DB::table('greenhouse_assignments')->where('user_id', $ctx['actor']->id)->update(['active' => true]);
        DB::table('report_snapshots')->where('id', $op['entity_id'])->update(['content' => '{}']);
        $this->getJson('/api/v2/reports/'.$op['entity_id'])->assertStatus(500)->assertJsonPath('code', 'report_snapshot_integrity');
    }

    public function test_cash_revenue_preserves_rounding_per_paid_transaction(): void
    {
        $ctx = $this->prepared();
        $this->postJson('/api/v2/sales/'.$ctx['sale']['entity_id'].'/cancel', $this->operation('sale', ['reason' => 'Ganti fixture'], 'cancel', $ctx['sale']['entity_id'], 1))->assertOk();
        for ($i = 0; $i < 2; $i++) {
            $sale = $this->operation('sale', [...$ctx['sale']['payload'], 'items' => [['id' => (string) Str::uuid(), 'harvest_item_id' => $ctx['source'], 'quantity_kg' => '0.5', 'price_per_kg' => '0.01']]]);
            $this->postJson('/api/v2/sales', $sale)->assertCreated()->assertJsonPath('data.total_amount', '0.01');
        }
        $m = $this->postJson('/api/v2/season-reports/preview', $this->input($ctx))->assertOk()->json('metrics');
        $this->assertSame('0.02', $m['TR']['value']);
        $this->assertSame('1.000000', $m['Q']['value']);
        $this->assertSame('0.020000', $m['P']['value']);
    }

    public function test_receipt_failure_rolls_back_snapshot_identity_audit_and_feed(): void
    {
        $ctx = $this->prepared();
        $input = $this->input($ctx);
        $preview = $this->postJson('/api/v2/season-reports/preview', $input)->assertOk()->json();
        $op = $this->report($input, $preview);
        $fired = false;
        DB::listen(function (QueryExecuted $query) use ($op, &$fired) {
            if (! $fired && str_starts_with($query->sql, 'insert into `sync_operations`') && in_array($op['operation_id'], $query->bindings, true)) {
                $fired = true;
                throw new \RuntimeException('Injected report receipt failure');
            }
        });
        $this->postJson('/api/v2/season-reports', $op)->assertStatus(500);
        $this->assertTrue($fired);
        foreach (['entity_identities' => 'entity_id', 'report_snapshots' => 'id', 'audit_logs' => 'entity_id', 'sync_operations' => 'entity_id', 'change_feed' => 'entity_id'] as $table => $column) {
            $this->assertSame(0, DB::table($table)->where($column, $op['entity_id'])->count());
        }
        $this->postJson('/api/v2/season-reports', $op)->assertCreated();
    }

    public function test_report_identity_cannot_reuse_a_master_or_financial_uuid(): void
    {
        $ctx = $this->prepared();
        $input = $this->input($ctx);
        $preview = $this->postJson('/api/v2/season-reports/preview', $input)->assertOk()->json();
        foreach ([$ctx['group']['id'], $ctx['sale']['entity_id']] as $used) {
            $operation = $this->report($input, $preview);
            $operation['entity_id'] = $used;
            $this->postJson('/api/v2/season-reports', $operation)->assertStatus(409)->assertJsonPath('code', 'entity_exists');
            $this->assertSame(0, DB::table('report_snapshots')->where('id', $used)->count());
            $this->assertSame(0, DB::table('sync_operations')->where('operation_id', $operation['operation_id'])->count());
        }
    }

    public function test_source_capacity_fails_explicitly_before_any_partial_report(): void
    {
        $ctx = $this->prepared();
        // Synthetic SQL-only over-capacity fixture; never treated as accepted accounting facts.
        $template = (array) DB::table('cost_allocations')->where('season_id', $ctx['season']['id'])->first();
        $rows = [];
        for ($index = 0; $index < 10001; $index++) {
            $rows[] = [...$template, 'id' => (string) Str::uuid()];
            if (count($rows) === 250) {
                DB::table('cost_allocations')->insert($rows);
                $rows = [];
            }
        }
        if ($rows) {
            DB::table('cost_allocations')->insert($rows);
        }
        $this->postJson('/api/v2/season-reports/preview', $this->input($ctx))->assertStatus(409)->assertJsonPath('code', 'report_source_limit');
        $this->assertDatabaseCount('report_snapshots', 0);
    }
}
