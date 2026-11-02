<?php

use App\Http\Middleware\DeviceAuth;
use App\Http\Middleware\FinanceSession;
use App\Http\Middleware\OwnerScope;
use App\Http\Middleware\PayloadLimit;
use App\Support\TestDatabaseGuard;
use Illuminate\Auth\AuthenticationException;
use Illuminate\Database\Eloquent\ModelNotFoundException;
use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Request;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;
use Symfony\Component\HttpKernel\Exception\HttpExceptionInterface;

$app = Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        api: __DIR__.'/../routes/api.php',
        commands: __DIR__.'/../routes/console.php',
        health: '/up',
    )
    ->withMiddleware(function (Middleware $middleware): void {
        $middleware->alias(['device' => DeviceAuth::class, 'owner' => OwnerScope::class, 'finance-session' => FinanceSession::class]);
        $middleware->api(prepend: [PayloadLimit::class]);
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        $exceptions->render(function (Throwable $e, Request $request) {
            if (! $request->is('api/v2/*')) {
                return null;
            }
            $status = match (true) {
                $e instanceof ValidationException => 422,
                $e instanceof AuthenticationException => 401,
                $e instanceof ModelNotFoundException => 404,
                $e instanceof HttpExceptionInterface => $e->getStatusCode(),
                default => 500,
            };
            $codes = [401 => 'authentication_required', 403 => 'access_denied', 404 => 'not_found', 409 => 'version_conflict', 413 => 'payload_too_large', 422 => 'validation_failed', 429 => 'rate_limited'];
            $message = $status >= 500 ? 'Server belum dapat memproses permintaan.' : ($status === 401 ? 'Silakan masuk kembali dengan akun yang sama.' : $e->getMessage());

            return response()->json(['code' => $codes[$status] ?? 'server_error', 'message' => $message,
                'field_errors' => (object) ($e instanceof ValidationException ? $e->errors() : []),
                'request_id' => (string) Str::uuid(), 'reasons' => [$message]], $status,
                $e instanceof HttpExceptionInterface ? $e->getHeaders() : []);
        });
        $exceptions->shouldRenderJsonWhen(
            fn (Request $request) => $request->is('api/*') || $request->expectsJson(),
        );
    })->create();

TestDatabaseGuard::install($app);

return $app;
