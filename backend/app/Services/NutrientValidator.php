<?php

namespace App\Services;

use Carbon\CarbonImmutable;

final class NutrientValidator
{
    public function evaluate(array $event, ?array $config, ?array $ownerPhase, bool $trusted, CarbonImmutable $evaluatedAt): array
    {
        $checks = [];
        $add = function (string $check, string $result, string $code, string $message) use (&$checks) {
            $checks[] = compact('check', 'result', 'code', 'message');
        };
        $confirmed = $trusted && $event['confirmedAt'] ? CarbonImmutable::parse($event['confirmedAt'])->utc() : null;
        if (! $confirmed) {
            $add('clock', 'incomplete', 'clock_untrusted', 'Waktu konfirmasi tidak dapat dipercaya.');
        }
        if (! $ownerPhase) {
            $add('phase', 'incomplete', 'owner_phase_missing', 'Fase aktif pada waktu pencatatan belum tersedia.');
        } elseif ($ownerPhase['phase'] !== $event['phase']) {
            $add('phase', 'warning', 'phase_mismatch', "Fase pekerja {$event['phase']} berbeda dari fase owner {$ownerPhase['phase']}; input asli dipertahankan.");
        } else {
            $add('phase', 'pass', 'phase_match', 'Fase pencatatan sesuai fase aktif owner.');
        }
        $range = $config['ppmRanges'][$event['phase']] ?? null;
        if (! $range) {
            $add('ppm', 'incomplete', 'ppm_range_missing', 'Rentang PPM fase pencatatan belum diisi.');
        } else {
            $ppm = $event['manualPpm'];
            $code = $ppm < $range['min'] ? 'ppm_low' : ($ppm > $range['max'] ? 'ppm_high' : 'ppm_in_range');
            $message = match ($code) {
                'ppm_low' => "PPM manual $ppm di bawah minimum {$range['min']} untuk fase {$event['phase']}.",
                'ppm_high' => "PPM manual $ppm di atas maksimum {$range['max']} untuk fase {$event['phase']}.",
                default => "PPM manual $ppm berada dalam rentang {$range['min']}–{$range['max']} untuk fase {$event['phase']}.",
            };
            $add('ppm', $code === 'ppm_in_range' ? 'pass' : 'issue', $code, $message);
        }
        if (! $confirmed || ! $config || ! $config['schedules'] || $config['earlyMinutes'] === null || $config['lateMinutes'] === null) {
            $add('schedule', 'incomplete', 'schedule_incomplete', 'Jadwal/toleransi/waktu sumber belum lengkap.');
        } else {
            $local = $confirmed->setTimezone('Asia/Jakarta');
            $nearest = null;
            $distance = INF;
            $inWindow = false;
            for ($day = -1; $day <= 1; $day++) {
                foreach ($config['schedules'] as $schedule) {
                    $date = $local->startOfDay()->addDays($day);
                    if (! in_array($date->isoWeekday(), $schedule['days'], true)) {
                        continue;
                    }
                    [$hour,$minute] = array_map('intval', explode(':', $schedule['time']));
                    $occurrence = $date->setTime($hour, $minute)->utc();
                    $delta = ((float) $confirmed->format('U.u') - (float) $occurrence->format('U.u')) / 60;
                    if ($delta >= -$config['earlyMinutes'] && $delta <= $config['lateMinutes']) {
                        $inWindow = true;
                    }
                    if (abs($delta) < $distance) {
                        $nearest = $delta;
                        $distance = abs($delta);
                    }
                }
            }
            if ($inWindow) {
                $add('schedule', 'pass', 'schedule_in_window', 'Waktu konfirmasi berada dalam toleransi owner.');
            } elseif ($nearest === null) {
                $add('schedule', 'issue', 'no_nutrient_occurrence', 'Tidak ada occurrence jadwal pada hari sumber/sekitar batas hari.');
            } elseif ($nearest < -$config['earlyMinutes']) {
                $add('schedule', 'issue', 'recorded_early', 'Dicatat sebelum toleransi awal jadwal.');
            } elseif ($nearest > $config['lateMinutes']) {
                $add('schedule', 'issue', 'recorded_late', 'Dicatat sesudah toleransi akhir jadwal.');
            } else {
                $add('schedule', 'pass', 'schedule_in_window', 'Waktu konfirmasi berada dalam toleransi owner.');
            }
        }
        $snapshot = $event['sensorSnapshot'];
        $capture = $snapshot['capturedAt'] ? CarbonImmutable::parse($snapshot['capturedAt'])->utc() : null;
        if (! $confirmed || ! $capture || ($config['sensorMaxAgeSeconds'] ?? null) === null) {
            $add('sensor_time', 'incomplete', 'sensor_age_unknown', 'Usia snapshot sensor belum dapat diperiksa.');
        } elseif ($capture->greaterThan($confirmed)) {
            $add('sensor_time', 'incomplete', 'sensor_from_future', 'Snapshot sensor berada sesudah konfirmasi.');
        } elseif ((float) $confirmed->format('U.u') - (float) $capture->format('U.u') > $config['sensorMaxAgeSeconds']) {
            $add('sensor_time', 'incomplete', 'sensor_stale', 'Snapshot lebih tua daripada batas usia yang diisi owner.');
        } else {
            $add('sensor_time', 'pass', 'sensor_time_ok', 'Snapshot sensor berada dalam usia yang diisi owner.');
        }
        foreach (Input::METRICS as $metric) {
            $label = ['waterTemperatureC' => 'Suhu air (°C)', 'solutionPh' => 'pH larutan', 'airTemperatureC' => 'Suhu ruangan (°C)', 'relativeHumidityPercent' => 'Kelembapan (%)'][$metric];
            $quality = $snapshot['quality'][$metric];
            $value = $snapshot['readings'][$metric];
            $bounds = $config['sensorBounds'][$metric] ?? null;
            if ($quality !== 'ok' || $value === null) {
                $reason = ['missing' => 'bacaan belum tersedia', 'failed' => 'sensor gagal membaca', 'uncalibrated' => 'belum dikalibrasi'][$quality] ?? 'bacaan tidak tersedia';
                $add($metric, 'incomplete', 'sensor_'.$quality, "$label: $reason, belum dapat dianggap normal.");
            }
            if (! $bounds) {
                $add($metric, 'incomplete', 'sensor_bound_missing', "$label: ambang belum diisi owner.");
            }
            if ($quality === 'ok' && $value !== null && $bounds) {
                $inRange = $value >= $bounds['min'] && $value <= $bounds['max'];
                $position = $inRange ? 'dalam' : 'di luar';
                $add($metric, $inRange ? 'pass' : 'issue', $inRange ? 'sensor_in_range' : 'sensor_out_of_range', "$label $value berada $position batas owner {$bounds['min']}–{$bounds['max']}.");
            }
        }
        $results = array_column($checks, 'result');
        $status = in_array('incomplete', $results, true) ? 'incomplete' : ((in_array('issue', $results, true) || in_array('warning', $results, true)) ? 'issues' : 'valid');

        return ['status' => $status, 'checks' => $checks, 'evaluatedAt' => $evaluatedAt->toISOString(), 'authority' => 'server', 'ruleVersion' => 'B-1', 'configSnapshot' => $config, 'ownerPhaseSnapshot' => $ownerPhase];
    }
}
