<?php

use App\Http\Middleware\DeviceAuth;
use App\Http\Middleware\OwnerScope;
use App\Http\Middleware\PayloadLimit;
use App\Support\TestDatabaseGuard;
use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Request;

$app = Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        api: __DIR__.'/../routes/api.php',
        commands: __DIR__.'/../routes/console.php',
        health: '/up',
    )
    ->withMiddleware(function (Middleware $middleware): void {
        $middleware->alias(['device' => DeviceAuth::class, 'owner' => OwnerScope::class]);
        $middleware->api(prepend: [PayloadLimit::class]);
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        $exceptions->shouldRenderJsonWhen(
            fn (Request $request) => $request->is('api/*') || $request->expectsJson(),
        );
    })->create();

TestDatabaseGuard::install($app);

return $app;
