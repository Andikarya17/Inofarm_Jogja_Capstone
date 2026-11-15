<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;

/**
 * Early gate for manager-only writes, so other roles get 403 before payload validation can describe the request shape.
 * The domain services still re-check the role inside their own transaction.
 */
final class FinanceManager
{
    public function handle(Request $request, Closure $next)
    {
        abort_unless($request->user()?->role === 'manager', 403, 'Akses pengelola diperlukan.');

        return $next($request);
    }
}
