<?php

namespace App\Domain\Harvest;

use App\Domain\Contracts\DecimalValue;
use App\Domain\Cultivation\GroupContext;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Database\Query\Builder;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;
use InvalidArgumentException;

final class HarvestProgress
{
    public function __construct(private GroupContext $context) {}

    public static function decode(?string $value): ?array
    {
        return $value === null ? null : json_decode($value, true, flags: JSON_THROW_ON_ERROR);
    }

    public static function changeTarget(User $actor, ?array $input, ?array $previous, int $version): ?string
    {
        $fruit = $input['fruit_count'] ?? null;
        $kg = $input['weight_kg'] ?? null;
        $basis = trim($input['basis'] ?? '');
        if ($fruit === null && $kg === null && $basis === '') {
            return null;
        }
        if ($fruit === null || $basis === '') {
            throw ValidationException::withMessages(['harvest_target' => 'Isi target buah dan dasar penetapannya.']);
        }
        if ($kg !== null) {
            try {
                DecimalValue::parse($kg);
            } catch (InvalidArgumentException $e) {
                throw ValidationException::withMessages(['harvest_target.weight_kg' => $e->getMessage()]);
            }
            if (bccomp($kg, '0', 6) <= 0) {
                throw ValidationException::withMessages(['harvest_target.weight_kg' => 'Target kg harus lebih besar dari nol.']);
            }
            $kg = bcadd($kg, '0', 6);
        }
        $next = ['fruit_count' => $fruit, 'weight_kg' => $kg, 'basis' => $basis];
        if ($previous !== null && array_intersect_key($previous, $next) == $next) {
            return json_encode($previous, JSON_THROW_ON_ERROR);
        }

        return json_encode($next + ['version' => $version, 'set_by' => $actor->public_id, 'set_by_name' => $actor->name,
            'set_at' => CarbonImmutable::now('UTC')->toISOString()], JSON_THROW_ON_ERROR);
    }

    public function read(User $actor, string $id): array
    {
        $ctx = $this->context->get($actor, $id);
        $group = $ctx['group'];
        $target = self::decode($group->harvest_target);
        $totals = $this->emptyTotals();
        $rows = $this->items($id);
        $records = (clone $rows)->distinct()->count('harvest.id');
        $amounts = (clone $rows)->groupBy('item.quality')->selectRaw('item.quality, SUM(item.fruit_count) as fruit_count, CAST(SUM(item.weight_kg) AS CHAR) as weight_kg')->get();
        foreach ($amounts as $row) {
            $totals[$row->quality ?? 'unknown'] = ['fruit_count' => (int) $row->fruit_count, 'weight_kg' => $row->weight_kg];
        }
        $saleable = $totals['saleable']['fruit_count'];
        $completeQuality = $totals['unknown']['fruit_count'] === 0 && bccomp($totals['unknown']['weight_kg'], '0', 6) === 0;
        $comparable = $target !== null && $records > 0 && $completeQuality;
        $variety = DB::table('varieties')->where('id', $group->variety_id)->first(['id', 'name', 'version']);

        if ($actor->role === 'worker') {
            $byStatus = array_fill_keys(['valid', 'invalid', 'pending', 'manager', 'legacy'], $this->emptyTotals());
            $status = self::statusSql();
            $amounts = $this->joinReviews($rows)->groupBy('item.quality', 'verification_status')
                ->selectRaw("$status as verification_status, item.quality, SUM(item.fruit_count) as fruit_count, CAST(SUM(item.weight_kg) AS CHAR) as weight_kg")->get();
            foreach ($amounts as $row) {
                $byStatus[$row->verification_status][$row->quality ?? 'unknown'] = ['fruit_count' => (int) $row->fruit_count, 'weight_kg' => $row->weight_kg];
            }
            return ['greenhouse_id' => $ctx['greenhouse']->id, 'greenhouse' => $ctx['greenhouse']->name,
                'season' => $ctx['season']->name, 'season_status' => $ctx['season']->status, 'group_id' => $id, 'group' => $group->name,
                'variety' => $variety?->name, 'completed' => $group->harvest_completion !== null,
                'target' => $target === null ? null : array_intersect_key($target, array_flip(['fruit_count', 'weight_kg', 'basis', 'version'])),
                'records' => $records, 'totals' => $totals, 'verification' => $byStatus, 'complete_quality' => $completeQuality,
                'achievement_percent' => $comparable ? bcdiv(bcmul((string) $saleable, '100', 0), (string) $target['fruit_count'], 6) : null,
                'weight_achievement_percent' => $comparable && $target['weight_kg'] !== null
                    ? bcdiv(bcmul($totals['saleable']['weight_kg'], '100', 6), $target['weight_kg'], 6) : null,
                'as_of' => CarbonImmutable::now('UTC')->toISOString()];
        }

        return ['greenhouse_id' => $ctx['greenhouse']->id, 'greenhouse' => $ctx['greenhouse']->name,
            'season_id' => $ctx['season']->id, 'season' => $ctx['season']->name, 'season_status' => $ctx['season']->status,
            'group_id' => $id, 'group' => $group->name, 'group_version' => $group->version,
            'variety' => $variety, 'population' => $group->population,
            'sowing_date' => $group->sowing_date, 'transplant_date' => $group->transplant_date,
            'target' => $target, 'completion' => self::decode($group->harvest_completion), 'records' => $records,
            'totals' => $totals, 'complete_quality' => $completeQuality,
            'achievement_percent' => $comparable ? bcdiv(bcmul((string) $saleable, '100', 0), (string) $target['fruit_count'], 6) : null,
            'difference_fruit' => $comparable ? $target['fruit_count'] - $saleable : null,
            'as_of' => CarbonImmutable::now('UTC')->toISOString()];
    }

