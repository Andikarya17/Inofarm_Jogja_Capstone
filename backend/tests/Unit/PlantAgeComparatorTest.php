<?php

namespace Tests\Unit;

use App\Domain\Cultivation\PlantAge;
use App\Domain\Cultivation\TargetComparator;
use PHPUnit\Framework\TestCase;

class PlantAgeComparatorTest extends TestCase
{
    public function test_age_uses_jakarta_calendar_and_first_day_one_not_elapsed_hours(): void
    {
        $this->assertSame(1, PlantAge::calculate('2026-10-01', null, '2026-10-01T16:59:59Z')['hss']);
        $this->assertSame(2, PlantAge::calculate('2026-10-01', null, '2026-10-01T17:00:00Z')['hss']);
        $this->assertNull(PlantAge::calculate('2026-10-01', '2026-10-10', '2026-10-09T12:00:00+07:00')['hst']);
        $this->assertSame(1, PlantAge::calculate('2026-10-01', '2026-10-10', '2026-10-10T00:00:00+07:00')['hst']);
        $this->assertNull(PlantAge::calculate('2026-10-01', null, '2026-09-30T12:00:00+07:00')['hss']);
        $this->assertSame(3, PlantAge::calculate('2024-02-28', null, '2024-03-01T00:00:00+07:00')['hss']);
    }

    public function test_missing_single_tolerance_and_range_do_not_replace_actual_values(): void
    {
        $single = ['target_value' => '900', 'minimum' => null, 'maximum' => null, 'tolerance' => null];
        $this->assertSame('unavailable', TargetComparator::compare(null, null)['status']);
        $this->assertSame('unavailable', TargetComparator::compare('900', null)['status']);
        $delta = TargetComparator::compare('950', $single);
        $this->assertSame('delta_only', $delta['status']);
        $this->assertSame('50.000000', $delta['delta']);
        $this->assertSame('950', $delta['actual']);
        $single['tolerance'] = '50';
        $this->assertSame('within_range', TargetComparator::compare('950', $single)['status']);
        $this->assertSame('outside_range', TargetComparator::compare('950.000001', $single)['status']);
        $this->assertSame('within_range', TargetComparator::compare('0', ['target_value' => null, 'minimum' => '0', 'maximum' => '0', 'tolerance' => null])['status']);
    }
}
