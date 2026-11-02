<?php

namespace App\Domain\Sync;

use App\Domain\Contracts\DomainFailure;
use Illuminate\Support\Facades\DB;

/** Caller must first authorize the parent; this class is not an authorization shortcut. */
final class AcceptedSnapshot
{
    /** Bounded batches avoid one ledger query per report source. Caller authorizes every row first. */
    public function readMany(string $kind, array $rows): array
    {
        $result = [];
        foreach (array_chunk($rows, 200) as $batch) {
            $logs = DB::table('audit_logs')->where('entity_type', $kind)->where(function ($query) use ($batch) {
                foreach ($batch as $row) {
                    $query->orWhere(fn ($pair) => $pair->where('entity_id', $row->id)->where('version', $row->version));
                }
            })->get(['entity_id', 'version', 'changes'])->groupBy('entity_id');
            foreach ($batch as $row) {
                $versions = $logs->get($row->id, collect());
                if ($versions->count() !== 1) {
                    throw new DomainFailure('record_snapshot_missing', 'Revisi sumber laporan belum lengkap atau ambigu.', 500);
                }
                $body = json_decode($versions[0]->changes, true, flags: JSON_THROW_ON_ERROR)['after'] ?? null;
                if (! is_array($body) || ($body['id'] ?? null) !== $row->id || ($body['version'] ?? null) !== $row->version) {
                    throw new DomainFailure('record_snapshot_missing', 'Identitas revisi sumber laporan tidak sesuai.', 500);
                }
                $result[$row->id] = $body;
            }
        }

        return $result;
    }

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
