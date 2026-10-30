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
$path = dirname(__DIR__).'/tmp/finance-asset-fixture.json';
$mode = $argv[1] ?? 'create';
if ($mode === 'create') {
    if (is_file($path)) {
        throw new RuntimeException('Existing asset fixture manifest: inspect ownership before continuing.');
    }
    $fixture = DB::transaction(function () {
        DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
        $fixture = (string) Str::uuid();
        $password = Str::random(40);
        $users = [];
        foreach (['manager', 'worker'] as $role) {
            $id = (string) Str::uuid();
            app(EntityIdentity::class)->claim('users', $id);
            $users[$role] = User::create(['public_id' => $id, 'name' => 'Aset perangkat sintetis '.$role, 'email' => 'asset-'.$role.'-'.$fixture.'@example.invalid', 'password' => $password, 'role' => $role]);
        }
        $epoch = DB::table('sync_state')->where('id', 1)->value('epoch');
        $op = fn ($kind, $payload) => ['schema_version' => '2', 'sync_epoch' => $epoch, 'operation_id' => (string) Str::uuid(), 'entity_id' => (string) Str::uuid(), 'entity_type' => $kind, 'action' => 'create', 'base_version' => null, 'payload' => $payload];
        $master = fn ($kind, $p) => app(MasterService::class)->mutate($users['manager'], $kind, $op($kind, $p))['body']['data'];
        $gh = $master('greenhouses', ['name' => 'GH aset sintetis '.$fixture]);
        $season = $master('seasons', ['greenhouse_id' => $gh['id'], 'name' => 'Musim aset sintetis', 'start_date' => '2026-01-01', 'end_date' => null]);
        $category = $master('expense_categories', ['greenhouse_id' => $gh['id'], 'name' => 'Kategori pembelian aset sintetis']);
        $master('greenhouse_assignments', ['greenhouse_id' => $gh['id'], 'user_id' => $users['worker']->public_id, 'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);
        $expense = app(ExpenseService::class)->mutate($users['manager'], $op('expense', ['greenhouse_id' => $gh['id'], 'category_id' => $category['id'], 'amount' => '100.00', 'paid_at' => '2026-01-01T08:00:00+07:00', 'payment_kind' => 'asset_acquisition', 'notes' => 'Pembayaran aset sintetis '.$fixture]))['body']['data'];

        return ['database' => 'inofarm_test', 'fixture_id' => $fixture, 'base_url' => 'http://10.0.2.2:8001/api/v2/', 'password' => $password,
            'manager_id' => $users['manager']->public_id, 'worker_id' => $users['worker']->public_id, 'manager_email' => $users['manager']->email, 'worker_email' => $users['worker']->email,
            'greenhouse_id' => $gh['id'], 'season_id' => $season['id'], 'expense_id' => $expense['id']];
    });
    if (file_put_contents($path, json_encode($fixture, JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR), LOCK_EX) === false) {
        throw new RuntimeException('Manifest write failed');
    }
    echo "Synthetic asset fixture created only in inofarm_test; private manifest written.\n";
    exit(0);
}
$f = json_decode(file_get_contents($path), true, flags: JSON_THROW_ON_ERROR);
if ($f['database'] !== 'inofarm_test' || ! Str::isUuid($f['fixture_id'])) {
    throw new RuntimeException('Invalid fixture manifest');
}
$actors = User::whereIn('public_id', [$f['manager_id'], $f['worker_id']])->get();
if ($actors->count() !== 2 || $actors->contains(fn ($a) => ! str_ends_with($a->email, $f['fixture_id'].'@example.invalid'))) {
    throw new RuntimeException('Fixture accounts ownership mismatch');
}
$manager = $actors->firstWhere('public_id', $f['manager_id']);
if (! DB::table('greenhouses')->where('id', $f['greenhouse_id'])->where('created_by', $manager->id)->exists()
    || ! DB::table('seasons')->where('id', $f['season_id'])->where('greenhouse_id', $f['greenhouse_id'])->exists()
    || ! DB::table('expenses')->where('id', $f['expense_id'])->where('greenhouse_id', $f['greenhouse_id'])->where('recorded_by', $manager->id)->exists()) {
    throw new RuntimeException('Fixture parent ownership mismatch');
}
if ($mode === 'verify') {
    foreach (['assets', 'depreciation_allocations'] as $table) {
        $rows = DB::table($table)->where('greenhouse_id', $f['greenhouse_id'])->get();
        if ($rows->count() !== 1 || $rows[0]->recorded_by !== $manager->id || $rows[0]->record_state !== 'cancelled' || $rows[0]->version !== 2) {
            throw new RuntimeException('Asset/depreciation lifecycle mismatch');
        }
        $kind = $table === 'assets' ? 'asset' : 'depreciation_allocation';
        foreach (['audit_logs', 'sync_operations', 'change_feed'] as $ledger) {
            if (DB::table($ledger)->where('entity_type', $kind)->where('entity_id', $rows[0]->id)->count() !== 2) {
                throw new RuntimeException('Asset/depreciation replay ledger mismatch');
            }
        }
    }
    $a = DB::table('assets')->where('greenhouse_id', $f['greenhouse_id'])->first();
    $d = DB::table('depreciation_allocations')->where('greenhouse_id', $f['greenhouse_id'])->first();
    if ($a->cost !== '100.00' || $a->residual !== '0.00' || $a->life_years !== 3 || $a->expense_id !== $f['expense_id'] || $d->asset_id !== $a->id || $d->amount !== '33.33' || $d->year_budget !== '33.33') {
        throw new RuntimeException('Asset allocation numeric/source mismatch');
    }
    echo "PASS MySQL inofarm_test: asset100.00/0.00/3 years and allocation33.33; both cancelled/version2; exactly2 audit/receipt/feed each after native replay.\n";
    exit(0);
}
if ($mode !== 'cleanup') {
    throw new InvalidArgumentException('Use create|verify|cleanup');
}
DB::transaction(function () use ($actors, $manager, $f) {
    DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
    $ids = [...$actors->pluck('public_id')->all(), ...DB::table('sync_operations')->whereIn('actor_id', $actors->pluck('id'))->pluck('entity_id')->all(),
        ...DB::table('greenhouse_assignments')->where('greenhouse_id', $f['greenhouse_id'])->pluck('id')->all(), ...DB::table('expenses')->where('greenhouse_id', $f['greenhouse_id'])->pluck('balance_id')->all()];
    foreach (['depreciation_allocations', 'assets', 'expenses'] as $table) {
        DB::table($table)->where('greenhouse_id', $f['greenhouse_id'])->where('recorded_by', $manager->id)->delete();
    }
    foreach (['audit_logs', 'sync_operations'] as $table) {
        DB::table($table)->whereIn('actor_id', $actors->pluck('id'))->delete();
    }
    DB::table('change_feed')->where('greenhouse_id', $f['greenhouse_id'])->delete();
    DB::table('expense_categories')->where('greenhouse_id', $f['greenhouse_id'])->delete();
    DB::table('seasons')->where('id', $f['season_id'])->delete();
    DB::table('greenhouse_assignments')->where('greenhouse_id', $f['greenhouse_id'])->delete();
    DB::table('greenhouses')->where('id', $f['greenhouse_id'])->where('created_by', $manager->id)->delete();
    DB::table('entity_identities')->whereIn('entity_id', $ids)->delete();
    User::whereIn('id', $actors->pluck('id'))->delete();
});
unlink($path);
echo "Only owned synthetic asset fixture removed.\n";
