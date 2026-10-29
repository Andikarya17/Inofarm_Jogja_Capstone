<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Database\Events\QueryExecuted;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use RuntimeException;
use Tests\FinanceTestCase;

class FinanceActivitySyncTest extends FinanceTestCase
{
    private function payload(string $group, array $override = []): array
    {
        return [...['planting_group_id' => $group, 'schedule_id' => null, 'activity_type' => 'general', 'occurred_at' => '2026-10-29T08:00:00+07:00',
            'manual_ppm' => null, 'manual_ph' => null, 'actual_stage' => null, 'work_state' => 'completed', 'skipped_reason' => null, 'materials' => [], 'notes' => 'Pembersihan sintetis'], ...$override];
    }

    private function assignedWorker(array $ctx): User
    {
        $worker = $this->account('worker');
        $this->createMaster('greenhouse_assignments', ['greenhouse_id' => $ctx['gh']['id'], 'user_id' => $worker->public_id,
            'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);

        return $worker;
    }

    public function test_worker_operation_replay_and_direct_route_share_receipt_and_scoped_history(): void
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $worker = $this->assignedWorker($ctx);
        $this->asUser($worker);
        $op = $this->operation('activity', $this->payload($ctx['group']['id']));
        $first = $this->postJson('/api/v2/sync/operations', $op)->assertCreated()->assertJsonPath('data.recorded_by', $worker->public_id)->json();
        $this->postJson('/api/v2/activities', $op)->assertCreated()->assertExactJson($first);
        $this->assertDatabaseCount('activities', 1);
        $this->assertDatabaseCount('activity_reference_snapshots', 1);
        $op['payload']['notes'] .= ' different';
        $this->postJson('/api/v2/sync/operations', $op)->assertConflict()->assertJsonPath('code', 'operation_conflict');
        $op['operation_id'] = (string) Str::uuid();
        $op['payload']['recorded_by'] = $manager->public_id;
        $this->postJson('/api/v2/sync/operations', $op)->assertUnprocessable();
        $this->asUser($manager)->getJson('/api/v2/activities')->assertJsonCount(1, 'data');
        $other = $this->context($this->account());
        $this->getJson('/api/v2/activities/'.$first['entity_id'])->assertNotFound();
        $this->postJson('/api/v2/sync/operations', $this->operation('activity', $this->payload($ctx['group']['id'])))->assertNotFound();
    }

    public function test_structural_validation_multimaterial_zero_missing_and_skipped_are_distinct(): void
    {
        $ctx = $this->context($this->account());
        $unit = $this->createMaster('units', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'gram uji', 'dimension' => 'mass']);
        $a = $this->createMaster('materials', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Bahan A uji', 'dimension' => 'mass']);
        $b = $this->createMaster('materials', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Bahan B uji', 'dimension' => 'mass']);
        $materials = array_map(fn ($m) => ['material_id' => $m['id'], 'unit_id' => $unit['id'], 'quantity' => '0'], [$a, $b]);
        $this->postJson('/api/v2/activities', $this->operation('activity', $this->payload($ctx['group']['id'], ['activity_type' => 'spray', 'materials' => $materials])))
            ->assertCreated()->assertJsonCount(2, 'data.materials');
        foreach ([['manual_ph' => '14.000001'], ['manual_ppm' => '-1'], ['manual_ph' => 'NaN'], ['manual_ph' => 6.0], ['activity_type' => 'nutrition'], ['work_state' => 'skipped'], ['activity_type' => 'stage']] as $invalid) {
            $this->postJson('/api/v2/activities', $this->operation('activity', $this->payload($ctx['group']['id'], $invalid)))->assertUnprocessable();
        }
        $this->postJson('/api/v2/activities', $this->operation('activity', $this->payload($ctx['group']['id'], ['activity_type' => 'nutrition', 'manual_ppm' => '0', 'manual_ph' => '0', 'materials' => $materials])))
            ->assertCreated()->assertJsonPath('data.manual_ppm', '0.000000')->assertJsonPath('data.reference_snapshot.checks.manual_ppm.status', 'unavailable');
        $this->postJson('/api/v2/activities', $this->operation('activity', $this->payload($ctx['group']['id'], ['work_state' => 'skipped', 'skipped_reason' => 'Hujan sintetis'])))
            ->assertCreated()->assertJsonPath('data.work_state', 'skipped');
    }

    public function test_worker_own_correction_manager_cancel_version_conflict_and_closed_queue(): void
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $worker = $this->assignedWorker($ctx);
        $other = $this->assignedWorker($ctx);
        $p = $this->payload($ctx['group']['id']);
        $this->asUser($worker);
        $op = $this->operation('activity', $p);
        $this->postJson('/api/v2/activities', $op)->assertCreated();
        $edit = $this->operation('activity', [...$p, 'notes' => 'Koreksi uji', 'reason' => 'Keterangan lebih lengkap', 'reference_policy' => 'preserve'], 'update', $op['entity_id'], 1);
        $this->asUser($other)->patchJson('/api/v2/activities/'.$op['entity_id'], $edit)->assertForbidden();
        $this->asUser($worker)->patchJson('/api/v2/activities/'.$op['entity_id'], $edit)->assertOk()->assertJsonPath('version', 2);
        $edit['operation_id'] = (string) Str::uuid();
        $this->patchJson('/api/v2/activities/'.$op['entity_id'], $edit)->assertConflict()->assertJsonPath('current_version', 2);
        $cancel = $this->operation('activity', ['reason' => 'Catatan keliru uji'], 'cancel', $op['entity_id'], 2);
        $this->postJson('/api/v2/activities/'.$op['entity_id'].'/cancel', $cancel)->assertForbidden();
        $this->asUser($manager)->postJson('/api/v2/activities/'.$op['entity_id'].'/cancel', $cancel)->assertOk()->assertJsonPath('data.record_state', 'cancelled');
        $this->getJson('/api/v2/activities/'.$op['entity_id'].'/audit')->assertJsonCount(3, 'data')->assertJsonPath('data.0.actor_name', $worker->name)->assertJsonPath('data.2.actor_id', $manager->public_id);
        $this->postJson('/api/v2/seasons/'.$ctx['season']['id'].'/close', $this->operation('seasons', ['reason' => 'Uji tutup'], 'close', $ctx['season']['id'], 1))->assertOk();
        $this->asUser($worker)->postJson('/api/v2/sync/operations', $this->operation('activity', $p))->assertConflict()->assertJsonPath('code', 'season_closed');
    }

    public function test_history_filters_wib_dates_scope_state_and_audit_pages(): void
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $worker = $this->assignedWorker($ctx);
        $this->asUser($worker);
        $first = $this->postJson('/api/v2/activities', $this->operation('activity', $this->payload($ctx['group']['id'], ['occurred_at' => '2026-10-29T00:30:00+07:00'])))->assertCreated()->json();
        $second = $this->postJson('/api/v2/activities', $this->operation('activity', $this->payload($ctx['group']['id'], ['occurred_at' => '2026-10-28T23:30:00+07:00'])))->assertCreated()->json();
        $query = http_build_query(['greenhouse_id' => $ctx['gh']['id'], 'season_id' => $ctx['season']['id'], 'from' => '2026-10-29', 'until' => '2026-10-29', 'record_state' => 'active']);
        $this->getJson('/api/v2/activities?'.$query)->assertJsonCount(1, 'data')->assertJsonPath('data.0.id', $first['entity_id']);
        $this->getJson('/api/v2/activities?until=2026-10-28')->assertJsonCount(1, 'data')->assertJsonPath('data.0.id', $second['entity_id']);
        $this->getJson('/api/v2/activities?from=2026-10-29&until=2026-10-28')->assertUnprocessable();
        $this->asUser($manager)->postJson('/api/v2/activities/'.$first['entity_id'].'/cancel', $this->operation('activity', ['reason' => 'Uji filter pembatalan'], 'cancel', $first['entity_id'], 1))->assertOk();
        $this->getJson('/api/v2/activities?record_state=cancelled')->assertJsonCount(1, 'data')->assertJsonPath('data.0.id', $first['entity_id']);
        $audit = $this->getJson('/api/v2/activities/'.$first['entity_id'].'/audit?limit=1')->assertJsonCount(1, 'data')->assertJsonPath('data.0.actor_id', $worker->public_id)->json();
        $this->assertNotNull($audit['next_cursor']);
        $this->getJson('/api/v2/activities/'.$first['entity_id'].'/audit?limit=1&cursor='.$audit['next_cursor'])->assertJsonCount(1, 'data')->assertJsonPath('data.0.action', 'cancel')->assertJsonPath('next_cursor', null);
        $foreign = $this->context($this->account());
        $this->getJson('/api/v2/activities?season_id='.$ctx['season']['id'])->assertJsonCount(0, 'data');
        $this->getJson('/api/v2/activities/'.$first['entity_id'].'/audit')->assertNotFound();
    }

