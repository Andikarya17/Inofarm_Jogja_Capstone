<?php

namespace Tests\Unit;

use App\Domain\Cultivation\RunningPeriod;
use Carbon\CarbonImmutable;
use PHPUnit\Framework\TestCase;

final class RunningPeriodTest extends TestCase
{
    public function test_legacy_open_season_does_not_designate_a_group(): void
    {
        $resolver = new RunningPeriod;
        $at = CarbonImmutable::parse('2026-11-08T00:01:00+07:00');
        $gh = (object) ['active' => true, 'running_group_id' => null, 'running_period_reviewed' => false];
        $group = (object) ['group_id' => 'synthetic', 'group_active' => true, 'season_status' => 'open',
            'sowing_date' => '2026-11-08', 'transplant_date' => '2026-11-09'];
        $groups = collect([$group]);
        $this->assertSame('needs_review', $resolver->resolve($gh, $groups, $at)['state']);
        $gh->running_period_reviewed = true;
        $this->assertSame('no_active_period', $resolver->resolve($gh, $groups, $at)['state']);
        $gh->running_group_id = $group->group_id;
        $period = $resolver->resolve($gh, $groups, $at);
        $this->assertSame('ready', $period['state']);
        $this->assertSame(1, $period['age']['hss']);
        $this->assertNull($period['age']['hst']);
        $group->group_active = false;
        $this->assertSame('needs_review', $resolver->resolve($gh, $groups, $at)['state']);
        $gh->active = false;
        $this->assertSame('inactive', $resolver->resolve($gh, $groups, $at)['state']);
    }
}
