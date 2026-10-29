<?php

namespace Tests\Feature;

use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use RuntimeException;
use Tests\FinanceTestCase;

class FinanceMasterTest extends FinanceTestCase
{
    public function test_master_parent_constraints_deactivation_and_history_are_preserved(): void
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $ghB = $this->createMaster('greenhouses', ['name' => 'GH B sintetis']);
        $foreignVariety = $this->createMaster('varieties', ['greenhouse_id' => $ghB['id'], 'name' => 'Varietas B']);
        $payload = ['season_id' => $ctx['season']['id'], 'variety_id' => $foreignVariety['id'], 'name' => 'Tidak sah',
            'population' => 10, 'sowing_date' => '2026-10-01', 'transplant_date' => null];
        $this->postJson('/api/v2/planting-groups', $this->operation('planting_groups', $payload))->assertUnprocessable();
        $payload['variety_id'] = $ctx['variety']['id'];
        $payload['transplant_date'] = '2026-09-30';
        $this->postJson('/api/v2/planting-groups', $this->operation('planting_groups', $payload))->assertUnprocessable();
        $this->patchJson('/api/v2/varieties/'.$ctx['variety']['id'], $this->operation('varieties',
            ['name' => 'Varietas lama', 'active' => false, 'reason' => 'Dinonaktifkan uji'], 'update', $ctx['variety']['id'], 1))->assertOk();
        $this->getJson('/api/v2/planting-groups/'.$ctx['group']['id'])->assertOk()->assertJsonPath('variety_id', $ctx['variety']['id']);
        $this->assertDatabaseHas('varieties', ['id' => $ctx['variety']['id'], 'active' => false]);
        $this->deleteJson('/api/v2/varieties/'.$ctx['variety']['id'])->assertStatus(405);
    }

    public function test_receipt_replay_payload_conflict_version_conflict_and_atomic_audit(): void
    {
        $manager = $this->account();
        $this->asUser($manager);
        $op = $this->operation('greenhouses', ['name' => 'GH idempoten']);
        $first = $this->postJson('/api/v2/greenhouses', $op)->assertCreated()->json();
        $this->postJson('/api/v2/greenhouses', $op)->assertCreated()->assertExactJson($first);
        $changed = $op;
        $changed['payload']['name'] = 'Isi berbeda';
        $this->postJson('/api/v2/greenhouses', $changed)->assertConflict()->assertJsonPath('code', 'operation_conflict');
        $this->assertDatabaseCount('greenhouses', 1);
        $this->assertDatabaseCount('audit_logs', 1);
        $this->assertDatabaseCount('change_feed', 1);
        $this->assertDatabaseCount('sync_operations', 1);
        $edit = $this->operation('greenhouses', ['name' => 'Nama baru', 'active' => true, 'reason' => 'Koreksi uji'], 'update', $op['entity_id'], 1);
        $this->patchJson('/api/v2/greenhouses/'.$op['entity_id'], $edit)->assertOk()->assertJsonPath('version', 2);
        $edit['operation_id'] = (string) Str::uuid();
        $this->patchJson('/api/v2/greenhouses/'.$op['entity_id'], $edit)->assertConflict()->assertJsonPath('current_version', 2);
        $this->assertDatabaseCount('audit_logs', 2);
        $epoch = $this->operation('greenhouses', ['name' => 'Old epoch']);
        $epoch['sync_epoch'] = (string) Str::uuid();
        $this->postJson('/api/v2/greenhouses', $epoch)->assertConflict()->assertJsonPath('code', 'epoch_mismatch');
    }

    public function test_scope_and_role_are_checked_even_for_replay_and_assignment_revocation(): void
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $worker = $this->account('worker');
        $assignment = $this->createMaster('greenhouse_assignments', ['greenhouse_id' => $ctx['gh']['id'], 'user_id' => $worker->public_id,
            'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);
        $this->asUser($worker)->getJson('/api/v2/planting-groups')->assertJsonCount(1, 'data');
        $this->postJson('/api/v2/greenhouses', $this->operation('greenhouses', ['name' => 'Forbidden']))->assertForbidden();
        $this->getJson('/api/v2/expense-categories')->assertForbidden();
        $this->getJson('/api/v2/assignments')->assertForbidden();
        $this->asUser($manager);
        $op = $this->operation('varieties', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Replay scope']);
        $this->postJson('/api/v2/varieties', $op)->assertCreated();
        DB::table('greenhouse_assignments')->where('user_id', $manager->id)->update(['active' => false]);
        $this->postJson('/api/v2/varieties', $op)->assertNotFound();
    }

    public function test_close_reopen_is_versioned_reasoned_and_closed_group_writes_are_held(): void
    {
        $ctx = $this->context($this->account());
        $id = $ctx['season']['id'];
        $this->postJson('/api/v2/seasons/'.$id.'/close', $this->operation('seasons', ['reason' => 'Review sintetis selesai'], 'close', $id, 1))
            ->assertOk()->assertJsonPath('data.status', 'closed');
        $group = $ctx['group'];
        $payload = ['name' => 'Koreksi', 'variety_id' => $ctx['variety']['id'], 'population' => 100, 'sowing_date' => '2026-10-01', 'transplant_date' => null, 'active' => true, 'reason' => 'Uji'];
        $this->patchJson('/api/v2/planting-groups/'.$group['id'], $this->operation('planting_groups', $payload, 'update', $group['id'], 1))
            ->assertConflict()->assertJsonPath('code', 'season_closed');
        $this->postJson('/api/v2/seasons/'.$id.'/reopen', $this->operation('seasons', ['reason' => 'Koreksi diterima'], 'reopen', $id, 2))
            ->assertOk()->assertJsonPath('data.status', 'open')->assertJsonPath('version', 3);
        $this->assertDatabaseHas('audit_logs', ['entity_id' => $id, 'action' => 'reopen', 'reason' => 'Koreksi diterima']);
        $this->getJson('/api/v2/seasons/'.$id.'/audit')->assertOk()->assertJsonCount(3, 'data')->assertJsonPath('data.2.reason', 'Koreksi diterima');
    }

    public function test_ledger_failure_rolls_back_mutation_audit_receipt_and_feed(): void
    {
        $manager = $this->account();
        $op = $this->operation('greenhouses', ['name' => 'Failpoint']);
        try {
            app(MutationLedger::class)->run($manager, $op, fn () => null, function () use ($manager, $op) {
                DB::table('greenhouses')->insert(['id' => $op['entity_id'], 'name' => 'Tidak boleh tersisa', 'created_by' => $manager->id]);
                throw new RuntimeException('Synthetic failpoint after mutation');
            });
            $this->fail('Failpoint should throw.');
        } catch (RuntimeException $e) {
            $this->assertSame('Synthetic failpoint after mutation', $e->getMessage());
        }
        foreach (['greenhouses', 'audit_logs', 'sync_operations', 'change_feed'] as $table) {
            $this->assertDatabaseCount($table, 0);
        }
    }

    public function test_user_admin_is_scoped_audited_and_revokes_existing_sessions_without_password_leak(): void
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $op = $this->operation('users', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Pekerja sintetis', 'role' => 'worker',
            'email' => 'worker-admin@example.invalid', 'password' => 'synthetic-test-password']);
        $receipt = $this->postJson('/api/v2/users', $op)->assertCreated()->assertJsonMissingPath('data.password')->json();
        $this->postJson('/api/v2/users', $op)->assertCreated()->assertExactJson($receipt);
        $user = User::where('public_id', $op['entity_id'])->firstOrFail();
        $this->assertNotSame($op['payload']['password'], $user->password);
        $token = $user->createToken('test-existing', ['finance-v2'], now()->addDay())->plainTextToken;
        $edit = $this->operation('users', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Pekerja nonaktif', 'role' => 'worker', 'active' => false, 'reason' => 'Akses berakhir'], 'update', $user->public_id, 1);
        $this->patchJson('/api/v2/users/'.$user->public_id, $edit)->assertOk();
        $this->app['auth']->forgetGuards();
        $this->withToken($token)->getJson('/api/v2/me')->assertUnauthorized();
        $this->assertStringNotContainsString('synthetic-test-password', DB::table('audit_logs')->where('entity_id', $user->public_id)->value('changes'));
        $this->asUser($user)->getJson('/api/v2/users?greenhouse_id='.$ctx['gh']['id'])->assertForbidden();
        $foreignManager = $this->account();
        $foreign = $this->context($foreignManager);
        $this->getJson('/api/v2/users?greenhouse_id='.$ctx['gh']['id'])->assertNotFound();
        $this->patchJson('/api/v2/users/'.$user->public_id, $edit)->assertNotFound();
    }
}
