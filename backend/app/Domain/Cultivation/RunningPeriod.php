<?php

namespace App\Domain\Cultivation;

use App\Domain\Contracts\DomainFailure;
use Carbon\CarbonImmutable;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;

final class RunningPeriod
{
    public function groups(array $greenhouseIds): Collection
    {
        return DB::table('planting_groups as g')
            ->join('seasons as s', 's.id', '=', 'g.season_id')
            ->join('varieties as v', fn ($join) => $join->on('v.id', '=', 'g.variety_id')->on('v.greenhouse_id', '=', 's.greenhouse_id'))
            ->whereIn('s.greenhouse_id', $greenhouseIds)
            ->orderByDesc('g.sowing_date')->orderBy('g.id')
            ->get(['g.id as group_id', 'g.name as group_name', 'g.active as group_active', 'g.sowing_date', 'g.transplant_date',
                's.id as season_id', 's.name as season_name', 's.status as season_status', 's.greenhouse_id', 'v.name as variety']);
    }

    public function eligible(object $group, string $day): bool
    {
        return $group->group_active && $group->season_status === 'open' && $group->sowing_date <= $day;
    }

    public function resolve(object $greenhouse, Collection $groups, CarbonImmutable $at): array
    {
        if (! $greenhouse->active) {
            return ['state' => 'inactive'];
        }
        $group = $groups->firstWhere('group_id', $greenhouse->running_group_id);
        if ($group && $this->eligible($group, $at->toDateString())) {
            return ['state' => 'ready', ...(array) $group,
                'age' => PlantAge::calculate($group->sowing_date, $group->transplant_date, $at->toIso8601String())];
        }
        $legacy = ! $greenhouse->running_period_reviewed &&
            $groups->contains(fn ($item) => $item->group_active && $item->season_status === 'open');

        return ['state' => $greenhouse->running_group_id !== null || $legacy ? 'needs_review' : 'no_active_period'];
    }

    public function designation(object $greenhouse, ?string $groupId): array
    {
        if ($groupId !== null) {
            $group = $this->groups([$greenhouse->id])->firstWhere('group_id', $groupId);
            abort_unless($group && $this->eligible($group, CarbonImmutable::now('Asia/Jakarta')->toDateString()),
                422, 'Pilih kelompok aktif di periode terbuka pada greenhouse ini yang sudah mulai disemai.');
        }
        abort_if($greenhouse->running_period_reviewed && $greenhouse->running_group_id === $groupId, 422, 'Periode berjalan sudah sesuai pilihan.');

        return ['running_group_id' => $groupId, 'running_period_reviewed' => true];
    }

    public function guardMasterChange(string $table, string $action, string $id, ?string $groupId, array $data, ?array $before): void
    {
        if ($groupId === null || ! in_array($table, ['greenhouses', 'seasons', 'planting_groups'], true)) {
            return;
        }
        $current = DB::table('planting_groups')->where('id', $groupId)->first();
        $endsDesignation = ($table === 'greenhouses' && ($data['active'] ?? true) === false)
            || ($table === 'seasons' && $action === 'close' && $current?->season_id === $id)
            || ($table === 'planting_groups' && $id === $groupId && (
                ($data['active'] ?? true) === false || ($data['sowing_date'] ?? $before['sowing_date']) > CarbonImmutable::now('Asia/Jakarta')->toDateString()
            ));
        abort_if($endsDesignation, 422, 'Akhiri atau ganti periode berjalan terlebih dahulu.');
        if ($table === 'planting_groups' && $current?->season_id === ($before['season_id'] ?? $data['season_id'] ?? null)) {
            abort_if($id !== $groupId && ! ($before['active'] ?? false) && ($data['active'] ?? true),
                422, 'Periode berjalan sudah memiliki kelompok tanam.');
        }
    }

    public function assertHomeParent(array $context, int $reviewedVersion): void
    {
        $gh = $context['greenhouse'];
        if ($gh->running_group_id !== $context['group']->id || $gh->version !== $reviewedVersion) {
            throw new DomainFailure('running_period_changed', 'Periode tanam berubah. Tinjau draf dengan konteks aslinya.', 422);
        }
    }
}
