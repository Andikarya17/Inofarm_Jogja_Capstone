<?php

namespace Tests\Feature;

use Illuminate\Database\Events\QueryExecuted;
use Illuminate\Support\Facades\DB;
use RuntimeException;
use Tests\FinanceTestCase;

class FinanceExpenseTest extends FinanceTestCase
{
    private function prepared(): array
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $category = $this->createMaster('expense_categories', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Kategori sintetis']);
        $second = $this->createMaster('seasons', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Musim dua sintetis', 'start_date' => '2026-10-01', 'end_date' => null]);

        return [...$ctx, 'manager' => $manager, 'category' => $category, 'second' => $second];
    }

    private function payment(array $ctx, array $override = []): array
    {
        return [...['greenhouse_id' => $ctx['gh']['id'], 'category_id' => $ctx['category']['id'], 'amount' => '100.01',
            'paid_at' => '2026-10-29T00:30:00+07:00', 'payment_kind' => 'operating', 'notes' => 'Pembayaran aktual sintetis'], ...$override];
    }

    private function allocation(string $expense, string $season, string $amount = '60'): array
    {
        return ['expense_id' => $expense, 'season_id' => $season, 'amount' => $amount, 'cost_class' => 'VC', 'allocation_basis' => 'Nominal eksplisit menurut catatan sintetis; bukan aturan produksi.'];
    }

    public function test_split_source_balance_replay_correction_and_cancellation_reconcile_exactly(): void
    {
        $ctx = $this->prepared();
        $expense = $this->operation('expense', $this->payment($ctx));
        $first = $this->postJson('/api/v2/expenses', $expense)->assertCreated()->assertJsonPath('data.amount', '100.01')->json();
        $a = $this->operation('cost_allocation', $this->allocation($expense['entity_id'], $ctx['season']['id']));
        $b = $this->operation('cost_allocation', $this->allocation($expense['entity_id'], $ctx['second']['id'], '40.01'));
        $accepted = $this->postJson('/api/v2/cost-allocations', $a)->assertCreated()->json();
        $this->postJson('/api/v2/cost-allocations', $b)->assertCreated();
        $this->postJson('/api/v2/cost-allocations', $a)->assertCreated()->assertExactJson($accepted);
        $this->postJson('/api/v2/expenses', $expense)->assertCreated()->assertExactJson($first);
        $this->getJson('/api/v2/expenses/'.$expense['entity_id'])->assertExactJson($first['data']);
        $this->getJson('/api/v2/expenses/'.$expense['entity_id'].'/availability')->assertJsonPath('data.available_amount', '0.00')->assertJsonPath('data.version', 3);
        $this->postJson('/api/v2/cost-allocations', $this->operation('cost_allocation', $this->allocation($expense['entity_id'], $ctx['second']['id'], '0.01')))
            ->assertConflict()->assertJsonPath('code', 'allocation_exceeds_source');
        $this->patchJson('/api/v2/expenses/'.$expense['entity_id'], $this->operation('expense', [...$expense['payload'], 'amount' => '100', 'reason' => 'Koreksi sintetis'], 'update', $expense['entity_id'], 1))
            ->assertConflict()->assertJsonPath('code', 'expense_in_use');
        $this->postJson('/api/v2/expenses/'.$expense['entity_id'].'/cancel', $this->operation('expense', ['reason' => 'Uji saldo'], 'cancel', $expense['entity_id'], 1))
            ->assertConflict()->assertJsonPath('code', 'expense_in_use');
        $this->postJson('/api/v2/cost-allocations/'.$b['entity_id'].'/cancel', $this->operation('cost_allocation', ['reason' => 'Salah alokasi sintetis'], 'cancel', $b['entity_id'], 1))->assertOk();
        $this->getJson('/api/v2/expenses/'.$expense['entity_id'].'/availability')->assertJsonPath('data.available_amount', '40.01');
        $edit = $this->operation('cost_allocation', [...$a['payload'], 'amount' => '70.01', 'cost_class' => 'FC', 'reason' => 'Koreksi basis sintetis'], 'update', $a['entity_id'], 1);
        $this->patchJson('/api/v2/cost-allocations/'.$a['entity_id'], $edit)->assertOk()->assertJsonPath('data.amount', '70.01');
        $this->patchJson('/api/v2/cost-allocations/'.$a['entity_id'], $this->operation('cost_allocation', $edit['payload'], 'update', $a['entity_id'], 1))->assertConflict()->assertJsonPath('current_version', 2);
        $this->assertSame('70.01', DB::table('expenses')->where('id', $expense['entity_id'])->value('allocated_amount'));
        $this->assertSame('70.01', DB::table('cost_allocations')->where('record_state', 'active')->sum('amount'));
        $this->postJson('/api/v2/cost-allocations/'.$a['entity_id'].'/cancel', $this->operation('cost_allocation', ['reason' => 'Salah alokasi sintetis'], 'cancel', $a['entity_id'], 2))->assertOk();
        $this->postJson('/api/v2/expenses/'.$expense['entity_id'].'/cancel', $this->operation('expense', ['reason' => 'Pembayaran salah catat sintetis'], 'cancel', $expense['entity_id'], 1))->assertOk();
        $this->getJson('/api/v2/expenses/'.$expense['entity_id'].'/availability')->assertJsonPath('data.active', false)->assertJsonPath('data.available_amount', '0.00');
        $this->assertDatabaseCount('expenses', 1);
        $this->assertDatabaseCount('cost_allocations', 2);
    }

    public function test_worker_foreign_manager_and_revoked_assignment_cannot_read_write_replay_or_pull_finance(): void
    {
        $ctx = $this->prepared();
        $expense = $this->operation('expense', $this->payment($ctx));
        $this->postJson('/api/v2/expenses', $expense)->assertCreated();
        $allocation = $this->operation('cost_allocation', $this->allocation($expense['entity_id'], $ctx['season']['id']));
        $this->postJson('/api/v2/cost-allocations', $allocation)->assertCreated();
        $worker = $this->account('worker');
        $this->createMaster('greenhouse_assignments', ['greenhouse_id' => $ctx['gh']['id'], 'user_id' => $worker->public_id,
            'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);
        $this->asUser($worker);
        foreach (['expenses' => $expense, 'cost-allocations' => $allocation] as $path => $op) {
            $this->getJson('/api/v2/'.$path)->assertForbidden();
            $this->postJson('/api/v2/'.$path, $op)->assertForbidden();
            foreach (['', '/audit'] as $suffix) {
                $this->getJson('/api/v2/'.$path.'/'.$op['entity_id'].$suffix)->assertForbidden();
            }
        }
        $this->getJson('/api/v2/expenses/'.$expense['entity_id'].'/availability')->assertForbidden();
        $feed = $this->getJson('/api/v2/sync/changes')->assertOk()->json('data');
        $this->assertEmpty(array_filter($feed, fn ($row) => in_array($row['entity_type'], ['expense', 'cost_allocation', 'expense_balance', 'expense_categories'], true)));
        $this->context($this->account());
        foreach (['expenses' => $expense, 'cost-allocations' => $allocation] as $path => $op) {
            $this->getJson('/api/v2/'.$path)->assertJsonCount(0, 'data');
            $this->postJson('/api/v2/'.$path, $op)->assertNotFound();
            $this->getJson('/api/v2/'.$path.'/'.$op['entity_id'])->assertNotFound();
            $this->getJson('/api/v2/'.$path.'/'.$op['entity_id'].'/audit')->assertNotFound();
        }
        $this->getJson('/api/v2/expenses/'.$expense['entity_id'].'/availability')->assertNotFound();
        DB::table('greenhouse_assignments')->where('user_id', $ctx['manager']->id)->update(['active' => false]);
        $this->asUser($ctx['manager'])->postJson('/api/v2/expenses', $expense)->assertNotFound();
        $this->getJson('/api/v2/expenses/'.$expense['entity_id'])->assertNotFound();
    }

    public function test_money_basis_and_source_type_are_strict_and_missing_is_not_zero(): void
    {
        $ctx = $this->prepared();
        foreach ([null, '', '1,00', '1e2', 'NaN', '-0.01', '1.001', '1000000000000000000', 1, 1.25, true] as $invalid) {
            $this->postJson('/api/v2/expenses', $this->operation('expense', $this->payment($ctx, ['amount' => $invalid])))->assertUnprocessable();
        }
        $asset = $this->operation('expense', $this->payment($ctx, ['payment_kind' => 'asset_acquisition']));
        $this->postJson('/api/v2/expenses', $asset)->assertCreated();
        $this->postJson('/api/v2/cost-allocations', $this->operation('cost_allocation', $this->allocation($asset['entity_id'], $ctx['season']['id'], '1')))->assertUnprocessable();
        $zero = $this->operation('expense', $this->payment($ctx, ['amount' => '0']));
        $this->postJson('/api/v2/expenses', $zero)->assertCreated()->assertJsonPath('data.amount', '0.00');
        foreach ([['allocation_basis' => '   '], ['cost_class' => 'automatic'], ['amount' => null], ['amount' => '-1'], ['amount' => '0.001']] as $invalid) {
            $this->postJson('/api/v2/cost-allocations', $this->operation('cost_allocation', [...$this->allocation($zero['entity_id'], $ctx['season']['id'], '0'), ...$invalid]))->assertUnprocessable();
        }
        $this->postJson('/api/v2/cost-allocations', $this->operation('cost_allocation', $this->allocation($zero['entity_id'], $ctx['season']['id'], '0')))->assertCreated();
        $this->postJson('/api/v2/expenses/'.$zero['entity_id'].'/cancel', $this->operation('expense', ['reason' => 'Nol eksplisit masih direferensikan'], 'cancel', $zero['entity_id'], 1))->assertConflict();
        $this->getJson('/api/v2/expenses/'.$asset['entity_id'].'/availability')->assertJsonPath('data.active', false);
    }

    public function test_closed_season_holds_new_changes_but_preserves_accepted_receipts(): void
    {
        $ctx = $this->prepared();
        $expense = $this->operation('expense', $this->payment($ctx));
        $receipt = $this->postJson('/api/v2/expenses', $expense)->assertCreated()->json();
        $a = $this->operation('cost_allocation', $this->allocation($expense['entity_id'], $ctx['season']['id']));
        $allocated = $this->postJson('/api/v2/cost-allocations', $a)->assertCreated()->json();
        $this->postJson('/api/v2/seasons/'.$ctx['season']['id'].'/close', $this->operation('seasons', ['reason' => 'Tutup sintetis'], 'close', $ctx['season']['id'], 1))->assertOk();
        $this->postJson('/api/v2/expenses', $expense)->assertCreated()->assertExactJson($receipt);
        $this->postJson('/api/v2/cost-allocations', $a)->assertCreated()->assertExactJson($allocated);
        $this->patchJson('/api/v2/expenses/'.$expense['entity_id'], $this->operation('expense', [...$expense['payload'], 'amount' => '200', 'reason' => 'Koreksi sintetis'], 'update', $expense['entity_id'], 1))
            ->assertConflict()->assertJsonPath('code', 'season_closed');
        $this->postJson('/api/v2/cost-allocations/'.$a['entity_id'].'/cancel', $this->operation('cost_allocation', ['reason' => 'Koreksi sintetis'], 'cancel', $a['entity_id'], 1))
            ->assertConflict()->assertJsonPath('code', 'season_closed');
        $this->postJson('/api/v2/cost-allocations', $this->operation('cost_allocation', $this->allocation($expense['entity_id'], $ctx['season']['id'], '1')))->assertConflict()->assertJsonPath('code', 'season_closed');
        $this->postJson('/api/v2/seasons/'.$ctx['season']['id'].'/reopen', $this->operation('seasons', ['reason' => 'Tinjau ulang sintetis'], 'reopen', $ctx['season']['id'], 2))->assertOk();
        $this->patchJson('/api/v2/expenses/'.$expense['entity_id'], $this->operation('expense', [...$expense['payload'], 'payment_kind' => 'asset_acquisition', 'reason' => 'Ganti jenis'], 'update', $expense['entity_id'], 1))->assertUnprocessable();
        $edit = $this->operation('expense', [...$expense['payload'], 'amount' => '200', 'reason' => 'Koreksi sintetis'], 'update', $expense['entity_id'], 1);
        $this->patchJson('/api/v2/expenses/'.$expense['entity_id'], $edit)->assertOk()->assertJsonPath('version', 2);
        $this->getJson('/api/v2/expenses/'.$expense['entity_id'].'/availability')->assertJsonPath('data.available_amount', '140.00')->assertJsonPath('data.expense_version', 2);
    }

    public function test_failure_after_receipt_rolls_back_allocation_balance_feed_and_audit(): void
    {
        $ctx = $this->prepared();
        $expense = $this->operation('expense', $this->payment($ctx));
        $this->postJson('/api/v2/expenses', $expense)->assertCreated();
        $op = $this->operation('cost_allocation', $this->allocation($expense['entity_id'], $ctx['season']['id']));
        $feedBefore = DB::table('change_feed')->count();
        $injected = false;
        DB::listen(function (QueryExecuted $event) use (&$injected) {
            if (! $injected && str_starts_with($event->sql, 'insert into `sync_operations`')) {
                $injected = true;
                throw new RuntimeException('Synthetic allocation post-receipt fault');
            }
        });
        $this->postJson('/api/v2/cost-allocations', $op)->assertStatus(500);
        $this->assertTrue($injected);
        $this->assertDatabaseCount('cost_allocations', 0);
        $this->assertDatabaseHas('expenses', ['id' => $expense['entity_id'], 'allocated_amount' => '0.00', 'balance_version' => 1]);
        $this->assertSame($feedBefore, DB::table('change_feed')->count());
        $this->assertSame(0, DB::table('audit_logs')->where('operation_id', $op['operation_id'])->count());
        $this->assertSame(0, DB::table('sync_operations')->where('operation_id', $op['operation_id'])->count());
        $this->postJson('/api/v2/cost-allocations', $op)->assertCreated();
        $this->assertDatabaseHas('expenses', ['id' => $expense['entity_id'], 'allocated_amount' => '60.00', 'balance_version' => 2]);
    }

    public function test_corrupt_counter_holds_writes_instead_of_using_an_invented_balance(): void
    {
        $ctx = $this->prepared();
        $expense = $this->operation('expense', $this->payment($ctx));
        $this->postJson('/api/v2/expenses', $expense)->assertCreated();
        DB::table('expenses')->where('id', $expense['entity_id'])->update(['allocated_amount' => '1']);
        $this->getJson('/api/v2/expenses/'.$expense['entity_id'].'/availability')->assertStatus(500)->assertJsonPath('code', 'expense_balance_mismatch');
        $this->postJson('/api/v2/cost-allocations', $this->operation('cost_allocation', $this->allocation($expense['entity_id'], $ctx['season']['id'])))
            ->assertStatus(500)->assertJsonPath('code', 'expense_balance_mismatch');
        $this->assertDatabaseCount('cost_allocations', 0);
    }

    public function test_foreign_relations_filters_wib_pagination_and_audit_keep_history(): void
    {
        $ctx = $this->prepared();
        $a = $this->operation('expense', $this->payment($ctx));
        $this->postJson('/api/v2/expenses', $a)->assertCreated();
        $b = $this->operation('expense', $this->payment($ctx, ['paid_at' => '2026-10-28T23:30:00+07:00']));
        $this->postJson('/api/v2/expenses', $b)->assertCreated();
        $this->getJson('/api/v2/expenses?from=2026-10-29&until=2026-10-29')->assertJsonCount(1, 'data')->assertJsonPath('data.0.id', $a['entity_id']);
        $page = $this->getJson('/api/v2/expenses?limit=1')->assertJsonCount(1, 'data')->json();
        $this->getJson('/api/v2/expenses?limit=1&cursor='.urlencode($page['next_cursor']))->assertJsonCount(1, 'data')->assertJsonPath('data.0.id', $b['entity_id']);
        $this->getJson('/api/v2/expenses/'.$a['entity_id'].'/audit?limit=1')->assertJsonPath('data.0.actor_id', $ctx['manager']->public_id)->assertJsonPath('data.0.changes.after.amount', '100.01');
        $foreign = $this->context($ctx['manager']);
        $otherCategory = $this->createMaster('expense_categories', ['greenhouse_id' => $foreign['gh']['id'], 'name' => 'Kategori GH lain']);
        $this->postJson('/api/v2/expenses', $this->operation('expense', $this->payment($ctx, ['category_id' => $otherCategory['id']])))->assertUnprocessable();
        $this->postJson('/api/v2/cost-allocations', $this->operation('cost_allocation', $this->allocation($a['entity_id'], $foreign['season']['id'])))->assertNotFound();
        $this->postJson('/api/v2/cost-allocations', $this->operation('cost_allocation', $this->allocation($a['entity_id'], $ctx['season']['id'])))->assertCreated();
        $this->getJson('/api/v2/expenses?season_id='.$ctx['season']['id'])->assertJsonCount(1, 'data');
        $this->getJson('/api/v2/cost-allocations?greenhouse_id='.$foreign['gh']['id'])->assertJsonCount(0, 'data');
        $this->getJson('/api/v2/cost-allocations?expense_id='.$a['entity_id'])->assertJsonCount(1, 'data');
    }
}