    public function test_activity_snapshot_is_immutable_after_group_dates_change_and_explicit_refresh_is_required(): void
    {
        $ctx = $this->context($this->account());
        $p = $this->payload($ctx['group']['id']);
        $op = $this->operation('activity', $p);
        $first = $this->postJson('/api/v2/activities', $op)->assertCreated()->json('data.reference_snapshot');
        $this->assertSame(29, $first['age']['hss']);
        $g = ['name' => 'Tanggal dikoreksi', 'variety_id' => $ctx['variety']['id'], 'population' => 100, 'sowing_date' => '2026-10-02', 'transplant_date' => null, 'active' => true, 'reason' => 'Koreksi tanggal'];
        $this->patchJson('/api/v2/planting-groups/'.$ctx['group']['id'], $this->operation('planting_groups', $g, 'update', $ctx['group']['id'], 1))->assertOk();
        $this->getJson('/api/v2/activities/'.$op['entity_id'])->assertJsonPath('reference_snapshot.age.hss', 29);
        $edit = $this->operation('activity', [...$p, 'reason' => 'Koreksi keterangan', 'reference_policy' => 'preserve'], 'update', $op['entity_id'], 1);
        $this->patchJson('/api/v2/activities/'.$op['entity_id'], $edit)->assertOk()->assertJsonPath('data.reference_snapshot.age.hss', 29);
        $edit['base_version'] = 2;
        $edit['operation_id'] = (string) Str::uuid();
        $edit['payload']['reference_policy'] = 'refresh';
        $this->patchJson('/api/v2/activities/'.$op['entity_id'], $edit)->assertOk()->assertJsonPath('data.reference_snapshot.age.hss', 28);
        $old = json_decode(DB::table('activity_reference_snapshots')->where('activity_id', $op['entity_id'])->where('activity_version', 1)->value('body'), true);
        $this->assertSame(29, $old['age']['hss']);
    }

