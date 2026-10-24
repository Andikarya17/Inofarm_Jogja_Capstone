<?php

namespace Tests\Feature;

use App\Models\ConfigVersion;
use App\Models\Device;
use App\Models\Event;
use App\Models\Tank;
use App\Models\User;
use App\Services\Input;
use Carbon\CarbonImmutable;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\TestCase;

class InofarmTest extends TestCase
{
    use RefreshDatabase;

    private User $owner;

    private Device $device;

    private string $deviceToken = 'fixture-device-token-only';

    protected function setUp(): void
    {
        parent::setUp();
        if (config('database.connections.mysql.database') !== 'inofarm_test') {
            throw new \RuntimeException('Tests require dedicated inofarm_test database.');
        }
        $this->travelTo(CarbonImmutable::parse('2026-10-10T00:00:00Z'));
        $tank = Tank::create(['name' => 'TEST ONLY']);
        $this->owner = User::create(['name' => 'Fixture owner', 'email' => 'test@example.invalid', 'password' => 'test-password-only', 'tank_id' => $tank->id, 'role' => 'owner']);
        $this->device = Device::create(['id' => (string) Str::uuid(), 'tank_id' => $tank->id, 'name' => 'TEST ONLY', 'token_hash' => hash('sha256', $this->deviceToken)]);
    }

    private function ownerRequest()
    {
        return $this->actingAs($this->owner, 'sanctum');
    }

    private function sendEvent(array $data, string $kind = 'record')
    {
        return $this->withHeader('Authorization', 'Bearer '.$this->deviceToken)->postJson('/api/v1/device/events/'.$kind, $data);
    }

    private function fixture(): array
    {
        return ['schemaVersion' => '1', 'eventId' => (string) Str::uuid(), 'deviceId' => $this->device->id, 'clockSource' => 'rtc', 'clockStatus' => 'trusted', 'confirmedAt' => '2026-10-10T01:00:00Z', 'measuredAt' => '2026-10-10T00:59:00Z', 'phase' => 'vegetatif', 'manualPpm' => 900, 'sensorSnapshot' => ['capturedAt' => '2026-10-10T01:00:00Z', 'readings' => array_combine(Input::METRICS, [25, 6, 27, 70]), 'quality' => array_fill_keys(Input::METRICS, 'ok')]];
    }

    private function configFixture(): array
    {
        return ['timezone' => 'Asia/Jakarta', 'schedules' => [['days' => [1, 2, 3, 4, 5, 6, 7], 'time' => '08:00']], 'earlyMinutes' => 0, 'lateMinutes' => 15, 'sensorMaxAgeSeconds' => 60, 'deviceStaleSeconds' => 60, 'ppmRanges' => ['vegetatif' => ['min' => 800, 'max' => 1000], 'generatif' => ['min' => 1100, 'max' => 1400]], 'sensorBounds' => array_combine(Input::METRICS, [['min' => 20, 'max' => 28], ['min' => 5.5, 'max' => 6.5], ['min' => 20, 'max' => 32], ['min' => 40, 'max' => 90]])];
    }

    private function configure(): void
    {
        $this->ownerRequest()->putJson('/api/v1/config', ['baseVersion' => null, 'config' => $this->configFixture()])->assertOk();
        $this->ownerRequest()->putJson('/api/v1/active-phase', ['baseVersion' => null, 'phase' => 'vegetatif'])->assertOk();
        $this->travelTo(CarbonImmutable::parse('2026-10-10T02:00:00Z'));
    }

    public function test_empty_production_and_auth_boundaries(): void
    {
        $this->assertSame('+00:00', DB::selectOne('SELECT @@session.time_zone AS zone')->zone);
        $this->getJson('/api/v1/dashboard')->assertUnauthorized();
        $this->ownerRequest()->getJson('/api/v1/config')->assertJsonPath('config', null)->assertJsonPath('activePhase', null);
        $this->withHeader('Authorization', 'Bearer wrong')->postJson('/api/v1/device/events/record', $this->fixture())->assertUnauthorized();
        $this->postJson('/api/v1/login', ['email' => $this->owner->email, 'password' => 'incorrect'])->assertUnprocessable();
        $token = $this->postJson('/api/v1/login', ['email' => $this->owner->email, 'password' => 'test-password-only'])->assertOk()->json('token');
        $this->assertStringNotContainsString('test-password-only', $token);
    }

