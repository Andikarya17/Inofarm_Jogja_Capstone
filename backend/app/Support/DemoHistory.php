<?php

namespace App\Support;

use App\Http\Controllers\IngestController;
use App\Models\ConfigVersion;
use App\Models\Device;
use App\Models\Event;
use App\Models\PhaseVersion;
use App\Models\ShiftVersion;
use App\Models\Tank;
use App\Models\User;
use App\Models\Worker;
use App\Models\WorkerVersion;
use App\Services\Input;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use RuntimeException;

/** Explicit local demonstration, never a production default or automatic seeder. */
final class DemoHistory
{
    public const EMAIL = 'demo.inofarm@example.invalid';

    public const TANK = 'DEMO DUMMY - BUKAN DATA GREENHOUSE';

    public function create(CarbonImmutable $asOf, string $password): array
    {
        $connection = DB::connection();
        $config = $connection->getConfig();
        $expected = app()->environment('testing') ? 'inofarm_test' : 'inofarm_local';
        if (! app()->environment(['local', 'testing']) || $connection->getDriverName() !== 'mysql'
            || $connection->getDatabaseName() !== $expected || ! in_array($config['host'], ['127.0.0.1', 'localhost'], true)
            || (string) $config['port'] !== '3307' || ! empty($config['url']) || ! empty($config['read']) || ! empty($config['write'])
            || ! empty($config['unix_socket'])) {
            throw new RuntimeException('Demo hanya boleh pada MySQL lokal port 3307, inofarm_local atau testing/inofarm_test tanpa override.');
        }
        if ($asOf->startOfDay()->isAfter(CarbonImmutable::now('Asia/Jakarta')->startOfDay())) {
            throw new RuntimeException('Tanggal demo tidak boleh di masa depan.');
        }

        return DB::transaction(function () use ($asOf, $password) {
            if (User::where('email', self::EMAIL)->exists() || Tank::where('name', self::TANK)->exists()) {
                throw new RuntimeException('Demo sudah ada; tidak menimpa akun, menambah duplikat atau menghapus riwayat.');
            }
            $start = $asOf->setTimezone('Asia/Jakarta')->startOfDay()->subDays(10);
            $effective = $start->utc();
            $tank = Tank::create(['name' => self::TANK]);
            $owner = User::create(['name' => 'Owner DEMO DUMMY', 'email' => self::EMAIL, 'password' => $password, 'role' => 'owner', 'tank_id' => $tank->id]);
            $device = Device::create(['id' => (string) Str::uuid(), 'tank_id' => $tank->id, 'name' => 'SIMULATOR DUMMY - TANPA ESP32 FISIK', 'enabled' => false, 'token_hash' => hash('sha256', random_bytes(32))]);
            // Deliberate historical demo setup; never backdate an existing owner's configuration.
            ConfigVersion::create(['tank_id' => $tank->id, 'created_by' => $owner->id, 'effective_from' => $effective, 'body' => Input::config($this->configuration())]);
            PhaseVersion::create(['tank_id' => $tank->id, 'created_by' => $owner->id, 'effective_from' => $effective, 'body' => ['phase' => 'vegetatif']]);
            foreach ([['DEMO Budi', '06:00', '14:00'], ['DEMO Sari', '14:00', '22:00']] as [$name, $from, $until]) {
                $worker = Worker::create(['tank_id' => $tank->id]);
                WorkerVersion::create(['worker_id' => $worker->id, 'name' => $name, 'active' => true, 'effective_from' => $effective]);
                ShiftVersion::create(['shift_key' => (string) Str::uuid(), 'tank_id' => $tank->id, 'worker_id' => $worker->id, 'created_by' => $owner->id, 'effective_from' => $effective, 'body' => ['workerId' => $worker->id, 'days' => range(1, 7), 'startTime' => $from, 'endTime' => $until, 'startDate' => $start->toDateString(), 'endDate' => null]]);
            }
            $scenarios = ['normal', 'terlambat', 'terlalu_awal', 'ppm_rendah', 'ppm_tinggi', 'sensor_anomali', 'fase_berbeda', 'sensor_gagal', 'sensor_hilang', 'belum_kalibrasi', 'sensor_usang', 'jam_invalid', 'tanpa_shift', 'gabungan_masalah'];
            $rows = [];
            for ($day = 0; $day < 10; $day++) {
                foreach ([8, 12, 16] as $slot => $hour) {
                    $scenario = $scenarios[($day * 3 + $slot) % count($scenarios)];
                    $body = $this->payload($device->id, $start->addDays($day)->setTime($hour, 0), $scenario);
                    $record = $this->ingest($device, $body, 'record');
                    $telemetry = array_intersect_key($body, array_flip(['schemaVersion', 'deviceId', 'clockSource', 'clockStatus', 'sensorSnapshot']));
                    $telemetry['eventId'] = (string) Str::uuid();
                    $this->ingest($device, $telemetry, 'telemetry');
                    $rows[] = ['id' => $record->id, 'scenario' => $scenario, 'confirmedAt' => $body['confirmedAt'], 'status' => $record->validation_result['status'], 'assignment' => $record->assignment['status']];
                }
            }

            return ['tankId' => $tank->id, 'email' => self::EMAIL, 'from' => $start->toDateString(), 'to' => $start->addDays(9)->toDateString(), 'recordCount' => count($rows), 'telemetryCount' => count($rows), 'statusCounts' => array_count_values(array_column($rows, 'status')), 'rows' => $rows];
        });
    }

