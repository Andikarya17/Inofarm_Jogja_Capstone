<?php

namespace App\Domain\Cultivation;

use App\Domain\Activities\ActivityReviewService;
use App\Domain\Contracts\DomainFailure;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\AcceptedSnapshot;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Database\Query\Builder;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;

final class ManagerHome
{
    public function __construct(private GreenhouseAccess $access, private RunningPeriod $period, private AcceptedSnapshot $accepted) {}

    public function snapshot(User $actor, ?string $selected): array
    {
        $this->access->manager($actor);
        $at = CarbonImmutable::now('Asia/Jakarta');
        $all = $this->access->query($actor)->orderBy('name')->orderBy('id')->get();
        abort_if($selected !== null && ! $all->contains('id', $selected), 404, 'Greenhouse tidak ditemukan.');
        $greenhouses = $selected === null ? $all : $all->where('id', $selected);
        $ids = $greenhouses->pluck('id')->all();
        $groups = $this->period->groups($ids)->groupBy('greenhouse_id');
        $periods = $greenhouses->mapWithKeys(fn ($row) => [$row->id => $this->period->resolve($row, $groups->get($row->id, collect()), $at)]);
        $activeGroups = $periods->where('state', 'ready')->pluck('group_id')->all();

        $activities = $this->operations('activities', $ids);
        $harvests = $this->operations('harvests', $ids);
        $last = $this->lastByGreenhouse($activities, $harvests);
        $stage = $this->latestPerGroup((clone $activities)->whereIn('r.planting_group_id', $activeGroups)
            ->where('r.activity_type', 'stage')->where('r.work_state', 'completed')
            ->whereNotNull('r.actual_stage')->where('r.occurred_at', '<=', $this->sqlTime($at)));
        $dayStart = $this->sqlTime($at->startOfDay());
        $tomorrow = $this->sqlTime($at->startOfDay()->addDay());
        $measurementRows = (clone $activities)->whereIn('r.planting_group_id', $activeGroups)
            ->where('r.work_state', 'completed')->whereNotNull('r.manual_ppm')->where('r.occurred_at', '<', $tomorrow);
        $pairs = $this->latestPerGroup((clone $measurementRows)->whereNotExists(ActivityReviewService::current('invalid', 'r')));
        $invalidToday = $this->latestPerGroup((clone $measurementRows)->where('r.occurred_at', '>=', $dayStart)
            ->whereExists(ActivityReviewService::current('invalid', 'r')));
        $measurements = $this->readAcceptedSection(fn () => $this->accepted->readMany('activity', $pairs->values()->all()));
        if ($measurements !== null) {
            $decisions = DB::table('activity_reviews')->whereIn('activity_id', $pairs->pluck('id'))->get()
                ->keyBy(fn ($review) => $review->activity_id.':'.$review->activity_version);
            foreach ($pairs as $pair) {
                $review = $decisions->get($pair->id.':'.$pair->version);
                $measurements[$pair->id]['verification_status'] = $review->decision ?? match ($pair->recorded_by_role) {
                    'worker' => 'pending', 'manager' => 'manager', default => 'legacy',
                };
            }
        }
        $harvestTotals = (clone $harvests)->whereIn('r.planting_group_id', $activeGroups)
            ->join('harvest_items as i', 'i.harvest_id', '=', 'r.id')->where('i.active', true)
            ->groupBy('r.planting_group_id')
            ->selectRaw('r.planting_group_id, COUNT(DISTINCT r.id) AS records, CAST(SUM(i.weight_kg) AS CHAR) AS weight_kg, SUM(i.fruit_count) AS fruit_count')
            ->get()->keyBy('planting_group_id');

        $cards = $greenhouses->map(function ($row) use ($periods, $last, $stage, $harvestTotals) {
            $period = $periods[$row->id];
            $phase = $stage->get($period['group_id'] ?? '');
            if ($phase) {
                $period['phase'] = ['id' => $phase->id, 'value' => $phase->actual_stage, 'occurred_at' => $this->iso($phase->occurred_at)];
            }

            return ['id' => $row->id, 'name' => $row->name, 'active' => (bool) $row->active, 'version' => $row->version,
                'period' => $period, 'last_operational_at' => $this->iso($last[$row->id] ?? null),
                'harvest' => $harvestTotals->get($period['group_id'] ?? '')];
        })->values();
        $attention = [];
        foreach ($cards as $card) {
            $gh = $card['id'];
            $period = $card['period'];
            if ($period['state'] === 'needs_review') {
                $attention[] = $this->finding('period_review', $gh, $card['name'], $at, 'running-period-v1', 'periods');
            }
            if ($period['state'] === 'ready' && $measurements !== null) {
                $pair = $pairs->get($period['group_id']);
                if (($pair->occurred_at ?? '') < $dayStart) {
                    if (! $invalidToday->has($period['group_id'])) {
                        $attention[] = $this->finding('measurement_missing_today', $gh, $card['name'], $at, 'manual-ppm-day-wib-v1', 'measurements')
                            + ['group_id' => $period['group_id']];
                    }
                } elseif ($finding = $this->outOfRange($card, $measurements[$pair->id] ?? null, $at)) {
                    $attention[] = $finding;
                }
            }
        }
        usort($attention, fn ($a, $b) => ($a['type'] !== 'period_review') <=> ($b['type'] !== 'period_review'));

        $detail = null;
        if ($selected !== null) {
            $period = $periods[$selected];
            $group = $period['group_id'] ?? null;
            $activities->where('r.planting_group_id', $group);
            $harvests->where('r.planting_group_id', $group);
            $pair = $pairs->get($group ?? '');
            $dayState = match (true) {
                $group === null || $measurements === null => 'unavailable',
                ($pair->occurred_at ?? '') >= $dayStart => 'recorded',
                $invalidToday->has($group) => 'invalid_only',
                default => 'missing',
            };
            $detail = $this->detail($selected, $groups->get($selected, collect()), $group, $measurements[$pair->id ?? ''] ?? null,
                $measurements !== null, $dayState, $at, $harvestTotals->get($group ?? ''));
        }
        $accessUntil = $this->access->sees($actor) ? null : DB::table('greenhouse_assignments')->where('user_id', $actor->id)
            ->whereIn('greenhouse_id', $all->pluck('id'))->min('effective_until');
        $recent = $this->readAcceptedSection(fn () => $this->recent($activities, $harvests));
        $complete = $recent !== null && $measurements !== null;

        return ['account_id' => $actor->public_id, 'sync_epoch' => DB::table('sync_state')->where('id', 1)->value('epoch'),
            'scope_ids' => $all->pluck('id')->sort()->values(), 'choices' => $all->map(fn ($row) => ['id' => $row->id, 'name' => $row->name]),
            'access_until' => $this->iso($accessUntil),
            'greenhouse_id' => $selected, 'as_of' => $at->utc()->toISOString(), 'calendar' => 'Asia/Jakarta', 'day' => $at->toDateString(),
            'coverage' => $complete ? 'complete' : 'partial', 'greenhouses' => $cards, 'attention' => $attention,
            'recent' => $recent, 'detail' => $detail, 'cash' => $this->cash($ids, $at)];
    }

