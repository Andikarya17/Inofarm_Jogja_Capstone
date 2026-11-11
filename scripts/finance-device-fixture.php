<?php

use App\Domain\Cultivation\MasterService;
use App\Domain\Finance\SaleService;
use App\Domain\Harvest\HarvestService;
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
$fixtureName = match ($argv[2] ?? '') {
    '' => 'finance-device-fixture.json',
    'manager-home' => 'manager-home-device-fixture.json',
    'worker-alignment' => 'worker-alignment-device-fixture.json',
    default => throw new InvalidArgumentException('Unknown synthetic device fixture name.'),
};
$path = dirname(__DIR__).'/tmp/'.$fixtureName;
$mode = $argv[1] ?? 'create';
if ($mode === 'create') {
    if (is_file($path)) {
        throw new RuntimeException('Synthetic fixture manifest exists; verify/cleanup that fixture first.');
    }
    $baseUrl = getenv('FINANCE_DEVICE_TEST_BASE_URL') ?: 'http://10.0.2.2:8001/api/v2/';
    if (! in_array($baseUrl, ['http://10.0.2.2:8001/api/v2/', 'http://127.0.0.1:8001/api/v2/'], true)) {
        throw new RuntimeException('Device fixture URL must use the isolated localhost test API.');
    }
    $fixture = DB::transaction(function () use ($baseUrl, $fixtureName) {
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
            $groupInput = ['season_id' => $season['id'], 'variety_id' => $variety['id'], 'name' => 'Kelompok '.$name.' sintetis',
                'population' => 10, 'sowing_date' => '2026-10-01', 'transplant_date' => null];
            if ($fixtureName === 'worker-alignment-device-fixture.json' && $name === 'A') {
                $groupInput['harvest_target'] = ['fruit_count' => 5, 'weight_kg' => '8', 'basis' => 'Target sintetis untuk uji perangkat'];
            }
            $group = $create('planting_groups', $groupInput);
            $grades = [];
            foreach (['A', 'B'] as $gradeName) {
                $grades[] = $create('grades', ['greenhouse_id' => $gh['id'], 'name' => 'Grade '.$gradeName.' perangkat sintetis'])['id'];
            }
            $category = $create('expense_categories', ['greenhouse_id' => $gh['id'], 'name' => 'Kategori biaya perangkat sintetis']);
            $contexts[] = ['greenhouse_id' => $gh['id'], 'season_id' => $season['id'], 'group_id' => $group['id'], 'variety_id' => $variety['id'], 'grade_ids' => $grades, 'category_id' => $category['id']];
        }
        $create('greenhouse_assignments', ['greenhouse_id' => $contexts[0]['greenhouse_id'], 'user_id' => $worker->public_id, 'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);

        return ['database' => 'inofarm_test', 'fixture_id' => $id, 'base_url' => $baseUrl,
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
$actorIds = array_filter([$fixture['manager_id'], $fixture['worker_id'], $fixture['rival_id'] ?? null]);
$actors = User::whereIn('public_id', $actorIds)->get();
if ($actors->count() !== count($actorIds)) {
    throw new RuntimeException('All synthetic fixture accounts must still exist before verification/cleanup.');
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
if ($mode === 'verify-manager-home' && $fixtureName === 'manager-home-device-fixture.json') {
    $context = $fixture['contexts'][0];
    $greenhouse = DB::table('greenhouses')->where('id', $context['greenhouse_id'])->first();
    $worker = $actors->firstWhere('public_id', $fixture['worker_id']);
    $notes = 'MANAGER HOME '.$fixture['fixture_id'];
    $activity = DB::table('activities')->where('planting_group_id', $context['group_id'])->where('notes', $notes)->sole();
    $expense = DB::table('expenses')->where('greenhouse_id', $greenhouse->id)->where('notes', $notes)->sole();
    if ($greenhouse->running_group_id !== $context['group_id'] || $greenhouse->version !== 2 ||
        $activity->recorded_by !== $worker->id || $activity->manual_ec !== '1.250000' || $activity->manual_ppm !== '625.500000' ||
        $expense->recorded_by !== $manager->id || $expense->amount !== '100.01' || $expense->payment_kind !== 'asset_acquisition' ||
        ! DB::table('audit_logs')->where('entity_id', $greenhouse->id)->where('action', 'designate_period')->where('actor_id', $manager->id)->exists()) {
        throw new RuntimeException('Manager Home SQL reconciliation failed.');
    }
    echo 'PASS: guarded inofarm_test reconciled Android designation/audit, worker pair and exact asset payment.'.PHP_EOL;
    exit(0);
}
if ($mode === 'verify-worker-harvest' && $fixtureName === 'worker-alignment-device-fixture.json') {
    $worker = $actors->firstWhere('public_id', $fixture['worker_id']);
    $harvest = DB::table('harvests')->where('planting_group_id', $groups[0])
        ->where('recorded_by', $worker->id)->where('notes', 'Panen uji perangkat '.$fixture['fixture_id'])->sole();
    $decisions = DB::table('harvest_reviews')->where('harvest_id', $harvest->id)->where('reviewer_id', $manager->id)
        ->orderBy('harvest_version')->get();
    $items = DB::table('harvest_items')->where('harvest_id', $harvest->id)->where('active', true)->get()->keyBy('quality');
    if ($harvest->version !== 2 || $harvest->recorded_by_role !== 'worker' ||
        $decisions->pluck('harvest_version')->all() !== [1, 2] ||
        $decisions->pluck('decision')->all() !== ['invalid', 'valid'] ||
        $items->count() !== 2 || $items['saleable']->weight_kg !== '9.250000' || $items['defective']->weight_kg !== '3.250000' ||
        $items->pluck('sold_weight_kg')->unique()->values()->all() !== ['0.000000']) {
        throw new RuntimeException('Worker harvest SQL reconciliation failed.');
    }
    foreach (['audit_logs', 'sync_operations', 'change_feed'] as $table) {
        if (DB::table($table)->where('entity_type', 'harvest_review')->whereIn('entity_id', $decisions->pluck('id'))->count() !== 2) {
            throw new RuntimeException('Review ledger reconciliation failed.');
        }
    }
    echo 'PASS: guarded MySQL reconciled two grades, two revisions, two decisions, audit, receipts, feed and sold weight.'.PHP_EOL;
    exit(0);
}
if ($mode === 'update-worker-motion-target' && $fixtureName === 'worker-alignment-device-fixture.json') {
    $group = DB::table('planting_groups')->where('id', $groups[0])->sole();
    $operation = ['schema_version' => '2', 'sync_epoch' => DB::table('sync_state')->where('id', 1)->value('epoch'),
        'operation_id' => (string) Str::uuid(), 'entity_id' => $group->id, 'entity_type' => 'planting_groups',
        'action' => 'update', 'base_version' => $group->version, 'payload' => [
            'name' => $group->name, 'variety_id' => $group->variety_id, 'population' => $group->population,
            'sowing_date' => $group->sowing_date, 'transplant_date' => $group->transplant_date, 'active' => (bool) $group->active,
            'reason' => 'Uji transisi target pada emulator',
            'harvest_target' => ['fruit_count' => 30, 'weight_kg' => '30', 'basis' => 'Target sintetis untuk uji transisi'],
        ]];
    app(MasterService::class)->mutate($manager, 'planting_groups', $operation);
    echo 'PASS: target synthetic worker-motion fixture updated through the ledger.'.PHP_EOL;
    exit(0);
}
if ($mode === 'race-worker-review' && $fixtureName === 'worker-alignment-device-fixture.json') {
    $context = $fixture['contexts'][0];
    $worker = $actors->firstWhere('public_id', $fixture['worker_id']);
    $raceNote = 'Race worker review '.$fixture['fixture_id'];
    if (DB::table('harvests')->where('planting_group_id', $groups[0])->where('notes', $raceNote)->exists()) {
        throw new RuntimeException('Race already initialized for this synthetic fixture.');
    }
    $epoch = DB::table('sync_state')->where('id', 1)->value('epoch');
    $op = fn ($kind, $payload) => ['schema_version' => '2', 'sync_epoch' => $epoch, 'operation_id' => (string) Str::uuid(),
        'entity_id' => (string) Str::uuid(), 'entity_type' => $kind, 'action' => 'create', 'base_version' => null, 'payload' => $payload];
    $rival = $actors->firstWhere('public_id', $fixture['rival_id'] ?? null);
    if ($rival === null) {
        $rival = DB::transaction(function () use ($fixture) {
            DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
            $id = (string) Str::uuid();
            app(EntityIdentity::class)->claim('users', $id);

            return User::create(['public_id' => $id, 'name' => 'Pengelola kedua sintetis', 'role' => 'worker',
                'email' => 'device-rival-'.$fixture['fixture_id'].'@example.invalid', 'password' => Str::random(40)]);
        });
        $fixture['rival_id'] = $rival->public_id;
        file_put_contents($path, json_encode($fixture, JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR));
    }
    if (! DB::table('greenhouse_assignments')->where('user_id', $rival->id)->where('greenhouse_id', $context['greenhouse_id'])->exists()) {
        $rival->update(['role' => 'worker']);
        $assignment = $op('greenhouse_assignments', ['greenhouse_id' => $context['greenhouse_id'], 'user_id' => $rival->public_id,
            'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);
        app(MasterService::class)->mutate($manager, 'greenhouse_assignments', $assignment);
    }
    $rival->update(['role' => 'manager']);
    $line = (string) Str::uuid();
    $harvest = $op('harvest', ['planting_group_id' => $groups[0], 'occurred_at' => '2026-11-10T08:00:00+07:00',
        'notes' => $raceNote,
        'items' => [['id' => $line, 'grade_id' => $context['grade_ids'][0], 'fruit_count' => 3, 'weight_kg' => '5', 'quality' => 'saleable']]]);
    app(HarvestService::class)->mutate($worker, $harvest);
    $sale = fn () => $op('sale', ['season_id' => $context['season_id'], 'paid_at' => '2026-11-10T09:00:00+07:00',
        'payment_status' => 'paid', 'customer_name' => '', 'notes' => 'Penjualan sintetis untuk regresi review',
        'items' => [['id' => (string) Str::uuid(), 'harvest_item_id' => $line, 'quantity_kg' => '1', 'price_per_kg' => '10000']]]);
    $initial = app(SaleService::class)->mutate($manager, $sale());
    $dir = dirname(__DIR__).'/tmp/worker-review-race-'.Str::uuid();
    mkdir($dir, 0700);
    $files = [$dir.'/go'];
    $processes = [];
    $operations = [];
    try {
        foreach ([$manager, $rival] as $i => $reviewer) {
            $operations[] = $decision = $op('harvest_review', ['harvest_id' => $harvest['entity_id'], 'harvest_version' => 1,
                'decision' => 'invalid', 'reason' => 'Koreksi hasil uji simultan.']);
            $input = "$dir/input-$i.json";
            $ready = "$dir/ready-$i";
            $result = "$dir/result-$i.json";
            $error = "$dir/error-$i.txt";
            array_push($files, $input, $ready, $result, $error);
            file_put_contents($input, json_encode(['actor_id' => $reviewer->id, 'operation' => $decision], JSON_THROW_ON_ERROR));
            $process = proc_open([PHP_BINARY, dirname(__DIR__).'/backend/tests/Support/finance-race-worker.php', $input, $ready, "$dir/go", $result],
                [['pipe', 'r'], ['file', $error, 'a'], ['file', $error, 'a']], $pipes, dirname(__DIR__).'/backend');
            if (! is_resource($process)) {
                throw new RuntimeException('Could not start guarded review worker.');
            }
            fclose($pipes[0]);
            $processes[] = $process;
        }
        $wait = static function (array $paths) {
            $deadline = microtime(true) + 20;
            do {
                clearstatcache();
                if (count(array_filter($paths, is_file(...))) === count($paths)) {
                    return;
                }
                usleep(20000);
            } while (microtime(true) < $deadline);
            throw new RuntimeException('Worker review race timed out.');
        };
        $wait(["$dir/ready-0", "$dir/ready-1"]);
        file_put_contents("$dir/go", 'go');
        $wait(["$dir/result-0.json", "$dir/result-1.json"]);
        $results = array_map(fn ($i) => json_decode(file_get_contents("$dir/result-$i.json"), true, flags: JSON_THROW_ON_ERROR), [0, 1]);
        $statuses = array_column($results, 'status');
        sort($statuses);
        if ($statuses !== ['accepted', 'harvest_already_reviewed'] ||
            count(array_unique(array_column($results, 'pid'))) !== 2 || count(array_unique(array_column($results, 'connection_id'))) !== 2 ||
            DB::table('harvest_items')->where('id', $line)->value('sold_weight_kg') !== '1.000000' ||
            DB::table('sales')->where('id', $initial['body']['entity_id'])->value('total_amount') !== '10000.00') {
            throw new RuntimeException('Review race or preserved sale balance check failed: '.json_encode($results));
        }
        foreach (['audit_logs', 'sync_operations', 'change_feed'] as $table) {
            if (DB::table($table)->where('entity_type', 'harvest_review')->whereIn('entity_id', array_column($operations, 'entity_id'))->count() !== 1) {
                throw new RuntimeException('Competing review ledger did not reconcile.');
            }
        }
        app(SaleService::class)->mutate($manager, $sale());
        if (DB::table('harvest_items')->where('id', $line)->value('sold_weight_kg') !== '2.000000') {
            throw new RuntimeException('Review changed sale eligibility.');
        }
        echo 'PASS: two managers on separate MySQL connections, one review accepted, one stale; pending and invalid harvest sale eligibility unchanged.'.PHP_EOL;
    } finally {
        foreach ($processes as $process) {
            if (proc_get_status($process)['running']) {
                proc_terminate($process);
            }
            proc_close($process);
        }
        foreach ($files as $file) {
            if (is_file($file)) {
                unlink($file);
            }
        }
        rmdir($dir);
    }
    exit(0);
}
if (in_array($mode, ['verify', 'verify-measurements'], true)) {
    $activities = DB::table('activities')->whereIn('planting_group_id', $groups)->limit(10)->get();
    if ($mode === 'verify-measurements' && ($activities->count() !== 1 || $activities[0]->activity_type !== 'measurement' || $activities[0]->manual_ec !== '1.250000' || $activities[0]->manual_ppm !== '625.500000')) {
        throw new RuntimeException('Manual EC/PPM SQL reconciliation failed.');
    }
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
    DB::table('harvest_reviews')->whereIn('harvest_id', $harvests)->whereIn('reviewer_id', $actors->pluck('id'))->delete();
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
