<?php

namespace Tests\Feature;

use App\Domain\Contracts\CanonicalJson;
use App\Domain\Recovery\RecoveryPackage;
use Carbon\CarbonImmutable;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;

class FinanceRecoveryTest extends FinanceTestCase
{
    protected function setUp(): void
    {
        parent::setUp();
        Storage::fake('finance_private');
        config(['recovery.operators' => [], 'recovery.root' => base_path('../tmp/fin15-packages/'.Str::uuid())]);
    }

    private function operator(): array
    {
        $actor = $this->account();
        $ctx = $this->context($actor);
        config(['recovery.operators' => [$actor->public_id]]);

        return [$actor, $ctx];
    }

    private function backup(): array
    {
        $input = ['request_id' => (string) Str::uuid(), 'reason' => 'Latihan pemulihan sintetis'];
        $data = $this->postJson('/api/v2/recovery/backups', $input)->assertOk()->assertJsonPath('data.state', 'completed')->assertJsonPath('data.summary.restore_verified', false)->json('data');

        return [$input, $data];
    }

    public function test_capability_is_not_inferred_from_role_assignment_or_client_payload(): void
    {
        $manager = $this->account();
        $this->context($manager);
        $this->getJson('/api/v2/recovery/policy')->assertOk()->assertJsonPath('data.available', false)->assertJsonPath('data.rpo', null)->assertJsonPath('data.production_restore_available', false);
        $this->postJson('/api/v2/recovery/backups', ['request_id' => (string) Str::uuid(), 'reason' => 'test'])->assertForbidden();
        $worker = $this->account('worker');
        $this->asUser($worker);
        config(['recovery.operators' => [$worker->public_id]]);
        $this->getJson('/api/v2/recovery/policy')->assertForbidden();
        $this->getJson('/api/v2/recovery/backups')->assertForbidden();
        $this->postJson('/api/v2/restores', [])->assertNotFound();
        $this->assertDatabaseCount('backup_runs', 0);
    }

    public function test_backup_contains_lossless_rows_and_private_blobs_and_retry_does_not_rebuild(): void
    {
        [$actor, $ctx] = $this->operator();
        // Binary and decimal values are preserved without JSON float conversion.
        $png = base64_decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j7p8AAAAASUVORK5CYII=', true);
        config(['finance.attachments' => ['max_bytes' => 32768, 'max_files' => 2, 'allowed_mime_types' => ['image/png']]]);
        $op = $this->operation('activity', ['planting_group_id' => $ctx['group']['id'], 'activity_type' => 'general', 'occurred_at' => '2026-11-02T08:00:00+07:00', 'work_state' => 'completed', 'materials' => [], 'notes' => "Literal α\n=SUM(1,2)"]);
        $this->postJson('/api/v2/activities', $op)->assertCreated();
        $file = $this->operation('activity_attachment', ['owner_type' => 'activity', 'owner_id' => $op['entity_id'], 'owner_version' => 1, 'original_name' => 'synthetic.png', 'mime' => 'image/png', 'bytes' => strlen($png), 'sha256' => hash('sha256', $png)]);
        $this->post('/api/v2/attachments', ['operation' => json_encode($file), 'file' => UploadedFile::fake()->createWithContent('test.bin', $png)], ['Accept' => 'application/json'])->assertCreated();
        [$input, $data] = $this->backup();
        $package = app(RecoveryPackage::class);
        $m = $package->validate($data['id'], $data['manifest_hash']);
        $this->assertArrayHasKey('users', $m['tables']);
        $this->assertArrayHasKey('tanks', $m['tables']);
        $this->assertSame(1, $m['tables']['activities']['rows']);
        $this->assertCount(1, $m['files']);
        $this->assertSame($png, file_get_contents($package->blobPath($data['id'], hash('sha256', $png))));
        $row = json_decode(file($package->tablePath($data['id'], 'activities'))[0], true);
        $this->assertSame($op['payload']['notes'], base64_decode($row['notes']));
        $this->postJson('/api/v2/recovery/backups', $input)->assertOk()->assertJsonPath('data.manifest_hash', $data['manifest_hash']);
        $this->postJson('/api/v2/recovery/backups', [...$input, 'reason' => 'Different'])->assertConflict();
        $this->assertDatabaseCount('backup_runs', 1);
        $this->postJson('/api/v2/recovery/backups/'.$data['id'].'/validate', ['expected_manifest_hash' => $data['manifest_hash']])->assertOk()->assertJsonPath('data.state', 'package_validated')->assertJsonPath('data.restore_verified', false);
        $this->getJson('/api/v2/recovery/backups?limit=1')->assertOk()->assertJsonCount(1, 'data');
        $this->assertStringNotContainsString('password', json_encode($data));
        $this->assertArrayNotHasKey('path', $data);
        Storage::disk('finance_private')->delete('blobs/'.substr(hash('sha256', $png), 0, 2).'/'.hash('sha256', $png));
        $this->postJson('/api/v2/recovery/backups', ['request_id' => (string) Str::uuid(), 'reason' => 'Missing source blob'])->assertStatus(500)->assertJsonPath('code', 'backup_failed');
        $this->assertSame(1, DB::table('backup_runs')->where('state', 'failed')->count());
    }