    private function operations(string $table, array $ids): Builder
    {
        return DB::table("$table as r")->join('planting_groups as g', 'g.id', '=', 'r.planting_group_id')
            ->join('seasons as s', 's.id', '=', 'g.season_id')->whereIn('s.greenhouse_id', $ids)->where('r.record_state', 'active');
    }

    private function lastByGreenhouse(Builder ...$queries): array
    {
        $result = [];
        foreach ($queries as $query) {
            $last = (clone $query)->groupBy('s.greenhouse_id')->selectRaw('s.greenhouse_id, MAX(r.occurred_at) as last_at')->get();
            foreach ($last as $row) {
                $result[$row->greenhouse_id] = max($row->last_at, $result[$row->greenhouse_id] ?? '');
            }
        }

        return $result;
    }

    private function latestPerGroup(Builder $query): Collection
    {
        $ranked = $query->select('r.*')->selectRaw('ROW_NUMBER() OVER (PARTITION BY r.planting_group_id ORDER BY r.occurred_at DESC, r.id DESC) AS position_in_group');

        return DB::query()->fromSub($ranked, 'ranked')->where('position_in_group', 1)->get()->keyBy('planting_group_id');
    }

    private function recent(Builder $activities, Builder $harvests): array
    {
        $recent = [];
        foreach (['activity' => $activities, 'harvest' => $harvests] as $kind => $query) {
            $rows = $query->orderByDesc('r.occurred_at')->orderByDesc('r.id')->limit(5)
                ->get(['r.id', 'r.version', 's.greenhouse_id', 's.id as season_id', 'g.id as group_id']);
            $snapshots = $this->accepted->readMany($kind, $rows->all());
            foreach ($rows as $row) {
                $body = $snapshots[$row->id];
                $recent[] = ['id' => $row->id, 'kind' => $kind, 'activity_type' => $body['activity_type'] ?? null,
                    'greenhouse_id' => $row->greenhouse_id, 'season_id' => $row->season_id, 'group_id' => $row->group_id,
                    'recorded_by_name' => $body['recorded_by_name'] ?? null, 'occurred_at' => $body['occurred_at'], 'received_at' => $body['created_at']];
            }
        }
        usort($recent, fn ($a, $b) => [$b['occurred_at'], $b['id']] <=> [$a['occurred_at'], $a['id']]);

        return array_slice($recent, 0, 5);
    }

