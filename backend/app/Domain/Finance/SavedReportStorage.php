<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\CanonicalJson;
use App\Domain\Contracts\DomainFailure;

/** Callers authorize current report scope before accessing this immutable representation. */
final class SavedReportStorage
{
    public function full(object $row): array
    {
        $body = json_decode($row->content, true, flags: JSON_THROW_ON_ERROR);
        if (! hash_equals($row->snapshot_hash, CanonicalJson::hash($body)) || ($body['id'] ?? null) !== $row->id || ($body['greenhouse_id'] ?? null) !== $row->greenhouse_id || ($body['season_id'] ?? null) !== $row->season_id
            || ($body['scenario_id'] ?? null) !== ($row->scenario_id ?? null) || ($body['kind'] ?? null) !== $row->kind || ($body['version'] ?? null) !== $row->version || ($body['sources_hash'] ?? null) !== $row->sources_hash
            || count($body['sources'] ?? []) !== $row->source_count || ! hash_equals($row->sources_hash, CanonicalJson::hash(['scope' => $body['scope'] ?? null, 'sources' => $body['sources'] ?? null]))) {
            throw new DomainFailure('report_snapshot_integrity', 'Integritas laporan tersimpan perlu diperiksa; hasil tidak dihitung ulang.', 500);
        }

        return $body;
    }

    public function publicRow(object $row): array
    {
        $body = $this->full($row);
        unset($body['sources']);

        return [...$body, 'snapshot_hash' => $row->snapshot_hash];
    }
}