    public function test_missing_corrupt_and_unexpected_package_files_fail_closed(): void
    {
        $this->operator();
        [, $data] = $this->backup();
        $packages = app(RecoveryPackage::class);
        $path = $packages->directory($data['id']);
        $endpoint = '/api/v2/recovery/backups/'.$data['id'].'/validate';
        $input = ['expected_manifest_hash' => $data['manifest_hash']];
        $table = $packages->tablePath($data['id'], 'users');
        $original = file_get_contents($table);
        file_put_contents($table, $original.'x');
        $this->postJson($endpoint, $input)->assertConflict()->assertJsonPath('code', 'backup_integrity_failed');
        file_put_contents($table, $original);
        file_put_contents($path.'/unexpected.txt', 'not trusted');
        $this->postJson($endpoint, $input)->assertConflict();
        unlink($path.'/unexpected.txt');
        rename($table, $table.'.held');
        $this->postJson($endpoint, $input)->assertConflict();
        rename($table.'.held', $table);
        file_put_contents($path.'/manifest.hmac', str_repeat('0', 64));
        $this->postJson($endpoint, $input)->assertConflict();
        $this->assertSame('completed', DB::table('backup_runs')->where('id', $data['id'])->value('state'));
    }

    public function test_private_root_is_required_and_does_not_report_success(): void
    {
        $this->operator();
        config(['recovery.root' => public_path('unsafe-recovery')]);
        $this->postJson('/api/v2/recovery/backups', ['request_id' => (string) Str::uuid(), 'reason' => 'invalid root'])->assertStatus(500)->assertJsonPath('code', 'backup_failed');
        $this->assertSame('failed', DB::table('backup_runs')->value('state'));
        config(['recovery.root' => config('filesystems.disks.local.root').'/recovery-not-served']);
        $this->postJson('/api/v2/recovery/backups', ['request_id' => (string) Str::uuid(), 'reason' => 'Signed disk is not a backup root'])->assertStatus(500)->assertJsonPath('code', 'backup_failed');
        $this->assertSame(2, DB::table('backup_runs')->where('state', 'failed')->count());
    }

