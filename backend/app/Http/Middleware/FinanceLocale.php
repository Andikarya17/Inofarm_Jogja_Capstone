<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\App;

final class FinanceLocale
{
    public function handle(Request $request, Closure $next)
    {
        $previous = App::currentLocale();
        App::setLocale('id');
        try {
            return $next($request);
        } finally {
            App::setLocale($previous);
        }
    }
}
