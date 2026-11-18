<?php

namespace App\Domain\Cultivation;

use App\Domain\Contracts\DomainFailure;
use App\Domain\Identity\GreenhouseAccess;
use App\Models\User;
use Illuminate\Support\Facades\DB;

final class GroupContext
{
    public function __construct(private GreenhouseAccess $access) {}

    public function get(User $actor, string $id, bool $write = false): array
    {
        $group = DB::table('planting_groups')->where('id', $id)->first() ?? abort(404);
        $context = $this->season($actor, $group->season_id);
        if ($write) {
            $this->writable($context, $group->active);
        }

        return [...$context, 'group' => $group];
    }

    /** Greenhouse-level context: one nutrient tank per greenhouse, so no planting group. */
    public function season(User $actor, string $id, bool $write = false): array
    {
        $season = DB::table('seasons')->where('id', $id)->first() ?? abort(404);
        $gh = $this->access->require($actor, $season->greenhouse_id);
        $context = ['group' => null, 'season' => $season, 'greenhouse' => $gh];
        if ($write) {
            $this->writable($context, true);
        }

        return $context;
    }

    private function writable(array $context, bool $groupActive): void
    {
        abort_unless($context['greenhouse']->active && $groupActive, 422, 'Greenhouse atau kelompok sudah tidak aktif.');
        if ($context['season']->status !== 'open') {
            throw new DomainFailure('season_closed', 'Musim ditutup. Antrean perlu ditinjau pengelola.');
        }
    }

    /** Context of an activity row or payload: its group when it names one (spray, legacy rows), else its season. */
    public function activity(User $actor, object|array $row, bool $write = false): array
    {
        $row = (array) $row;

        return ($row['planting_group_id'] ?? null) !== null
            ? $this->get($actor, $row['planting_group_id'], $write)
            : $this->season($actor, $row['season_id'] ?? abort(422, 'Pilih greenhouse dan musim.'), $write);
    }
}
