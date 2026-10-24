<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;

class OwnerScope
{
    public function handle(Request $request, Closure $next)
    {
        abort_unless(in_array($request->user()?->role, ['owner', 'leader'], true) && $request->user()?->tank_id, 403, 'Akses owner/leader diperlukan.');

        return $next($request);
    }
}
