<?php

use Illuminate\Foundation\Inspiring;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Schedule;

Artisan::command('inspire', function () {
    $this->comment(Inspiring::quote());
})->purpose('Display an inspiring quote');

// Butuh cron `php artisan schedule:run` tiap menit di server; token kedaluwarsa disimpan 24 jam untuk penelusuran.
Schedule::command('sanctum:prune-expired --hours=24')->daily();