    public function test_multi_reason_validation_missing_failed_and_phase_warning(): void
    {
        $this->configure();
        $event = $this->fixture();
        $event['phase'] = 'generatif';
        $event['manualPpm'] = 500;
        $event['confirmedAt'] = '2026-10-10T01:30:00Z';
        $event['sensorSnapshot']['quality']['solutionPh'] = 'failed';
        $event['sensorSnapshot']['readings']['solutionPh'] = null;
        $response = $this->sendEvent($event)->assertCreated()->assertJsonPath('event.validation_result.status', 'incomplete');
        $codes = array_column($response->json('event.validation_result.checks'), 'code');
        foreach (['phase_mismatch', 'ppm_low', 'recorded_late', 'sensor_failed', 'sensor_stale'] as $code) {
            $this->assertContains($code, $codes);
        }
        $this->assertSame('generatif', $response->json('event.phase'));
    }

    public function test_valid_inclusive_bounds_replay_conflict_and_snapshot(): void
    {
        $this->configure();
        $event = $this->fixture();
        $event['manualPpm'] = 800;
        $response = $this->sendEvent($event)->assertCreated()->assertJsonPath('event.validation_result.status', 'valid');
        $this->sendEvent($event)->assertOk()->assertJsonPath('duplicate', true);
        $event['manualPpm'] = 1000;
        $this->sendEvent($event)->assertConflict();
        $this->assertSame(1, Event::count());
        $old = Event::first()->validation_result;
        $latest = ConfigVersion::first();
        $new = $this->configFixture();
        $new['ppmRanges']['vegetatif']['min'] = 999;
        $this->ownerRequest()->putJson('/api/v1/config', ['baseVersion' => $latest->id, 'config' => $new])->assertOk();
        $this->assertEquals($old, Event::first()->validation_result);
        $event['eventId'] = (string) Str::uuid();
        $event['manualPpm'] = 800;
        $this->sendEvent($event)->assertCreated()->assertJsonPath('event.config_version_id', $latest->id)->assertJsonPath('event.validation_result.status', 'valid');
    }

    public function test_missing_configuration_and_invalid_clock_cannot_be_valid(): void
    {
        $this->travelTo(CarbonImmutable::parse('2026-10-10T02:00:00Z'));
        $event = $this->fixture();
        $event['clockStatus'] = 'invalid';
        $event['confirmedAt'] = null;
        $this->sendEvent($event)->assertCreated()->assertJsonPath('event.validation_result.status', 'incomplete')->assertJsonPath('event.assignment.status', 'time_unknown');
        $event['eventId'] = (string) Str::uuid();
        $event['phase'] = 'fake';
        $this->sendEvent($event)->assertUnprocessable();
        $event = $this->fixture();
        $event['workerId'] = 999;
        $this->sendEvent($event)->assertUnprocessable();
        $event = $this->fixture();
        $event['confirmedAt'] = '2026-02-30T01:00:00Z';
        $this->sendEvent($event)->assertUnprocessable();
    }

    private function worker(string $name): array
    {
        return $this->ownerRequest()->postJson('/api/v1/workers', ['name' => $name])->assertCreated()->json();
    }

    private function shift(int $id, string $start, string $end, array $days = [6]): array
    {
        return ['workerId' => $id, 'baseVersion' => null, 'days' => $days, 'startTime' => $start, 'endTime' => $end, 'startDate' => '2026-10-10', 'endDate' => null];
    }

    public function test_shift_boundaries_overlap_overnight_replay_rename_and_delete(): void
    {
        $a = $this->worker('A');
        $b = $this->worker('B');
        $first = $this->ownerRequest()->postJson('/api/v1/shifts', $this->shift($a['id'], '20:00', '00:00'))->assertCreated()->json();
        $second = $this->ownerRequest()->postJson('/api/v1/shifts', $this->shift($b['id'], '00:00', '04:00', [7]))->assertCreated()->json();
        $this->ownerRequest()->postJson('/api/v1/shifts', $this->shift($a['id'], '23:00', '02:00'))->assertUnprocessable();
        $this->ownerRequest()->postJson('/api/v1/shifts', $this->shift($a['id'], '08:00', '08:00'))->assertUnprocessable();
        $this->travelTo(CarbonImmutable::parse('2026-10-11T10:00:00Z'));
        $event = $this->fixture();
        $event['confirmedAt'] = '2026-10-11T00:00:00+07:00';
        $this->sendEvent($event)->assertCreated()->assertJsonPath('event.assignment.workerNameSnapshot', 'B');
        $event['eventId'] = (string) Str::uuid();
        $event['confirmedAt'] = '2026-10-10T23:59:59+07:00';
        $result = $this->sendEvent($event)->assertCreated()->assertJsonPath('event.assignment.workerNameSnapshot', 'A');
        $this->ownerRequest()->putJson('/api/v1/workers/'.$a['id'], ['baseVersion' => $a['version'], 'name' => 'Renamed', 'active' => true])->assertOk();
        $this->ownerRequest()->deleteJson('/api/v1/shifts/'.$first['shift_key'], ['baseVersion' => $first['id']])->assertNoContent();
        $event['eventId'] = (string) Str::uuid();
        $this->sendEvent($event)->assertCreated()->assertJsonPath('event.assignment.workerNameSnapshot', 'A');
        $this->assertSame('A', Event::find($result->json('event.id'))->assignment['workerNameSnapshot']);
        $event['eventId'] = (string) Str::uuid();
        $event['confirmedAt'] = '2026-10-11T09:00:00+07:00';
        $this->sendEvent($event)->assertCreated()->assertJsonPath('event.assignment.status', 'unscheduled');
    }

