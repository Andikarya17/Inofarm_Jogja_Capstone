<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;

class PayloadLimit
{
    public function handle(Request $request, Closure $next)
    {
        abort_if(strlen($request->getContent()) > 16384, 413, 'Payload melebihi 16 KiB.');

        return $next($request);
    }
}
