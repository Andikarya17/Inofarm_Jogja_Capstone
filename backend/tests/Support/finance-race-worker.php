<?php

use App\Domain\Activities\ActivityService;
use App\Domain\Contracts\DomainFailure;
use App\Models\User;
use App\Support\TestDatabaseGuard;
use Illuminate\Contracts\Console\Kernel;

// Dedicated child process. Never boot a database before the guard.
putenv('APP_ENV=testing');
$_ENV['APP_ENV'] = $_SERVER['APP_ENV'] = 'testing';
require dirname(__DIR__, 2).'/vendor/autoload.php';
$app = require dirname(__DIR__, 2).'/bootstrap/app.php';
TestDatabaseGuard::install($app, required: true);
$app->make(Kernel::class)->bootstrap();
[$script, $inputPath, $readyPath, $goPath, $resultPath] = $argv;
$input = json_decode(file_get_contents($inputPath), true, flags: JSON_THROW_ON_ERROR);
$actor = User::findOrFail($input['actor_id']);
file_put_contents($readyPath, 'ready');
$deadline = microtime(true) + 15;
while (! is_file($goPath) && microtime(true) < $deadline) {
    usleep(10000);
    clearstatcache(true, $goPath);
}
if (! is_file($goPath)) {
    file_put_contents($resultPath, json_encode(['status' => 'barrier_timeout']));
    exit(1);
}
try {
    $receipt = app(ActivityService::class)->mutate($actor, $input['operation']);
    $result = ['status' => 'accepted', 'version' => $receipt['body']['version']];
} catch (DomainFailure $e) {
    $result = ['status' => $e->domainCode, 'version' => $e->currentVersion];
} catch (Throwable $e) {
    $result = ['status' => 'error', 'class' => $e::class];
}
file_put_contents($resultPath, json_encode($result, JSON_THROW_ON_ERROR));
