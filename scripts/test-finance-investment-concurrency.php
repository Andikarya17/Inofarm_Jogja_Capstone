<?php

use App\Domain\Contracts\CanonicalJson;
use App\Domain\Finance\InvestmentReportService;
use App\Domain\Finance\InvestmentScenarioService;
use App\Models\User;
use App\Support\TestDatabaseGuard;
use Illuminate\Contracts\Console\Kernel;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

putenv('APP_ENV=testing');
$_ENV['APP_ENV'] = $_SERVER['APP_ENV'] = 'testing';
require dirname(__DIR__).'/backend/vendor/autoload.php';
$app = require dirname(__DIR__).'/backend/bootstrap/app.php';
TestDatabaseGuard::install($app, required: true);
$app->make(Kernel::class)->bootstrap();
$mode = $argv[1] ?? 'duplicate';
if (! in_array($mode, ['duplicate', 'source'], true)) {
    throw new InvalidArgumentException('Use duplicate|source');
}
$root = dirname(__DIR__);
$manifest = $root.'/tmp/finance-investment-fixture.json';
if (is_file($manifest)) {
    throw new RuntimeException('Another report fixture exists; refusing to touch it');
}
$children = [];
$owned = false;
$dir = $root.'/.runtime/report-race-'.Str::uuid();
mkdir($dir, 0700, true);
$fixtureTool = $root.'/scripts/finance-investment-fixture.php';
$tool = function (string $action) use ($fixtureTool) {
    $process = proc_open([PHP_BINARY, $fixtureTool, $action], [0 => ['pipe', 'r'], 1 => ['pipe', 'w'], 2 => ['pipe', 'w']], $pipes);
    if (! is_resource($process)) {
        throw new RuntimeException('Fixture child did not start');
    }
    fclose($pipes[0]);
    $out = stream_get_contents($pipes[1]);
    $err = stream_get_contents($pipes[2]);
    fclose($pipes[1]);
    fclose($pipes[2]);
    if (proc_close($process) !== 0) {
        throw new RuntimeException('Fixture action failed: '.$err);
    }

    return $out;
};
try {
    $tool('create');
    $owned = true;
    $f = json_decode(file_get_contents($manifest), true, flags: JSON_THROW_ON_ERROR);
    $actor = User::where('public_id', $f['manager_id'])->firstOrFail();
    $payload = ['greenhouse_id' => $f['greenhouse_id'], 'name' => 'Independent investment race', 'base_date' => '2026-01-01', 'horizon_years' => 2, 'initial_outlay' => '100000000.00', 'initial_source' => ['kind' => 'actual', 'reference' => 'Synthetic', 'basis' => 'Initial once'], 'discount_rate' => '0.10000000', 'payback_convention' => 'interpolation', 'notes' => 'Not production', 'cash_flows' => array_map(fn ($year) => ['year' => $year, 'amount' => '60000000.00', 'source_kind' => 'projection', 'source_reference' => 'Synthetic', 'basis' => 'Annual net cash'], [1, 2])];
    $create = ['schema_version' => '2', 'sync_epoch' => DB::table('sync_state')->where('id', 1)->value('epoch'), 'operation_id' => (string) Str::uuid(), 'entity_id' => $f['scenario_id'], 'entity_type' => 'investment_scenario', 'action' => 'create', 'base_version' => null, 'payload' => $payload];
    app(InvestmentScenarioService::class)->mutate($actor, $create);
    $input = ['scenario_id' => $f['scenario_id'], 'review_note' => 'Independent investment report race'];
    $preview = DB::transaction(function () use ($actor, $input) {
        DB::table('sync_state')->where('id', 1)->sharedLock()->first();

        return app(InvestmentReportService::class)->preview($actor, $input);
    });
    $op = ['schema_version' => '2', 'sync_epoch' => DB::table('sync_state')->where('id', 1)->value('epoch'), 'operation_id' => (string) Str::uuid(), 'entity_id' => (string) Str::uuid(),
        'entity_type' => 'investment_report', 'action' => 'create', 'base_version' => null, 'payload' => [...$input, 'expected_scenario_version' => $preview['scenario_version'], 'expected_sources_hash' => $preview['sources_hash'], 'expected_formula_version' => $preview['formula_version']]];
    $second = $mode === 'duplicate' ? $op : [...$create, 'operation_id' => (string) Str::uuid(), 'action' => 'update', 'base_version' => 1, 'payload' => [...$payload, 'initial_outlay' => '150000000.00', 'reason' => 'Independent concurrent scenario correction']];
    foreach ([$op, $second] as $index => $operation) {
        file_put_contents("$dir/input-$index", json_encode(['actor_id' => $actor->id, 'operation' => $operation], JSON_THROW_ON_ERROR));
        $command = [PHP_BINARY, $root.'/backend/tests/Support/finance-race-worker.php', "$dir/input-$index", "$dir/ready-$index", "$dir/go", "$dir/result-$index"];
        $process = proc_open($command, [0 => ['pipe', 'r'], 1 => ['file', "$dir/stdout-$index", 'w'], 2 => ['file', "$dir/stderr-$index", 'w']], $pipes);
        if (! is_resource($process)) {
            throw new RuntimeException('Race child did not start');
        }
        fclose($pipes[0]);
        $children[] = $process;
    }
    $wait = function (array $files) {
        $deadline = microtime(true) + 30;
        do {
            clearstatcache();
            if (count(array_filter($files, 'is_file')) === count($files)) {
                return;
            }usleep(10000);
        } while (microtime(true) < $deadline);
        throw new RuntimeException('Independent race barrier timeout');
    };
    $wait(["$dir/ready-0", "$dir/ready-1"]);
    file_put_contents("$dir/go", 'go');
    $wait(["$dir/result-0", "$dir/result-1"]);
    $results = array_map(fn ($i) => json_decode(file_get_contents("$dir/result-$i"), true, flags: JSON_THROW_ON_ERROR), [0, 1]);
    if (count(array_unique(array_column($results, 'pid'))) !== 2 || count(array_unique(array_column($results, 'connection_id'))) !== 2) {
        throw new RuntimeException('Missing independent MySQL processes/connections');
    }
    $statuses = array_column($results, 'status');
    sort($statuses);
    if ($mode === 'duplicate' && $statuses !== ['accepted', 'accepted']) {
        throw new RuntimeException(json_encode($results));
    }
    if ($mode === 'source' && ! in_array($statuses, [['accepted', 'accepted'], ['accepted', 'report_sources_changed']], true)) {
        throw new RuntimeException(json_encode($results));
    }
    $rows = DB::table('report_snapshots')->where('id', $op['entity_id'])->get();
    $expected = $mode === 'duplicate' || $results[0]['status'] === 'accepted' ? 1 : 0;
    if ($rows->count() !== $expected) {
        throw new RuntimeException('Snapshot count inconsistent with accepted result');
    }
    foreach (['audit_logs', 'sync_operations', 'change_feed'] as $table) {
        if (DB::table($table)->where('entity_type', 'investment_report')->where('entity_id', $op['entity_id'])->count() !== $expected) {
            throw new RuntimeException('Duplicate or partial report ledger');
        }
    }
    if ($expected) {
        $row = $rows[0];
        $full = json_decode($row->content, true, flags: JSON_THROW_ON_ERROR);
        if (! hash_equals($row->snapshot_hash, CanonicalJson::hash($full)) || $full['metrics']['NPV']['value'] !== '4132231.40') {
            throw new RuntimeException('Report mixed source states or integrity mismatch');
        }
        if ($full['scenario_version'] !== 1 || count(array_unique(array_column($full['sources'], 'version'))) !== 1 || $full['sources'][0]['version'] !== 1) {
            throw new RuntimeException('Concurrent report mixed scenario/annual versions');
        }
    }
    echo 'PASS '.$mode.' independent PIDs/connections '.json_encode($results).' reports='.$expected."\n";
} finally {
    foreach ($children as $process) {
        if (proc_get_status($process)['running']) {
            proc_terminate($process);
        }proc_close($process);
    }
    if ($owned) {
        $tool('cleanup');
    }
    foreach (glob($dir.'/*') as $file) {
        unlink($file);
    }rmdir($dir);
}
