<?php

use App\Domain\Activities\ActivityService;
use App\Domain\Attachments\AttachmentService;
use App\Domain\Cultivation\MasterService;
use App\Domain\Recovery\BackupService;
use App\Domain\Recovery\RecoveryPackage;
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
set_exception_handler(function (Throwable $e) {
    fwrite(STDERR, 'Recovery fixture failed: '.$e->getMessage().PHP_EOL);
    exit(1);
});
$root = dirname(__DIR__);
$path = $root.'/tmp/finance-recovery-fixture.json';
$mode = $argv[1] ?? 'create';
$parent = function (string $action) use ($root) {
    $p = proc_open([PHP_BINARY, $root.'/scripts/finance-report-export-fixture.php', $action], [0 => ['pipe', 'r'], 1 => ['pipe', 'w'], 2 => ['pipe', 'w']], $pipes);
    if (! is_resource($p)) {
        throw new RuntimeException('Parent fixture tool unavailable');
    }fclose($pipes[0]);
    $out = stream_get_contents($pipes[1]);
    $err = stream_get_contents($pipes[2]);
    fclose($pipes[1]);
    fclose($pipes[2]);
    if (proc_close($p) !== 0) {
        throw new RuntimeException('Parent fixture failed: '.$err);
    }

    return $out;
};
$save = fn ($f) => file_put_contents($path, json_encode($f, JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR), LOCK_EX);
if ($mode === 'create') {
    if (is_file($path)) {
        throw new RuntimeException('Existing recovery fixture preserved');
    }
    $nonce = getenv('FINANCE_RECOVERY_DRILL_ID');
    if (! Str::isUuid($nonce)) {
        throw new RuntimeException('Explicit drill identity required');
    }
    $parentOutput = $parent('create');
    if (! is_file($root.'/tmp/finance-report-export-fixture.json')) {
        file_put_contents($root.'/tmp/fin15-parent-failure.txt', $parentOutput);
        throw new RuntimeException('Parent fixture did not produce its manifest; inspect private fin15-parent-failure.txt');
    }
    $f = json_decode(file_get_contents($root.'/tmp/finance-report-export-fixture.json'), true, flags: JSON_THROW_ON_ERROR);
    $f['drill_id'] = $nonce;
    $f['base_url'] = 'http://127.0.0.1:8001/api/v2/';
    $save($f);
    $actor = User::where('public_id', $f['manager_id'])->firstOrFail();
    $epoch = DB::table('sync_state')->where('id', 1)->value('epoch');
    $op = fn ($kind, $payload) => ['schema_version' => '2', 'sync_epoch' => $epoch, 'operation_id' => (string) Str::uuid(), 'entity_id' => (string) Str::uuid(), 'entity_type' => $kind, 'action' => 'create', 'base_version' => null, 'payload' => $payload];
    $variety = app(MasterService::class)->mutate($actor, 'varieties', $op('varieties', ['greenhouse_id' => $f['greenhouse_id'], 'name' => 'Varietas drill '.$nonce]))['body']['data'];
    $group = app(MasterService::class)->mutate($actor, 'planting_groups', $op('planting_groups', ['season_id' => $f['season_id'], 'variety_id' => $variety['id'], 'name' => 'Kelompok drill', 'sowing_date' => '2026-01-01', 'transplant_date' => '2026-01-10', 'population' => 10]))['body']['data'];
    $f += ['variety_id' => $variety['id'], 'group_id' => $group['id'], 'old_epoch' => $epoch, 'payload' => ['planting_group_id' => $group['id'], 'activity_type' => 'general', 'occurred_at' => '2026-11-02T08:00:00+07:00', 'work_state' => 'completed', 'materials' => [], 'notes' => 'Antrean recovery sintetis']];
    $save($f);
    echo "Owned recovery fixture created only in inofarm_test.\n";
    exit;
}
$f = json_decode(file_get_contents($path), true, flags: JSON_THROW_ON_ERROR);
$manager = User::where('public_id', $f['manager_id'])->firstOrFail();
$worker = User::where('public_id', $f['worker_id'])->firstOrFail();
if (! str_ends_with($manager->email, $f['fixture_id'].'@example.invalid') || ! DB::table('greenhouses')->where('id', $f['greenhouse_id'])->where('created_by', $manager->id)->exists()) {
    throw new RuntimeException('Recovery fixture ownership mismatch');
}
if ($mode === 'seed') {
    $op = ['schema_version' => '2', 'sync_epoch' => $f['old_epoch'], 'operation_id' => (string) Str::uuid(), 'entity_id' => (string) Str::uuid(), 'entity_type' => 'activity', 'action' => 'create', 'base_version' => null, 'payload' => $f['payload']];
    app(ActivityService::class)->mutate($worker, $op);
    $f['accepted_operation'] = $op;
    $save($f);
    echo "Synthetic accepted-before-backup operation created.\n";
    exit;
}
if ($mode === 'backup') {
    $proofPath = $root.'/tmp/finance-recovery-device-before.json';
    if (is_file($proofPath)) {
        $proof = json_decode(file_get_contents($proofPath), true, flags: JSON_THROW_ON_ERROR);
        if ($proof['fixture_id'] !== $f['fixture_id']) {
            throw new RuntimeException('Device fixture mismatch');
        }$f['accepted_operation'] = $proof['accepted_operation'];
        $f['missing_operation'] = $proof['missing_operation'];
    }
    $accepted = $f['accepted_operation'];
    $activity = DB::table('activities')->where('id', $accepted['entity_id'])->where('planting_group_id', $f['group_id'])->where('recorded_by', $worker->id)->first() ?? throw new RuntimeException('Expected accepted device operation before backup');
    $png = base64_decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j7p8AAAAASUVORK5CYII=', true);
    $file = $root.'/.runtime/recovery-drills/'.$f['drill_id'].'/source.png';
    file_put_contents($file, $png);
    config(['finance.attachments' => ['max_bytes' => 32768, 'max_files' => 2, 'allowed_mime_types' => ['image/png']], 'recovery.operators' => [$manager->public_id]]);
    $metadata = ['path' => $file, 'mime' => 'image/png', 'bytes' => strlen($png), 'sha256' => hash('sha256', $png)];
    $attachment = ['schema_version' => '2', 'sync_epoch' => $f['old_epoch'], 'operation_id' => (string) Str::uuid(), 'entity_id' => (string) Str::uuid(), 'entity_type' => 'activity_attachment', 'action' => 'create', 'base_version' => null, 'payload' => ['owner_type' => 'activity', 'owner_id' => $activity->id, 'owner_version' => 1, 'original_name' => 'drill.png', ...array_diff_key($metadata, ['path' => true])]];
    app(AttachmentService::class)->mutate($worker, $attachment, $metadata);
    $f['attachment_id'] = $attachment['entity_id'];
    $f['blob_sha256'] = $metadata['sha256'];
    $id = (string) Str::uuid();
    $service = app(BackupService::class);
    DB::transaction(function () use ($service, $manager, $id) {
        DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
        $service->request($manager, ['request_id' => $id, 'reason' => 'U19 synthetic isolated recovery drill']);
    });
    DB::transaction(function () use ($service, $manager, $id) {
        DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
        $service->capture($manager, $id);
    });
    $row = DB::table('backup_runs')->where('id', $id)->first();
    $f['backup_id'] = $id;
    $f['manifest_hash'] = $row->manifest_hash;
    $save($f);
    app(RecoveryPackage::class)->validate($id, $row->manifest_hash);
    echo "Backup + signed manifest + private PNG preflight PASS; restore not yet verified.\n";
    exit;
}
if ($mode === 'verify-source') {
    if (DB::table('sync_state')->where('id', 1)->value('epoch') !== $f['old_epoch']) {
        throw new RuntimeException('Source epoch was changed by isolated restore');
    }
    if (DB::table('activities')->where('id', $f['accepted_operation']['entity_id'])->value('version') !== 1 || DB::table('sync_operations')->where('operation_id', $f['accepted_operation']['operation_id'])->count() !== 1) {
        throw new RuntimeException('Source operation ledger changed');
    }
    if (isset($f['missing_operation']) && DB::table('sync_operations')->where('operation_id', $f['missing_operation']['operation_id'])->exists()) {
        throw new RuntimeException('Missing operation was replayed to source');
    }
    echo "PASS: original source epoch/receipt unchanged; no missing-operation replay.\n";
    exit;
}
if ($mode !== 'cleanup') {
    throw new RuntimeException('Use create|seed|backup|verify-source|cleanup');
}
DB::transaction(function () use ($f, $worker, $manager) {
    DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
    $ids = DB::table('activities')->where('planting_group_id', $f['group_id'])->where('recorded_by', $worker->id)->pluck('id');
    $attachments = DB::table('record_attachments')->whereIn('owner_id', $ids)->where('greenhouse_id', $f['greenhouse_id'])->pluck('id');
    DB::table('record_attachments')->whereIn('id', $attachments)->delete();
    foreach (['activity_materials', 'activity_reference_snapshots'] as $table) {
        DB::table($table)->whereIn('activity_id', $ids)->delete();
    }
    DB::table('activities')->whereIn('id', $ids)->delete();
    DB::table('planting_groups')->where('id', $f['group_id'])->delete();
    DB::table('varieties')->where('id', $f['variety_id'])->delete();
    $backups = DB::table('backup_runs')->where('actor_id', $manager->id)->pluck('id');
    DB::table('backup_runs')->whereIn('id', $backups)->delete();
    DB::table('entity_identities')->whereIn('entity_id', [...$ids, ...$attachments, ...$backups, $f['group_id'], $f['variety_id']])->delete();
});
$parent('cleanup');
unlink($path);
echo "Owned source recovery fixture cleaned; packages/drill target retained privately as evidence.\n";
