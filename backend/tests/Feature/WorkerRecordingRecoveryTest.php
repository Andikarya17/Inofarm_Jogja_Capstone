<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;

class WorkerRecordingRecoveryTest extends FinanceTestCase
{
    public function test_scope_growth_recovers_older_masters_and_revocation_hides_them(): void
    {
        $manager = $this->account();
        $worker = $this->account('worker');
        $ctx = $this->context($manager);
        $gh = $ctx['gh']['id'];
        $other = $this->context($manager);
        $foreign = $this->context($this->account());
        $this->asUser($worker)->getJson('/api/v2/me')->assertOk()->assertJsonCount(0, 'greenhouses');
        $cursor = $this->getJson('/api/v2/sync/changes')->assertOk()->assertJsonCount(0, 'scope')->assertJsonCount(0, 'data')->json('next_cursor');
        $this->postJson('/api/v2/users/assignment-candidate', ['greenhouse_id' => $gh, 'email' => $worker->email])->assertForbidden();

        $this->asUser($manager)->getJson('/api/v2/users?for_assignment=1&greenhouse_id='.$gh)->assertJsonCount(0, 'data');
        $this->postJson('/api/v2/users/assignment-candidate', ['greenhouse_id' => $gh, 'email' => strtoupper($worker->email)])
            ->assertOk()->assertJsonPath('data.id', $worker->public_id);
        $this->postJson('/api/v2/users/assignment-candidate', ['greenhouse_id' => $foreign['gh']['id'], 'email' => $worker->email])->assertNotFound();
        $from = now()->subMinute()->format('Y-m-d\TH:i:sP');
        $assign = $this->operation('greenhouse_assignments', ['greenhouse_id' => $gh, 'user_id' => $worker->public_id,
            'effective_from' => $from, 'effective_until' => null]);
        $unassignedManager = $this->account();
        $this->postJson('/api/v2/users/assignment-candidate', ['greenhouse_id' => $gh, 'email' => $unassignedManager->email])->assertNotFound();
        $this->postJson('/api/v2/assignments', $this->operation('greenhouse_assignments',
            [...$assign['payload'], 'user_id' => $unassignedManager->public_id]))->assertUnprocessable();
        $receipt = $this->postJson('/api/v2/assignments', $assign)->assertCreated()->json();
        $this->postJson('/api/v2/assignments', $assign)->assertCreated()->assertExactJson($receipt);
        $this->postJson('/api/v2/assignments', $this->operation('greenhouse_assignments', $assign['payload']))->assertUnprocessable();
        $this->getJson('/api/v2/users?for_assignment=1&greenhouse_id='.$other['gh']['id'])->assertJsonFragment(['id' => $worker->public_id]);
        $this->postJson('/api/v2/users/assignment-candidate', ['greenhouse_id' => $gh, 'email' => $worker->email])->assertNotFound();
        $this->createMaster('greenhouse_assignments', [...$assign['payload'], 'greenhouse_id' => $other['gh']['id'],
            'effective_from' => now()->addDay()->format('Y-m-d\TH:i:sP')]);
        $this->assertDatabaseCount('greenhouse_assignments', 5);

        $this->asUser($worker)->getJson('/api/v2/greenhouses')->assertOk()->assertJsonCount(1, 'data')->assertJsonPath('data.0.id', $gh);
        $feed = $this->getJson('/api/v2/sync/changes?'.http_build_query(['cursor' => $cursor]))->assertOk()->assertJsonCount(1, 'scope')->json();
        $this->assertSame(['greenhouses', 'varieties', 'seasons', 'planting_groups'], array_column($feed['data'], 'entity_type'));
        $this->getJson('/api/v2/sync/changes?'.http_build_query(['cursor' => $feed['next_cursor']]))->assertOk()->assertJsonCount(0, 'data');

        $activity = $this->operation('activity', ['planting_group_id' => $ctx['group']['id'], 'activity_type' => 'general',
            'occurred_at' => '2026-11-09T08:00:00+07:00', 'work_state' => 'completed', 'materials' => [],
            'notes' => 'Uji pengamatan tanaman: tinggi 42 cm, 9 helai daun, lebar 15 cm.']);
        $this->postJson('/api/v2/activities', $activity)->assertCreated()->assertJsonPath('data.recorded_by', $worker->public_id);
        $stage = $this->operation('activity', [...$activity['payload'], 'activity_type' => 'stage',
            'actual_stage' => 'Polinasi', 'notes' => 'Stadia diamati pada kelompok uji.']);
        $this->postJson('/api/v2/activities', $stage)->assertCreated()->assertJsonPath('data.actual_stage', 'Polinasi');
        $this->getJson('/api/v2/activities/'.$stage['entity_id'])->assertOk()->assertJsonPath('actual_stage', 'Polinasi');
        $this->asUser($manager)->getJson('/api/v2/activities/'.$activity['entity_id'])->assertOk();
        $this->patchJson('/api/v2/greenhouses/'.$gh, $this->operation('greenhouses', ['name' => 'GH uji diperbarui', 'active' => false,
            'reason' => 'Uji penutupan input'], 'update', $gh, 1))->assertOk()->assertJsonPath('data.active', false);
        $this->getJson('/api/v2/greenhouses/'.$gh)->assertOk()->assertJsonPath('active', false);
        $this->asUser($worker)->postJson('/api/v2/activities', $this->operation('activity', $activity['payload']))->assertUnprocessable();
        $this->asUser($manager)->patchJson('/api/v2/greenhouses/'.$gh, $this->operation('greenhouses', ['name' => 'GH uji diperbarui', 'active' => true,
            'reason' => 'Uji buka input'], 'update', $gh, 2))->assertOk()->assertJsonPath('data.active', true);
        $this->patchJson('/api/v2/assignments/'.$assign['entity_id'], $this->operation('greenhouse_assignments', ['effective_from' => $from, 'effective_until' => null,
            'active' => false, 'reason' => 'Uji pencabutan'], 'update', $assign['entity_id'], 1))->assertOk();
        $this->asUser($worker)->getJson('/api/v2/sync/changes?'.http_build_query(['cursor' => $feed['next_cursor']]))
            ->assertOk()->assertJsonCount(0, 'scope')->assertJsonCount(0, 'data');
        $this->getJson('/api/v2/activities/'.$activity['entity_id'])->assertNotFound();
        $this->asUser($manager)->getJson('/api/v2/activities/'.$activity['entity_id'])->assertOk();
    }

