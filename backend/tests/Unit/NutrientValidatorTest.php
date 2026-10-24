<?php

namespace Tests\Unit;

use App\Services\Input;
use App\Services\NutrientValidator;
use Carbon\CarbonImmutable;
use PHPUnit\Framework\TestCase;

class NutrientValidatorTest extends TestCase
{
    public function test_any_configured_window_can_pass_and_phase_warning_keeps_input(): void
    {
        $time = '2026-10-10T01:40:00Z';
        $event = ['confirmedAt' => $time, 'phase' => 'vegetatif', 'manualPpm' => 900,
            'sensorSnapshot' => ['capturedAt' => $time, 'readings' => array_fill_keys(Input::METRICS, 5), 'quality' => array_fill_keys(Input::METRICS, 'ok')]];
        $config = ['schedules' => [['days' => [6], 'time' => '08:00'], ['days' => [6], 'time' => '09:00']], 'earlyMinutes' => 0, 'lateMinutes' => 60,
            'sensorMaxAgeSeconds' => 0, 'ppmRanges' => ['vegetatif' => ['min' => 900, 'max' => 900]], 'sensorBounds' => array_fill_keys(Input::METRICS, ['min' => 5, 'max' => 5])];
        $validator = new NutrientValidator;
        $result = $validator->evaluate($event, $config, ['phase' => 'generatif'], true, CarbonImmutable::parse($time));
        $this->assertSame('issues', $result['status']);
        $this->assertContains('phase_mismatch', array_column($result['checks'], 'code'));
        $event['sensorSnapshot']['capturedAt'] = '2026-10-10T01:40:00.000001Z';
        $result = $validator->evaluate($event, $config, ['phase' => 'vegetatif'], true, CarbonImmutable::parse($time));
        $this->assertSame('incomplete', $result['status']);
    }
}
