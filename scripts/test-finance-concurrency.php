<?php

use App\Domain\Activities\ActivityService;
use App\Domain\Cultivation\MasterService;
use App\Domain\Harvest\HarvestService;
use App\Models\User;
use App\Support\TestDatabaseGuard;
use Illuminate\Contracts\Console\Kernel;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

/** Independent-process MySQL optimistic-version race. Cleans only its synthetic UUID/actor rows. */
putenv('APP_ENV=testing');
$_ENV['APP_ENV'] = $_SERVER['APP_ENV'] = 'testing';
require dirname(__DIR__).'/backend/vendor/autoload.php';
$app = require dirname(__DIR__).'/backend/bootstrap/app.php';
TestDatabaseGuard::install($app, required: true);
$app->make(Kernel::class)->bootstrap();

$kind = $argv[1] ?? 'activity';
if (! in_array($kind, ['activity', 'harvest'], true)) {
    throw new InvalidArgumentException('Use activity or harvest.');
}
$entityTable = $kind === 'harvest' ? 'harvests' : 'activities';
$service = $kind === 'harvest' ? HarvestService::class : ActivityService::class;
$epoch = DB::table('sync_state')->where('id', 1)->value('epoch');
$op = fn (string $type, array $p, string $action = 'create', ?string $id = null, ?int $v = null) => [
    'schema_version' => '2', 'sync_epoch' => $epoch, 'operation_id' => (string) Str::uuid(), 'entity_id' => $id ?? (string) Str::uuid(),
    'entity_type' => $type, 'action' => $action, 'base_version' => $v, 'payload' => $p];
$actor = User::create(['public_id' => (string) Str::uuid(), 'name' => 'Synthetic race', 'role' => 'manager',
    'email' => Str::uuid().'@example.invalid', 'password' => Str::random(40)]);