    public function test_config_structure_optimistic_lock_and_owner_cannot_ingest(): void
    {
        $bad = $this->configFixture();
        $bad['ppmRanges']['vegetatif'] = ['min' => 1000, 'max' => 800];
        $this->ownerRequest()->putJson('/api/v1/config', ['baseVersion' => null, 'config' => $bad])->assertUnprocessable();
        $this->configure();
        $this->ownerRequest()->putJson('/api/v1/config', ['baseVersion' => null, 'config' => $this->configFixture()])->assertConflict();
        $this->ownerRequest()->postJson('/api/v1/device/events/record', $this->fixture())->assertUnauthorized();
    }

    public function test_multiple_times_on_same_day_and_duplicate_day_in_one_row(): void
    {
        $config = $this->configFixture();
        $config['schedules'] = [['days' => [1, 2], 'time' => '08:00'], ['days' => [1, 2], 'time' => '16:00']];
        $response = $this->ownerRequest()->putJson('/api/v1/config', ['baseVersion' => null, 'config' => $config])->assertOk();
        $config['schedules'][0]['days'] = [1, 1];
        $this->ownerRequest()->putJson('/api/v1/config', ['baseVersion' => $response->json('config.id'), 'config' => $config])->assertUnprocessable()->assertJsonValidationErrors('schedules.0.days');
    }

    public function test_history_cursor_and_date_filter_export(): void
    {
        $this->configure();
        for ($i = 0; $i < 3; $i++) {
            $this->sendEvent($this->fixture())->assertCreated();
        }
        $first = $this->ownerRequest()->getJson('/api/v1/history?limit=2')->assertOk();
        $this->assertCount(2, $first->json('data'));
        $this->ownerRequest()->getJson('/api/v1/history?limit=2&cursor='.urlencode($first->json('next_cursor')))->assertOk()->assertJsonCount(1, 'data');
        $this->ownerRequest()->getJson('/api/v1/history?from=2026-10-11&to=2026-10-11')->assertJsonCount(0, 'data');
        $this->ownerRequest()->getJson('/api/v1/history/export')->assertOk();
    }

    public function test_dashboard_distinguishes_latest_receipt_and_newest_trusted_capture(): void
    {
        $this->configure();
        $telemetry = $this->fixture();
        unset($telemetry['confirmedAt'], $telemetry['measuredAt'], $telemetry['phase'], $telemetry['manualPpm']);
        $trusted = $this->sendEvent($telemetry, 'telemetry')->assertCreated()->json('event.id');
        $telemetry['eventId'] = (string) Str::uuid();
        $telemetry['sensorSnapshot']['capturedAt'] = '2026-10-10T00:30:00Z';
        $older = $this->sendEvent($telemetry, 'telemetry')->assertCreated()->json('event.id');
        $this->ownerRequest()->getJson('/api/v1/dashboard')->assertJsonPath('telemetry.id', $trusted)->assertJsonPath('latestTelemetryReceipt.id', $older);
        $telemetry['eventId'] = (string) Str::uuid();
        $telemetry['clockStatus'] = 'invalid';
        $telemetry['sensorSnapshot']['capturedAt'] = null;
        $invalid = $this->sendEvent($telemetry, 'telemetry')->assertCreated()->json('event.id');
        $this->ownerRequest()->getJson('/api/v1/dashboard')->assertJsonPath('telemetry.id', $trusted)->assertJsonPath('latestTelemetryReceipt.id', $invalid)->assertJsonPath('latestTelemetryReceipt.clock_trusted', false);
        $record = $this->fixture();
        $record['clockStatus'] = 'invalid';
        $record['confirmedAt'] = null;
        $id = $this->sendEvent($record)->assertCreated()->json('event.id');
        $this->ownerRequest()->getJson('/api/v1/dashboard')->assertJsonPath('latestRecordReceipt.id', $id)->assertJsonPath('latestRecord', null);
    }
}
