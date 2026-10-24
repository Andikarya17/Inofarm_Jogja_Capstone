<?php

namespace App\Services;

use App\Models\ShiftVersion;
use App\Models\WorkerVersion;
use Carbon\CarbonImmutable;

final class ShiftResolver
{
    public function occurrences(ShiftVersion $shift, CarbonImmutable $day): array
    {
        $body = $shift->body;
        $day = $day->setTimezone('Asia/Jakarta')->startOfDay();
        if (! in_array($day->isoWeekday(), $body['days'], true) || $day->toDateString() < $body['startDate'] || ($body['endDate'] && $day->toDateString() > $body['endDate'])) {
            return [];
        }
        [$hour,$minute] = array_map('intval', explode(':', $body['startTime']));
        $start = $day->setTime($hour, $minute)->utc();
        [$hour,$minute] = array_map('intval', explode(':', $body['endTime']));
        $end = $day->setTime($hour, $minute)->utc();
        if ($end->lessThanOrEqualTo($start)) {
            $end = $end->addDay();
        }
        $from = CarbonImmutable::instance($shift->effective_from);
        $until = $shift->effective_until ? CarbonImmutable::instance($shift->effective_until) : null;
        if ($start->lessThan($from)) {
            $start = $from;
        }
        if ($until && $end->greaterThan($until)) {
            $end = $until;
        }

        return $start->lessThan($end) ? [['start' => $start, 'end' => $end]] : [];
    }

    public function overlaps(ShiftVersion $a, ShiftVersion $b): bool
    {
        $start = max($a->body['startDate'], $b->body['startDate'], $a->effective_from->setTimezone('Asia/Jakarta')->toDateString(), $b->effective_from->setTimezone('Asia/Jakarta')->toDateString());
        $day = CarbonImmutable::parse($start, 'Asia/Jakarta')->subDay();
        // Weekly recurrence repeats; eight days cover every weekday plus overnight boundary.
        for ($i = 0; $i < 9; $i++) {
            foreach ($this->occurrences($a, $day->addDays($i)) as $oa) {
                for ($j = -1; $j <= 1; $j++) {
                    foreach ($this->occurrences($b, $day->addDays($i + $j)) as $ob) {
                        if ($oa['start']->lessThan($ob['end']) && $ob['start']->lessThan($oa['end'])) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public function assign(int $tankId, ?CarbonImmutable $confirmedAt, bool $trusted): array
    {
        $assignedAt = now()->toISOString();
        if (! $trusted || ! $confirmedAt) {
            return ['status' => 'time_unknown', 'assignedAt' => $assignedAt];
        }
        $matches = [];
        $sourceTime = $confirmedAt->format('Y-m-d H:i:s.u');
        $shifts = ShiftVersion::where('tank_id', $tankId)->where('effective_from', '<=', $sourceTime)->where(fn ($q) => $q->whereNull('effective_until')->orWhere('effective_until', '>', $sourceTime))->get();
        foreach ($shifts as $shift) {
            for ($day = -1; $day <= 0; $day++) {
                foreach ($this->occurrences($shift, $confirmedAt->addDays($day)) as $occurrence) {
                    if ($confirmedAt->greaterThanOrEqualTo($occurrence['start']) && $confirmedAt->lessThan($occurrence['end'])) {
                        $name = WorkerVersion::where('worker_id', $shift->worker_id)->where('effective_from', '<=', $sourceTime)->orderByDesc('effective_from')->orderByDesc('id')->first();
                        $matches[] = ['workerId' => $shift->worker_id, 'workerNameSnapshot' => $name?->name, 'shiftVersionId' => $shift->id, 'shiftSnapshot' => $shift->body, 'startAt' => $occurrence['start']->toISOString(), 'endAt' => $occurrence['end']->toISOString()];
                    }
                }
            }
        }

        return count($matches) === 1 ? ['status' => 'scheduled', 'assignedAt' => $assignedAt, ...$matches[0]] : ['status' => count($matches) > 1 ? 'conflict' : 'unscheduled', 'assignedAt' => $assignedAt];
    }
}
