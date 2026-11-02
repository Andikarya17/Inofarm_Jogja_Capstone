<?php

namespace App\Http\Controllers\V2;

use App\Domain\Contracts\DomainFailure;
use App\Domain\Finance\ReportExportService;
use App\Domain\Sync\ScopedRead;
use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\Request;

final class ReportExportController extends Controller
{
    public function __invoke(Request $request, string $id, string $format, ReportExportService $exports, ScopedRead $reads)
    {
        $input = $request->validate(['expected_snapshot_hash' => ['required', 'string', 'regex:/^[0-9a-f]{64}$/']]);
        abort_if(array_diff(array_keys($request->query()), ['expected_snapshot_hash']) !== [], 422, 'Parameter unduhan tidak didukung.');
        $snapshot = $reads->run($request, fn (User $actor) => $exports->snapshot($actor, $id, $input['expected_snapshot_hash']));
        // Render outside the database lock, so a large PDF does not hold all mutations.
        $file = $exports->render($snapshot, $format);
        $reads->run($request, function (User $actor) use ($exports, $snapshot, $id, $input) {
            $latest = $exports->snapshot($actor, $id, $input['expected_snapshot_hash']);
            if ($latest['epoch'] !== $snapshot['epoch']) {
                throw new DomainFailure('export_epoch_changed', 'Server berubah selama menyiapkan unduhan. Sinkronkan dan buka laporan kembali.', 409);
            }
        });

        return response($file['bytes'], 200, ['Content-Type' => $file['mime'], 'Content-Length' => (string) strlen($file['bytes']), 'Content-Disposition' => 'attachment; filename="'.$file['filename'].'"', 'Cache-Control' => 'private, no-store, max-age=0', 'Pragma' => 'no-cache', 'X-Content-Type-Options' => 'nosniff', 'X-Report-Snapshot-Hash' => $input['expected_snapshot_hash'], 'X-Content-SHA256' => $file['content_hash']]);
    }
}
