<?php

use App\Domain\Cultivation\MasterService;
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
$epoch = DB::table('sync_state')->where('id', 1)->value('epoch');
$operation = fn (string $kind, array $payload) => ['schema_version' => '2', 'sync_epoch' => $epoch, 'operation_id' => (string) Str::uuid(),
    'entity_id' => (string) Str::uuid(), 'entity_type' => $kind, 'action' => 'create', 'base_version' => null, 'payload' => $payload];
$actor = DB::transaction(function () {
    DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();$id = (string) Str::uuid();app(EntityIdentity::class)->claim('users', $id);

    return User::create(['public_id' => $id, 'name' => 'Synthetic attachment race', 'role' => 'manager', 'email' => Str::uuid().'@example.invalid', 'password' => Str::random(40)]);
});
$ids = [];$children = [];$files = [];
$dir = dirname(__DIR__).'/tmp/finance-attachment-race-'.Str::uuid();mkdir($dir, 0700, true);
$private = $dir.'/private';
$png = base64_decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j0ioAAAAASUVORK5CYII=', true);
$sha = hash('sha256', $png);
try {
    $create = fn (string $kind, array $p) => app(MasterService::class)->mutate($actor, $kind, $operation($kind, $p))['body']['entity_id'];
    $ids['gh'] = $create('greenhouses', ['name' => 'Synthetic private file GH']);
    $ids['category'] = $create('expense_categories', ['greenhouse_id' => $ids['gh'], 'name' => 'Synthetic category']);
    $expense = $operation('expense', ['greenhouse_id' => $ids['gh'], 'category_id' => $ids['category'], 'amount' => '100.00', 'paid_at' => '2026-10-30T08:00:00+07:00', 'payment_kind' => 'operating', 'notes' => 'Synthetic file source']);
    app(ExpenseService::class)->mutate($actor, $expense);$ids['expense'] = $expense['entity_id'];
    $source = $dir.'/source.png';file_put_contents($source, $png);$files[] = $source;
    $go = $dir.'/go';$files[] = $go;
    foreach ([0, 1] as $index) {
        $input = $dir.'/input-'.$index.'.json';$ready = $dir.'/ready-'.$index;$result = $dir.'/result-'.$index.'.json';$stderr = $dir.'/error-'.$index.'.txt';
        array_push($files, $input, $ready, $result, $stderr);
        $op = $operation('expense_attachment', ['owner_type' => 'expense', 'owner_id' => $ids['expense'], 'owner_version' => 1, 'original_name' => 'Synthetic-'.$index.'.png', 'mime' => 'image/png', 'bytes' => strlen($png), 'sha256' => $sha]);
        file_put_contents($input, json_encode(['actor_id' => $actor->id, 'operation' => $op, 'attachment_fixture' => ['source' => $source, 'private_root' => $private]], JSON_THROW_ON_ERROR));
        $process = proc_open([PHP_BINARY, dirname(__DIR__).'/backend/tests/Support/finance-race-worker.php', $input, $ready, $go, $result],
            [0 => ['pipe', 'r'], 1 => ['file', $stderr, 'a'], 2 => ['file', $stderr, 'a']], $pipes, dirname(__DIR__).'/backend');
        if (! is_resource($process)) {throw new RuntimeException('Could not launch independent evidence process.');}
        fclose($pipes[0]);$children[] = $process;
    }
    $wait = function (array $paths): void {
        $deadline = microtime(true) + 20;
        do {clearstatcache();if (count(array_filter($paths, is_file(...))) === count($paths)) {return;}usleep(20000);} while (microtime(true) < $deadline);
        throw new RuntimeException('Evidence race barrier timed out.');
    };
    $wait([$dir.'/ready-0', $dir.'/ready-1']);file_put_contents($go, 'go');$wait([$dir.'/result-0.json', $dir.'/result-1.json']);
    $results = array_map(fn ($i) => json_decode(file_get_contents($dir.'/result-'.$i.'.json'), true, flags: JSON_THROW_ON_ERROR), [0, 1]);
    $statuses = array_column($results, 'status');sort($statuses);
    $rows = DB::table('record_attachments')->where('owner_type', 'expense')->where('owner_id', $ids['expense'])->get();
    $sourceRow = DB::table('expenses')->where('id', $ids['expense'])->first();
    if ($statuses !== ['accepted', 'attachment_limit_reached'] || count(array_unique(array_column($results, 'pid'))) !== 2 || count(array_unique(array_column($results, 'connection_id'))) !== 2
        || $rows->count() !== 1 || $rows[0]->sha256 !== $sha || $rows[0]->version !== 1 || $sourceRow->allocated_amount !== '0.00' || $sourceRow->version !== 1 || $sourceRow->balance_version !== 1) {
        throw new RuntimeException('Evidence count/identity/payment isolation did not reconcile: '.json_encode($results));
    }
    foreach (['audit_logs', 'sync_operations', 'change_feed'] as $table) {
        if (DB::table($table)->where('entity_id', $rows[0]->id)->count() !== 1) {throw new RuntimeException('Evidence ledger count did not reconcile.');}
    }
    $blob = $private.'/blobs/'.substr($sha, 0, 2).'/'.$sha;
    if (! is_file($blob) || hash_file('sha256', $blob) !== $sha || filesize($blob) !== strlen($png)) {throw new RuntimeException('Private blob did not match accepted checksum.');}
    echo 'PASS: two independent PHP/MySQL processes compete for one fixture attachment slot; one accepted/one attachment_limit_reached, one metadata/registry/audit/receipt/feed and verified private blob; payment/balance versions and allocation amount unchanged. MySQL inofarm_test only.'.PHP_EOL;
} finally {
    foreach ($children as $process) {if (proc_get_status($process)['running']) {proc_terminate($process);}proc_close($process);}
    DB::transaction(function () use ($ids, $actor) {
        $registry = [...array_values($ids), $actor->public_id, ...DB::table('greenhouse_assignments')->where('user_id', $actor->id)->pluck('id')->all(),
            ...DB::table('expenses')->where('id', $ids['expense'] ?? '')->pluck('balance_id')->all(), ...DB::table('record_attachments')->where('owner_id', $ids['expense'] ?? '')->where('recorded_by', $actor->id)->pluck('id')->all()];
        DB::table('record_attachments')->where('owner_id', $ids['expense'] ?? '')->where('recorded_by', $actor->id)->delete();
        DB::table('entity_identities')->whereIn('entity_id', array_unique($registry))->delete();
        DB::table('expenses')->where('id', $ids['expense'] ?? '')->where('recorded_by', $actor->id)->delete();
        DB::table('audit_logs')->where('actor_id', $actor->id)->delete();DB::table('sync_operations')->where('actor_id', $actor->id)->delete();
        DB::table('change_feed')->where('greenhouse_id', $ids['gh'] ?? '')->delete();DB::table('expense_categories')->where('id', $ids['category'] ?? '')->delete();
        DB::table('greenhouse_assignments')->where('user_id', $actor->id)->delete();DB::table('greenhouses')->where('id', $ids['gh'] ?? '')->where('created_by', $actor->id)->delete();$actor->delete();
    });
    // Known files in this newly generated workspace/tmp fixture only; no recursive deletion.
    $blob = $private.'/blobs/'.substr($sha, 0, 2).'/'.$sha;
    if (is_file($blob)) {unlink($blob);}
    foreach ([$private.'/blobs/'.substr($sha, 0, 2), $private.'/blobs', $private.'/staging', $private] as $folder) {if (is_dir($folder) && ! rmdir($folder)) {throw new RuntimeException('Unexpected fixture files retained for inspection.');}}
    foreach ($files as $file) {if (is_file($file)) {unlink($file);}}
    rmdir($dir);
}
