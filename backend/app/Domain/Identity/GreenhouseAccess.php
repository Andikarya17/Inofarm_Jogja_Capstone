<?php

namespace App\Domain\Identity;

use App\Models\User;
use Illuminate\Database\Query\Builder;
use Illuminate\Support\Facades\DB;

final class GreenhouseAccess
{
    public function ids(User $actor): Builder
    {
        return DB::table('greenhouse_assignments')->select('greenhouse_id')
            ->where('user_id', $actor->id)->where('active', true)
            ->where('effective_from', '<=', now())
            ->where(fn (Builder $q) => $q->whereNull('effective_until')->orWhere('effective_until', '>', now()));
    }

    public function query(User $actor): Builder
    {
        return DB::table('greenhouses')->whereIn('id', $this->ids($actor));
    }

    public function require(User $actor, string $id): object
    {
        return $this->query($actor)->where('id', $id)->first() ?? abort(404, 'Data tidak ditemukan dalam penugasan Anda.');
    }

    public function manager(User $actor): void
    {
        abort_unless($actor->role === 'manager', 403, 'Akses pengelola diperlukan.');
    }
}
