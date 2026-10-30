<?php

namespace Tests\Feature;

use App\Domain\Finance\DepreciationMath;
use Illuminate\Database\Events\QueryExecuted;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;

class FinanceAssetTest extends FinanceTestCase
{
    private function prepared(): array
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $category = $this->createMaster('expense_categories', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Aset sintetis']);
        $payment = $this->operation('expense', ['greenhouse_id' => $ctx['gh']['id'], 'category_id' => $category['id'],
            'amount' => '100.00', 'paid_at' => '2026-10-01T08:00:00+07:00', 'payment_kind' => 'asset_acquisition', 'notes' => 'Fixture']);
        $this->postJson('/api/v2/expenses', $payment)->assertCreated();
        $asset = $this->operation('asset', ['expense_id' => $payment['entity_id'], 'name' => 'Pompa sintetis', 'cost' => '100.00',
            'residual' => '0.00', 'life_years' => 3, 'in_service_date' => '2026-01-01', 'usage_basis' => 'Pemakaian manual sintetis']);
        $this->postJson('/api/v2/assets', $asset)->assertCreated();

        return [...$ctx, 'manager' => $manager, 'payment' => $payment, 'asset' => $asset];
    }

    private function allocation(array $ctx, string $amount = '33.33', int $year = 1): array
    {
        return $this->operation('depreciation_allocation', ['asset_id' => $ctx['asset']['entity_id'], 'season_id' => $ctx['season']['id'],
            'usage_reference' => (string) Str::uuid(), 'usage_year' => $year, 'period_start' => (2025 + $year).'-01-01',
            'period_end' => (2026 + $year).'-01-01', 'amount' => $amount, 'allocation_basis' => 'Nominal dan pemakaian sintetis eksplisit']);
    }

    public function test_caps_duplicate_usage_payment_locks_and_reasoned_cancellation(): void
    {
        $ctx = $this->prepared();
        $a = $this->allocation($ctx);
        $accepted = $this->postJson('/api/v2/depreciation-allocations', $a)->assertCreated()->json();
        file_put_contents(base_path('../tmp/fin11-public.json'), json_encode(['asset' => $this->getJson('/api/v2/assets/'.$ctx['asset']['entity_id'])->assertOk()->json(), 'allocation' => $accepted['data']], JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR));
        $this->postJson('/api/v2/depreciation-allocations', $a)->assertCreated()->assertExactJson($accepted);
        $this->postJson('/api/v2/depreciation-allocations', $this->allocation($ctx))->assertConflict()->assertJsonPath('code', 'duplicate_asset_usage');
        $b = $this->allocation($ctx, '0.01');
        $b['payload']['period_end'] = '2026-06-01';
        $this->postJson('/api/v2/depreciation-allocations', $b)->assertConflict()->assertJsonPath('code', 'depreciation_exceeds_budget');
        $this->postJson('/api/v2/assets', $this->operation('asset', $ctx['asset']['payload']))->assertConflict()->assertJsonPath('code', 'asset_exceeds_payment');
        $this->patchJson('/api/v2/expenses/'.$ctx['payment']['entity_id'], $this->operation('expense', [...$ctx['payment']['payload'], 'amount' => '99.99', 'reason' => 'Koreksi'], 'update', $ctx['payment']['entity_id'], 1))->assertConflict();
        $this->postJson('/api/v2/expenses/'.$ctx['payment']['entity_id'].'/cancel', $this->operation('expense', ['reason' => 'Batal'], 'cancel', $ctx['payment']['entity_id'], 1))->assertConflict();
        $this->postJson('/api/v2/assets/'.$ctx['asset']['entity_id'].'/cancel', $this->operation('asset', ['reason' => 'Batal'], 'cancel', $ctx['asset']['entity_id'], 1))->assertConflict();
        $this->getJson('/api/v2/depreciation-allocations/'.$a['entity_id'])->assertExactJson($accepted['data']);
        $this->postJson('/api/v2/depreciation-allocations/'.$a['entity_id'].'/cancel', $this->operation('depreciation_allocation', ['reason' => 'Salah pemakaian'], 'cancel', $a['entity_id'], 1))->assertOk();
        $this->postJson('/api/v2/assets/'.$ctx['asset']['entity_id'].'/cancel', $this->operation('asset', ['reason' => 'Salah aset'], 'cancel', $ctx['asset']['entity_id'], 1))->assertOk();
        $this->postJson('/api/v2/expenses/'.$ctx['payment']['entity_id'].'/cancel', $this->operation('expense', ['reason' => 'Salah bayar'], 'cancel', $ctx['payment']['entity_id'], 1))->assertOk();
    }