    public function test_edit_email_and_password_keep_account_identity_and_revoke_previous_session(): void
    {
        $manager = $this->account();
        $gh = $this->context($manager)['gh']['id'];
        $oldEmail = Str::uuid().'@example.invalid';
        $oldPassword = 'fixture-original-pass';
        $create = $this->operation('users', ['greenhouse_id' => $gh, 'name' => 'Akun uji awal', 'role' => 'manager',
            'email' => $oldEmail, 'password' => $oldPassword]);
        $id = $this->postJson('/api/v2/users', $create)->assertCreated()->json('entity_id');
        $token = $this->postJson('/api/v2/auth/login', ['email' => $oldEmail, 'password' => $oldPassword])
            ->assertOk()->assertJsonPath('user.role', 'manager')->json('token');
        $path = '/api/v2/users/'.$id;
        $newEmail = Str::uuid().'@example.invalid';
        $newPassword = 'fixture-replacement-pass';
        $payload = ['greenhouse_id' => $gh, 'name' => 'Akun uji diperbarui', 'role' => 'worker', 'active' => true,
            'email' => strtoupper($newEmail), 'password' => $newPassword, 'reason' => 'Uji koreksi akun'];
        $this->asUser($manager)->patchJson($path, $this->operation('users', [...$payload, 'email' => 'salah'], 'update', $id, 1))->assertUnprocessable();
        $this->patchJson($path, $this->operation('users', [...$payload, 'email' => strtoupper($manager->email)], 'update', $id, 1))->assertUnprocessable();
        $edit = $this->operation('users', $payload, 'update', $id, 1);
        $receipt = $this->patchJson($path, $edit)->assertOk()->assertJsonPath('data.email', $newEmail)->assertJsonPath('data.id', $id)
            ->assertJsonPath('data.role', 'worker')->assertJsonMissingPath('data.password')->json();
        $this->patchJson($path, $edit)->assertOk()->assertExactJson($receipt);
        $this->patchJson($path, $this->operation('users', $payload, 'update', $id, 1))->assertConflict();
        $this->patchJson($path, $this->operation('users', [...$payload, 'email' => $newEmail], 'update', $id, 2))->assertOk();
        $this->patchJson('/api/v2/users/'.$manager->public_id, $this->operation('users', [...$payload, 'email' => $manager->email],
            'update', $manager->public_id, 1))->assertUnprocessable();
        $this->app['auth']->forgetGuards();
        $this->withToken($token)->postJson('/api/v2/greenhouses', $this->operation('greenhouses', ['name' => 'Tolak sesi lama']))->assertUnauthorized();
        $this->postJson('/api/v2/auth/login', ['email' => $oldEmail, 'password' => $newPassword])->assertUnprocessable();
        $this->postJson('/api/v2/auth/login', ['email' => $newEmail, 'password' => $oldPassword])->assertUnprocessable();
        $fresh = $this->postJson('/api/v2/auth/login', ['email' => strtoupper($newEmail), 'password' => $newPassword])
            ->assertOk()->assertJsonPath('user.id', $id)->assertJsonPath('user.role', 'worker')->json('token');
        $this->app['auth']->forgetGuards();
        $this->withToken($fresh)->getJson('/api/v2/greenhouses')->assertOk()->assertJsonPath('data.0.id', $gh);
        $this->postJson('/api/v2/greenhouses', $this->operation('greenhouses', ['name' => 'Tolak role pekerja']))->assertForbidden();
        $this->assertNotSame($newPassword, User::where('public_id', $id)->value('password'));
        foreach (['audit_logs' => 'changes', 'sync_operations' => 'receipt', 'change_feed' => 'body'] as $table => $column) {
            foreach (DB::table($table)->where('entity_id', $id)->pluck($column) as $stored) {
                $this->assertStringNotContainsString($oldPassword, $stored);
                $this->assertStringNotContainsString($newPassword, $stored);
            }
        }
        unset($payload['password']);
        $this->asUser($manager)->patchJson($path, $this->operation('users', [...$payload, 'active' => false], 'update', $id, 3))
            ->assertOk()->assertJsonPath('data.active', false);
        $this->app['auth']->forgetGuards();
        $this->withToken($fresh)->getJson('/api/v2/me')->assertUnauthorized();
        $this->asUser($manager)->patchJson($path, $this->operation('users', $payload, 'update', $id, 4))
            ->assertOk()->assertJsonPath('data.active', true);
        $this->postJson('/api/v2/auth/login', ['email' => $newEmail, 'password' => $newPassword])
            ->assertOk()->assertJsonPath('user.id', $id)->assertJsonPath('user.version', 5);
    }
}
