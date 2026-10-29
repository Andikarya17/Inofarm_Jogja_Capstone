<?php

use App\Domain\Cultivation\MasterService;
use App\Models\User;
use App\Support\TestDatabaseGuard;
use Illuminate\Contracts\Console\Kernel;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

// Synthetic device integration only. Guard is installed before providers/SQL.
putenv('APP_ENV=testing');
$_ENV['APP_ENV'] = $_SERVER['APP_ENV'] = 'testing';
require dirname(__DIR__).'/backend/vendor/autoload.php';
$app = require dirname(__DIR__).'/backend/bootstrap/app.php';
TestDatabaseGuard::install($app, required: true);
$app->make(Kernel::class)->bootstrap();
$path = dirname(__DIR__).'/tmp/finance-device-fixture.json';
$mode = $argv[1] ?? 'create';
if ($mode === 'create') {
    if (is_file($path)) {
        throw new RuntimeException('Synthetic fixture manifest exists; verify/cleanup that fixture first.');
    }
    $fixture = DB::transaction(function () {
        $id = (string) Str::uuid();
        $password = 'device-fixture-only-password';
        $manager = User::create(['public_id' => (string) Str::uuid(), 'name' => 'Pengelola perangkat sintetis', 'email' => 'device-manager-'.$id.'@example.invalid', 'role' => 'manager', 'password' => $password]);
        $worker = User::create(['public_id' => (string) Str::uuid(), 'name' => 'Pekerja perangkat sintetis', 'email' => 'device-worker-'.$id.'@example.invalid', 'role' => 'worker', 'password' => $password]);
        $epoch = DB::table('sync_state')->where('id', 1)->value('epoch');
        $create = function (string $type, array $payload) use ($manager, $epoch) {
            return app(MasterService::class)->mutate($manager, $type, ['schema_version' => '2', 'sync_epoch' => $epoch, 'operation_id' => (string) Str::uuid(),
                'entity_id' => (string) Str::uuid(), 'entity_type' => $type, 'action' => 'create', 'base_version' => null, 'payload' => $payload])['body']['data'];
        };
        $contexts = [];
        foreach (['A', 'B'] as $name) {
            $gh = $create('greenhouses', ['name' => 'GH '.$name.' perangkat sintetis']);
            $variety = $create('varieties', ['greenhouse_id' => $gh['id'], 'name' => 'Varietas perangkat sintetis']);
            $season = $create('seasons', ['greenhouse_id' => $gh['id'], 'name' => 'Musim '.$name.' sintetis', 'start_date' => '2026-10-01', 'end_date' => null]);
            $group = $create('planting_groups', ['season_id' => $season['id'], 'variety_id' => $variety['id'], 'name' => 'Kelompok '.$name.' sintetis', 'population' => 10, 'sowing_date' => '2026-10-01', 'transplant_date' => null]);
            $contexts[] = ['greenhouse_id' => $gh['id'], 'season_id' => $season['id'], 'group_id' => $group['id'], 'variety_id' => $variety['id']];
        }
        $create('greenhouse_assignments', ['greenhouse_id' => $contexts[0]['greenhouse_id'], 'user_id' => $worker->public_id, 'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);

        return ['database' => 'inofarm_test', 'fixture_id' => $id, 'base_url' => 'http://10.0.2.2:8001/api/v2/',
            'manager_id' => $manager->public_id, 'manager_email' => $manager->email, 'worker_id' => $worker->public_id, 'worker_email' => $worker->email,
            'password' => $password, 'contexts' => $contexts];
    });
    file_put_contents($path, json_encode($fixture, JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR));
    echo 'Synthetic two-role/two-GH fixture created only in guarded inofarm_test; manifest in ignored tmp.'.PHP_EOL;
    exit(0);
}
$fixture = json_decode(file_get_contents($path), true, flags: JSON_THROW_ON_ERROR);
if ($fixture['database'] !== 'inofarm_test' || ! Str::isUuid($fixture['fixture_id'])) {
    throw new RuntimeException('Invalid synthetic provenance.');
}
$actors = User::whereIn('public_id', [$fixture['manager_id'], $fixture['worker_id']])->get();
foreach ($actors as $actor) {
    if (! str_ends_with($actor->email, $fixture['fixture_id'].'@example.invalid')) {
        throw new RuntimeException('Refusing unrelated user rows.');
    }
}
$groups = array_column($fixture['contexts'], 'group_id');
$greenhouses = array_column($fixture['contexts'], 'greenhouse_id');
if ($mode === 'verify') {
    $activities = DB::table('activities')->whereIn('planting_group_id', $groups)->limit(10)->get();
    if ($activities->count() !== 1 || $activities[0]->notes !== 'DEVICE OFFLINE '.$fixture['fixture_id']) {
        throw new RuntimeException('Expected exactly one accepted synthetic activity.');
    }
    $id = $activities[0]->id;
    foreach (['audit_logs', 'change_feed', 'sync_operations'] as $table) {
        if (DB::table($table)->where('entity_id', $id)->count() !== 1) {
            throw new RuntimeException('Expected one create ledger row in '.$table);
        }
    }
    if (DB::table('activity_reference_snapshots')->where('activity_id', $id)->count() !== 1) {
        throw new RuntimeException('Snapshot count mismatch.');
    }
    echo 'PASS: MySQL inofarm_test has exactly one device activity, version1, snapshot/audit/feed/receipt=1 each after HTTP replay.'.PHP_EOL;
    exit(0);
}
if ($mode !== 'cleanup') {
    throw new RuntimeException('Use create, verify or cleanup.');
}
DB::transaction(function () use ($fixture, $actors, $groups, $greenhouses) {
    $activities = DB::table('activities')->whereIn('planting_group_id', $groups)->select('id');
    foreach (['activity_materials', 'activity_reference_snapshots'] as $table) {
        DB::table($table)->whereIn('activity_id', $activities)->delete();
    }
    DB::table('activities')->whereIn('planting_group_id', $groups)->delete();
    DB::table('audit_logs')->whereIn('actor_id', $actors->pluck('id'))->delete();
    DB::table('sync_operations')->whereIn('actor_id', $actors->pluck('id'))->delete();
    DB::table('change_feed')->whereIn('greenhouse_id', $greenhouses)->delete();
    DB::table('planting_groups')->whereIn('id', $groups)->delete();
    DB::table('seasons')->whereIn('id', array_column($fixture['contexts'], 'season_id'))->delete();
    DB::table('varieties')->whereIn('id', array_column($fixture['contexts'], 'variety_id'))->delete();
    DB::table('greenhouse_assignments')->whereIn('greenhouse_id', $greenhouses)->delete();
    DB::table('greenhouses')->whereIn('id', $greenhouses)->delete();
    foreach ($actors as $actor) {
        $actor->tokens()->delete();
        $actor->delete();
    }
});
unlink($path);
echo 'Only this synthetic fixture UUID/actor rows and ignored manifest cleaned; no reset or user database operation.'.PHP_EOL;