    private function detail(string $gh, Collection $groups, ?string $group, ?array $measurement, bool $measurementAvailable, string $dayState, CarbonImmutable $at, ?object $harvest): array
    {
        $periods = $groups->map(fn ($row) => [...(array) $row, 'eligible' => $this->period->eligible($row, $at->toDateString())])->values();
        $seasons = DB::table('seasons')->where('greenhouse_id', $gh)->orderByDesc('start_date')->orderBy('id')
            ->get(['id', 'name', 'greenhouse_id', 'start_date', 'end_date', 'status', 'version']);
        if ($group === null) {
            return ['periods' => $periods, 'seasons' => $seasons, 'measurement' => null, 'measurement_day_state' => $dayState, 'harvest' => null, 'schedules' => []];
        }
        $schedules = DB::table('schedules')->where('planting_group_id', $group)->where('active', true)
            ->where('planned_at', '>=', $this->sqlTime($at->startOfDay()))->orderBy('planned_at')->orderBy('id')
            ->limit(3)->get(['id', 'activity_type', 'planned_at', 'notes'])
            ->map(fn ($row) => [...(array) $row, 'planned_at' => $this->iso($row->planned_at)]);

        return ['periods' => $periods, 'seasons' => $seasons, 'measurement' => $measurement, 'measurement_available' => $measurementAvailable,
            'measurement_day_state' => $dayState,
            'harvest' => $harvest === null ? ['records' => 0, 'weight_kg' => '0', 'fruit_count' => 0]
                : ['records' => $harvest->records, 'weight_kg' => $harvest->weight_kg, 'fruit_count' => $harvest->fruit_count],
            'schedules' => $schedules];
    }

    private function outOfRange(array $card, ?array $measurement, CarbonImmutable $at): ?array
    {
        $reference = $measurement['reference_snapshot'] ?? [];
        $check = $reference['checks']['manual_ppm'] ?? [];
        $target = $check['target'] ?? [];
        if (($check['status'] ?? null) !== 'outside_range' || ($target['unit'] ?? null) !== 'ppm' ||
            ! isset($target['id'], $target['version'], $reference['comparator_version'], $check['actual']) ||
            (! isset($target['minimum'], $target['maximum']) && ! isset($target['target_value'], $target['tolerance']))) {
            return null;
        }
        $finding = $this->finding('measurement_outside_range', $card['id'], $card['name'], $at,
            'target-comparator-'.$reference['comparator_version'], 'measurements');

        return [...$finding, 'id' => $finding['id'].':'.$measurement['id'], 'group_id' => $card['period']['group_id'],
            'source_record_id' => $measurement['id'], 'occurred_at' => $measurement['occurred_at'],
            'actual' => $check['actual'], 'unit' => 'ppm', 'target' => array_intersect_key($target,
                array_flip(['id', 'version', 'minimum', 'maximum', 'target_value', 'tolerance']))];
    }

    private function readAcceptedSection(callable $read): ?array
    {
        try {
            return $read();
        } catch (DomainFailure $failure) {
            if ($failure->domainCode !== 'record_snapshot_missing') {
                throw $failure;
            }
            return null;
        }
    }

    private function cash(array $ids, CarbonImmutable $at): array
    {
        $start = $at->startOfMonth();
        $end = $start->addMonth();
        $sales = DB::table('sales')->whereIn('season_id', DB::table('seasons')->select('id')->whereIn('greenhouse_id', $ids))
            ->where('payment_status', 'paid');
        $expenses = DB::table('expenses')->whereIn('greenhouse_id', $ids);
        $totals = function (Builder $query, string $amount) use ($start, $end): object {
            return $query->where('record_state', 'active')->where('paid_at', '>=', $this->sqlTime($start))->where('paid_at', '<', $this->sqlTime($end))
                ->selectRaw("COUNT(*) AS records, CAST(COALESCE(SUM($amount),CAST(0 AS DECIMAL(20,2))) AS CHAR) AS amount")->first();
        };

        return ['from' => $start->toDateString(), 'until' => $end->subDay()->toDateString(), 'currency' => 'IDR',
            'receipts' => $totals($sales, 'total_amount'), 'payments' => $totals($expenses, 'amount')];
    }

    private function finding(string $type, string $gh, string $name, CarbonImmutable $at, string $rule, string $destination): array
    {
        return ['id' => "$type:$gh", 'type' => $type, 'greenhouse_id' => $gh, 'greenhouse_name' => $name,
            'checked_at' => $at->utc()->toISOString(), 'rule' => $rule, 'destination' => $destination];
    }

    private function sqlTime(CarbonImmutable $at): string
    {
        return $at->utc()->format('Y-m-d H:i:s.u');
    }

    private function iso(?string $at): ?string
    {
        return $at === null ? null : CarbonImmutable::parse($at, 'UTC')->toISOString();
    }
}
