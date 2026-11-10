<?php

namespace App\Domain\Harvest;

use App\Domain\Contracts\DecimalValue;
use App\Domain\Cultivation\GroupContext;
use App\Models\User;
use Carbon\CarbonImmutable;
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
        $totals = ['saleable' => ['fruit_count' => 0, 'weight_kg' => '0.000000'],
            'defective' => ['fruit_count' => 0, 'weight_kg' => '0.000000'],
            'unknown' => ['fruit_count' => 0, 'weight_kg' => '0.000000']];
        $rows = DB::table('harvest_items as item')->join('harvests as harvest', 'harvest.id', '=', 'item.harvest_id')
            ->where('harvest.planting_group_id', $id)->where('harvest.record_state', 'active')->where('item.active', true);
        $records = (clone $rows)->distinct()->count('harvest.id');
        $amounts = $rows->groupBy('item.quality')->selectRaw('item.quality, SUM(item.fruit_count) as fruit_count, CAST(SUM(item.weight_kg) AS CHAR) as weight_kg')->get();
        foreach ($amounts as $row) {
            $totals[$row->quality ?? 'unknown'] = ['fruit_count' => (int) $row->fruit_count, 'weight_kg' => $row->weight_kg];
        }
        $saleable = $totals['saleable']['fruit_count'];
        $completeQuality = $totals['unknown']['fruit_count'] === 0 && bccomp($totals['unknown']['weight_kg'], '0', 6) === 0;
        $comparable = $target !== null && $records > 0 && $completeQuality;
        $variety = DB::table('varieties')->where('id', $group->variety_id)->first(['id', 'name', 'version']);

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
}
