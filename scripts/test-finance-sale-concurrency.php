<?php

use App\Domain\Cultivation\MasterService;
use App\Domain\Harvest\HarvestService;
use App\Domain\Finance\SaleService;
use App\Domain\Identity\EntityIdentity;
use App\Models\User;
use App\Support\TestDatabaseGuard;
use Illuminate\Contracts\Console\Kernel;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

putenv('APP_ENV=testing');$_ENV['APP_ENV'] = $_SERVER['APP_ENV'] = 'testing';
require dirname(__DIR__).'/backend/vendor/autoload.php';$app = require dirname(__DIR__).'/backend/bootstrap/app.php';
TestDatabaseGuard::install($app, required: true);$app->make(Kernel::class)->bootstrap();
$epoch = DB::table('sync_state')->where('id', 1)->value('epoch');
$op = fn ($kind, $payload) => ['schema_version' => '2', 'sync_epoch' => $epoch, 'operation_id' => (string) Str::uuid(), 'entity_id' => (string) Str::uuid(), 'entity_type' => $kind, 'action' => 'create', 'base_version' => null, 'payload' => $payload];
$actor = DB::transaction(function () {
    DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();$id = (string) Str::uuid();app(EntityIdentity::class)->claim('users', $id);
    return User::create(['public_id' => $id, 'name' => 'Synthetic sale race', 'role' => 'manager', 'email' => Str::uuid().'@example.invalid', 'password' => Str::random(40)]);
});
$mode = $argv[1] ?? 'oversell';if (!in_array($mode, ['oversell', 'update', 'cancel'], true)) {throw new InvalidArgumentException('Use oversell, update or cancel.');}
$ids = [];$children = [];$files = [];$dir = dirname(__DIR__).'/tmp/finance-sale-race-'.Str::uuid();mkdir($dir, 0700, true);
try {
    $create = fn ($kind, $payload) => app(MasterService::class)->mutate($actor, $kind, $op($kind, $payload))['body']['entity_id'];
    $ids['gh'] = $create('greenhouses', ['name' => 'Synthetic sale GH']);
    $ids['variety'] = $create('varieties', ['greenhouse_id' => $ids['gh'], 'name' => 'Synthetic variety']);
    $ids['season'] = $create('seasons', ['greenhouse_id' => $ids['gh'], 'name' => 'Synthetic season', 'start_date' => '2026-10-01', 'end_date' => null]);
    $ids['group'] = $create('planting_groups', ['season_id' => $ids['season'], 'variety_id' => $ids['variety'], 'name' => 'Synthetic group', 'population' => 10, 'sowing_date' => '2026-10-01', 'transplant_date' => null]);
    $ids['grade'] = $create('grades', ['greenhouse_id' => $ids['gh'], 'name' => 'Synthetic grade']);$ids['source'] = (string) Str::uuid();
    $harvest = $op('harvest', ['planting_group_id' => $ids['group'], 'occurred_at' => '2026-10-30T08:00:00+07:00', 'notes' => 'Synthetic harvest', 'items' => [['id' => $ids['source'], 'grade_id' => $ids['grade'], 'fruit_count' => 3, 'weight_kg' => '5']]]);
    app(HarvestService::class)->mutate($actor, $harvest);$ids['harvest'] = $harvest['entity_id'];
    $initial = null;
    if ($mode !== 'oversell') {
        $ids['initial_line'] = (string) Str::uuid();
        $initial = $op('sale', ['season_id' => $ids['season'], 'paid_at' => '2026-10-30T09:00:00+07:00', 'payment_status' => 'paid', 'customer_name' => '', 'notes' => 'Synthetic initial sale', 'items' => [['id' => $ids['initial_line'], 'harvest_item_id' => $ids['source'], 'quantity_kg' => $mode === 'update' ? '1' : '3.01', 'price_per_kg' => '10000']]]);
        app(SaleService::class)->mutate($actor, $initial);$ids['initial_sale'] = $initial['entity_id'];
    }
    $go = $dir.'/go';$files[] = $go;
    foreach ([0, 1] as $i) {
        $input = $dir.'/input-'.$i.'.json';$ready = $dir.'/ready-'.$i;$result = $dir.'/result-'.$i.'.json';$error = $dir.'/error-'.$i.'.txt';array_push($files, $input, $ready, $result, $error);
        $ids['line'.$i] = (string) Str::uuid();$sale = $op('sale', ['season_id' => $ids['season'], 'paid_at' => '2026-10-30T09:00:00+07:00', 'payment_status' => 'paid', 'customer_name' => '', 'notes' => 'Synthetic competing paid sale', 'items' => [['id' => $ids['line'.$i], 'harvest_item_id' => $ids['source'], 'quantity_kg' => '3.01', 'price_per_kg' => '10000']]]);
        if ($mode === 'update') {
            $sale = [...$initial, 'operation_id' => (string) Str::uuid(), 'action' => 'update', 'base_version' => 1];
            $sale['payload']['reason'] = 'Synthetic competing correction';$sale['payload']['items'][0]['quantity_kg'] = (string) (2 + $i);
        } elseif ($mode === 'cancel' && $i === 0) {
            $sale = [...$initial, 'operation_id' => (string) Str::uuid(), 'action' => 'cancel', 'base_version' => 1, 'payload' => ['reason' => 'Synthetic racing cancellation']];
        }
        $ids['sale'.$i] = $sale['entity_id'];file_put_contents($input, json_encode(['actor_id' => $actor->id, 'operation' => $sale], JSON_THROW_ON_ERROR));
        $process = proc_open([PHP_BINARY, dirname(__DIR__).'/backend/tests/Support/finance-race-worker.php', $input, $ready, $go, $result], [0 => ['pipe', 'r'], 1 => ['file', $error, 'a'], 2 => ['file', $error, 'a']], $pipes, dirname(__DIR__).'/backend');
        if (!is_resource($process)) {throw new RuntimeException('Unable to start sale worker.');}fclose($pipes[0]);$children[] = $process;
    }
    $wait = function ($paths) { $deadline = microtime(true) + 20;do {clearstatcache();if (count(array_filter($paths, is_file(...))) === count($paths)) {return;}usleep(20000);} while (microtime(true) < $deadline);throw new RuntimeException('Sale barrier timed out.');};
    $wait([$dir.'/ready-0', $dir.'/ready-1']);file_put_contents($go, 'go');$wait([$dir.'/result-0.json', $dir.'/result-1.json']);
    $results = array_map(fn ($i) => json_decode(file_get_contents($dir.'/result-'.$i.'.json'), true, flags: JSON_THROW_ON_ERROR), [0, 1]);$statuses = array_column($results, 'status');sort($statuses);
    $source = DB::table('harvest_items')->where('id', $ids['source'])->first();
    if (count(array_unique(array_column($results, 'pid'))) !== 2 || count(array_unique(array_column($results, 'connection_id'))) !== 2) {throw new RuntimeException('Independent process/connection proof missing.');}
    $active = DB::table('sale_items')->join('sales', 'sales.id', '=', 'sale_items.sale_id')->where('sale_items.harvest_item_id', $source->id)->where('sales.record_state', 'active')->where('sale_items.active', true)->sum('sale_items.quantity_kg');
    if (bccomp((string) $active, $source->sold_weight_kg, 6) !== 0 || bccomp($source->sold_weight_kg, '5', 6) > 0) {throw new RuntimeException('Accepted sales and counter do not reconcile.');}
    $sales = DB::table('sales')->where('season_id', $ids['season']);
    if ($mode === 'oversell') {
        if ($statuses !== ['accepted', 'harvest_exceeded'] || $source->sold_weight_kg !== '3.010000' || $source->balance_version !== 2 || $sales->count() !== 1 || $sales->value('total_amount') !== '30100.00') {throw new RuntimeException('Oversell race failed: '.json_encode($results));}
        $expectedLedger = 1;$expectedBalances = 2;
    } elseif ($mode === 'update') {
        if ($statuses !== ['accepted', 'version_conflict'] || !in_array($source->sold_weight_kg, ['2.000000', '3.000000'], true) || $source->balance_version !== 3 || $sales->count() !== 1 || $sales->value('version') !== 2) {throw new RuntimeException('Sale update race failed: '.json_encode($results));}
        $expectedLedger = 2;$expectedBalances = 3;
    } else {
        if (!in_array($statuses, [['accepted', 'accepted'], ['accepted', 'harvest_exceeded']], true) || !in_array($source->sold_weight_kg, ['0.000000', '3.010000'], true) || DB::table('sales')->where('id', $initial['entity_id'])->value('record_state') !== 'cancelled') {throw new RuntimeException('Sale cancel/create race failed: '.json_encode($results));}
        $expectedLedger = count(array_filter($results, fn ($r) => $r['status'] === 'accepted')) + 1;$expectedBalances = $expectedLedger + 1;
        if ($source->balance_version !== $expectedBalances) {throw new RuntimeException('Cancellation balance version mismatch.');}
    }
    foreach (['sync_operations', 'audit_logs'] as $table) {if (DB::table($table)->where('actor_id', $actor->id)->where('entity_type', 'sale')->count() !== $expectedLedger) {throw new RuntimeException('Sale ledger mismatch.');}}
    if (DB::table('change_feed')->where('greenhouse_id', $ids['gh'])->where('entity_type', 'sale')->count() !== $expectedLedger || DB::table('change_feed')->where('entity_id', $source->id)->where('entity_type', 'harvest_balance')->count() !== $expectedBalances) {throw new RuntimeException('Sale feed mismatch.');}
    // Synthetic public DTO only, for independent schema validation; no token/password/private path.
    $sale = DB::table('sales')->where('season_id', $ids['season'])->first();
    file_put_contents(dirname(__DIR__).'/tmp/finance-sale-contract-output.json', json_encode(app(\App\Domain\Finance\SaleService::class)->publicRow($sale), JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR));
    file_put_contents(dirname(__DIR__).'/tmp/finance-sale-contract-'.$mode.'.json', json_encode(app(\App\Domain\Finance\SaleService::class)->publicRow($sale), JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR));
    echo 'PASS: sale '.$mode.' race, independent PHP/MySQL processes; statuses='.implode(',', $statuses).'; active quantity=counter'.$source->sold_weight_kg.' <=5kg, balance version'.$source->balance_version.', ledger/feed'.$expectedLedger.'. MySQL inofarm_test only.'.PHP_EOL;
} finally {
    foreach ($children as $process) {if (proc_get_status($process)['running']) {proc_terminate($process);}proc_close($process);}
    DB::transaction(function () use ($ids, $actor) {
        $ownedSales = DB::table('sales')->where('season_id', $ids['season'] ?? '')->where('recorded_by', $actor->id)->pluck('id')->all();
        $registry = [...DB::table('sync_operations')->where('actor_id', $actor->id)->pluck('entity_id')->all(), $actor->public_id,
            ...DB::table('harvest_items')->where('harvest_id', $ids['harvest'] ?? '')->pluck('id')->all(),
            ...DB::table('sale_items')->whereIn('sale_id', $ownedSales)->pluck('id')->all(),
            ...DB::table('greenhouse_assignments')->where('user_id', $actor->id)->pluck('id')->all()];
        DB::table('sale_items')->whereIn('sale_id', $ownedSales)->delete();DB::table('sales')->whereIn('id', $ownedSales)->delete();
        DB::table('harvest_reference_snapshots')->where('harvest_id', $ids['harvest'] ?? '')->delete();DB::table('harvest_items')->where('harvest_id', $ids['harvest'] ?? '')->delete();DB::table('harvests')->where('id', $ids['harvest'] ?? '')->delete();
        DB::table('entity_identities')->whereIn('entity_id', $registry)->delete();
        foreach (['audit_logs', 'sync_operations'] as $table) {DB::table($table)->where('actor_id', $actor->id)->delete();}DB::table('change_feed')->where('greenhouse_id', $ids['gh'] ?? '')->delete();
        DB::table('planting_groups')->where('id', $ids['group'] ?? '')->delete();DB::table('grades')->where('id', $ids['grade'] ?? '')->delete();DB::table('varieties')->where('id', $ids['variety'] ?? '')->delete();DB::table('seasons')->where('id', $ids['season'] ?? '')->delete();
        DB::table('greenhouse_assignments')->where('user_id', $actor->id)->delete();DB::table('greenhouses')->where('id', $ids['gh'] ?? '')->where('created_by', $actor->id)->delete();$actor->delete();
    });
    foreach ($files as $file) {if (is_file($file)) {unlink($file);}}rmdir($dir);
}
