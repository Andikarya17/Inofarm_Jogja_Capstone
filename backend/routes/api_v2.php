<?php

use App\Http\Controllers\V2\ActivityController;
use App\Http\Controllers\V2\AuthController;
use App\Http\Controllers\V2\ChangeFeedController;
use App\Http\Controllers\V2\ContextController;
use App\Http\Controllers\V2\HarvestController;
use App\Http\Controllers\V2\MasterController;
use App\Http\Controllers\V2\ReferenceController;
use App\Http\Controllers\V2\SyncStatusController;
use App\Http\Controllers\V2\UserController;
use App\Http\Middleware\FinanceLocale;
use Illuminate\Support\Facades\Route;

Route::prefix('v2')->middleware(FinanceLocale::class)->group(function () {
    Route::post('auth/login', [AuthController::class, 'login'])->middleware('throttle:20,1');
    Route::middleware(['auth:sanctum', 'finance-session', 'throttle:120,1'])->group(function () {
        Route::get('me', [AuthController::class, 'me']);
        Route::post('auth/logout', [AuthController::class, 'logout']);
        Route::get('greenhouses', [ContextController::class, 'greenhouses']);
        Route::get('greenhouses/{id}', [ContextController::class, 'greenhouse'])->whereUuid('id');
        Route::get('sync/status', SyncStatusController::class);
        Route::post('sync/operations', [ActivityController::class, 'sync']);
        Route::get('sync/changes', ChangeFeedController::class);
        Route::get('activities', [ActivityController::class, 'index']);
        Route::get('activities/{id}', [ActivityController::class, 'show'])->whereUuid('id');
        Route::post('activities', [ActivityController::class, 'store']);
        Route::patch('activities/{id}', [ActivityController::class, 'update'])->whereUuid('id');
        Route::post('activities/{id}/cancel', [ActivityController::class, 'cancel'])->whereUuid('id');
        Route::get('activities/{id}/audit', [ActivityController::class, 'audit'])->whereUuid('id');
        Route::get('harvests', [HarvestController::class, 'index']);
        Route::get('harvests/{id}', [HarvestController::class, 'show'])->whereUuid('id');
        Route::post('harvests', [HarvestController::class, 'store']);
        Route::patch('harvests/{id}', [HarvestController::class, 'update'])->whereUuid('id');
        Route::post('harvests/{id}/cancel', [HarvestController::class, 'cancel'])->whereUuid('id');
        Route::get('harvests/{id}/audit', [HarvestController::class, 'audit'])->whereUuid('id');
        Route::get('harvests/{id}/availability', [HarvestController::class, 'availability'])->whereUuid('id');
        Route::get('users', [UserController::class, 'index']);
        Route::post('users', [UserController::class, 'store']);
        Route::patch('users/{id}', [UserController::class, 'update'])->whereUuid('id');
        Route::get('{reference}', [ReferenceController::class, 'index'])->whereIn('reference', ['schedules', 'targets']);
        Route::get('{reference}/{id}', [ReferenceController::class, 'show'])->whereIn('reference', ['schedules', 'targets'])->whereUuid('id');
        Route::get('{reference}/{id}/versions', [ReferenceController::class, 'versions'])->whereIn('reference', ['schedules', 'targets'])->whereUuid('id');
        Route::post('{reference}', [ReferenceController::class, 'store'])->whereIn('reference', ['schedules', 'targets']);
        Route::patch('{reference}/{id}', [ReferenceController::class, 'update'])->whereIn('reference', ['schedules', 'targets'])->whereUuid('id');
        Route::get('{resource}', [MasterController::class, 'index'])->whereIn('resource', ['seasons', 'planting-groups', 'varieties', 'materials', 'units', 'grades', 'expense-categories', 'assignments']);
        Route::get('{resource}/{id}', [MasterController::class, 'show'])->whereIn('resource', ['seasons', 'planting-groups', 'varieties', 'materials', 'units', 'grades', 'expense-categories', 'assignments'])->whereUuid('id');
        Route::post('{resource}', [MasterController::class, 'store'])->whereIn('resource', ['greenhouses', 'seasons', 'planting-groups', 'varieties', 'materials', 'units', 'grades', 'expense-categories', 'assignments']);
        Route::patch('{resource}/{id}', [MasterController::class, 'update'])->whereIn('resource', ['greenhouses', 'seasons', 'planting-groups', 'varieties', 'materials', 'units', 'grades', 'expense-categories', 'assignments'])->whereUuid('id');
        Route::post('{resource}/{id}/{action}', [MasterController::class, 'transition'])->where('resource', 'seasons')->whereIn('action', ['close', 'reopen'])->whereUuid('id');
        Route::get('{resource}/{id}/audit', [MasterController::class, 'audit'])->whereIn('resource', ['greenhouses', 'seasons', 'planting-groups', 'varieties', 'materials', 'units', 'grades', 'expense-categories', 'assignments'])->whereUuid('id');
    });
});
