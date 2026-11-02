<?php

use App\Domain\Contracts\CanonicalJson;
use App\Domain\Finance\InvestmentReportService;
use App\Domain\Finance\InvestmentScenarioService;
use App\Domain\Finance\SeasonReportService;
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
$root = dirname(__DIR__);
$path = $root.'/tmp/finance-report-export-fixture.json';
$base = $root.'/tmp/finance-season-report-fixture.json';
$mode = $argv[1] ?? 'create';
$baseAction = function (string $action) use ($root) {
    $process = proc_open([PHP_BINARY, $root.'/scripts/finance-season-report-fixture.php', $action], [0 => ['pipe', 'r'], 1 => ['pipe', 'w'], 2 => ['pipe', 'w']], $pipes);
    if (! is_resource($process)) {
        throw new RuntimeException('Base fixture tool unavailable');
    }fclose($pipes[0]);
    $out = stream_get_contents($pipes[1]);
    $error = stream_get_contents($pipes[2]);
    fclose($pipes[1]);
    fclose($pipes[2]);
    if (proc_close($process) !== 0) {
        throw new RuntimeException('Base fixture action failed: '.$error);
    }

    return $out;
};
if ($mode === 'create') {
    if (is_file($path) || is_file($base)) {
        throw new RuntimeException('Existing export/base fixture; inspect ownership first.');
    }
    $baseAction('create');
    $f = json_decode(file_get_contents($base), true, flags: JSON_THROW_ON_ERROR);
    try {
        $actor = User::where('public_id', $f['manager_id'])->firstOrFail();
        $epoch = DB::table('sync_state')->where('id', 1)->value('epoch');
        $op = fn ($kind, $payload) => ['schema_version' => '2', 'sync_epoch' => $epoch, 'operation_id' => (string) Str::uuid(), 'entity_id' => (string) Str::uuid(), 'entity_type' => $kind, 'action' => 'create', 'base_version' => null, 'payload' => $payload];
        $input = ['season_id' => $f['season_id'], 'coverage' => ['costs' => 'complete', 'sales' => 'complete'], 'known_pending_count' => 0, 'requested_state' => 'provisional', 'review_note' => 'Ekspor musim sintetis'];
        $preview = DB::transaction(function () use ($actor, $input) {
            DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();

            return app(SeasonReportService::class)->preview($actor, $input);
        });
        $season = app(SeasonReportService::class)->create($actor, $op('season_report', [...$input, 'expected_sources_hash' => $preview['sources_hash'], 'expected_formula_version' => $preview['formula_version']]))['body']['data'];
        $p = ['greenhouse_id' => $f['greenhouse_id'], 'name' => 'Investasi ekspor sintetis α', 'base_date' => '2026-01-01', 'horizon_years' => 2, 'initial_outlay' => '100000000.00', 'initial_source' => ['kind' => 'actual', 'reference' => 'Fixture', 'basis' => 'Awal sekali'], 'discount_rate' => '0.10', 'payback_convention' => 'interpolation', 'cash_flows' => array_map(fn ($year) => ['year' => $year, 'amount' => '60000000.00', 'source_kind' => 'projection', 'source_reference' => 'Fixture', 'basis' => 'Kas neto tahunan'], [1, 2]), 'notes' => 'Bukan nilai produksi'];
        $scenario = app(InvestmentScenarioService::class)->mutate($actor, $op('investment_scenario', $p))['body']['data'];
        $input = ['scenario_id' => $scenario['id'], 'review_note' => 'Ekspor investasi sintetis'];
        $preview = DB::transaction(function () use ($actor, $input) {
            DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();

            return app(InvestmentReportService::class)->preview($actor, $input);
        });
        $investment = app(InvestmentReportService::class)->create($actor, $op('investment_report', [...$input, 'expected_scenario_version' => $preview['scenario_version'], 'expected_sources_hash' => $preview['sources_hash'], 'expected_formula_version' => $preview['formula_version']]))['body']['data'];
        $f += ['season_report_id' => $season['id'], 'season_report_hash' => $season['snapshot_hash'], 'investment_report_id' => $investment['id'], 'investment_report_hash' => $investment['snapshot_hash'], 'scenario_id' => $scenario['id'], 'scenario_payload' => $p];
        file_put_contents($path, json_encode($f, JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR), LOCK_EX);
        echo "Owned export fixture created only in inofarm_test; private manifest written.\n";
        exit;
    } catch (Throwable $e) {
        file_put_contents($path, json_encode($f, JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR), LOCK_EX);
        throw $e;
    }
}
$f = json_decode(file_get_contents($path), true, flags: JSON_THROW_ON_ERROR);
$parent = json_decode(file_get_contents($base), true, flags: JSON_THROW_ON_ERROR);
if ($f['database'] !== 'inofarm_test' || $f['fixture_id'] !== $parent['fixture_id'] || $f['greenhouse_id'] !== $parent['greenhouse_id']) {
    throw new RuntimeException('Export fixture ownership mismatch');
}
$actor = User::where('public_id', $f['manager_id'])->firstOrFail();
if (! str_ends_with($actor->email, $f['fixture_id'].'@example.invalid') || ! DB::table('greenhouses')->where('id', $f['greenhouse_id'])->where('created_by', $actor->id)->exists()) {
    throw new RuntimeException('Export actor/GH mismatch');
}
if ($mode === 'verify') {
    $rows = DB::table('report_snapshots')->where('greenhouse_id', $f['greenhouse_id'])->get();
    if ($rows->count() !== 2) {
        throw new RuntimeException('Expected exactly two saved export reports');
    }
    foreach ($rows as $row) {
        $full = json_decode($row->content, true, flags: JSON_THROW_ON_ERROR);
        if (! hash_equals($row->snapshot_hash, CanonicalJson::hash($full))) {
            throw new RuntimeException('Snapshot changed');
        }
        foreach (['audit_logs', 'sync_operations', 'change_feed'] as $table) {
            if (DB::table($table)->where('entity_type', $row->kind)->where('entity_id', $row->id)->count() !== 1) {
                throw new RuntimeException('Read-only export changed report ledger');
            }
        }
        $key = $row->kind === 'season_report' ? 'TC' : 'NPV';
        $expected = $key === 'TC' ? '113.33' : '4132231.40';
        if ($full['metrics'][$key]['value'] !== $expected) {
            throw new RuntimeException('Old report recalculated');
        }
    }
    if (DB::table('depreciation_allocations')->where('id', $f['depreciation_id'])->value('record_state') !== 'cancelled' || DB::table('investment_scenarios')->where('id', $f['scenario_id'])->value('version') !== 2) {
        throw new RuntimeException('Source correction missing');
    }
    echo "PASS: two unchanged report snapshots/ledgers after native PDF/CSV downloads and source corrections.\n";
    exit;
}
if ($mode !== 'cleanup') {
    throw new InvalidArgumentException('Use create|verify|cleanup');
}
DB::transaction(function () use ($f, $actor) {
    DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
    $ownedActors = User::whereIn('public_id', [$f['manager_id'], $f['worker_id']])->pluck('id');
    DB::table('personal_access_tokens')->where('tokenable_type', User::class)->whereIn('tokenable_id', $ownedActors)->delete();
    $ids = DB::table('investment_scenarios')->where('greenhouse_id', $f['greenhouse_id'])->where('recorded_by', $actor->id)->pluck('id');
    DB::table('report_snapshots')->where('greenhouse_id', $f['greenhouse_id'])->where('kind', 'investment_report')->where('recorded_by', $actor->id)->delete();
    DB::table('investment_cash_flow_versions')->whereIn('scenario_id', $ids)->delete();
    DB::table('investment_scenarios')->whereIn('id', $ids)->delete();
});
$baseAction('cleanup');
unlink($path);
echo "Only owned export/base fixture removed.\n";
