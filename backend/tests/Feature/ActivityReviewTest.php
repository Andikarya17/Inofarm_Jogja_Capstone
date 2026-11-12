<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;

final class ActivityReviewTest extends FinanceTestCase
{
    private function assign(array $ctx, User $user): array
    {
        return $this->createMaster('greenhouse_assignments', ['greenhouse_id' => $ctx['gh']['id'], 'user_id' => $user->public_id,
            'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);
    }

    public function test_worker_revision_review_feedback_scope_and_replay(): void
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $worker = $this->account('worker');
        $otherWorker = $this->account('worker');
        $otherManager = $this->account('worker');
        $this->assign($ctx, $worker);
        $this->assign($ctx, $otherWorker);
        $assignment = $this->assign($ctx, $otherManager);
        $otherManager->update(['role' => 'manager']);
        $payload = ['planting_group_id' => $ctx['group']['id'], 'activity_type' => 'measurement',
            'occurred_at' => '2026-10-29T00:15:00+07:00', 'manual_ec' => '1.250', 'manual_ppm' => '600', 'manual_ph' => '6.1',
            'work_state' => 'completed', 'notes' => 'Pengukuran sintetis', 'materials' => []];
        $this->asUser($worker);
        $activity = $this->operation('activity', $payload);
        $id = $activity['entity_id'];
        $path = '/api/v2/activities/'.$id.'/verification';
        $this->postJson('/api/v2/sync/operations', $activity)->assertCreated()->assertJsonPath('data.recorded_by_role', 'worker');
        $this->getJson('/api/v2/activity-reviews')->assertForbidden();
        $review = $this->operation('activity_review', ['activity_id' => $id, 'activity_version' => 1, 'decision' => 'invalid', 'reason' => 'Periksa satuan pengukuran.']);
        $this->postJson('/api/v2/activity-reviews', $review)->assertForbidden();

        $this->asUser($manager);
        $this->postJson('/api/v2/activities', $this->operation('activity', $payload))->assertCreated();
        $this->getJson('/api/v2/activity-reviews?from=2026-10-29&until=2026-10-29&worker_id='.$worker->public_id)
            ->assertOk()->assertJsonCount(1, 'data')->assertJsonPath('data.0.activity.id', $id);
        $this->getJson('/api/v2/activity-reviews?until=2026-10-28')->assertOk()->assertJsonCount(0, 'data');
        $this->getJson($path)->assertOk()->assertJsonPath('activity.manual_ec', '1.250000')
            ->assertJsonPath('activity.reference_snapshot.checks.manual_ec.status', 'unavailable')->assertJsonPath('status', 'pending');
        $this->postJson('/api/v2/activity-reviews', [...$review, 'payload' => [...$review['payload'], 'reason' => '  ']])->assertUnprocessable();
        $receipt = $this->postJson('/api/v2/activity-reviews', $review)->assertCreated()
            ->assertJsonPath('data.reviewer_id', $manager->public_id)->json();
        $this->getJson('/api/v2/activity-reviews')->assertJsonCount(0, 'data');
        $this->getJson('/api/v2/activity-reviews?status=invalid')->assertJsonCount(1, 'data');

        $this->asUser($otherManager)->postJson('/api/v2/activity-reviews', $this->operation('activity_review', $review['payload']))
            ->assertConflict()->assertJsonPath('code', 'activity_already_reviewed');
        $this->asUser($otherWorker)->getJson($path)->assertNotFound();
        $otherFeed = $this->getJson('/api/v2/sync/changes?activity_reviews=1&limit=100')->assertOk()->json('data');
        $this->assertNotContains('activity_review', array_column($otherFeed, 'entity_type'));
        $statusFeed = $this->getJson('/api/v2/sync/changes?activity_reviews=1&measurement_review_status=1&limit=100')->assertOk()->json('data');
        $this->assertEquals([
            'id' => $review['entity_id'], 'version' => 1, 'activity_id' => $id, 'activity_version' => 1, 'decision' => 'invalid',
        ], collect($statusFeed)->firstWhere('entity_type', 'activity_review')['data']);
        $this->asUser($worker)->getJson($path)->assertOk()->assertJsonPath('decision.reason', $review['payload']['reason']);
        $oldFeed = $this->getJson('/api/v2/sync/changes?limit=100')->assertOk()->json();
        $this->assertNotContains('activity_review', array_column($oldFeed['data'], 'entity_type'));
        $newFeed = $this->getJson('/api/v2/sync/changes?activity_reviews=1&measurement_review_status=1&limit=100&cursor='.$oldFeed['next_cursor'])->assertOk()->json('data');
        $this->assertContains('activity_review', array_column($newFeed, 'entity_type'));
        $this->assertSame($review['payload']['reason'], collect($newFeed)->firstWhere('entity_type', 'activity_review')['data']['reason']);

        $correction = $this->operation('activity', [...$payload, 'manual_ppm' => '610', 'reason' => 'Uji koreksi', 'reference_policy' => 'preserve'], 'update', $id, 1);
        $this->patchJson('/api/v2/activities/'.$id, $correction)->assertOk()->assertJsonPath('version', 2);
        $this->getJson($path)->assertOk()->assertJsonPath('status', 'pending')->assertJsonPath('history.0.activity_version', 1);
        $this->getJson($path.'?activity_version=1')->assertOk()->assertJsonPath('status', 'invalid')->assertJsonPath('activity.manual_ppm', '600.000000');

        $this->asUser($manager)->postJson('/api/v2/activity-reviews', $review)->assertCreated()->assertExactJson($receipt);
        $this->postJson('/api/v2/activity-reviews', $this->operation('activity_review', $review['payload']))
            ->assertConflict()->assertJsonPath('code', 'activity_revision_changed');
        $next = $this->operation('activity_review', ['activity_id' => $id, 'activity_version' => 2, 'decision' => 'valid']);
        $this->postJson('/api/v2/activity-reviews', $next)->assertCreated();
        $this->getJson($path.'?limit=1')->assertOk()->assertJsonCount(1, 'history')->assertJsonPath('status', 'valid');
        $this->assertDatabaseCount('activity_reviews', 2);
        $this->assertSame(2, DB::table('audit_logs')->where('entity_type', 'activity_review')->count());
        $this->assertSame(2, DB::table('sync_operations')->where('entity_type', 'activity_review')->count());

        DB::table('greenhouse_assignments')->where('id', $assignment['id'])->update(['active' => false]);
        // A manager reaches every greenhouse; the assignment only says who works there.
        $this->asUser($otherManager)->getJson($path)->assertOk();
        $this->asUser($manager)->postJson('/api/v2/activity-reviews', [...$next, 'sync_epoch' => (string) Str::uuid()])
            ->assertConflict()->assertJsonPath('code', 'epoch_mismatch');
    }
}
