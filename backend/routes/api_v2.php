<?php

use App\Http\Controllers\V2\AuthController;
use App\Http\Controllers\V2\ContextController;
use App\Http\Controllers\V2\MasterController;
use App\Http\Controllers\V2\SyncStatusController;
use App\Http\Controllers\V2\UserController;
use Illuminate\Support\Facades\Route;

Route::prefix('v2')->group(function () {
    Route::post('auth/login', [AuthController::class, 'login'])->middleware('throttle:20,1');
    Route::middleware(['auth:sanctum', 'finance-session', 'throttle:120,1'])->group(function () {
        Route::get('me', [AuthController::class, 'me']);
        Route::post('auth/logout', [AuthController::class, 'logout']);
        Route::get('greenhouses', [ContextController::class, 'greenhouses']);
        Route::get('greenhouses/{id}', [ContextController::class, 'greenhouse'])->whereUuid('id');
        Route::get('sync/status', SyncStatusController::class);
        Route::get('users', [UserController::class, 'index']);
        Route::post('users', [UserController::class, 'store']);
        Route::patch('users/{id}', [UserController::class, 'update'])->whereUuid('id');
        Route::get('{resource}', [MasterController::class, 'index'])->whereIn('resource', ['seasons', 'planting-groups', 'varieties', 'materials', 'units', 'grades', 'expense-categories', 'assignments']);
        Route::get('{resource}/{id}', [MasterController::class, 'show'])->whereIn('resource', ['seasons', 'planting-groups', 'varieties', 'materials', 'units', 'grades', 'expense-categories', 'assignments'])->whereUuid('id');
        Route::post('{resource}', [MasterController::class, 'store'])->whereIn('resource', ['greenhouses', 'seasons', 'planting-groups', 'varieties', 'materials', 'units', 'grades', 'expense-categories', 'assignments']);
        Route::patch('{resource}/{id}', [MasterController::class, 'update'])->whereIn('resource', ['greenhouses', 'seasons', 'planting-groups', 'varieties', 'materials', 'units', 'grades', 'expense-categories', 'assignments'])->whereUuid('id');
        Route::post('{resource}/{id}/{action}', [MasterController::class, 'transition'])->where('resource', 'seasons')->whereIn('action', ['close', 'reopen'])->whereUuid('id');
        Route::get('{resource}/{id}/audit', [MasterController::class, 'audit'])->whereIn('resource', ['greenhouses', 'seasons', 'planting-groups', 'varieties', 'materials', 'units', 'grades', 'expense-categories', 'assignments'])->whereUuid('id');
    });
});
