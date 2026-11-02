<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\DomainFailure;
use App\Domain\Identity\GreenhouseAccess;
use App\Models\User;
use Illuminate\Support\Facades\DB;

final class ReportExportService
{
    public function __construct(private GreenhouseAccess $access, private SavedReportStorage $saved, private ReportDocumentRenderer $renderer) {}

    /** Invoked under ScopedRead before rendering and again immediately before delivery. */
    public function snapshot(User $actor, string $id, string $expectedHash): array
    {
        $this->access->manager($actor);
        $row = DB::table('report_snapshots')->where('id', $id)->whereIn('kind', ['season_report', 'investment_report'])->whereIn('greenhouse_id', $this->access->ids($actor))->first() ?? abort(404);
        if (! hash_equals($row->snapshot_hash, $expectedHash)) {
            throw new DomainFailure('export_snapshot_changed', 'Buka dan tinjau laporan tersimpan yang ingin diunduh.', 409);
        }
        $full = $this->saved->full($row);

        return ['document' => [...$full, 'snapshot_hash' => $row->snapshot_hash, 'export_schema_version' => ReportDocumentRenderer::VERSION], 'epoch' => DB::table('sync_state')->where('id', 1)->value('epoch')];
    }

    public function render(array $snapshot, string $format): array
    {
        if (! in_array($format, ['pdf', 'csv'], true)) {
            throw new \InvalidArgumentException('Unsupported export format');
        }
        $document = $snapshot['document'];
        $bytes = $format === 'pdf' ? $this->renderer->pdf($document) : $this->renderer->csv($document);

        return ['bytes' => $bytes, 'mime' => $format === 'pdf' ? 'application/pdf' : 'text/csv; charset=UTF-8', 'filename' => 'inofarm-'.$document['kind'].'-'.$document['id'].'.'.$format, 'content_hash' => hash('sha256', $bytes)];
    }
}
