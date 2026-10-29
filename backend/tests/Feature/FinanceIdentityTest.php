<?php

namespace Tests\Feature;

use App\Domain\Identity\EntityIdentity;
use Illuminate\Database\Events\QueryExecuted;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use RuntimeException;
use Tests\FinanceTestCase;

class FinanceIdentityTest extends FinanceTestCase
{
    public function test_explicit_cli_bootstrap_reserves_public_identity_without_an_assignment(): void
    {
        $email = Str::uuid().'@example.invalid';
        $this->artisan('inofarm:finance-user', ['email' => $email, '--name' => 'CLI sintetis', '--role' => 'worker'])
            ->expectsQuestion('Password (minimal 12 karakter)', 'fixture-only-long-password')
            ->expectsOutput('Akun tersimpan. Penugasan greenhouse harus ditetapkan eksplisit.')->assertSuccessful();
        $user = DB::table('users')->where('email', $email)->first();
        $this->assertDatabaseHas('entity_identities', ['entity_id' => $user->public_id, 'entity_type' => 'users']);
        $this->assertSame(0, DB::table('greenhouse_assignments')->where('user_id', $user->id)->count());
    }

    private function activity(array $ctx): array
    {
        return ['planting_group_id' => $ctx['group']['id'], 'activity_type' => 'general', 'occurred_at' => '2026-10-30T08:00:00+07:00', 'work_state' => 'completed', 'materials' => [], 'notes' => 'Identitas sintetis'];
    }

    private function harvest(array $ctx, string $grade, ?string $item = null): array
    {
        return ['planting_group_id' => $ctx['group']['id'], 'occurred_at' => '2026-10-30T08:00:00+07:00', 'notes' => '',
            'items' => [['id' => $item ?? (string) Str::uuid(), 'grade_id' => $grade, 'fruit_count' => 1, 'weight_kg' => '1']]];
    }

