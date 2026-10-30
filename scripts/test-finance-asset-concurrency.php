<?php

use App\Domain\Cultivation\MasterService;
use App\Domain\Finance\AssetService;
use App\Domain\Finance\ExpenseService;
use App\Domain\Identity\EntityIdentity;
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
$mode = $argv[1] ?? 'allocation';
if (! in_array($mode, ['payment', 'allocation', 'update', 'cancel'], true)) {
    throw new InvalidArgumentException('Unknown race mode');
}
$actor = DB::transaction(function () {
    DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
    $id = (string) Str::uuid();
    app(EntityIdentity::class)->claim('users', $id);

    return User::create(['public_id' => $id, 'name' => 'Synthetic asset race', 'role' => 'manager', 'email' => Str::uuid().'@example.invalid', 'password' => Str::random(40)]);
});
$epoch = DB::table('sync_state')->where('id', 1)->value('epoch');
$op = fn ($kind, $payload) => ['schema_version' => '2', 'sync_epoch' => $epoch, 'operation_id' => (string) Str::uuid(), 'entity_id' => (string) Str::uuid(), 'entity_type' => $kind, 'action' => 'create', 'base_version' => null, 'payload' => $payload];
$dir = dirname(__DIR__).'/tmp/asset-race-'.Str::uuid();
mkdir($dir, 0700, true);
$children = [];
$gh = null;
try {
    $master = fn ($kind, $p) => app(MasterService::class)->mutate($actor, $kind, $op($kind, $p))['body']['entity_id'];
    $gh = $master('greenhouses', ['name' => 'Synthetic asset GH']);
    $season = $master('seasons', ['greenhouse_id' => $gh, 'name' => 'Synthetic season', 'start_date' => '2026-01-01', 'end_date' => null]);
    $category = $master('expense_categories', ['greenhouse_id' => $gh, 'name' => 'Synthetic category']);
    $payment = $op('expense', ['greenhouse_id' => $gh, 'category_id' => $category, 'amount' => '100', 'paid_at' => '2026-01-01T08:00:00+07:00', 'payment_kind' => 'asset_acquisition', 'notes' => 'Synthetic']);
    app(ExpenseService::class)->mutate($actor, $payment);
    $asset = $op('asset', ['expense_id' => $payment['entity_id'], 'name' => 'Synthetic asset', 'cost' => '100', 'residual' => '0', 'life_years' => 3, 'in_service_date' => '2026-01-01', 'usage_basis' => 'Synthetic explicit basis']);
    if ($mode !== 'payment') {
        app(AssetService::class)->mutate($actor, $asset);
    }
    $allocation = fn ($i) => $op('depreciation_allocation', ['asset_id' => $asset['entity_id'], 'season_id' => $season, 'usage_reference' => (string) Str::uuid(), 'usage_year' => 1, 'period_start' => $i ? '2026-06-01' : '2026-01-01', 'period_end' => $i ? '2027-01-01' : '2026-06-01', 'amount' => '20', 'allocation_basis' => 'Synthetic manual usage']);
    $initial = null;
    if ($mode === 'update') {
        $initial = $allocation(0);
        app(AssetService::class)->mutate($actor, $initial);
    }
    foreach ([0, 1] as $i) {
        $operation = $allocation($i);
        if ($mode === 'payment') {
            $operation = $op('asset', [...$asset['payload'], 'cost' => '60']);
        }
        if ($mode === 'update') {
            $operation = [...$initial, 'operation_id' => (string) Str::uuid(), 'action' => 'update', 'base_version' => 1, 'payload' => [...$initial['payload'], 'amount' => $i ? '21' : '22', 'reason' => 'Synthetic correction']];
        }
        if ($mode === 'cancel' && $i === 0) {
            $operation = [...$asset, 'operation_id' => (string) Str::uuid(), 'action' => 'cancel', 'base_version' => 1, 'payload' => ['reason' => 'Synthetic cancellation']];
        }
        file_put_contents("$dir/input-$i", json_encode(['actor_id' => $actor->id, 'operation' => $operation], JSON_THROW_ON_ERROR));
        $process = proc_open([PHP_BINARY, dirname(__DIR__).'/backend/tests/Support/finance-race-worker.php', "$dir/input-$i", "$dir/ready-$i", "$dir/go", "$dir/result-$i"], [0 => ['pipe', 'r'], 1 => ['file', "$dir/log-$i", 'a'], 2 => ['file', "$dir/log-$i", 'a']], $pipes, dirname(__DIR__).'/backend');
        if (! is_resource($process)) {
            throw new RuntimeException('Worker start failed');
        } fclose($pipes[0]);
        $children[] = $process;
    }
    $wait = function ($paths) {
        $end = microtime(true) + 30;
        do {
            clearstatcache();
            if (count(array_filter($paths, is_file(...))) === count($paths)) {
                return;
            } usleep(20000);
        } while (microtime(true) < $end);
        throw new RuntimeException('Barrier timeout');
    };
    $wait(["$dir/ready-0", "$dir/ready-1"]);
    file_put_contents("$dir/go", 'go');
    $wait(["$dir/result-0", "$dir/result-1"]);
    $results = array_map(fn ($i) => json_decode(file_get_contents("$dir/result-$i"), true, flags: JSON_THROW_ON_ERROR), [0, 1]);
    if (count(array_unique(array_column($results, 'pid'))) !== 2 || count(array_unique(array_column($results, 'connection_id'))) !== 2) {
        throw new RuntimeException('Independent connections missing');
    }
    $statuses = array_column($results, 'status');
    sort($statuses);
    $expected = match ($mode) {
        'payment' => ['accepted', 'asset_exceeds_payment'], 'allocation' => ['accepted', 'depreciation_exceeds_budget'], 'update' => ['accepted', 'version_conflict'], 'cancel' => null
    };
    if ($expected && $statuses !== $expected) {
        throw new RuntimeException(json_encode($results));
    }
    if ($mode === 'cancel' && ! in_array($statuses, [['accepted', 'asset_in_use'], ['accepted', 'http_422']], true)) {
        throw new RuntimeException(json_encode($results));
    }
    $cost = (string) DB::table('assets')->where('expense_id', $payment['entity_id'])->where('record_state', 'active')->sum('cost');
    $allocated = (string) DB::table('depreciation_allocations')->where('greenhouse_id', $gh)->where('record_state', 'active')->sum('amount');
    if (bccomp($cost, '100', 2) > 0 || bccomp($allocated, '33.33', 2) > 0 || ($mode === 'cancel' && bccomp($cost, '0', 2) === 0 && bccomp($allocated, '0', 2) > 0)) {
        throw new RuntimeException('Financial cap violated');
    }
    $ledger = DB::table('sync_operations')->where('actor_id', $actor->id)->whereIn('entity_type', ['asset', 'depreciation_allocation'])->count();
    foreach (['audit_logs', 'change_feed'] as $table) {
        if (DB::table($table)->whereIn('entity_type', ['asset', 'depreciation_allocation'])->where($table === 'change_feed' ? 'greenhouse_id' : 'actor_id', $table === 'change_feed' ? $gh : $actor->id)->count() !== $ledger) {
            throw new RuntimeException('Ledger atomicity mismatch');
        }
    }
    echo 'PASS '.$mode.' independent PIDs/connections '.json_encode($results)." active cost=$cost allocation=$allocated ledger=$ledger\n";
} finally {
    foreach ($children as $process) {
        if (proc_get_status($process)['running']) {
            proc_terminate($process);
        } proc_close($process);
    }
    DB::transaction(function () use ($actor, $gh) {
        DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
        $ids = DB::table('sync_operations')->where('actor_id', $actor->id)->pluck('entity_id')->all();
        $ids = [...$ids, $actor->public_id, ...DB::table('greenhouse_assignments')->where('user_id', $actor->id)->pluck('id')->all(), ...DB::table('expenses')->where('recorded_by', $actor->id)->pluck('balance_id')->all()];
        foreach (['depreciation_allocations', 'assets', 'expenses'] as $table) {
            DB::table($table)->where('recorded_by', $actor->id)->delete();
        }
        foreach (['audit_logs', 'sync_operations'] as $table) {
            DB::table($table)->where('actor_id', $actor->id)->delete();
        }
        DB::table('change_feed')->where('greenhouse_id', $gh)->delete();
        DB::table('seasons')->where('greenhouse_id', $gh)->delete();
        DB::table('expense_categories')->where('greenhouse_id', $gh)->delete();
        DB::table('greenhouse_assignments')->where('user_id', $actor->id)->delete();
        DB::table('greenhouses')->where('id', $gh)->where('created_by', $actor->id)->delete();
        DB::table('entity_identities')->whereIn('entity_id', $ids)->delete();
        $actor->delete();
    });
    foreach (glob($dir.'/*') as $file) {
        unlink($file);
    } rmdir($dir);
}
