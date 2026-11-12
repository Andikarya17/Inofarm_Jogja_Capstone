<?php

namespace App\Domain\Identity;

use App\Models\User;
use Illuminate\Database\Eloquent\Builder as UserQuery;
use Illuminate\Database\Query\Builder;
use Illuminate\Support\Facades\DB;

/**
 * A manager reaches every greenhouse, active or not, without an assignment.
 * Other roles reach only their active, effective assignments.
 */
final class GreenhouseAccess
{
    public function ids(User $actor): Builder
    {
        if ($this->sees($actor)) {
            return DB::table('greenhouses')->select('id as greenhouse_id');
        }

        return DB::table('greenhouse_assignments')->select('greenhouse_id')
            ->where('user_id', $actor->id)->where('active', true)
            ->where('effective_from', '<=', now())
            ->where(fn (Builder $q) => $q->whereNull('effective_until')->orWhere('effective_until', '>', now()));
    }

    public function query(User $actor): Builder
    {
        if ($this->sees($actor)) {
            return DB::table('greenhouses');
        }

        return DB::table('greenhouses')->whereIn('id', $this->ids($actor));
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

        return User::whereNotNull('public_id')->whereNotIn('id', DB::table('greenhouse_assignments')
            ->select('user_id')->whereNotIn('greenhouse_id', $this->ids($actor)));
    }
}
