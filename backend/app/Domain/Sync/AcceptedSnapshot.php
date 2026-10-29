<?php

namespace App\Domain\Sync;

use App\Domain\Contracts\DomainFailure;
use Illuminate\Support\Facades\DB;

/** Caller must first authorize the parent; this class is not an authorization shortcut. */
final class AcceptedSnapshot
{
    public function read(string $kind, object $row): array
    {
        $versions = DB::table('audit_logs')->where('entity_type', $kind)->where('entity_id', $row->id)->where('version', $row->version)->limit(2)->get(['changes']);
        if ($versions->count() !== 1) {
            throw new DomainFailure('record_snapshot_missing', 'Revisi catatan belum lengkap atau ambigu.', 500);
        }
        $body = json_decode($versions[0]->changes, true, flags: JSON_THROW_ON_ERROR)['after'] ?? null;
        if (! is_array($body) || ($body['id'] ?? null) !== $row->id || ($body['version'] ?? null) !== $row->version) {
            throw new DomainFailure('record_snapshot_missing', 'Identitas revisi catatan tidak sesuai.', 500);
        }

        return $body;
    }
}
