<?php

namespace App\Services;

use Illuminate\Support\Facades\Validator;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

final class Input
{
    public const METRICS = ['waterTemperatureC', 'solutionPh', 'airTemperatureC', 'relativeHumidityPercent'];

    public static function timestamp(): \Closure
    {
        return function ($attribute, $value, $fail) {
            if (! is_string($value) || ! preg_match('/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?(?:Z|[+-]\d{2}:\d{2})$/D', $value)) {
                $fail('Waktu harus RFC3339 dengan offset.');

                return;
            }
            try {
                new \DateTimeImmutable($value);
                $errors = \DateTimeImmutable::getLastErrors();
                if ($errors && ($errors['warning_count'] || $errors['error_count'])) {
                    $fail('Tanggal/waktu tidak valid.');
                }
            } catch (\Exception) {
                $fail('Waktu tidak valid.');
            }
        };
    }

    public static function finite(): \Closure
    {
        return function ($attribute, $value, $fail) {
            if (! is_numeric($value) || ! is_finite((float) $value)) {
                $fail('Nilai harus angka finite.');
            }
        };
    }

    public static function sensorRules(string $prefix): array
    {
        $rules = [$prefix => ['required', 'array:capturedAt,readings,quality'], "$prefix.capturedAt" => ['present', 'nullable', self::timestamp()], "$prefix.readings" => ['required', 'array:'.implode(',', self::METRICS)], "$prefix.quality" => ['required', 'array:'.implode(',', self::METRICS)]];
        foreach (self::METRICS as $metric) {
            $rules["$prefix.readings.$metric"] = ['present', 'nullable', 'numeric', self::finite()];
            $rules["$prefix.quality.$metric"] = ['required', Rule::in(['ok', 'missing', 'failed', 'uncalibrated'])];
        }

        return $rules;
    }

    public static function event(array $data, string $kind): array
    {
        $rules = ['schemaVersion' => ['required', Rule::in(['1'])], 'eventId' => ['required', 'uuid'], 'deviceId' => ['required', 'uuid'], 'clockSource' => ['required', Rule::in(['rtc', 'ntp', 'unknown'])], 'clockStatus' => ['required', Rule::in(['trusted', 'invalid'])]];
        if ($kind === 'record') {
            $rules += ['confirmedAt' => ['present', 'nullable', self::timestamp()], 'measuredAt' => ['present', 'nullable', self::timestamp()], 'phase' => ['required', Rule::in(['vegetatif', 'generatif'])], 'manualPpm' => ['required', 'numeric', 'decimal:0,3', 'min:0', 'max:999999999', self::finite()], 'notes' => ['nullable', 'string', 'max:1000']];
            $rules += self::sensorRules('sensorSnapshot');
        } else {
            $rules += self::sensorRules('sensorSnapshot');
        }
        $allowed = array_map(fn ($key) => explode('.', $key)[0], array_keys($rules));
        if (array_diff(array_keys($data), $allowed)) {
            throw ValidationException::withMessages(['payload' => 'Field tidak dikenal.']);
        }
        $valid = Validator::make($data, $rules)->validate();
        foreach (self::METRICS as $metric) {
            $value = $valid['sensorSnapshot']['readings'][$metric];
            $quality = $valid['sensorSnapshot']['quality'][$metric];
            if (($quality === 'ok') !== ($value !== null)) {
                throw ValidationException::withMessages(["sensorSnapshot.$metric" => 'Quality ok memerlukan angka; failed/missing/uncalibrated memakai null.']);
            }
        }

        return $valid;
    }

    public static function config(array $data): array
    {
        $rules = ['timezone' => ['required', Rule::in(['Asia/Jakarta'])], 'schedules' => ['present', 'array', 'max:14'], 'schedules.*' => ['array:days,time'], 'schedules.*.days' => ['required', 'array', 'min:1', 'max:7'], 'schedules.*.days.*' => ['integer', 'between:1,7'], 'schedules.*.time' => ['required', 'date_format:H:i'], 'earlyMinutes' => ['present', 'nullable', 'integer', 'between:0,1440'], 'lateMinutes' => ['present', 'nullable', 'integer', 'between:0,1440'], 'sensorMaxAgeSeconds' => ['present', 'nullable', 'integer', 'between:0,604800'], 'deviceStaleSeconds' => ['present', 'nullable', 'integer', 'between:1,604800'], 'ppmRanges' => ['present', 'array:vegetatif,generatif'], 'sensorBounds' => ['present', 'array:'.implode(',', self::METRICS)]];
        $rules['telemetryIntervalSeconds'] = ['sometimes', 'nullable', 'integer', 'between:2,86400'];
        $rules['alarmSeconds'] = ['sometimes', 'nullable', 'integer', 'between:1,3600'];
        foreach (['vegetatif', 'generatif'] as $phase) {
            $rules["ppmRanges.$phase"] = ['sometimes', 'nullable', 'array:min,max'];
            $rules["ppmRanges.$phase.min"] = ['required_with:ppmRanges.'.$phase, 'numeric', 'min:0', self::finite()];
            $rules["ppmRanges.$phase.max"] = ['required_with:ppmRanges.'.$phase, 'numeric', 'gte:ppmRanges.'.$phase.'.min', self::finite()];
        }
        foreach (self::METRICS as $metric) {
            $rules["sensorBounds.$metric"] = ['sometimes', 'nullable', 'array:min,max'];
            $rules["sensorBounds.$metric.min"] = ['required_with:sensorBounds.'.$metric, 'numeric', self::finite()];
            $rules["sensorBounds.$metric.max"] = ['required_with:sensorBounds.'.$metric, 'numeric', 'gte:sensorBounds.'.$metric.'.min', self::finite()];
        }
        if (array_diff(array_keys($data), array_keys(array_filter($rules, fn ($v, $k) => ! str_contains($k, '.'), ARRAY_FILTER_USE_BOTH)))) {
            throw ValidationException::withMessages(['config' => 'Field tidak dikenal.']);
        }

        $valid = Validator::make($data, $rules)->validate();
        foreach ($valid['schedules'] as $index => $row) {
            if (count(array_unique($row['days'], SORT_NUMERIC)) !== count($row['days'])) {
                throw ValidationException::withMessages(["schedules.$index.days" => 'Hari dalam satu jadwal tidak boleh berulang.']);
            }
        }

        return $valid;
    }
}