    public function test_historical_receipt_review_is_authorized_hash_bound_and_never_replays(): void
    {
        [$actor, $ctx] = $this->operator();
        $op = $this->operation('activity', ['planting_group_id' => $ctx['group']['id'], 'activity_type' => 'general', 'occurred_at' => '2026-11-02T08:00:00+07:00', 'work_state' => 'completed', 'materials' => [], 'notes' => 'old epoch']);
        $receipt = $this->postJson('/api/v2/activities', $op)->assertCreated()->json();
        $ledgerCount = DB::table('sync_operations')->count();
        $epoch = (string) Str::uuid();
        DB::table('sync_state')->where('id', 1)->update(['epoch' => $epoch]);
        $this->postJson('/api/v2/activities', $op)->assertConflict()->assertJsonPath('code', 'epoch_mismatch');
        $input = ['sync_epoch' => $epoch, 'operation' => $op];
        $result = $this->postJson('/api/v2/sync/reconcile', $input)->assertOk()->assertJsonPath('state', 'receipt_found')->assertJsonPath('request_hash', CanonicalJson::hash($op))->json();
        $this->assertSame(CanonicalJson::hash($receipt), CanonicalJson::hash($result['receipt']));
        $bad = $op;
        $bad['payload']['notes'] = 'altered';
        $this->postJson('/api/v2/sync/reconcile', ['sync_epoch' => $epoch, 'operation' => $bad])->assertConflict();
        $missing = $op;
        $missing['operation_id'] = (string) Str::uuid();
        $missing['entity_id'] = (string) Str::uuid();
        $this->postJson('/api/v2/sync/reconcile', ['sync_epoch' => $epoch, 'operation' => $missing])->assertOk()->assertJsonPath('state', 'manual_review')->assertJsonPath('receipt', null);
        $this->postJson('/api/v2/sync/reconcile', ['sync_epoch' => (string) Str::uuid(), 'operation' => $op])->assertConflict();
        $bad = $op;
        $bad['payload']['bad_float'] = 1.25;
        $this->postJson('/api/v2/sync/reconcile', ['sync_epoch' => $epoch, 'operation' => $bad])->assertUnprocessable();
        $bad = $op;
        $bad['payload']['planting_group_id'] = [];
        $this->postJson('/api/v2/sync/reconcile', ['sync_epoch' => $epoch, 'operation' => $bad])->assertUnprocessable();
        $this->assertDatabaseCount('activities', 1);
        $this->assertDatabaseCount('sync_operations', $ledgerCount);
        DB::table('greenhouse_assignments')->where('user_id', $actor->id)->update(['active' => false]);
        $this->postJson('/api/v2/sync/reconcile', $input)->assertOk()->assertJsonPath('state', 'receipt_found');
        $this->postJson('/api/v2/sync/reconcile', ['sync_epoch' => $epoch, 'operation' => $missing])->assertOk()->assertJsonPath('state', 'manual_review');
        $this->postJson('/api/v2/sync/reconcile', [...$input, 'actor_id' => 'forged'])->assertUnprocessable();
    }

    public function test_backup_history_pages_by_creation_time_and_rejects_noncanonical_or_forged_requests(): void
    {
        $this->operator();
        $first = 'ffffffff-ffff-4fff-afff-ffffffffffff';
        $second = '00000000-0000-4000-a000-000000000001';
        foreach ([$first, $second] as $index => $id) {
            $this->travelTo(CarbonImmutable::parse('2026-11-02T08:00:00Z')->addSeconds($index));
            $this->postJson('/api/v2/recovery/backups', ['request_id' => $id, 'reason' => 'Paging fixture'])->assertOk();
        }
        $page = $this->getJson('/api/v2/recovery/backups?limit=1')->assertOk()->assertJsonPath('data.0.id', $second)->json();
        $this->getJson('/api/v2/recovery/backups?limit=1&before='.$page['next_cursor'])->assertOk()->assertJsonPath('data.0.id', $first)->assertJsonPath('next_cursor', null);
        $this->postJson('/api/v2/recovery/backups', ['request_id' => strtoupper($first), 'reason' => 'test'])->assertUnprocessable();
        $this->postJson('/api/v2/recovery/backups', ['request_id' => (string) Str::uuid(), 'reason' => ' ', 'operator' => true])->assertUnprocessable();
        $this->getJson('/api/v2/recovery/restores?limit=51')->assertUnprocessable();
    }
}
