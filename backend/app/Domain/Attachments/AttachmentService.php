<?php

namespace App\Domain\Attachments;

use App\Domain\Contracts\DomainFailure;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;

final class AttachmentService
{
    public function __construct(private AttachmentOwner $owners, private AttachmentPolicy $policy, private PrivateEvidenceStore $blobs, private MutationLedger $ledger) {}

    public function publicRow(object $row): array
    {
        $data = (array) $row;
        $data['recorded_by'] = User::findOrFail($row->recorded_by)->public_id;
        foreach (['created_at', 'updated_at'] as $key) {
            $data[$key] = CarbonImmutable::parse($data[$key], 'UTC')->toISOString();
        }

        return $data;
    }

    public function authorizedRow(User $actor, string $id): object
    {
        $row = $this->owners->metadataScope($actor)->where('id', $id)->first() ?? abort(404);
        $ctx = $this->owners->get($actor, $row->owner_type, $row->owner_id);
        abort_unless($ctx['greenhouse_id'] === $row->greenhouse_id, 404);

        return $row;
    }

    public function mutate(User $actor, array $op, ?array $file = null): array
    {
        return $this->ledger->run($actor, $op, function (User $current) use ($op) {
            $source = $op['action'] === 'create' ? (object) $op['payload'] : $this->authorizedRow($current, $op['entity_id']);
            $ctx = $this->owners->get($current, $source->owner_type, $source->owner_id);
            abort_unless($op['entity_type'] === $source->owner_type.'_attachment', 422, 'Jenis operasi tidak sesuai catatan pemilik.');
            if ($op['action'] === 'create') {$this->owners->canUpload($current, $ctx);} else {app(GreenhouseAccess::class)->manager($current);}
        }, function (User $current) use ($op, $file) {
            $before = null;
            $p = $op['payload'];
            if ($op['action'] === 'cancel') {
                $row = $this->authorizedRow($current, $op['entity_id']);
                $before = $this->publicRow($row);
                $this->ledger->version($row, $op['base_version'], $before);
                abort_unless($row->record_state === 'active', 422, 'Lampiran telah dibatalkan.');
                $ctx = $this->owners->get($current, $row->owner_type, $row->owner_id, true);
                DB::table('record_attachments')->where('id', $row->id)->update(['record_state' => 'cancelled', 'version' => $row->version + 1, 'updated_at' => now()]);
            } else {
                $ctx = $this->owners->get($current, $p['owner_type'], $p['owner_id'], true);
                $this->owners->canUpload($current, $ctx);
                if ($ctx['row']->version !== $p['owner_version']) {
                    throw new DomainFailure('parent_version_conflict', 'Catatan pemilik telah berubah. Tinjau versi terbaru sebelum mengajukan lampiran kembali.', currentVersion: $ctx['row']->version);
                }
                if ($file === null) {throw new \LogicException('New attachment requires verified binary evidence.');}
                foreach (['bytes', 'mime', 'sha256'] as $key) {
                    if ($file[$key] !== $p[$key]) {
                        throw ValidationException::withMessages(['file' => 'Identitas berkas tidak cocok dengan operasi yang ditinjau.']);
                    }
                }
                $profile = $this->policy->validateNew($file);
                $count = DB::table('record_attachments')->where('owner_type', $p['owner_type'])->where('owner_id', $p['owner_id'])->where('record_state', 'active')->count();
                if ($count >= $profile['max_files']) {
                    throw new DomainFailure('attachment_limit_reached', 'Jumlah lampiran aktif telah mencapai batas server. Tinjau lampiran terkait melalui pengelola.');
                }
                $this->blobs->put($file);
                DB::table('record_attachments')->insert(['id' => $op['entity_id'], 'owner_type' => $p['owner_type'], 'owner_id' => $p['owner_id'], 'owner_version' => $p['owner_version'],
                    'greenhouse_id' => $ctx['greenhouse_id'], 'recorded_by' => $current->id, 'recorded_by_name' => $current->name,
                    'original_name' => $p['original_name'], 'mime' => $file['mime'], 'bytes' => $file['bytes'], 'sha256' => $file['sha256'], 'created_at' => now(), 'updated_at' => now()]);
            }

            return ['before' => $before, 'after' => $this->publicRow(DB::table('record_attachments')->where('id', $op['entity_id'])->first()),
                'greenhouse_id' => $ctx['greenhouse_id'], 'audience' => $ctx['audience']];
        });
    }
}
