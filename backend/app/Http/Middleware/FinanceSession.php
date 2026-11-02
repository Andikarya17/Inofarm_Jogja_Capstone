<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;

final class FinanceSession
{
    public function handle(Request $request, Closure $next)
    {
        $user = $request->user();
        abort_unless($user?->active && $user->public_id && in_array($user->role, ['worker', 'manager'], true)
            && $user->tokenCan('finance-v2'), 403, 'Akun atau sesi tidak memiliki akses aplikasi operasional.');

        return $next($request);
    }
}
