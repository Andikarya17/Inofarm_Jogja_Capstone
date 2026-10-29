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
        $season = DB::table('seasons')->where('id', $group->season_id)->first() ?? abort(404);
        $gh = $this->access->require($actor, $season->greenhouse_id);
        if ($write) {
            abort_unless($gh->active && $group->active, 422, 'Greenhouse atau kelompok sudah tidak aktif.');
            if ($season->status !== 'open') {
                throw new DomainFailure('season_closed', 'Musim ditutup. Antrean perlu ditinjau pengelola.');
            }
        }

        return ['group' => $group, 'season' => $season, 'greenhouse' => $gh];
    }
}
