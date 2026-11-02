<?php

use App\Domain\Cultivation\MasterService;
use App\Domain\Identity\EntityIdentity;
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
        DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
        $id = (string) Str::uuid();
        $password = 'device-fixture-only-password';
        $managerId = (string) Str::uuid();$workerId = (string) Str::uuid();
        app(EntityIdentity::class)->claim('users', $managerId);app(EntityIdentity::class)->claim('users', $workerId);
        $manager = User::create(['public_id' => $managerId, 'name' => 'Pengelola perangkat sintetis', 'email' => 'device-manager-'.$id.'@example.invalid', 'role' => 'manager', 'password' => $password]);
        $worker = User::create(['public_id' => $workerId, 'name' => 'Pekerja perangkat sintetis', 'email' => 'device-worker-'.$id.'@example.invalid', 'role' => 'worker', 'password' => $password]);
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
            $grades = [];
            foreach (['A', 'B'] as $gradeName) {
                $grades[] = $create('grades', ['greenhouse_id' => $gh['id'], 'name' => 'Grade '.$gradeName.' perangkat sintetis'])['id'];
            }
            $category = $create('expense_categories', ['greenhouse_id' => $gh['id'], 'name' => 'Kategori biaya perangkat sintetis']);
            $contexts[] = ['greenhouse_id' => $gh['id'], 'season_id' => $season['id'], 'group_id' => $group['id'], 'variety_id' => $variety['id'], 'grade_ids' => $grades, 'category_id' => $category['id']];
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
if ($actors->count() !== 2) {
    throw new RuntimeException('Both synthetic fixture accounts must still exist before verification/cleanup.');
}
foreach ($actors as $actor) {
    if (! str_ends_with($actor->email, $fixture['fixture_id'].'@example.invalid')) {
        throw new RuntimeException('Refusing unrelated user rows.');
    }
}
$groups = array_column($fixture['contexts'], 'group_id');
$greenhouses = array_column($fixture['contexts'], 'greenhouse_id');
$manager = $actors->firstWhere('public_id', $fixture['manager_id']);
foreach ($fixture['contexts'] as $context) {
    if (! DB::table('greenhouses')->where('id', $context['greenhouse_id'])->where('created_by', $manager->id)->exists()
        || ! DB::table('seasons')->where('id', $context['season_id'])->where('greenhouse_id', $context['greenhouse_id'])->exists()
        || ! DB::table('planting_groups')->where('id', $context['group_id'])->where('season_id', $context['season_id'])->where('variety_id', $context['variety_id'])->exists()
        || ! DB::table('expense_categories')->where('id', $context['category_id'])->where('greenhouse_id', $context['greenhouse_id'])->exists()) {
        throw new RuntimeException('Fixture context ownership/relations do not match; cleanup refused.');
    }
}
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
    $harvests = DB::table('harvests')->whereIn('planting_group_id', $groups)->where('notes', 'DEVICE HARVEST OFFLINE '.$fixture['fixture_id'])->get();
    if ($harvests->count() !== 1 || $harvests[0]->version !== 2 || $harvests[0]->record_state !== 'cancelled' || $harvests[0]->notes !== 'DEVICE HARVEST OFFLINE '.$fixture['fixture_id']) {
        throw new RuntimeException('Expected one accepted then manager-cancelled synthetic harvest, version2.');
    }
    $harvestId = $harvests[0]->id;
    foreach (['audit_logs', 'change_feed', 'sync_operations'] as $table) {
        if (DB::table($table)->where('entity_id', $harvestId)->count() !== 2) {
            throw new RuntimeException('Expected exactly two harvest ledger rows in '.$table);
        }
    }
    $items = DB::table('harvest_items')->where('harvest_id', $harvestId)->get();
    $weights = $items->pluck('weight_kg')->sort()->values()->all();
    $counts = $items->pluck('fruit_count')->sort()->values()->all();
    if ($items->count() !== 2 || $weights !== ['0.000000', '4.500000'] || $counts !== [0, 3]
        || $items->contains(fn ($item) => bccomp($item->sold_weight_kg, '0', 6) !== 0)
        || DB::table('harvest_reference_snapshots')->where('harvest_id', $harvestId)->count() !== 2
        || DB::table('change_feed')->where('entity_type', 'harvest_balance')->whereIn('entity_id', $items->pluck('id'))->count() !== 4) {
        throw new RuntimeException('Harvest quantities, snapshots or balance feed did not reconcile.');
    }
    $cancel = DB::table('audit_logs')->where('entity_type', 'harvest')->where('entity_id', $harvestId)->where('action', 'cancel')->first();
    if ($cancel->actor_id !== $manager->id || $cancel->reason !== 'DEVICE HARVEST CANCEL '.$fixture['fixture_id']) {
        throw new RuntimeException('Expected session-derived manager cancellation audit.');
    }
    $expenses = DB::table('expenses')->whereIn('greenhouse_id', $greenhouses)->get();
    if ($expenses->count() !== 1 || $expenses[0]->version !== 1 || $expenses[0]->record_state !== 'active' || $expenses[0]->amount !== '100.01'
        || $expenses[0]->allocated_amount !== '60.01' || $expenses[0]->balance_version !== 2 || $expenses[0]->recorded_by !== $manager->id
        || $expenses[0]->notes !== 'DEVICE EXPENSE — '.$fixture['fixture_id']) {
        throw new RuntimeException('Actual payment/counter/actor did not match device proof.');
    }
    $expense = $expenses[0];
    $allocations = DB::table('cost_allocations')->where('expense_id', $expense->id)->get();
    if ($allocations->count() !== 1 || $allocations[0]->version !== 1 || $allocations[0]->amount !== '60.01' || $allocations[0]->cost_class !== 'VC'
        || $allocations[0]->season_id !== $fixture['contexts'][0]['season_id'] || $allocations[0]->recorded_by !== $manager->id
        || $allocations[0]->allocation_basis !== 'DEVICE ALLOCATION '.$fixture['fixture_id'] || $allocations[0]->record_state !== 'active'
        || DB::table('cost_allocations')->where('expense_id', $expense->id)->where('record_state', 'active')->sum('amount') !== $expense->allocated_amount) {
        throw new RuntimeException('Actual cost allocation did not reconcile with payment.');
    }
    foreach ([$expense->id, $allocations[0]->id] as $financialId) {
        foreach (['audit_logs', 'change_feed', 'sync_operations'] as $table) {
            if (DB::table($table)->where('entity_id', $financialId)->count() !== 1) {
                throw new RuntimeException('Expected exactly one financial ledger row after replay.');
            }
        }
    }
    if (DB::table('change_feed')->where('entity_type', 'expense_balance')->where('entity_id', $expense->balance_id)->where('audience', 'manager')->count() !== 2) {
        throw new RuntimeException('Financial balance feed did not match source and allocation mutations.');
    }
    $attachments = DB::table('record_attachments')->whereIn('greenhouse_id', $greenhouses)->get();
    $privateRoot = dirname(__DIR__).'/tmp/finance-device-blobs-'.$fixture['fixture_id'];
    if ($attachments->count() !== 2 || $attachments->pluck('mime')->sort()->values()->all() !== ['application/pdf', 'image/png']) {
        throw new RuntimeException('Expected actual private PNG and PDF metadata.');
    }
    foreach ($attachments as $attachment) {
        $blob = $privateRoot.'/blobs/'.substr($attachment->sha256, 0, 2).'/'.$attachment->sha256;
        if ($attachment->owner_type !== 'expense' || $attachment->owner_id !== $expense->id || $attachment->owner_version !== 1 || $attachment->recorded_by !== $manager->id
            || $attachment->version !== 1 || $attachment->record_state !== 'active' || ! str_starts_with($attachment->original_name, 'DEVICE PROOF — '.$fixture['fixture_id'])
            || ! is_file($blob) || filesize($blob) !== $attachment->bytes || hash_file('sha256', $blob) !== $attachment->sha256
            || ($attachment->mime === 'application/pdf' && $attachment->bytes <= 16384)) {
            throw new RuntimeException('Actual private file, parent/version, actor or hash did not reconcile.');
        }
        foreach (['audit_logs', 'change_feed', 'sync_operations'] as $table) {
            if (DB::table($table)->where('entity_id', $attachment->id)->count() !== 1) {throw new RuntimeException('Attachment replay duplicated '.$table);}
        }
        if (! DB::table('entity_identities')->where('entity_id', $attachment->id)->where('entity_type', 'expense_attachment')->exists()) {throw new RuntimeException('Attachment identity was not reserved.');}
    }
    $source = DB::table('harvests')->whereIn('planting_group_id', $groups)->where('notes', 'DEVICE SALE HARVEST '.$fixture['fixture_id'])->first();
    $sales = DB::table('sales')->whereIn('greenhouse_id', $greenhouses)->get();
    if (!$source || $source->version !== 1 || $source->record_state !== 'active' || $source->recorded_by !== $actors->firstWhere('public_id', $fixture['worker_id'])->id
        || $sales->count() !== 1 || $sales[0]->version !== 2 || $sales[0]->record_state !== 'cancelled' || $sales[0]->total_amount !== '0.01' || $sales[0]->total_weight_kg !== '2.000000'
        || $sales[0]->notes !== 'DEVICE SALE '.$fixture['fixture_id'] || $sales[0]->recorded_by !== $manager->id) {throw new RuntimeException('Actual worker harvest/manager sale did not reconcile.');}
    $saleItems = DB::table('sale_items')->where('sale_id', $sales[0]->id)->get();$sourceItems = DB::table('harvest_items')->where('harvest_id', $source->id)->get();
    if ($saleItems->count() !== 2 || $sourceItems->count() !== 2 || $saleItems->contains(fn ($line) => $line->quantity_kg !== '1.000000' || $line->price_per_kg !== '0.004000')
        || $sourceItems->contains(fn ($item) => $item->sold_weight_kg !== '0.000000' || $item->weight_kg !== '5.000000' || $item->balance_version !== 3)) {throw new RuntimeException('Sale line products/source release did not reconcile.');}
    foreach (['audit_logs', 'sync_operations', 'change_feed'] as $table) {if (DB::table($table)->where('entity_type', 'sale')->where('entity_id', $sales[0]->id)->count() !== 2) {throw new RuntimeException('Sale exact replay/cancel ledger mismatch.');}}
    if (Illuminate\Support\Facades\Artisan::call('inofarm:finance-identities', ['--json' => true]) !== 0) {
        throw new RuntimeException('Read-only identity preflight did not reconcile the actual device graph.');
    }
    echo 'PASS: MySQL inofarm_test activity1/version1/ledger1; harvest1/version2/cancelled/items2/snapshots2/ledger2/balance feed4; payment100.01/version1/allocation60.01/version1/counter60.01/balance version2/feed2; paid sale cancelled/version2/items2/total0.01 and restored harvest counters; private PNG/PDF2 and verified hashes, one financial/file audit/receipt/feed per entity after replay.'.PHP_EOL;
    exit(0);
}
if ($mode !== 'cleanup') {
    throw new RuntimeException('Use create, verify or cleanup.');
}
$privateFiles = DB::table('record_attachments')->whereIn('greenhouse_id', $greenhouses)->whereIn('recorded_by', $actors->pluck('id'))->get();
DB::transaction(function () use ($fixture, $actors, $groups, $greenhouses) {
    $ownedSales = DB::table('sales')->whereIn('greenhouse_id', $greenhouses)->whereIn('recorded_by', $actors->pluck('id'))->pluck('id')->all();
    $registryIds = [...DB::table('change_feed')->whereIn('greenhouse_id', $greenhouses)->pluck('entity_id')->all(),
        ...DB::table('greenhouse_assignments')->whereIn('greenhouse_id', $greenhouses)->pluck('id')->all(), ...$actors->pluck('public_id')->all(), ...DB::table('sale_items')->whereIn('sale_id', $ownedSales)->pluck('id')->all()];
    DB::table('record_attachments')->whereIn('greenhouse_id', $greenhouses)->whereIn('recorded_by', $actors->pluck('id'))->delete();
    DB::table('sale_items')->whereIn('sale_id', $ownedSales)->delete();DB::table('sales')->whereIn('id', $ownedSales)->delete();
    DB::table('entity_identities')->whereIn('entity_id', array_unique($registryIds))->delete();
    $expenses = DB::table('expenses')->whereIn('greenhouse_id', $greenhouses)->whereIn('recorded_by', $actors->pluck('id'))->select('id');
    DB::table('cost_allocations')->whereIn('expense_id', $expenses)->whereIn('recorded_by', $actors->pluck('id'))->delete();
    DB::table('expenses')->whereIn('greenhouse_id', $greenhouses)->whereIn('recorded_by', $actors->pluck('id'))->delete();
    $harvests = DB::table('harvests')->whereIn('planting_group_id', $groups)->select('id');
    foreach (['harvest_reference_snapshots', 'harvest_items'] as $table) {
        DB::table($table)->whereIn('harvest_id', $harvests)->delete();
    }
    DB::table('harvests')->whereIn('planting_group_id', $groups)->delete();
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
    DB::table('grades')->whereIn('greenhouse_id', $greenhouses)->delete();
    DB::table('expense_categories')->whereIn('greenhouse_id', $greenhouses)->delete();
    DB::table('greenhouse_assignments')->whereIn('greenhouse_id', $greenhouses)->delete();
    DB::table('greenhouses')->whereIn('id', $greenhouses)->delete();
    foreach ($actors as $actor) {
        $actor->tokens()->delete();
        $actor->delete();
    }
});
$privateRoot = dirname(__DIR__).'/tmp/finance-device-blobs-'.$fixture['fixture_id'];
foreach ($privateFiles as $attachment) {
    if (! preg_match('/^[0-9a-f]{64}$/D', $attachment->sha256)) {throw new RuntimeException('Unsafe synthetic blob identity.');}
    $directory = $privateRoot.'/blobs/'.substr($attachment->sha256, 0, 2);
    $blob = $directory.'/'.$attachment->sha256;
    if (is_file($blob)) {
        if (hash_file('sha256', $blob) !== $attachment->sha256) {throw new RuntimeException('Synthetic file changed; refusing cleanup.');}
        unlink($blob);
    }
    if (is_dir($directory) && count(scandir($directory)) === 2) {rmdir($directory);}
}
// No recursive delete: preserve any unexpected/orphan file for inspection.
foreach ([$privateRoot.'/blobs', $privateRoot.'/staging', $privateRoot] as $directory) {
    if (is_dir($directory) && count(scandir($directory)) === 2) {rmdir($directory);}
}
unlink($path);
echo 'Only this synthetic fixture UUID/actor rows and ignored manifest cleaned; no reset or user database operation.'.PHP_EOL;
