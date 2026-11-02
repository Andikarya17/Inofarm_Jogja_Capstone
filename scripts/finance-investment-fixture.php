<?php

use App\Domain\Contracts\CanonicalJson;
use App\Domain\Cultivation\MasterService;
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
$path = dirname(__DIR__).'/tmp/finance-investment-fixture.json';
$mode = $argv[1] ?? 'create';
if ($mode === 'create') {
    if (is_file($path)) {
        throw new RuntimeException('Existing investment fixture; inspect ownership first.');
    }
    $f = DB::transaction(function () {
        DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
        $fixture = (string) Str::uuid();
        $password = Str::random(40);
        $users = [];
        foreach (['manager', 'worker'] as $role) {
            $id = (string) Str::uuid();
            app(EntityIdentity::class)->claim('users', $id);
            $users[$role] = User::create(['public_id' => $id, 'name' => 'Investasi perangkat sintetis '.$role, 'email' => 'investment-'.$role.'-'.$fixture.'@example.invalid', 'password' => $password, 'role' => $role]);
        }
        $operation = fn ($kind, $p) => ['schema_version' => '2', 'sync_epoch' => DB::table('sync_state')->where('id', 1)->value('epoch'), 'operation_id' => (string) Str::uuid(), 'entity_id' => (string) Str::uuid(), 'entity_type' => $kind, 'action' => 'create', 'base_version' => null, 'payload' => $p];
        $gh = app(MasterService::class)->mutate($users['manager'], 'greenhouses', $operation('greenhouses', ['name' => 'GH investasi sintetis '.$fixture]))['body']['data'];
        app(MasterService::class)->mutate($users['manager'], 'greenhouse_assignments', $operation('greenhouse_assignments', ['greenhouse_id' => $gh['id'], 'user_id' => $users['worker']->public_id, 'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]));

        return ['database' => 'inofarm_test', 'fixture_id' => $fixture, 'base_url' => 'http://10.0.2.2:8001/api/v2/', 'password' => $password, 'manager_id' => $users['manager']->public_id, 'manager_email' => $users['manager']->email, 'worker_id' => $users['worker']->public_id, 'worker_email' => $users['worker']->email, 'greenhouse_id' => $gh['id'], 'scenario_id' => (string) Str::uuid(), 'report_id' => (string) Str::uuid(), 'stale_report_id' => (string) Str::uuid()];
    });
    if (file_put_contents($path, json_encode($f, JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR), LOCK_EX) === false) {
        throw new RuntimeException('Private manifest write failed');
    }
    echo "Investment fixture created only in inofarm_test; private manifest written.\n";
    exit;
}
$f = json_decode(file_get_contents($path), true, flags: JSON_THROW_ON_ERROR);
if ($f['database'] !== 'inofarm_test' || ! Str::isUuid($f['fixture_id'])) {
    throw new RuntimeException('Invalid fixture manifest');
}
$actors = User::whereIn('public_id', [$f['manager_id'], $f['worker_id']])->get();
if ($actors->count() !== 2 || $actors->contains(fn ($a) => ! str_ends_with($a->email, $f['fixture_id'].'@example.invalid'))) {
    throw new RuntimeException('Fixture actor ownership mismatch');
}
$manager = $actors->firstWhere('public_id', $f['manager_id']);
if (! DB::table('greenhouses')->where('id', $f['greenhouse_id'])->where('created_by', $manager->id)->exists()) {
    throw new RuntimeException('Fixture GH ownership mismatch');
}
if ($mode === 'verify') {
    $scenario = DB::table('investment_scenarios')->where('id', $f['scenario_id'])->where('greenhouse_id', $f['greenhouse_id'])->where('recorded_by', $manager->id)->first();
    if (! $scenario || $scenario->version !== 3 || $scenario->record_state !== 'cancelled' || DB::table('investment_cash_flow_versions')->where('scenario_id', $scenario->id)->count() !== 6) {
        throw new RuntimeException('Scenario version/history mismatch');
    }
    foreach (['audit_logs', 'sync_operations', 'change_feed'] as $table) {
        if (DB::table($table)->where('entity_type', 'investment_scenario')->where('entity_id', $scenario->id)->count() !== 3) {
            throw new RuntimeException('Scenario ledger duplicated or partial');
        }
    }
    $rows = DB::table('report_snapshots')->where('greenhouse_id', $f['greenhouse_id'])->get();
    $npv = [];
    if ($rows->count() !== 2) {
        throw new RuntimeException('Expected two immutable investment reports');
    }
    foreach ($rows as $row) {
        $full = json_decode($row->content, true, flags: JSON_THROW_ON_ERROR);
        if ($row->kind !== 'investment_report' || $row->scenario_id !== $scenario->id || $row->version !== 1 || $row->source_count !== 3 || $row->recorded_by !== $manager->id || ! hash_equals($row->snapshot_hash, CanonicalJson::hash($full)) || ! hash_equals($row->sources_hash, CanonicalJson::hash(['scope' => $full['scope'], 'sources' => $full['sources']]))) {
            throw new RuntimeException('Snapshot identity/hash mismatch');
        }
        foreach (['audit_logs', 'sync_operations', 'change_feed'] as $table) {
            if (DB::table($table)->where('entity_type', 'investment_report')->where('entity_id', $row->id)->count() !== 1) {
                throw new RuntimeException('Report ledger duplicated or partial');
            }
        }
        $npv[] = $full['metrics']['NPV']['value'];
        if (! in_array($full['scenario_version'], [1, 2], true) || count(array_unique(array_column($full['sources'], 'version'))) !== 1 || $full['sources'][0]['version'] !== $full['scenario_version']) {
            throw new RuntimeException('Mixed annual source versions');
        }
    }
    sort($npv, SORT_STRING);
    if ($npv !== ['-45867768.60', '4132231.40']) {
        throw new RuntimeException('Old/new report values changed');
    }
    echo "PASS investment: scenario versions1/2/3, six annual rows; two frozen reports NPV4132231.40/-45867768.60; exact hashes and one ledger per accepted report.\n";
    exit;
}
if ($mode !== 'cleanup') {
    throw new InvalidArgumentException('Use create|verify|cleanup');
}
DB::transaction(function () use ($f, $manager, $actors) {
    DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
    $ids = [...$actors->pluck('public_id')->all(), ...DB::table('sync_operations')->whereIn('actor_id', $actors->pluck('id'))->pluck('entity_id')->all()];
    $scenarios = DB::table('investment_scenarios')->where('greenhouse_id', $f['greenhouse_id'])->where('recorded_by', $manager->id)->pluck('id');
    DB::table('report_snapshots')->where('greenhouse_id', $f['greenhouse_id'])->where('recorded_by', $manager->id)->delete();
    DB::table('investment_cash_flow_versions')->whereIn('scenario_id', $scenarios)->delete();
    DB::table('investment_scenarios')->whereIn('id', $scenarios)->delete();
    foreach (['audit_logs', 'sync_operations'] as $table) {
        DB::table($table)->whereIn('actor_id', $actors->pluck('id'))->delete();
    }
    DB::table('change_feed')->where('greenhouse_id', $f['greenhouse_id'])->delete();
    DB::table('greenhouse_assignments')->where('greenhouse_id', $f['greenhouse_id'])->delete();
    DB::table('greenhouses')->where('id', $f['greenhouse_id'])->where('created_by', $manager->id)->delete();
    DB::table('personal_access_tokens')->where('tokenable_type', User::class)->whereIn('tokenable_id', $actors->pluck('id'))->delete();
    DB::table('entity_identities')->whereIn('entity_id',$ids)->delete();
    User::whereIn('id',$actors->pluck('id'))->delete();
});
unlink($path);
echo "Only owned synthetic investment fixture removed.\n";
