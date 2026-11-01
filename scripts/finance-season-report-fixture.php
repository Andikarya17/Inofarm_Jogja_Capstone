<?php

use App\Domain\Contracts\CanonicalJson;
use App\Domain\Cultivation\MasterService;
use App\Domain\Finance\AssetService;
use App\Domain\Finance\CostAllocationService;
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
$path = dirname(__DIR__).'/tmp/finance-season-report-fixture.json';
$mode = $argv[1] ?? 'create';
if ($mode === 'create') {
    if (is_file($path)) {
        throw new RuntimeException('Existing season report fixture manifest: inspect ownership before continuing.');
    }
    $fixture = DB::transaction(function () {
        DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
        $fixture = (string) Str::uuid();
        $password = Str::random(40);
        $users = [];
        foreach (['manager', 'worker'] as $role) {
            $id = (string) Str::uuid();
            app(EntityIdentity::class)->claim('users', $id);
            $users[$role] = User::create(['public_id' => $id, 'name' => 'Laporan perangkat sintetis '.$role, 'email' => 'report-'.$role.'-'.$fixture.'@example.invalid', 'password' => $password, 'role' => $role]);
        }
        $epoch = DB::table('sync_state')->where('id', 1)->value('epoch');
        $op = fn ($kind, $payload) => ['schema_version' => '2', 'sync_epoch' => $epoch, 'operation_id' => (string) Str::uuid(), 'entity_id' => (string) Str::uuid(), 'entity_type' => $kind, 'action' => 'create', 'base_version' => null, 'payload' => $payload];
        $master = fn ($kind, $p) => app(MasterService::class)->mutate($users['manager'], $kind, $op($kind, $p))['body']['data'];
        $gh = $master('greenhouses', ['name' => 'GH laporan sintetis '.$fixture]);
        $season = $master('seasons', ['greenhouse_id' => $gh['id'], 'name' => 'Musim laporan sintetis', 'start_date' => '2026-01-01', 'end_date' => null]);
        $category = $master('expense_categories', ['greenhouse_id' => $gh['id'], 'name' => 'Kategori laporan sintetis']);
        $master('greenhouse_assignments', ['greenhouse_id' => $gh['id'], 'user_id' => $users['worker']->public_id, 'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);
        $expense = app(ExpenseService::class)->mutate($users['manager'], $op('expense', ['greenhouse_id' => $gh['id'], 'category_id' => $category['id'], 'amount' => '100.00', 'paid_at' => '2026-01-01T08:00:00+07:00', 'payment_kind' => 'asset_acquisition', 'notes' => 'Pembayaran aset sintetis '.$fixture]))['body']['data'];

        $operating = app(ExpenseService::class)->mutate($users['manager'], $op('expense', ['greenhouse_id' => $gh['id'], 'category_id' => $category['id'], 'amount' => '80.00', 'paid_at' => '2026-01-01T08:00:00+07:00', 'payment_kind' => 'operating', 'notes' => 'Sumber biaya laporan sintetis']))['body']['data'];
        foreach (['FC' => '20.00', 'VC' => '60.00'] as $class => $amount) {
            app(CostAllocationService::class)->mutate($users['manager'], $op('cost_allocation', ['expense_id' => $operating['id'], 'season_id' => $season['id'], 'amount' => $amount, 'cost_class' => $class, 'allocation_basis' => 'Alokasi sintetis eksplisit']));
        }
        $asset = app(AssetService::class)->mutate($users['manager'], $op('asset', ['expense_id' => $expense['id'], 'name' => 'Aset laporan sintetis', 'cost' => '100.00', 'residual' => '0.00', 'life_years' => 3, 'in_service_date' => '2026-01-01', 'usage_basis' => 'Pemakaian manual sintetis']))['body']['data'];
        $dep = app(AssetService::class)->mutate($users['manager'], $op('depreciation_allocation', ['asset_id' => $asset['id'], 'season_id' => $season['id'], 'usage_reference' => (string) Str::uuid(), 'usage_year' => 1, 'period_start' => '2026-01-01', 'period_end' => '2026-02-01', 'amount' => '33.33', 'allocation_basis' => 'Pemakaian laporan sintetis']))['body']['data'];

        return ['database' => 'inofarm_test', 'fixture_id' => $fixture, 'base_url' => 'http://10.0.2.2:8001/api/v2/', 'password' => $password,
            'manager_id' => $users['manager']->public_id, 'worker_id' => $users['worker']->public_id, 'manager_email' => $users['manager']->email, 'worker_email' => $users['worker']->email,
            'greenhouse_id' => $gh['id'], 'season_id' => $season['id'], 'expense_id' => $expense['id'], 'asset_id' => $asset['id'], 'depreciation_id' => $dep['id'], 'operating_expense_id' => $operating['id']];
    });
    if (file_put_contents($path, json_encode($fixture, JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR), LOCK_EX) === false) {
        throw new RuntimeException('Manifest write failed');
    }
    echo "Synthetic season report fixture created only in inofarm_test; private manifest written.\n";
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
    $rows = DB::table('report_snapshots')->where('greenhouse_id', $f['greenhouse_id'])->get();
    if ($rows->count() !== 2) {
        throw new RuntimeException('Expected two immutable reports');
    }
    $totals = [];
    foreach ($rows as $row) {
        $full = json_decode($row->content, true, flags: JSON_THROW_ON_ERROR);
        if ($row->kind !== 'season_report' || $row->version !== 1 || $row->recorded_by !== $manager->id || ! hash_equals($row->snapshot_hash, CanonicalJson::hash($full))) {
            throw new RuntimeException('Report ownership/version/hash mismatch');
        }
        $totals[] = $full['metrics']['TC']['value'];
        if ($full['metrics']['TR']['value'] !== '0.00' || $full['metrics']['P']['value'] !== null || $row->source_count !== 6) {
            throw new RuntimeException('Independent report numeric/source mismatch');
        }
        foreach (['audit_logs', 'sync_operations', 'change_feed'] as $ledger) {
            if (DB::table($ledger)->where('entity_type', 'season_report')->where('entity_id', $row->id)->count() !== 1) {
                throw new RuntimeException('Report replay duplicated a ledger row');
            }
        }
    }
    sort($totals, SORT_STRING);
    if ($totals !== ['113.33', '80.00']) {
        throw new RuntimeException('Old/new snapshots were recalculated or double-counted');
    }
    $dep = DB::table('depreciation_allocations')->where('id', $f['depreciation_id'])->first();
    if ($dep->version !== 2 || $dep->record_state !== 'cancelled') {
        throw new RuntimeException('Source correction was not accepted');
    }
    echo "PASS inofarm_test: old report TC113.33/new report TC80.00; TR0/Pnull; six frozen sources each; exactly one audit/receipt/feed per report.\n";
    exit(0);
}
if ($mode !== 'cleanup') {
    throw new InvalidArgumentException('Use create|verify|cleanup');
}
DB::transaction(function () use ($actors, $manager, $f) {
    DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
    $ids = [...$actors->pluck('public_id')->all(), ...DB::table('sync_operations')->whereIn('actor_id', $actors->pluck('id'))->pluck('entity_id')->all(),
        ...DB::table('greenhouse_assignments')->where('greenhouse_id', $f['greenhouse_id'])->pluck('id')->all(), ...DB::table('expenses')->where('greenhouse_id', $f['greenhouse_id'])->pluck('balance_id')->all()];
    DB::table('report_snapshots')->where('greenhouse_id', $f['greenhouse_id'])->where('recorded_by', $manager->id)->delete();
    DB::table('cost_allocations')->whereIn('expense_id', DB::table('expenses')->where('greenhouse_id', $f['greenhouse_id'])->select('id'))->delete();
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
echo "Only owned synthetic season report fixture removed.\n";