    public function test_pull_has_stable_high_water_scoped_audience_and_account_epoch_bound_cursor(): void
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $worker = $this->assignedWorker($ctx);
        $this->createMaster('expense_categories', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Privat pengelola']);
        $this->asUser($worker);
        $first = $this->getJson('/api/v2/sync/changes?limit=2')->assertOk()->json();
        $op = $this->operation('activity', $this->payload($ctx['group']['id']));
        $this->postJson('/api/v2/activities', $op)->assertCreated();
        $data = $first['data'];
        $page = $first;
        while ($page['has_more']) {
            $page = $this->getJson('/api/v2/sync/changes?limit=2&cursor='.urlencode($page['next_cursor']))->assertOk()->json();
            $data = [...$data, ...$page['data']];
        }
        $this->assertNotContains('expense_categories', array_column($data, 'entity_type'));
        $this->assertNotContains('greenhouse_assignments', array_column($data, 'entity_type'));
        $this->assertNotContains($op['entity_id'], array_column($data, 'entity_id'));
        $next = $this->getJson('/api/v2/sync/changes?cursor='.urlencode($page['next_cursor']))->assertOk()->json();
        $this->assertContains($op['entity_id'], array_column($next['data'], 'entity_id'));
        $this->getJson('/api/v2/sync/changes?cursor=tampered')->assertUnprocessable();
        $this->asUser($manager)->getJson('/api/v2/sync/changes?cursor='.urlencode($page['next_cursor']))->assertUnprocessable();
        DB::table('sync_state')->where('id', 1)->update(['epoch' => (string) Str::uuid()]);
        $this->asUser($worker)->getJson('/api/v2/sync/changes?cursor='.urlencode($page['next_cursor']))->assertConflict()->assertJsonPath('code', 'epoch_mismatch');
    }

    public function test_failure_after_receipt_sql_rolls_back_all_mutation_components(): void
    {
        $ctx = $this->context($this->account());
        $counts = [];
        foreach (['audit_logs', 'sync_operations', 'change_feed'] as $table) {
            $counts[$table] = DB::table($table)->count();
        }
        DB::listen(function (QueryExecuted $event) {
            if (str_starts_with($event->sql, 'insert into `sync_operations`')) {
                throw new RuntimeException('Synthetic fault after receipt SQL');
            }
        });
        $this->postJson('/api/v2/activities', $this->operation('activity', $this->payload($ctx['group']['id'])))->assertStatus(500)->assertJsonPath('code', 'server_error');
        $this->assertDatabaseCount('activities', 0);
        $this->assertDatabaseCount('activity_reference_snapshots', 0);
        foreach ($counts as $table => $count) {
            $this->assertDatabaseCount($table, $count);
        }
    }
}
