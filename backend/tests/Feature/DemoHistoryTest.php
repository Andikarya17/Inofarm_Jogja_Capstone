<?php

namespace Tests\Feature;

use App\Models\ConfigVersion;
use App\Models\Device;
use App\Models\Event;
use App\Models\Tank;
use App\Models\User;
use App\Support\DemoHistory;
use Carbon\CarbonImmutable;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use RuntimeException;
use Tests\TestCase;

class DemoHistoryTest extends TestCase
{
    use RefreshDatabase;

    public function test_demo_generates_real_validation_and_preserves_owner_scope(): void
    {
        $this->travelTo(CarbonImmutable::parse('2026-10-20T03:00:00Z'));
        $original = Tank::create(['name' => 'Existing owner tank']);
        $owner = User::create(['name' => 'Existing owner', 'email' => 'existing@example.invalid', 'password' => 'existing-test-only', 'tank_id' => $original->id, 'role' => 'owner']);
        $summary = app(DemoHistory::class)->create(CarbonImmutable::parse('2026-10-20', 'Asia/Jakarta'), 'demo-test-password-only');
        $this->assertSame('2026-10-10', $summary['from']);
        $this->assertSame('2026-10-19', $summary['to']);
        $this->assertSame(60, Event::count());
        $this->assertEqualsCanonicalizing(['valid', 'issues', 'incomplete'], array_keys($summary['statusCounts']));
        $this->assertSame(10, count(array_unique(array_map(fn ($row) => substr($row['confirmedAt'], 0, 10), $summary['rows']))));
        $this->assertSame(0, ConfigVersion::where('tank_id', $original->id)->count());
        $this->assertSame($original->id, $owner->fresh()->tank_id);
        $demo = User::where('email', DemoHistory::EMAIL)->firstOrFail();
        $this->assertTrue(Hash::check('demo-test-password-only', $demo->password));
        $this->assertFalse(Device::where('tank_id', $demo->tank_id)->firstOrFail()->enabled);
        $expected = ['normal' => ['valid', 'schedule_in_window'], 'terlambat' => ['issues', 'recorded_late'], 'terlalu_awal' => ['issues', 'recorded_early'], 'ppm_rendah' => ['issues', 'ppm_low'], 'ppm_tinggi' => ['issues', 'ppm_high'], 'sensor_anomali' => ['issues', 'sensor_out_of_range'], 'fase_berbeda' => ['issues', 'phase_mismatch'], 'sensor_gagal' => ['incomplete', 'sensor_failed'], 'sensor_hilang' => ['incomplete', 'sensor_missing'], 'belum_kalibrasi' => ['incomplete', 'sensor_uncalibrated'], 'sensor_usang' => ['incomplete', 'sensor_stale'], 'jam_invalid' => ['incomplete', 'clock_untrusted']];
        foreach ($summary['rows'] as $row) {
            $event = Event::findOrFail($row['id']);
            $this->assertStringStartsWith('[DUMMY SIMULASI]', $event->body['notes']);
            if (isset($expected[$row['scenario']])) {
                [$status, $code] = $expected[$row['scenario']];
                $this->assertSame($status, $event->validation_result['status'], $row['scenario']);
                $this->assertContains($code, array_column($event->validation_result['checks'], 'code'));
            }
            if ($row['scenario'] === 'jam_invalid') {
                $this->assertNull($event->source_at);
                $this->assertSame('time_unknown', $event->assignment['status']);
            } elseif ($row['scenario'] === 'tanpa_shift') {
                $this->assertSame('unscheduled', $event->assignment['status']);
            } else {
                $this->assertSame('scheduled', $event->assignment['status']);
                $this->assertStringStartsWith('DEMO ', $event->assignment['workerNameSnapshot']);
            }
        }
        $this->actingAs($demo, 'sanctum')->getJson('/api/v1/history')->assertOk()->assertJsonCount(25, 'data');
        $this->actingAs($owner, 'sanctum')->getJson('/api/v1/history')->assertOk()->assertJsonCount(0, 'data');
        try {
            app(DemoHistory::class)->create(CarbonImmutable::parse('2026-10-20', 'Asia/Jakarta'), 'another-test-password');
            $this->fail('Duplicate demo should be refused');
        } catch (RuntimeException $error) {
            $this->assertStringContainsString('Demo sudah ada', $error->getMessage());
        }
        $this->assertSame(60, Event::count());
    }

    public function test_demo_refuses_production_and_future_dates_before_writes(): void
    {
        $this->travelTo(CarbonImmutable::parse('2026-10-20T03:00:00Z'));
        foreach (['production', 'testing'] as $environment) {
            app()->instance('env', $environment);
            try {
                app(DemoHistory::class)->create(CarbonImmutable::parse('2026-10-21', 'Asia/Jakarta'), 'unused-test-password');
                $this->fail('Unsafe demo should be refused');
            } catch (RuntimeException) {
                $this->assertSame(0, Tank::count());
            }
        }
    }
}
