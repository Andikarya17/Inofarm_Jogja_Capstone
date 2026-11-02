<?php

use App\Http\Controllers\AuthController;
use App\Http\Controllers\ConfigController;
use App\Http\Controllers\IngestController;
use App\Http\Controllers\ReadController;
use App\Http\Controllers\ShiftController;
use App\Http\Controllers\WorkerController;
use Illuminate\Support\Facades\Route;

require __DIR__.'/api_v2.php';

Route::prefix('v1')->group(function () {
    Route::post('login', [AuthController::class, 'login'])->middleware('throttle:20,1');
    Route::middleware(['auth:sanctum', 'owner', 'throttle:120,1'])->group(function () {
        Route::post('logout', [AuthController::class, 'logout']);
        Route::get('dashboard', [ReadController::class, 'dashboard']);
        Route::get('history', [ReadController::class, 'history']);
        Route::get('history/export', [ReadController::class, 'export']);
        Route::get('telemetry', [ReadController::class, 'telemetry']);
        Route::get('config', [ConfigController::class, 'show']);
        Route::put('config', [ConfigController::class, 'save']);
        Route::put('active-phase', [ConfigController::class, 'phase']);
        Route::get('workers', [WorkerController::class, 'index']);
        Route::post('workers', [WorkerController::class, 'store']);
        Route::put('workers/{id}', [WorkerController::class, 'update']);
        Route::delete('workers/{id}', [WorkerController::class, 'destroy']);
        Route::get('shifts', [ShiftController::class, 'index']);
        Route::post('shifts', [ShiftController::class, 'store']);
        Route::put('shifts/{key}', [ShiftController::class, 'store']);
        Route::delete('shifts/{key}', [ShiftController::class, 'destroy']);
    });
    Route::prefix('device')->middleware(['device', 'throttle:120,1'])->group(function () {
        Route::post('events/{kind}', [IngestController::class, 'store']);
        Route::get('config', [ConfigController::class, 'show']);
        Route::post('config-applied', [ConfigController::class, 'applied']);
    });
});