$ids = [];
$processes = [];
$files = [];
$dir = dirname(__DIR__).'/tmp/finance-race-'.Str::uuid();
mkdir($dir, 0700, true);
try {
    $create = function (string $type, array $p) use ($actor, $op, &$ids) {
        $result = app(MasterService::class)->mutate($actor, $type, $op($type, $p))['body']['data'];
        $ids[$type] = $result['id'];

        return $result['id'];
    };
    $gh = $create('greenhouses', ['name' => 'Synthetic race GH']);
    $variety = $create('varieties', ['greenhouse_id' => $gh, 'name' => 'Synthetic variety']);
    $season = $create('seasons', ['greenhouse_id' => $gh, 'name' => 'Synthetic season', 'start_date' => '2026-10-01', 'end_date' => null]);
    $group = $create('planting_groups', ['season_id' => $season, 'variety_id' => $variety, 'name' => 'Synthetic group', 'population' => 10, 'sowing_date' => '2026-10-01', 'transplant_date' => null]);
    if ($kind === 'harvest') {
        $grade = $create('grades', ['greenhouse_id' => $gh, 'name' => 'Synthetic race grade']);
        $payload = ['planting_group_id' => $group, 'occurred_at' => '2026-10-29T08:00:00+07:00',
            'items' => [['id' => (string) Str::uuid(), 'grade_id' => $grade, 'fruit_count' => 1, 'weight_kg' => '2']], 'notes' => 'Synthetic harvest race'];
    } else {
        $payload = ['planting_group_id' => $group, 'activity_type' => 'general', 'occurred_at' => '2026-10-29T08:00:00+07:00', 'work_state' => 'completed', 'materials' => [], 'notes' => 'Synthetic race'];
    }
    $entity = app($service)->mutate($actor, $op($kind, $payload))['body']['entity_id'];
    $ids[$entityTable] = $entity;
    $go = $dir.'/go';
    $files[] = $go;
    for ($i = 0; $i < 2; $i++) {
        $input = $dir.'/input-'.$i.'.json';
        $ready = $dir.'/ready-'.$i;
        $result = $dir.'/result-'.$i.'.json';
        $stderr = $dir.'/error-'.$i.'.txt';
        array_push($files, $input, $ready, $result, $stderr);
        file_put_contents($input, json_encode(['actor_id' => $actor->id, 'operation' => $op($kind, [...$payload, 'notes' => 'Concurrent '.$i, 'reference_policy' => 'preserve', 'reason' => 'Synthetic race'], 'update', $entity, 1)], JSON_THROW_ON_ERROR));
        $process = proc_open([PHP_BINARY, dirname(__DIR__).'/backend/tests/Support/finance-race-worker.php', $input, $ready, $go, $result],
            [0 => ['pipe', 'r'], 1 => ['file', $stderr, 'a'], 2 => ['file', $stderr, 'a']], $pipes, dirname(__DIR__).'/backend');
        if (! is_resource($process)) {
            throw new RuntimeException('Unable to start synthetic child process.');
        }
        fclose($pipes[0]);
        $processes[] = $process;
    }
    $wait = function (array $paths): void {
        $until = microtime(true) + 20;
        do {
            clearstatcache();
            if (count(array_filter($paths, is_file(...))) === count($paths)) {
                return;
            }
            usleep(20000);
        } while (microtime(true) < $until);
        throw new RuntimeException('Synthetic concurrency barrier timed out.');
    };
    $wait([$dir.'/ready-0', $dir.'/ready-1']);
    file_put_contents($go, 'go');
    $wait([$dir.'/result-0.json', $dir.'/result-1.json']);
    $results = [json_decode(file_get_contents($dir.'/result-0.json'), true), json_decode(file_get_contents($dir.'/result-1.json'), true)];
    $statuses = array_column($results, 'status');
    sort($statuses);
    if (count(array_unique(array_column($results, 'pid'))) !== 2 || count(array_unique(array_column($results, 'connection_id'))) !== 2
        || $statuses !== ['accepted', 'version_conflict'] || DB::table($entityTable)->where('id', $entity)->value('version') !== 2) {
        throw new RuntimeException('Unexpected concurrency result: '.json_encode($results));
    }
    foreach (['audit_logs', 'change_feed', 'sync_operations'] as $table) {
        if (DB::table($table)->where('entity_id', $entity)->count() !== 2) {
            throw new RuntimeException('Ledger components did not match accepted versions.');
        }
    }
    if ($kind === 'harvest' && (DB::table('harvest_reference_snapshots')->where('harvest_id', $entity)->count() !== 2
        || DB::table('harvest_items')->where('harvest_id', $entity)->count() !== 1
        || DB::table('harvest_items')->where('harvest_id', $entity)->value('balance_version') !== 2)) {
        throw new RuntimeException('Concurrent harvest snapshots/items/balance versions did not reconcile.');
    }
    $db = DB::selectOne('SELECT DATABASE() AS db_name, VERSION() AS version');
    echo 'PASS: '.$kind.'; two independent PHP/MySQL connections, simultaneous base_version=1; one accepted, one conflict; version=2, atomic ledger rows=2. DB='.$db->db_name.' MySQL='.$db->version.PHP_EOL;
} finally {
    foreach ($processes as $process) {
        if (proc_get_status($process)['running']) {
            proc_terminate($process);
        }
        proc_close($process);
    }
    DB::transaction(function () use ($ids, $actor) {
        foreach (['activity_materials', 'activity_reference_snapshots'] as $table) {
            DB::table($table)->where('activity_id', $ids['activities'] ?? '')->delete();
        }
        if (isset($ids['activities'])) {
            DB::table('activities')->where('id', $ids['activities'])->delete();
        }
        if (isset($ids['harvests'])) {
            DB::table('harvest_reference_snapshots')->where('harvest_id', $ids['harvests'])->delete();
            DB::table('harvest_items')->where('harvest_id', $ids['harvests'])->delete();
            DB::table('harvests')->where('id', $ids['harvests'])->delete();
        }
        DB::table('audit_logs')->where('actor_id', $actor->id)->delete();
        DB::table('sync_operations')->where('actor_id', $actor->id)->delete();
        DB::table('change_feed')->where('greenhouse_id', $ids['greenhouses'] ?? '')->delete();
        foreach (['planting_groups', 'seasons', 'varieties', 'grades'] as $table) {
            if (isset($ids[$table])) {
                DB::table($table)->where('id', $ids[$table])->delete();
            }
        }
        DB::table('greenhouse_assignments')->where('user_id', $actor->id)->delete();
        if (isset($ids['greenhouses'])) {
            DB::table('greenhouses')->where('id', $ids['greenhouses'])->delete();
        }
        $actor->delete();
    });
    foreach ($files as $file) {
        if (is_file($file)) {
            unlink($file);
        }
    }
    rmdir($dir);
}
