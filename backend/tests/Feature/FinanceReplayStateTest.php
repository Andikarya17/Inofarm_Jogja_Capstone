<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\DB;
use Tests\FinanceTestCase;

class FinanceReplayStateTest extends FinanceTestCase
{
    public function test_receipts_survive_closed_inactive_state_but_new_writes_and_revoked_scope_do_not(): void
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $worker = $this->account('worker');
        $this->createMaster('greenhouse_assignments', ['greenhouse_id' => $ctx['gh']['id'], 'user_id' => $worker->public_id,
            'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);
        $reference = $this->operation('schedules', ['planting_group_id' => $ctx['group']['id'], 'activity_type' => 'general', 'planned_at' => '2026-10-29T08:00:00+07:00', 'notes' => 'Jadwal sintetis']);
        $referenceReceipt = $this->postJson('/api/v2/schedules', $reference)->assertCreated()->json();
        $master = $this->operation('varieties', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Varietas replay sintetis']);
        $masterReceipt = $this->postJson('/api/v2/varieties', $master)->assertCreated()->json();
        $activity = $this->operation('activity', ['planting_group_id' => $ctx['group']['id'], 'activity_type' => 'general', 'occurred_at' => '2026-10-29T08:00:00+07:00', 'work_state' => 'completed', 'materials' => [], 'notes' => 'Aktivitas replay sintetis']);
        $activityReceipt = $this->asUser($worker)->postJson('/api/v2/activities', $activity)->assertCreated()->json();
        $this->asUser($manager)->postJson('/api/v2/seasons/'.$ctx['season']['id'].'/close', $this->operation('seasons', ['reason' => 'Tutup sintetis'], 'close', $ctx['season']['id'], 1))->assertOk();
        $this->asUser($worker)->postJson('/api/v2/sync/operations', $activity)->assertCreated()->assertExactJson($activityReceipt);
        $this->postJson('/api/v2/activities', $this->operation('activity', $activity['payload']))->assertConflict()->assertJsonPath('code', 'season_closed');
        $this->asUser($manager)->postJson('/api/v2/schedules', $reference)->assertCreated()->assertExactJson($referenceReceipt);
        $this->postJson('/api/v2/schedules', $this->operation('schedules', $reference['payload']))->assertConflict()->assertJsonPath('code', 'season_closed');
        $this->patchJson('/api/v2/greenhouses/'.$ctx['gh']['id'], $this->operation('greenhouses', ['name' => 'GH nonaktif sintetis', 'active' => false, 'reason' => 'Uji histori'], 'update', $ctx['gh']['id'], 1))->assertOk();
        $this->postJson('/api/v2/varieties', $master)->assertCreated()->assertExactJson($masterReceipt);
        $this->postJson('/api/v2/varieties', $this->operation('varieties', $master['payload']))->assertUnprocessable();
        $this->postJson('/api/v2/schedules', $reference)->assertCreated()->assertExactJson($referenceReceipt);
        $this->asUser($worker)->postJson('/api/v2/activities', $activity)->assertCreated()->assertExactJson($activityReceipt);
        DB::table('greenhouse_assignments')->where('user_id', $worker->id)->update(['active' => false]);
        $this->postJson('/api/v2/activities', $activity)->assertNotFound();
        foreach ([$activity, $reference, $master] as $op) {
            $this->assertSame(1, DB::table('sync_operations')->where('operation_id', $op['operation_id'])->count());
            $this->assertSame(1, DB::table('audit_logs')->where('operation_id', $op['operation_id'])->count());
        }
    }

    public function test_greenhouse_create_replay_rechecks_assignment_after_creation(): void
    {
        $manager = $this->account();
        $this->asUser($manager);
        $operation = $this->operation('greenhouses', ['name' => 'GH replay scope sintetis']);
        $receipt = $this->postJson('/api/v2/greenhouses', $operation)->assertCreated()->json();
        $this->postJson('/api/v2/greenhouses', $operation)->assertCreated()->assertExactJson($receipt);
        DB::table('greenhouse_assignments')->where('user_id', $manager->id)->where('greenhouse_id', $operation['entity_id'])->update(['active' => false]);
        $this->postJson('/api/v2/greenhouses', $operation)->assertNotFound();
        $this->assertSame(1, DB::table('sync_operations')->where('operation_id', $operation['operation_id'])->count());
    }
}
