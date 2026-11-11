<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;

/**
 * The manager is a super admin: every greenhouse, every account, no assignment and no mandatory change note.
 * The worker keeps the assignment scope and the mandatory correction reason.
 */
final class ManagerSuperAdminAccessTest extends FinanceTestCase
{
    private function assignWorker(array $ctx, User $worker): array
    {
        return $this->createMaster('greenhouse_assignments', ['greenhouse_id' => $ctx['gh']['id'], 'user_id' => $worker->public_id,
            'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);
    }

    private function activity(array $ctx, array $extra = []): array
    {
        return ['planting_group_id' => $ctx['group']['id'], 'activity_type' => 'general', 'occurred_at' => '2026-11-02T08:00:00+07:00',
            'work_state' => 'completed', 'materials' => [], 'notes' => 'Catatan uji', ...$extra];
    }

    public function test_manager_without_assignment_reaches_every_greenhouse_active_or_not_and_edits_its_data(): void
    {
        $owner = $this->account();
        $ctx = $this->context($owner);
        $second = $this->createMaster('greenhouses', ['name' => 'GH kedua sintetis']);
        $this->patchJson('/api/v2/greenhouses/'.$second['id'],
            $this->operation('greenhouses', ['name' => 'GH kedua nonaktif', 'active' => false], 'update', $second['id'], 1))
            ->assertOk()->assertJsonPath('data.active', false);

        $newcomer = $this->account();
        $this->assertSame(0, DB::table('greenhouse_assignments')->where('user_id', $newcomer->id)->count());
        $this->asUser($newcomer)->getJson('/api/v2/me')->assertOk()->assertJsonCount(2, 'greenhouses');
        $this->getJson('/api/v2/greenhouses')->assertOk()->assertJsonCount(2, 'data');
        $this->getJson('/api/v2/greenhouses/'.$second['id'])->assertOk()->assertJsonPath('active', false);
        $this->getJson('/api/v2/manager-home')->assertOk()->assertJsonCount(2, 'greenhouses')->assertJsonPath('access_until', null);
        $this->getJson('/api/v2/manager-home?greenhouse_id='.$ctx['gh']['id'])->assertOk();
        $this->getJson('/api/v2/manager-home?greenhouse_id='.(string) Str::uuid())->assertNotFound();

        $variety = $this->createMaster('varieties', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Varietas dari pengelola lain']);
        $update = $this->patchJson('/api/v2/varieties/'.$variety['id'], $this->operation('varieties',
            ['name' => 'Varietas diperbarui', 'active' => true], 'update', $variety['id'], 1));
        $this->assertSame(200, $update->status(), $update->getContent());
        $this->postJson('/api/v2/activities', $this->operation('activity', $this->activity($ctx)))->assertCreated();
        $this->getJson('/api/v2/activities')->assertOk()->assertJsonCount(1, 'data');
    }

    public function test_a_worker_keeps_the_assignment_scope_and_gains_no_manager_access(): void
    {
        $manager = $this->account();
        $a = $this->context($manager);
        $b = $this->context($manager);
        $worker = $this->account('worker');
        $this->assignWorker($a, $worker);

        $this->asUser($worker)->getJson('/api/v2/me')->assertOk()->assertJsonCount(1, 'greenhouses');
        $this->getJson('/api/v2/greenhouses')->assertOk()->assertJsonCount(1, 'data')->assertJsonPath('data.0.id', $a['gh']['id']);
        $this->getJson('/api/v2/greenhouses/'.$b['gh']['id'])->assertNotFound()->assertJsonPath('code', 'not_found');
        $this->getJson('/api/v2/sync/changes')->assertOk()->assertJsonCount(1, 'scope');
        $this->postJson('/api/v2/activities', $this->operation('activity', $this->activity($b)))->assertNotFound();
        $this->getJson('/api/v2/manager-home')->assertForbidden();
        $this->getJson('/api/v2/assignments')->assertForbidden();
        $this->getJson('/api/v2/users?greenhouse_id='.$a['gh']['id'].'&all_accounts=1')->assertForbidden();
        $this->postJson('/api/v2/greenhouses', $this->operation('greenhouses', ['name' => 'Tidak boleh']))->assertForbidden();
    }

    public function test_worker_replay_still_checks_access_after_the_assignment_is_removed(): void
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $worker = $this->account('worker');
        $assignment = $this->assignWorker($ctx, $worker);
        $op = $this->operation('activity', $this->activity($ctx));
        $this->asUser($worker)->postJson('/api/v2/activities', $op)->assertCreated();
        DB::table('greenhouse_assignments')->where('id', $assignment['id'])->update(['active' => false]);
        $this->postJson('/api/v2/activities', $op)->assertNotFound();
    }

    public function test_manager_feed_scope_follows_a_greenhouse_created_by_someone_else(): void
    {
        $first = $this->account();
        $this->context($first);
        $viewer = $this->account();
        $feed = $this->asUser($viewer)->getJson('/api/v2/sync/changes')->assertOk()->assertJsonCount(1, 'scope')->json();

        $second = $this->account();
        $this->asUser($second);
        $created = $this->createMaster('greenhouses', ['name' => 'GH baru dari pengelola lain']);

        $next = $this->asUser($viewer)->getJson('/api/v2/sync/changes?cursor='.$feed['next_cursor'].'&limit=100')->assertOk()
            ->assertJsonCount(2, 'scope')->json();
        $this->assertContains($created['id'], array_column(array_filter($next['data'], fn ($row) => $row['entity_type'] === 'greenhouses'), 'entity_id'));
    }

    public function test_manager_manages_every_account_but_not_their_own_access(): void
    {
        $owner = $this->account();
        $ctx = $this->context($owner);
        $gh = $ctx['gh']['id'];
        $unassigned = $this->account();
        $worker = $this->account('worker');
        $this->assignWorker($ctx, $worker);

        $viewer = $this->account();
        $ids = collect($this->asUser($viewer)->getJson('/api/v2/users?greenhouse_id='.$gh.'&all_accounts=1&limit=100')->assertOk()->json('data'))->pluck('id');
        $this->assertTrue($ids->contains($unassigned->public_id));
        $this->assertTrue($ids->contains($worker->public_id));
        $this->getJson('/api/v2/users?greenhouse_id='.$gh.'&for_assignment=1')->assertOk()->assertJsonCount(0, 'data');

        $rename = $this->operation('users', ['greenhouse_id' => $gh, 'name' => 'Pengelola diganti nama', 'role' => 'manager', 'active' => true], 'update', $unassigned->public_id, 1);
        $this->patchJson('/api/v2/users/'.$unassigned->public_id, $rename)->assertOk();
        $this->assertSame('Pengelola diganti nama', $unassigned->fresh()->name);

        $self = $this->operation('users', ['greenhouse_id' => $gh, 'name' => 'Diri sendiri', 'role' => 'manager', 'active' => false], 'update', $viewer->public_id, 1);
        $this->patchJson('/api/v2/users/'.$viewer->public_id, $self)->assertUnprocessable()
            ->assertJsonFragment(['message' => 'Anda tidak bisa menonaktifkan atau mengubah peran akun sendiri.']);
        $demote = $this->operation('users', ['greenhouse_id' => $gh, 'name' => 'Diri sendiri', 'role' => 'worker', 'active' => true], 'update', $viewer->public_id, 1);
        $this->patchJson('/api/v2/users/'.$viewer->public_id, $demote)->assertUnprocessable();

        $deactivate = $this->operation('users', ['greenhouse_id' => $gh, 'name' => 'Pengelola nonaktif', 'role' => 'manager', 'active' => false], 'update', $unassigned->public_id, 2);
        $this->patchJson('/api/v2/users/'.$unassigned->public_id, $deactivate)->assertOk();
        $this->assertFalse($unassigned->fresh()->active);
    }

    public function test_the_last_active_manager_cannot_be_removed_through_the_api(): void
    {
        $first = $this->account();
        $ctx = $this->context($first);
        $gh = $ctx['gh']['id'];
        $second = $this->account();
        $this->assertSame(2, User::where('role', 'manager')->where('active', true)->count());

        $dropSecond = $this->operation('users', ['greenhouse_id' => $gh, 'name' => 'Pengelola kedua', 'role' => 'worker', 'active' => true], 'update', $second->public_id, 1);
        $this->asUser($first)->patchJson('/api/v2/users/'.$second->public_id, $dropSecond)->assertOk();
        $this->assertSame(1, User::where('role', 'manager')->where('active', true)->count());

        $dropFirst = $this->operation('users', ['greenhouse_id' => $gh, 'name' => 'Pengelola pertama', 'role' => 'worker', 'active' => true], 'update', $first->public_id, 1);
        $this->asUser($first)->patchJson('/api/v2/users/'.$first->public_id, $dropFirst)->assertUnprocessable();
        $this->assertSame(1, User::where('role', 'manager')->where('active', true)->count());

        $demoted = $this->asUser($second->fresh())->patchJson('/api/v2/users/'.$first->public_id, $dropFirst);
        $this->assertGreaterThanOrEqual(400, $demoted->getStatusCode());
        $this->assertSame('manager', $first->fresh()->role);
    }

    public function test_change_note_is_optional_for_a_manager_and_still_required_for_a_worker(): void
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $worker = $this->account('worker');
        $this->assignWorker($ctx, $worker);

        $created = $this->operation('activity', $this->activity($ctx));
        $this->asUser($worker)->postJson('/api/v2/activities', $created)->assertCreated();
        $correction = $this->operation('activity', [...$this->activity($ctx, ['notes' => 'Koreksi tanpa alasan']), 'reference_policy' => 'preserve'], 'update', $created['entity_id'], 1);
        $this->patchJson('/api/v2/activities/'.$created['entity_id'], $correction)->assertUnprocessable();

        $this->asUser($manager);
        $this->postJson('/api/v2/activities/'.$created['entity_id'].'/cancel',
            $this->operation('activity', [], 'cancel', $created['entity_id'], 1))->assertOk()->assertJsonPath('data.record_state', 'cancelled');
        $this->assertNull(DB::table('audit_logs')->where('entity_id', $created['entity_id'])->where('action', 'cancel')->value('reason'));

        $season = $ctx['season']['id'];
        $this->postJson("/api/v2/seasons/$season/close", $this->operation('seasons', [], 'close', $season, 1))->assertOk()->assertJsonPath('data.status', 'closed');
        $this->postJson("/api/v2/seasons/$season/reopen", $this->operation('seasons', [], 'reopen', $season, 2))->assertOk()->assertJsonPath('data.status', 'open');
        $this->assertNull(DB::table('audit_logs')->where('entity_id', $season)->where('action', 'reopen')->value('reason'));

        $noted = $this->operation('seasons', ['name' => 'Musim dengan catatan', 'start_date' => '2026-10-01', 'end_date' => null, 'reason' => 'Catatan pengelola'], 'update', $season, 3);
        $this->patchJson("/api/v2/seasons/$season", $noted)->assertOk();
        $this->assertSame('Catatan pengelola', DB::table('audit_logs')->where('entity_id', $season)->where('action', 'update')->value('reason'));
    }

    public function test_marking_a_worker_record_invalid_still_requires_a_reason_from_a_manager(): void
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $worker = $this->account('worker');
        $this->assignWorker($ctx, $worker);
        $activity = $this->operation('activity', $this->activity($ctx));
        $this->asUser($worker)->postJson('/api/v2/activities', $activity)->assertCreated();

        $review = fn (array $payload) => $this->operation('activity_review', ['activity_id' => $activity['entity_id'], 'activity_version' => 1, ...$payload]);
        $this->asUser($manager)->postJson('/api/v2/activity-reviews', $review(['decision' => 'invalid']))->assertUnprocessable();
        $this->postJson('/api/v2/activity-reviews', $review(['decision' => 'invalid', 'reason' => '   ']))->assertUnprocessable();
        $this->postJson('/api/v2/activity-reviews', $review(['decision' => 'valid']))->assertCreated();
    }
}