    public function test_yearly_budget_sums_exactly_and_closed_replay_is_immutable(): void
    {
        $ctx = $this->prepared();
        foreach (['33.33', '33.34', '33.33'] as $i => $amount) {
            $this->postJson('/api/v2/depreciation-allocations', $this->allocation($ctx, $amount, $i + 1))->assertCreated();
        }
        $this->assertSame('100.00', DB::table('depreciation_allocations')->sum('amount'));
        DB::table('seasons')->where('id', $ctx['season']['id'])->update(['status' => 'closed']);
        $this->postJson('/api/v2/assets', $ctx['asset'])->assertCreated();
        $this->postJson('/api/v2/depreciation-allocations', $this->allocation($ctx))->assertConflict()->assertJsonPath('code', 'season_closed');
    }

    public function test_manager_scope_worker_denial_versions_and_invalid_fields(): void
    {
        $ctx = $this->prepared();
        foreach (['residual' => '100.01', 'life_years' => 0, 'cost' => '1e2', 'in_service_date' => '9999-01-01'] as $key => $value) {
            $this->postJson('/api/v2/assets', $this->operation('asset', [...$ctx['asset']['payload'], $key => $value]))->assertUnprocessable();
        }
        $a = $this->allocation($ctx);
        $a['payload']['period_end'] = $a['payload']['period_start'];
        $this->postJson('/api/v2/depreciation-allocations', $a)->assertUnprocessable();
        $edit = $this->operation('asset', [...$ctx['asset']['payload'], 'name' => 'Nama baru', 'reason' => 'Koreksi'], 'update', $ctx['asset']['entity_id'], 1);
        $this->patchJson('/api/v2/assets/'.$ctx['asset']['entity_id'], $edit)->assertOk();
        $this->patchJson('/api/v2/assets/'.$ctx['asset']['entity_id'], $this->operation('asset', $edit['payload'], 'update', $ctx['asset']['entity_id'], 1))->assertConflict();
        $this->asUser($this->account('worker'));
        $this->getJson('/api/v2/assets')->assertForbidden();
        $this->postJson('/api/v2/assets', $ctx['asset'])->assertForbidden();
        $this->getJson('/api/v2/assets/'.$ctx['asset']['entity_id'].'/audit')->assertForbidden();
        $this->asUser($this->account());
        $this->getJson('/api/v2/assets/'.$ctx['asset']['entity_id'])->assertNotFound();
        $this->postJson('/api/v2/assets', $ctx['asset'])->assertNotFound();
    }

    public function test_rounding_anniversaries_and_zero_depreciable_base(): void
    {
        $this->assertSame('0.00', DepreciationMath::base('10', '10'));
        $this->assertSame(['start' => '2025-02-28', 'end' => '2026-02-28', 'budget' => '0.34', 'formula_version' => 'anniversary-cumulative-half-up-v1'], DepreciationMath::year('1.00', 3, 2, '2024-02-29'));
        $sum = '0';
        for ($year = 1; $year <= 997; $year++) {
            $sum = bcadd($sum, DepreciationMath::year('999999999999999999.99', 997, $year, '2024-02-29')['budget'], 2);
        }
        $this->assertSame('999999999999999999.99', $sum);
    }

    public function test_failure_after_receipt_sql_rolls_back_identity_allocation_audit_and_feed(): void
    {
        $ctx = $this->prepared();
        $op = $this->allocation($ctx);
        $fired = false;
        DB::listen(function (QueryExecuted $query) use ($op, &$fired) {
            if (! $fired && str_starts_with($query->sql, 'insert into `sync_operations`') && in_array($op['operation_id'], $query->bindings, true)) {
                $fired = true;
                throw new \RuntimeException('Synthetic failure after receipt');
            }
        });
        $this->postJson('/api/v2/depreciation-allocations', $op)->assertStatus(500);
        $this->assertTrue($fired);
        foreach (['entity_identities' => 'entity_id', 'depreciation_allocations' => 'id', 'audit_logs' => 'entity_id', 'change_feed' => 'entity_id', 'sync_operations' => 'entity_id'] as $table => $column) {
            $this->assertSame(0, DB::table($table)->where($column, $op['entity_id'])->count());
        }
        $this->postJson('/api/v2/depreciation-allocations', $op)->assertCreated();
    }
}
