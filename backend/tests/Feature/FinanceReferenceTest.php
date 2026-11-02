<?php

namespace Tests\Feature;

use App\Domain\Cultivation\ReferenceService;
use Illuminate\Support\Facades\DB;
use Tests\FinanceTestCase;

class FinanceReferenceTest extends FinanceTestCase
{
    private function target(string $group, array $override = []): array
    {
        return [...['planting_group_id' => $group, 'activity_type' => 'nutrition', 'parameter' => 'manual_ppm', 'unit' => 'ppm',
            'target_value' => '900', 'minimum' => null, 'maximum' => null, 'tolerance' => null,
            'effective_from' => '2026-10-01T00:00:00+07:00', 'effective_until' => null], ...$override];
    }

    public function test_target_interval_overlap_range_unit_and_decimal_rules_are_authoritative(): void
    {
        $ctx = $this->context($this->account());
        $target = $this->createMaster('targets', $this->target($ctx['group']['id']));
        $this->postJson('/api/v2/targets', $this->operation('targets', $this->target($ctx['group']['id'])))
            ->assertUnprocessable();
        foreach ([['target_value' => 'NaN'], ['unit' => 'pH'], ['target_value' => null, 'minimum' => '950', 'maximum' => '900'], ['parameter' => 'manual_ph', 'unit' => 'pH', 'target_value' => '14.1']] as $invalid) {
            $this->postJson('/api/v2/targets', $this->operation('targets', $this->target($ctx['group']['id'], $invalid)))->assertUnprocessable();
        }
        $this->getJson('/api/v2/targets/'.$target['id'].'/versions')->assertJsonCount(1, 'data');
        $emptySchedule = $this->operation('schedules', ['planting_group_id' => $ctx['group']['id'], 'activity_type' => 'general', 'planned_at' => '2026-10-29T08:00:00+07:00', 'notes' => '']);
        $this->postJson('/api/v2/schedules', $emptySchedule)->assertCreated();
        $emptySchedule['payload']['notes'] = ' ';
        $this->postJson('/api/v2/schedules', $emptySchedule)->assertConflict()->assertJsonPath('code', 'operation_conflict');
    }

    public function test_reference_revisions_remain_immutable_and_snapshots_use_occurred_time(): void
    {
        $ctx = $this->context($this->account());
        $target = $this->createMaster('targets', $this->target($ctx['group']['id']));
        $group = DB::table('planting_groups')->where('id', $ctx['group']['id'])->first();
        $snapshot = app(ReferenceService::class)->snapshot($group, 'nutrition', '2026-10-29T08:00:00+07:00', ['manual_ppm' => '950']);
        $this->assertSame('delta_only', $snapshot['checks']['manual_ppm']['status']);
        $this->assertSame('unavailable', $snapshot['checks']['manual_ph']['status']);
        $payload = $this->target($ctx['group']['id'], ['target_value' => '1000']);
        unset($payload['planting_group_id']);
        $payload += ['active' => true, 'reason' => 'Ubah target eksplisit'];
        $this->patchJson('/api/v2/targets/'.$target['id'], $this->operation('targets', $payload, 'update', $target['id'], 1))->assertOk();
        $versions = $this->getJson('/api/v2/targets/'.$target['id'].'/versions')->assertJsonCount(2, 'data')->json('data');
        $this->assertSame('900.000000', $versions[0]['target_value']);
        $this->assertSame('1000.000000', $versions[1]['target_value']);
        $this->assertSame('900.000000', $snapshot['checks']['manual_ppm']['target']['target_value']);
        $early = app(ReferenceService::class)->snapshot($group, 'nutrition', '2026-09-30T08:00:00+07:00', ['manual_ppm' => '950']);
        $this->assertSame('unavailable', $early['checks']['manual_ppm']['status']);
    }

    public function test_schedule_read_is_scoped_and_does_not_imply_completion(): void
    {
        $ctx = $this->context($this->account());
        $schedule = $this->createMaster('schedules', ['planting_group_id' => $ctx['group']['id'], 'activity_type' => 'spray', 'planned_at' => '2026-10-29T08:00:00+07:00', 'notes' => 'Uji bertahap']);
        $this->assertArrayNotHasKey('completed', $schedule);
        $this->asUser($this->account('worker'))->getJson('/api/v2/schedules/'.$schedule['id'])->assertNotFound();
        $this->getJson('/api/v2/targets')->assertJsonCount(0, 'data');
        $this->postJson('/api/v2/schedules', $this->operation('schedules', ['planting_group_id' => $ctx['group']['id'], 'activity_type' => 'general', 'planned_at' => '2026-10-29T08:00:00+07:00', 'notes' => 'Uji']))->assertForbidden();
    }
}
