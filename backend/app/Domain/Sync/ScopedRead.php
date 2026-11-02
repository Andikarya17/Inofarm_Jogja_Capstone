<?php

namespace App\Domain\Sync;

use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Laravel\Sanctum\PersonalAccessToken;

/** Small-farm consistency boundary, serialized with mutations and permission changes. */
final class ScopedRead
{
    public function run(Request $request, callable $read): mixed
    {
        return DB::transaction(function () use ($request, $read) {
            DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
            $sessionActor = $request->user();
            abort_unless($sessionActor instanceof User, 401);
            $actor = User::findOrFail($sessionActor->id);
            abort_unless($actor->active && in_array($actor->role, ['worker', 'manager'], true), 403, 'Hak akses akun telah berubah.');
            $token = $sessionActor->currentAccessToken();
            if ($token instanceof PersonalAccessToken) {
                abort_unless(DB::table('personal_access_tokens')->where('id', $token->id)->where('tokenable_id', $actor->id)
                    ->where(fn ($q) => $q->whereNull('expires_at')->orWhere('expires_at', '>', now()))->exists(), 401, 'Sesi telah dicabut atau berakhir.');
                $actor->withAccessToken($token);
            }

            return $read($actor);
        });
    }
}
