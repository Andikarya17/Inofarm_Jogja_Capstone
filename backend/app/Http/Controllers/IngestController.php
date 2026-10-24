<?php

namespace App\Http\Controllers;

use App\Models\ConfigVersion;
use App\Models\Event;
use App\Models\PhaseVersion;
use App\Models\Tank;
use App\Services\ConfigurationStore;
use App\Services\Input;
use App\Services\NutrientValidator;
use App\Services\ShiftResolver;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class IngestController extends Controller
{
    private function canonical(mixed $value): mixed
    {
        if (! is_array($value)) {
            return $value;
        } if (! array_is_list($value)) {
            ksort($value);
        }

        return array_map($this->canonical(...), $value);
    }

    public function store(Request $request, string $kind, ConfigurationStore $configStore, NutrientValidator $validator, ShiftResolver $shifts)
    {
        abort_unless(in_array($kind, ['record', 'telemetry'], true), 404);
        $body = Input::event($request->all(), $kind);
        $device = $request->attributes->get('device');
        abort_unless($device->id === $body['deviceId'], 403, 'Identitas perangkat tidak sesuai token.');
        $hash = hash('sha256', json_encode($this->canonical(['kind' => $kind, 'body' => $body]), JSON_PRESERVE_ZERO_FRACTION | JSON_THROW_ON_ERROR));

        return DB::transaction(function () use ($device, $body, $kind, $hash, $configStore, $validator, $shifts) {
            Tank::whereKey($device->tank_id)->lockForUpdate()->firstOrFail();
            $existing = Event::where('device_id', $device->id)->where('event_id', $body['eventId'])->first();
            if ($existing) {
                abort_unless(hash_equals($existing->payload_hash, $hash), 409, 'Event ID sudah digunakan untuk isi berbeda.');

                return ['persisted' => true, 'duplicate' => true, 'event' => $existing];
            }
            $received = CarbonImmutable::now();
            $source = $kind === 'record' ? $body['confirmedAt'] : $body['sensorSnapshot']['capturedAt'];
            $sourceAt = $source ? CarbonImmutable::parse($source)->utc() : null;
            $trusted = $body['clockStatus'] === 'trusted' && $body['clockSource'] !== 'unknown' && $sourceAt && $sourceAt->lessThanOrEqualTo($received);
            $config = $trusted ? $configStore->at(ConfigVersion::class, $device->tank_id, $sourceAt) : null;
            $phase = $trusted ? $configStore->at(PhaseVersion::class, $device->tank_id, $sourceAt) : null;
            $validation = $kind === 'record' ? $validator->evaluate($body, $config?->body, $phase?->body, (bool) $trusted, $received) : null;
            $assignment = $kind === 'record' ? $shifts->assign($device->tank_id, $sourceAt, (bool) $trusted) : null;
            $event = Event::create(['event_id' => $body['eventId'], 'device_id' => $device->id, 'tank_id' => $device->tank_id, 'kind' => $kind, 'payload_hash' => $hash, 'body' => $body, 'source_at' => $trusted ? $sourceAt : null, 'clock_trusted' => (bool) $trusted, 'received_at' => $received, 'phase' => $body['phase'] ?? null, 'manual_ppm' => $body['manualPpm'] ?? null, 'validation_result' => $validation, 'assignment' => $assignment, 'config_version_id' => $config?->id, 'phase_version_id' => $phase?->id]);

            return response()->json(['persisted' => true, 'duplicate' => false, 'event' => $event], 201);
        });
    }
}
