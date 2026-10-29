<?php

use App\Http\Controllers\V2\AuthController;
use App\Http\Controllers\V2\ContextController;
use Illuminate\Support\Facades\Route;

Route::prefix('v2')->group(function () {
    Route::post('auth/login', [AuthController::class, 'login'])->middleware('throttle:20,1');
    Route::middleware(['auth:sanctum', 'finance-session', 'throttle:120,1'])->group(function () {
        Route::get('me', [AuthController::class, 'me']);
        Route::post('auth/logout', [AuthController::class, 'logout']);
        Route::get('greenhouses', [ContextController::class, 'greenhouses']);
        Route::get('greenhouses/{id}', [ContextController::class, 'greenhouse'])->whereUuid('id');
    });
});