    public function sources(User $actor, string $id, array $filter): array
    {
        $this->context->get($actor, $id);
        $status = self::statusSql();
        $query = $this->joinReviews(DB::table('harvests as harvest'))->where('harvest.planting_group_id', $id)
            ->where('harvest.record_state', 'active')->select(['harvest.id', 'harvest.version', 'harvest.occurred_at'])
            ->selectRaw("$status as verification_status");
        if (isset($filter['status'])) {
            $query->whereRaw("$status = ?", [$filter['status']]);
        }
        $page = $query->orderByDesc('harvest.occurred_at')->orderByDesc('harvest.id')->cursorPaginate((int) ($filter['limit'] ?? 25));

        return ['data' => array_map(fn ($row) => ['id' => $row->id, 'version' => $row->version,
            'occurred_at' => CarbonImmutable::parse($row->occurred_at, 'UTC')->toISOString(),
            'verification_status' => $row->verification_status], $page->items()),
            'next_cursor' => $page->nextCursor()?->encode()];
    }

    private function items(string $group): Builder
    {
        return DB::table('harvest_items as item')->join('harvests as harvest', 'harvest.id', '=', 'item.harvest_id')
            ->where('harvest.planting_group_id', $group)->where('harvest.record_state', 'active')->where('item.active', true);
    }

    private function joinReviews(Builder $query): Builder
    {
        return $query->leftJoin('harvest_reviews as review', fn ($join) => $join
            ->on('review.harvest_id', '=', 'harvest.id')->on('review.harvest_version', '=', 'harvest.version'));
    }

    private static function statusSql(): string
    {
        return "COALESCE(review.decision, CASE harvest.recorded_by_role WHEN 'worker' THEN 'pending' WHEN 'manager' THEN 'manager' ELSE 'legacy' END)";
    }

    private function emptyTotals(): array
    {
        return array_fill_keys(['saleable', 'defective', 'unknown'], ['fruit_count' => 0, 'weight_kg' => '0.000000']);
    }
}