    public function test_cross_kind_roots_users_and_financial_ids_are_reserved_without_scope_disclosure(): void
    {
        $manager = $this->account();$ctx = $this->context($manager);
        $grade = $this->createMaster('grades', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Grade sintetis']);
        $category = $this->createMaster('expense_categories', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Kategori privat sintetis']);
        $expense = $this->operation('expense', ['greenhouse_id' => $ctx['gh']['id'], 'category_id' => $category['id'], 'paid_at' => '2026-10-30T08:00:00+07:00', 'amount' => '100.01', 'payment_kind' => 'operating', 'notes' => 'Rahasia biaya sintetis']);
        $this->postJson('/api/v2/expenses', $expense)->assertCreated();
        $worker = $this->account('worker');
        $this->createMaster('greenhouse_assignments', ['greenhouse_id' => $ctx['gh']['id'], 'user_id' => $worker->public_id, 'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);
        $this->asUser($worker);
        $activity = $this->operation('activity', $this->activity($ctx));
        $receipt = $this->postJson('/api/v2/activities', $activity)->assertCreated()->json();
        foreach ([$activity['entity_id'], $expense['entity_id'], $grade['id'], $manager->public_id] as $id) {
            $result = $this->postJson('/api/v2/harvests', $this->operation('harvest', $this->harvest($ctx, $grade['id']), id: $id))->assertConflict()->assertJsonPath('code', 'entity_exists');
            $this->assertStringNotContainsString('100.01', $result->getContent());
            $this->assertStringNotContainsString('Rahasia biaya sintetis', $result->getContent());
            $this->assertNull($result->json('current_data'));
        }
        $this->postJson('/api/v2/activities', $activity)->assertCreated()->assertExactJson($receipt);
        $this->assertDatabaseCount('harvests', 0);
        $foreign = $this->context($this->account());
        $this->postJson('/api/v2/harvests', $this->operation('harvest', $this->harvest($ctx, $grade['id']), id: $activity['entity_id']))->assertNotFound();
    }

    public function test_inline_children_cannot_reuse_root_or_balance_ids_and_cancel_retains_reservation(): void
    {
        $ctx = $this->context($this->account());
        $grade = $this->createMaster('grades', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Grade sintetis']);
        $op = $this->operation('harvest', $this->harvest($ctx, $grade['id'], $grade['id']));
        $this->postJson('/api/v2/harvests', $op)->assertConflict()->assertJsonPath('code', 'entity_exists');
        $this->assertDatabaseMissing('entity_identities', ['entity_id' => $op['entity_id']]);
        $self = $this->operation('harvest', $this->harvest($ctx, $grade['id']));
        $self['payload']['items'][0]['id'] = $self['entity_id'];
        $this->postJson('/api/v2/harvests', $self)->assertConflict();
        $this->assertDatabaseMissing('entity_identities', ['entity_id' => $self['entity_id']]);
        $category = $this->createMaster('expense_categories', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Kategori sintetis']);
        $payment = $this->operation('expense', ['greenhouse_id' => $ctx['gh']['id'], 'category_id' => $category['id'], 'amount' => '0', 'paid_at' => '2026-10-30T08:00:00+07:00', 'payment_kind' => 'operating', 'notes' => '']);
        $balanceId = $this->postJson('/api/v2/expenses', $payment)->assertCreated()->json('data.balance_id');
        $this->assertDatabaseHas('entity_identities', ['entity_id' => $balanceId, 'entity_type' => 'expense_balance']);
        $this->postJson('/api/v2/harvests', $this->operation('harvest', $this->harvest($ctx, $grade['id'], $balanceId)))->assertConflict();
        $op = $this->operation('harvest', $this->harvest($ctx, $grade['id']));
        $this->postJson('/api/v2/harvests', $op)->assertCreated();
        $this->assertDatabaseHas('entity_identities', ['entity_id' => $op['payload']['items'][0]['id'], 'entity_type' => 'harvest_item']);
        $this->postJson('/api/v2/activities', $this->operation('activity', $this->activity($ctx), id: $op['payload']['items'][0]['id']))->assertConflict();
        $this->postJson('/api/v2/harvests/'.$op['entity_id'].'/cancel', $this->operation('harvest', ['reason' => 'Batal sintetis'], 'cancel', $op['entity_id'], 1))->assertOk();
        $this->postJson('/api/v2/activities', $this->operation('activity', $this->activity($ctx), id: $op['entity_id']))->assertConflict();
        $this->assertDatabaseHas('entity_identities', ['entity_id' => $op['entity_id'], 'entity_type' => 'harvest']);
        $assignment = DB::table('greenhouse_assignments')->where('greenhouse_id', $ctx['gh']['id'])->first();
        $this->assertDatabaseHas('entity_identities', ['entity_id' => $assignment->id, 'entity_type' => 'greenhouse_assignments']);
    }

    public function test_fault_after_receipt_rolls_back_root_and_child_reservations_together(): void
    {
        $ctx = $this->context($this->account());$grade = $this->createMaster('grades', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Grade sintetis']);
        $op = $this->operation('harvest', $this->harvest($ctx, $grade['id']));$injected = false;
        DB::listen(function (QueryExecuted $event) use (&$injected) {
            if (! $injected && str_starts_with($event->sql, 'insert into `sync_operations`')) {
                $injected = true;throw new RuntimeException('Synthetic post-receipt identity fault');
            }
        });
        $this->postJson('/api/v2/harvests', $op)->assertStatus(500);
        $this->assertTrue($injected);
        foreach ([$op['entity_id'], $op['payload']['items'][0]['id']] as $id) {
            $this->assertDatabaseMissing('entity_identities', ['entity_id' => $id]);
        }
        $this->assertDatabaseCount('harvests', 0);
        $this->postJson('/api/v2/harvests', $op)->assertCreated();
        $this->assertSame(2, DB::table('entity_identities')->whereIn('entity_id', [$op['entity_id'], $op['payload']['items'][0]['id']])->count());
    }

    public function test_backfill_preflight_refuses_existing_collisions_without_rewriting_records(): void
    {
        $manager = $this->account();$ctx = $this->context($manager);
        DB::table('users')->where('id', $manager->id)->update(['public_id' => $ctx['gh']['id']]); // Synthetic pre-schema8 collision.
        $before = DB::table('entity_identities')->count();
        $migration = require database_path('migrations/2026_10_30_000008_reserve_global_finance_identities.php');
        try {$migration->preflight();$this->fail('Existing graph must not be converted automatically.');} catch (RuntimeException $e) {$this->assertStringContainsString('collide', $e->getMessage());}
        $this->assertSame($before, DB::table('entity_identities')->count());
        $this->assertDatabaseHas('users', ['id' => $manager->id, 'public_id' => $ctx['gh']['id']]);
        DB::table('users')->where('id', $manager->id)->update(['public_id' => strtoupper($manager->public_id)]);
        try {$migration->preflight();$this->fail('Noncanonical IDs must not be normalized silently.');} catch (RuntimeException $e) {$this->assertStringContainsString('not canonical', $e->getMessage());}
    }

    public function test_backfill_preserves_ids_receipts_and_historical_reservations_then_readonly_check_passes(): void
    {
        $ctx = $this->context($this->account());$op = $this->operation('activity', $this->activity($ctx));
        $receipt = $this->postJson('/api/v2/activities', $op)->assertCreated()->json();
        // Simulate an external historical deletion in the guarded test transaction only.
        DB::table('activity_reference_snapshots')->where('activity_id', $op['entity_id'])->delete();
        DB::table('activities')->where('id', $op['entity_id'])->delete();
        DB::table('entity_identities')->delete(); // Synthetic schema7 baseline; no production reset.
        $before = hash('sha256', json_encode(DB::table('sync_operations')->orderBy('id')->pluck('receipt')->all(), JSON_THROW_ON_ERROR));
        $expected = EntityIdentity::entries()->orderBy('entity_id')->get()->map(fn ($r) => (array) $r)->all();
        $migration = require database_path('migrations/2026_10_30_000008_reserve_global_finance_identities.php');$migration->backfill();
        $actual = DB::table('entity_identities')->orderBy('entity_id')->get(['entity_id', 'entity_type'])->map(fn ($r) => (array) $r)->all();
        $this->assertSame($expected, $actual);
        $this->assertSame($before, hash('sha256', json_encode(DB::table('sync_operations')->orderBy('id')->pluck('receipt')->all(), JSON_THROW_ON_ERROR)));
        $this->postJson('/api/v2/activities', $this->operation('activity', $op['payload'], id: $op['entity_id']))->assertConflict();
        $this->postJson('/api/v2/activities', $op)->assertCreated()->assertExactJson($receipt);
        $this->assertSame(0, Artisan::call('inofarm:finance-identities', ['--json' => true]));
        $report = json_decode(Artisan::output(), true, flags: JSON_THROW_ON_ERROR);
        $this->assertSame(0, $report['collisions']);$this->assertSame(0, $report['unregistered_or_mismatched']);$this->assertSame(0, $report['invalid_uuid']);
    }
}
