<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\DB;
use Tests\FinanceTestCase;

class FinanceMeasurementTest extends FinanceTestCase
{
    private function payload(string $group, array $extra = []): array
    {
        return [...['planting_group_id' => $group, 'activity_type' => 'measurement', 'occurred_at' => '2026-11-02T08:00:00+07:00',
            'manual_ec' => '1.25', 'manual_ppm' => '625.5', 'manual_ph' => '6.1', 'work_state' => 'completed', 'materials' => [], 'notes' => ''], ...$extra];
    }

    public function test_pair_without_materials_is_atomic_replayable_scoped_and_audited(): void
    {
        $manager = $this->account(); $ctx = $this->context($manager);
        $worker = $this->account('worker');
        $assignment = $this->createMaster('greenhouse_assignments', ['greenhouse_id' => $ctx['gh']['id'], 'user_id' => $worker->public_id,
            'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);
        $this->asUser($worker);
        $op = $this->operation('activity', $this->payload($ctx['group']['id']));
        $receipt = $this->postJson('/api/v2/sync/operations', $op)->assertCreated()->assertJsonPath('data.manual_ec', '1.250000')
            ->assertJsonPath('data.manual_ppm', '625.500000')->assertJsonPath('data.recorded_by', $worker->public_id)->json();
        $this->postJson('/api/v2/activities', $op)->assertCreated()->assertExactJson($receipt);
        $this->assertDatabaseCount('activities', 1);
        $this->getJson('/api/v2/activities/'.$op['entity_id'].'/audit')->assertOk()->assertJsonCount(1, 'data');
        $url = '/api/v2/measurements/latest?planting_group_id='.$ctx['group']['id'];
        $this->getJson($url)->assertOk()->assertJsonPath('data.id', $op['entity_id']);
        $this->asUser($manager);
        $edit = $this->operation('activity', [...$op['payload'], 'manual_ec' => '1.3', 'reason' => 'Koreksi ukur', 'reference_policy' => 'preserve'], 'update', $op['entity_id'], 1);
        $this->patchJson('/api/v2/activities/'.$op['entity_id'], $edit)->assertOk()->assertJsonPath('data.manual_ec', '1.300000');
        $edit['operation_id'] = (string) \Illuminate\Support\Str::uuid();
        $this->patchJson('/api/v2/activities/'.$op['entity_id'], $edit)->assertConflict();
        $this->postJson('/api/v2/activities/'.$op['entity_id'].'/cancel', $this->operation('activity', ['reason' => 'Salah ukur'], 'cancel', $op['entity_id'], 2))->assertOk();
        $this->getJson($url)->assertOk()->assertJsonPath('data', null);
        DB::table('greenhouse_assignments')->where('id', $assignment['id'])->update(['active' => false]);
        $this->asUser($worker)->getJson($url)->assertNotFound();
        $this->asUser($this->account())->getJson($url)->assertNotFound();
    }

    public function test_pair_validation_missing_zero_precision_actor_and_legacy_null_ec(): void
    {
        $ctx = $this->context($this->account());
        foreach ([['manual_ec' => null], ['manual_ppm' => null], ['manual_ec' => '1,2'], ['manual_ec' => 'NaN'], ['manual_ec' => '0.0000001'], ['manual_ec' => '-1'], ['manual_ec' => 1.2], ['work_state' => 'skipped', 'skipped_reason' => 'uji'], ['recorded_by' => 'forged']] as $bad) {
            $this->postJson('/api/v2/activities', $this->operation('activity', $this->payload($ctx['group']['id'], $bad)))->assertUnprocessable();
        }
        $this->assertDatabaseCount('activities', 0);
        $this->postJson('/api/v2/activities', $this->operation('activity', $this->payload($ctx['group']['id'], ['manual_ec' => '0', 'manual_ppm' => '0'])))->assertCreated();
        $legacy = $this->operation('activity', $this->payload($ctx['group']['id'], ['activity_type' => 'general', 'manual_ec' => null, 'notes' => 'Histori pengukuran manual', 'occurred_at' => '2026-11-02T10:00:00+07:00']));
        unset($legacy['payload']['manual_ec']);
        $this->postJson('/api/v2/activities', $legacy)->assertCreated()->assertJsonPath('data.manual_ec', null);
        $this->getJson('/api/v2/measurements/latest?planting_group_id='.$ctx['group']['id'])->assertOk()->assertJsonPath('data.id', $legacy['entity_id'])->assertJsonPath('data.manual_ec', null);
    }

    public function test_latest_uses_measurement_time_wib_tie_break_and_scopes_beyond_history_page(): void
    {
        $this->travelTo(\Carbon\Carbon::parse('2026-11-02T00:10:00+07:00'));
        $ctx = $this->context($this->account());
        $url = '/api/v2/measurements/latest?planting_group_id='.$ctx['group']['id'];
        $this->getJson($url)->assertJsonPath('data', null);
        $old = $this->operation('activity', $this->payload($ctx['group']['id'], ['occurred_at' => '2026-10-28T23:59:00+07:00']));
        $this->postJson('/api/v2/activities', $old)->assertCreated(); $this->getJson($url)->assertJsonPath('data.id', $old['entity_id']);
        $yesterday = $this->operation('activity', $this->payload($ctx['group']['id'], ['occurred_at' => '2026-11-01T23:59:00+07:00']));
        $this->postJson('/api/v2/activities', $yesterday)->assertCreated(); $this->getJson($url)->assertJsonPath('data.id', $yesterday['entity_id']);
        $a = $this->operation('activity', $this->payload($ctx['group']['id'], ['occurred_at' => '2026-11-02T00:01:00+07:00']));
        $b = $this->operation('activity', $a['payload']);
        foreach ([$b, $a] as $op) $this->postJson('/api/v2/activities', $op)->assertCreated();
        $expected = max($a['entity_id'], $b['entity_id']);
        $this->getJson($url)->assertJsonPath('data.id', $expected);
        // A delayed older write and a future calendar day never supersede today's reading.
        foreach (['2026-11-01T23:58:00+07:00', '2026-11-03T00:00:00+07:00'] as $at) $this->postJson('/api/v2/activities', $this->operation('activity', $this->payload($ctx['group']['id'], ['occurred_at' => $at])))->assertCreated();
        for ($i=0; $i<52; $i++) $this->postJson('/api/v2/activities', $this->operation('activity', $this->payload($ctx['group']['id'], ['activity_type' => 'general', 'manual_ec' => null, 'manual_ppm' => null, 'notes' => 'Pekerjaan tanpa pengukuran'])))->assertCreated();
        $this->getJson($url)->assertJsonPath('data.id', $expected);
        $this->travelTo(\Carbon\Carbon::parse('2026-11-03T00:01:00+07:00'));
        $this->getJson($url)->assertJsonPath('data.occurred_at', '2026-11-02T17:00:00.000000Z');
        $this->travelBack();
    }
}