    private function configuration(): array
    {
        // Arbitrary software fixtures only: not approved agronomic recommendations.
        return ['timezone' => 'Asia/Jakarta', 'schedules' => array_map(fn ($time) => ['days' => range(1, 7), 'time' => $time], ['08:00', '12:00', '16:00']), 'earlyMinutes' => 10, 'lateMinutes' => 15, 'sensorMaxAgeSeconds' => 120, 'deviceStaleSeconds' => 300, 'telemetryIntervalSeconds' => null, 'alarmSeconds' => null, 'ppmRanges' => ['vegetatif' => ['min' => 800, 'max' => 1000], 'generatif' => ['min' => 1100, 'max' => 1400]], 'sensorBounds' => array_combine(Input::METRICS, [['min' => 20, 'max' => 28], ['min' => 5.5, 'max' => 6.5], ['min' => 20, 'max' => 32], ['min' => 40, 'max' => 90]])];
    }

    private function payload(string $deviceId, CarbonImmutable $time, string $scenario): array
    {
        if (in_array($scenario, ['terlambat', 'gabungan_masalah'], true)) {
            $time = $time->addMinutes(45);
        } elseif ($scenario === 'terlalu_awal') {
            $time = $time->subMinutes(45);
        } elseif ($scenario === 'tanpa_shift') {
            $time = $time->setTime(23, 0);
        }
        $ppm = match ($scenario) {
            'ppm_rendah' => 650,
            'ppm_tinggi', 'gabungan_masalah' => 1200,
            'fase_berbeda' => 1250,
            default => 900,
        };
        $readings = array_combine(Input::METRICS, [25, 6, 28, 70]);
        $quality = array_fill_keys(Input::METRICS, 'ok');
        if (in_array($scenario, ['sensor_anomali', 'gabungan_masalah'], true)) {
            $readings['solutionPh'] = 8.2;
            $readings['waterTemperatureC'] = 35;
        }
        $sensorQuality = ['sensor_gagal' => 'failed', 'sensor_hilang' => 'missing', 'belum_kalibrasi' => 'uncalibrated'][$scenario] ?? null;
        if ($sensorQuality) {
            $quality['solutionPh'] = $sensorQuality;
            $readings['solutionPh'] = null;
        }

        return ['schemaVersion' => '1', 'eventId' => (string) Str::uuid(), 'deviceId' => $deviceId, 'clockSource' => $scenario === 'jam_invalid' ? 'unknown' : 'ntp', 'clockStatus' => $scenario === 'jam_invalid' ? 'invalid' : 'trusted', 'confirmedAt' => $time->toRfc3339String(), 'measuredAt' => $time->subMinute()->toRfc3339String(), 'phase' => $scenario === 'fase_berbeda' ? 'generatif' : 'vegetatif', 'manualPpm' => $ppm, 'notes' => "[DUMMY SIMULASI] $scenario; bukan input pekerja/sensor fisik. Tanggal adalah waktu sumber sintetis, diterima backend saat pembuatan demo.", 'sensorSnapshot' => ['capturedAt' => $time->subSeconds($scenario === 'sensor_usang' ? 600 : 10)->toRfc3339String(), 'readings' => $readings, 'quality' => $quality]];
    }

    private function ingest(Device $device, array $body, string $kind): Event
    {
        $request = Request::create('/api/v1/device/events/'.$kind, 'POST', $body);
        $request->attributes->set('device', $device);
        $response = app()->call([app(IngestController::class), 'store'], ['request' => $request, 'kind' => $kind]);

        return Event::findOrFail($response->getData(true)['event']['id']);
    }
}
