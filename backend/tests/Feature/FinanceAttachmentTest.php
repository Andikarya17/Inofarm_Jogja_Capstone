<?php

namespace Tests\Feature;

use Illuminate\Database\Events\QueryExecuted;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use RuntimeException;
use Tests\FinanceTestCase;

class FinanceAttachmentTest extends FinanceTestCase
{
    private const PNG = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j0ioAAAAASUVORK5CYII=';

    protected function setUp(): void
    {
        parent::setUp();
        Storage::fake('finance_private');
        config(['finance.attachments' => ['max_bytes' => null, 'max_files' => null, 'allowed_mime_types' => null]]);
    }

    private function configured(int $files = 2, int $bytes = 32768, array $mimes = ['image/png', 'application/pdf']): void
    {
        config(['finance.attachments' => ['max_bytes' => $bytes, 'max_files' => $files, 'allowed_mime_types' => $mimes]]);
    }

    private function prepared(): array
    {
        $manager = $this->account();$ctx = $this->context($manager);
        $category = $this->createMaster('expense_categories', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Kategori lampiran sintetis']);
        $expense = $this->operation('expense', ['greenhouse_id' => $ctx['gh']['id'], 'category_id' => $category['id'], 'amount' => '100.01', 'paid_at' => '2026-10-30T08:00:00+07:00', 'payment_kind' => 'operating', 'notes' => 'Pembayaran privat sintetis']);
        $this->postJson('/api/v2/expenses', $expense)->assertCreated();
        $worker = $this->account('worker');
        $this->createMaster('greenhouse_assignments', ['greenhouse_id' => $ctx['gh']['id'], 'user_id' => $worker->public_id,
            'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);
        $payload = ['planting_group_id' => $ctx['group']['id'], 'activity_type' => 'general', 'occurred_at' => '2026-10-30T08:00:00+07:00', 'work_state' => 'completed', 'materials' => [], 'notes' => 'Foto kegiatan sintetis'];
        $activity = $this->operation('activity', $payload);
        $this->asUser($worker)->postJson('/api/v2/activities', $activity)->assertCreated();
        $this->asUser($manager);

        return [...$ctx, 'manager' => $manager, 'worker' => $worker, 'expense' => $expense['entity_id'], 'activity' => $activity];
    }

    private function png(): string {return base64_decode(self::PNG, true);}
    private function op(array $ctx, string $kind = 'expense', ?string $bytes = null, int $ownerVersion = 1): array
    {
        $bytes ??= $this->png();

        return $this->operation($kind.'_attachment', ['owner_type' => $kind, 'owner_id' => match ($kind) {'expense' => $ctx['expense'], 'activity' => $ctx['activity']['entity_id'], 'harvest' => $ctx['harvest']}, 'owner_version' => $ownerVersion,
            'original_name' => 'Bukti — é.'.(str_starts_with($bytes, '%PDF-') ? 'pdf' : 'png'), 'mime' => str_starts_with($bytes, '%PDF-') ? 'application/pdf' : 'image/png', 'bytes' => strlen($bytes), 'sha256' => hash('sha256', $bytes)]);
    }
    private function upload(array $op, ?string $bytes = null, array $extra = [])
    {
        return $this->post('/api/v2/attachments', [...['operation' => json_encode($op, JSON_THROW_ON_ERROR), 'file' => UploadedFile::fake()->createWithContent('untrusted-name.bin', $bytes ?? $this->png())], ...$extra], ['Accept' => 'application/json']);
    }

    public function test_unset_production_policy_stays_unavailable_then_private_receipt_and_binary_are_scoped(): void
    {
        $ctx = $this->prepared();$op = $this->op($ctx);
        $this->getJson('/api/v2/attachments/policy?owner_type=expense&owner_id='.$ctx['expense'])->assertOk()->assertJsonPath('data.configured', false)->assertJsonPath('data.max_bytes', null)->assertJsonPath('data.max_files', null);
        $this->upload($op)->assertConflict()->assertJsonPath('code', 'attachment_configuration_missing');
        $this->assertDatabaseCount('record_attachments', 0);$this->assertDatabaseMissing('entity_identities', ['entity_id' => $op['entity_id']]);$this->assertSame([], Storage::disk('finance_private')->allFiles());
        $this->configured();
        $receipt = $this->upload($op)->assertCreated()->assertJsonPath('data.recorded_by', $ctx['manager']->public_id)->assertJsonPath('data.original_name', 'Bukti — é.png')->json();
        $this->upload($op)->assertCreated()->assertExactJson($receipt);
        $this->getJson('/api/v2/attachments/'.$op['entity_id'])->assertExactJson($receipt['data']);
        $download = $this->get('/api/v2/attachments/'.$op['entity_id'].'/content')->assertOk()->assertHeader('X-Content-Type-Options', 'nosniff')->assertHeader('Content-Type', 'image/png')->assertContent($this->png());
        $this->assertStringContainsString('attachment;', $download->headers->get('Content-Disposition'));
        $this->assertStringContainsString('no-store', $download->headers->get('Cache-Control'));
        $this->assertArrayNotHasKey('path', $receipt['data']);$this->assertArrayNotHasKey('url', $receipt['data']);
        $this->assertSame(1, count(Storage::disk('finance_private')->allFiles()));
        $this->getJson('/api/v2/attachments?owner_type=expense&owner_id='.$ctx['expense'])->assertJsonCount(1, 'data');
        $this->getJson('/api/v2/attachments/'.$op['entity_id'].'/audit')->assertJsonPath('data.0.changes.after.sha256', $op['payload']['sha256']);
    }

    public function test_worker_and_foreign_scope_cannot_get_financial_file_list_detail_content_audit_replay_or_feed(): void
    {
        $ctx = $this->prepared();$this->configured();$op = $this->op($ctx);$this->upload($op)->assertCreated();
        $this->asUser($ctx['worker']);
        foreach (['', '/policy'] as $suffix) {$this->getJson('/api/v2/attachments'.$suffix.'?owner_type=expense&owner_id='.$ctx['expense'])->assertForbidden();}
        foreach (['', '/content', '/audit'] as $suffix) {$this->getJson('/api/v2/attachments/'.$op['entity_id'].$suffix)->assertNotFound();}
        $this->upload($op)->assertForbidden();
        $feed = $this->getJson('/api/v2/sync/changes')->assertOk()->json('data');
        $this->assertEmpty(array_filter($feed, fn ($row) => $row['entity_type'] === 'expense_attachment'));
        $this->context($this->account());
        $this->getJson('/api/v2/attachments?owner_type=expense&owner_id='.$ctx['expense'])->assertNotFound();
        foreach (['', '/content', '/audit'] as $suffix) {$this->getJson('/api/v2/attachments/'.$op['entity_id'].$suffix)->assertNotFound();}
        $this->upload($op)->assertNotFound();
        DB::table('greenhouse_assignments')->where('user_id', $ctx['manager']->id)->update(['active' => false]);
        $this->asUser($ctx['manager'])->getJson('/api/v2/attachments/'.$op['entity_id'].'/content')->assertNotFound();
    }

    public function test_worker_can_upload_own_operational_photo_but_other_worker_only_reads_and_manager_cancels(): void
    {
        $ctx = $this->prepared();$this->configured();$op = $this->op($ctx, 'activity');
        $created = $this->asUser($ctx['worker'])->upload($op)->assertCreated()->assertJsonPath('data.recorded_by', $ctx['worker']->public_id)->json();
        $other = $this->account('worker');$this->asUser($ctx['manager']);
        $this->createMaster('greenhouse_assignments', ['greenhouse_id' => $ctx['gh']['id'], 'user_id' => $other->public_id, 'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);
        $this->asUser($other)->get('/api/v2/attachments/'.$op['entity_id'].'/content')->assertOk()->assertContent($this->png());
        $this->upload($this->op($ctx, 'activity'))->assertForbidden();
        $cancel = $this->operation('activity_attachment', ['reason' => 'Foto salah sintetis'], 'cancel', $op['entity_id'], 1);
        $this->asUser($ctx['worker'])->postJson('/api/v2/attachments/'.$op['entity_id'].'/cancel', $cancel)->assertForbidden();
        $this->asUser($ctx['manager'])->postJson('/api/v2/attachments/'.$op['entity_id'].'/cancel', $cancel)->assertOk()->assertJsonPath('data.record_state', 'cancelled');
        $this->asUser($ctx['worker'])->upload($op)->assertCreated()->assertExactJson($created);
        $this->get('/api/v2/attachments/'.$op['entity_id'].'/content')->assertOk();
    }

    public function test_parent_version_closed_state_and_changed_policy_do_not_rewrite_accepted_receipt(): void
    {
        $ctx = $this->prepared();$this->configured();$op = $this->op($ctx, 'activity');
        $activity = $ctx['activity'];
        $this->asUser($ctx['worker'])->patchJson('/api/v2/activities/'.$activity['entity_id'], $this->operation('activity', [...$activity['payload'], 'reason' => 'Koreksi sebelum foto', 'reference_policy' => 'preserve'], 'update', $activity['entity_id'], 1))->assertOk();
        $this->upload($op)->assertConflict()->assertJsonPath('code', 'parent_version_conflict')->assertJsonPath('current_version', 2);
        $this->assertDatabaseCount('record_attachments', 0);$this->assertSame([], Storage::disk('finance_private')->allFiles());
        $op = $this->op($ctx, 'activity', ownerVersion: 2);$receipt = $this->upload($op)->assertCreated()->json();
        $this->asUser($ctx['manager'])->postJson('/api/v2/seasons/'.$ctx['season']['id'].'/close', $this->operation('seasons', ['reason' => 'Tutup sintetis'], 'close', $ctx['season']['id'], 1))->assertOk();
        config(['finance.attachments' => ['max_bytes' => null, 'max_files' => null, 'allowed_mime_types' => null]]);
        $this->asUser($ctx['worker'])->upload($op)->assertCreated()->assertExactJson($receipt);
        $this->upload($op, $this->png().'changed')->assertUnprocessable();
        $this->upload($this->op($ctx, 'activity', ownerVersion: 2))->assertConflict()->assertJsonPath('code', 'season_closed');
    }

    public function test_harvest_photo_has_scoped_operational_feed_and_immutable_owner_reference(): void
    {
        $ctx = $this->prepared();$this->configured();
        $grade = $this->createMaster('grades', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Grade foto sintetis']);
        $harvest = $this->operation('harvest', ['planting_group_id' => $ctx['group']['id'], 'occurred_at' => '2026-10-30T08:00:00+07:00', 'notes' => '',
            'items' => [['id' => (string) Str::uuid(), 'grade_id' => $grade['id'], 'fruit_count' => 1, 'weight_kg' => '1']]]);
        $this->asUser($ctx['worker'])->postJson('/api/v2/harvests', $harvest)->assertCreated();$ctx['harvest'] = $harvest['entity_id'];
        $op = $this->op($ctx, 'harvest');$this->upload($op)->assertCreated()->assertJsonPath('data.owner_id', $harvest['entity_id'])->assertJsonPath('data.owner_version', 1);
        $this->get('/api/v2/attachments/'.$op['entity_id'].'/content')->assertOk();
        $feed = $this->getJson('/api/v2/sync/changes')->assertOk()->json('data');
        $this->assertCount(1, array_filter($feed, fn ($row) => $row['entity_type'] === 'harvest_attachment'));
        $this->assertDatabaseHas('entity_identities', ['entity_id' => $op['entity_id'], 'entity_type' => 'harvest_attachment']);
        $this->patchJson('/api/v2/attachments/'.$op['entity_id'], $op)->assertStatus(405);
    }

    public function test_multipart_metadata_and_transport_bounds_do_not_weaken_json_limit(): void
    {
        $ctx = $this->prepared();$this->configured();
        $this->post('/api/v2/attachments', ['operation' => str_repeat('x', 16385), 'file' => UploadedFile::fake()->createWithContent('test.png', $this->png())], ['Accept' => 'application/json'])->assertUnprocessable();
        $this->withHeader('Content-Length', '33619969')->upload($this->op($ctx))->assertStatus(413);$this->withoutHeader('Content-Length');
        $this->postJson('/api/v2/attachments', ['junk' => str_repeat('x', 16385)])->assertStatus(413);
        $this->assertDatabaseCount('record_attachments', 0);
    }

    public function test_content_identity_mime_filename_bounds_and_unknown_fields_are_not_trusted(): void
    {
        $ctx = $this->prepared();$this->configured();
        foreach ([['sha256' => str_repeat('0', 64)], ['bytes' => '68'], ['owner_version' => '1'], ['mime' => 'application/pdf'], ['original_name' => 'disguised.php'], ['original_name' => 'wrong.pdf'], ['original_name' => 'missing-extension'], ['original_name' => '../.env'], ['original_name' => "x\r\nInjected: y"]] as $bad) {
            $op = $this->op($ctx);$op['payload'] = [...$op['payload'], ...$bad];$this->upload($op)->assertUnprocessable();
        }
        $this->upload($this->op($ctx), extra: ['unexpected' => 'not permitted'])->assertUnprocessable();
        $bad = $this->op($ctx);$bad['entity_type'] = 'activity_attachment';$this->upload($bad)->assertUnprocessable();
        $this->configured(bytes: 10);$this->upload($this->op($ctx))->assertUnprocessable();
        $this->configured(mimes: ['application/pdf']);$this->upload($this->op($ctx))->assertUnprocessable();
        $this->assertDatabaseCount('record_attachments', 0);$this->assertSame([], Storage::disk('finance_private')->allFiles());
        $this->configured();$pdf = "%PDF-1.4\n".str_repeat('% synthetic evidence only\n', 900)."\n%%EOF";
        $this->upload($this->op($ctx, bytes: $pdf), $pdf)->assertCreated(); // Binary exceeds ordinary16KiB JSON limit.
    }

    public function test_count_limit_retry_cancel_history_and_shared_blob_do_not_duplicate_or_delete_evidence(): void
    {
        $ctx = $this->prepared();$this->configured(files: 1);$a = $this->op($ctx);$b = $this->op($ctx);
        $this->upload($a)->assertCreated();$this->upload($b)->assertConflict()->assertJsonPath('code', 'attachment_limit_reached');
        $cancel = $this->operation('expense_attachment', ['reason' => 'Bukti salah sintetis'], 'cancel', $a['entity_id'], 1);
        $this->postJson('/api/v2/attachments/'.$a['entity_id'].'/cancel', $cancel)->assertOk();
        $this->upload($b)->assertCreated();$this->assertDatabaseCount('record_attachments', 2);
        $this->assertSame(1, count(Storage::disk('finance_private')->allFiles()));
        $this->get('/api/v2/attachments/'.$a['entity_id'].'/content')->assertOk();
        $this->getJson('/api/v2/attachments?owner_type=expense&owner_id='.$ctx['expense'].'&limit=1')->assertJsonCount(1, 'data');
        $this->getJson('/api/v2/attachments?owner_type=expense&owner_id='.$ctx['expense'].'&record_state=active')->assertJsonCount(1, 'data');
        $this->assertDatabaseHas('entity_identities', ['entity_id' => $a['entity_id'], 'entity_type' => 'expense_attachment']);
    }

    public function test_post_receipt_fault_rolls_back_sql_and_reservation_while_private_orphan_is_not_public(): void
    {
        $ctx = $this->prepared();$this->configured();$op = $this->op($ctx);$injected = false;
        DB::listen(function (QueryExecuted $event) use (&$injected) {if (! $injected && str_starts_with($event->sql, 'insert into `sync_operations`')) {$injected = true;throw new RuntimeException('Synthetic evidence post-receipt fault');}});
        $this->upload($op)->assertStatus(500);$this->assertTrue($injected);
        $this->assertDatabaseCount('record_attachments', 0);$this->assertDatabaseMissing('entity_identities', ['entity_id' => $op['entity_id']]);
        $this->assertSame(0, DB::table('audit_logs')->where('entity_id', $op['entity_id'])->count());$this->assertSame(0, DB::table('sync_operations')->where('entity_id', $op['entity_id'])->count());
        $this->get('/api/v2/attachments/'.$op['entity_id'].'/content')->assertNotFound();
        $this->assertSame(1, count(Storage::disk('finance_private')->allFiles()));
        $this->upload($op)->assertCreated();$this->assertSame(1, count(Storage::disk('finance_private')->allFiles()));
    }

    public function test_corrupt_blob_is_not_served_and_public_storage_root_is_refused(): void
    {
        $ctx = $this->prepared();$this->configured();$op = $this->op($ctx);$this->upload($op)->assertCreated();
        $path = 'blobs/'.substr($op['payload']['sha256'], 0, 2).'/'.$op['payload']['sha256'];Storage::disk('finance_private')->put($path, 'Synthetic corruption');
        $this->getJson('/api/v2/attachments/'.$op['entity_id'].'/content')->assertStatus(500)->assertJsonPath('code', 'attachment_integrity_failed');
        config(['filesystems.disks.finance_private.root' => public_path()]);Storage::forgetDisk('finance_private');
        $this->upload($this->op($ctx))->assertConflict()->assertJsonPath('code', 'private_storage_misconfigured');
    }
}
