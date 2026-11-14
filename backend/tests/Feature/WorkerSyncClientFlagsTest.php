<?php

namespace Tests\Feature;

use Tests\FinanceTestCase;

class WorkerSyncClientFlagsTest extends FinanceTestCase
{
    // Android selalu mengirim ketiga flag ini; cabang pekerja membaca tabel harvest_reviews (migration 20).
    public function test_assigned_worker_pulls_changes_with_every_flag_the_android_client_sends(): void
    {
        $manager = $this->account();
        $worker = $this->account('worker');
        $gh = $this->context($manager)['gh']['id'];
        $this->asUser($manager)->createMaster('greenhouse_assignments', ['greenhouse_id' => $gh, 'user_id' => $worker->public_id,
            'effective_from' => now()->subMinute()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);

        $feed = $this->asUser($worker)
            ->getJson('/api/v2/sync/changes?activity_reviews=1&measurement_review_status=1&harvest_reviews=1')
            ->assertOk()->assertJsonCount(1, 'scope')->json();
        $this->getJson('/api/v2/sync/changes?activity_reviews=1&measurement_review_status=1&harvest_reviews=1&cursor='
            .urlencode($feed['next_cursor']))->assertOk()->assertJsonCount(0, 'data');
    }
}
