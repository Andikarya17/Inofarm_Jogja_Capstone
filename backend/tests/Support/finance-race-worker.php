<?php

use App\Domain\Activities\ActivityService;
use App\Domain\Attachments\AttachmentService;
use App\Domain\Attachments\UploadedEvidence;
use App\Domain\Contracts\DomainFailure;
use App\Domain\Finance\AssetService;
use App\Domain\Finance\CostAllocationService;
use App\Domain\Finance\ExpenseService;
use App\Domain\Finance\InvestmentReportService;
use App\Domain\Finance\InvestmentScenarioService;
use App\Domain\Finance\SaleService;
use App\Domain\Finance\SeasonReportService;
use App\Domain\Harvest\HarvestService;
use App\Models\User;
use App\Support\TestDatabaseGuard;
use Illuminate\Contracts\Console\Kernel;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\DB;
use Symfony\Component\HttpKernel\Exception\HttpExceptionInterface;

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
$connectionId = (int) DB::selectOne('SELECT CONNECTION_ID() AS connection_id')->connection_id;
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
    $service = match ($input['operation']['entity_type']) {
        'activity' => ActivityService::class,
        'harvest' => HarvestService::class,
        'cost_allocation' => CostAllocationService::class,
        'sale' => SaleService::class,
        'season_report' => SeasonReportService::class,
        'investment_scenario' => InvestmentScenarioService::class,
        'investment_report' => InvestmentReportService::class,
        'asset', 'depreciation_allocation' => AssetService::class,
        'expense' => ExpenseService::class,
        'expense_attachment' => AttachmentService::class,
        default => throw new InvalidArgumentException('Unsupported synthetic race kind.'),
    };
    if (in_array($service, [SeasonReportService::class, InvestmentReportService::class], true)) {
        $receipt = app($service)->create($actor, $input['operation']);
    } elseif ($service === AttachmentService::class) {
        $fixture = $input['attachment_fixture'];
        config(['finance.attachments' => ['max_bytes' => 1024, 'max_files' => 1, 'allowed_mime_types' => ['image/png']], 'filesystems.disks.finance_private.root' => $fixture['private_root']]);
        $file = new UploadedFile($fixture['source'], 'fixture.png', 'image/png', null, true);
        $verified = app(UploadedEvidence::class)->inspect($file, $input['operation']['payload']);
        $receipt = app($service)->mutate($actor, $input['operation'], $verified);
    } else {
        $receipt = app($service)->mutate($actor, $input['operation']);
    }
    $result = ['status' => 'accepted', 'version' => $receipt['body']['version']];
} catch (DomainFailure $e) {
    $result = ['status' => $e->domainCode, 'version' => $e->currentVersion];
} catch (HttpExceptionInterface $e) {
    $result = ['status' => 'http_'.$e->getStatusCode()];
} catch (Throwable $e) {
    $result = ['status' => 'error', 'class' => $e::class];
}
file_put_contents($resultPath, json_encode([...$result, 'pid' => getmypid(), 'connection_id' => $connectionId], JSON_THROW_ON_ERROR));
