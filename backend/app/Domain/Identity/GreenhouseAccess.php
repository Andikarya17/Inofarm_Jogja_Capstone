<?php

namespace App\Domain\Identity;

use App\Models\User;
use Illuminate\Database\Eloquent\Builder as UserQuery;
use Illuminate\Database\Query\Builder;
use Illuminate\Support\Facades\DB;

/**
 * A manager reaches every greenhouse, active or not. A worker reaches every active greenhouse
 * (user decision 17 Nov 2026); assignments no longer decide access and stay only as history.
 * Finance stays manager-only through manager(), not through this scope.
 */
final class GreenhouseAccess
{
    public function ids(User $actor): Builder
    {
        if ($this->sees($actor)) {
            return DB::table('greenhouses')->select('id as greenhouse_id');
        }

        return DB::table('greenhouses')->select('id as greenhouse_id')->where('active', true);
    }

    public function query(User $actor): Builder
    {
        if ($this->sees($actor)) {
            return DB::table('greenhouses');
        }

        return DB::table('greenhouses')->where('active', true);
    }

    public function require(User $actor, string $id): object
    {
        return $this->query($actor)->where('id', $id)->first() ?? abort(404, 'Data tidak ditemukan.');
    }

    public function manager(User $actor): void
    {
        abort_unless($actor->role === 'manager', 403, 'Akses pengelola diperlukan.');
    }

    /** True when assignments do not limit the actor's greenhouse scope. */
    public function sees(User $actor): bool
    {
        return $actor->role === 'manager';
    }

    public function manageableUsers(User $actor): UserQuery
    {
        if ($this->sees($actor)) {
            return User::whereNotNull('public_id');
        }

        // Only managers manage accounts; other roles see none.
        return User::whereRaw('1 = 0');
    }
}
