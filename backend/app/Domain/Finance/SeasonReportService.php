<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\CanonicalJson;
use App\Domain\Contracts\DomainFailure;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Illuminate\Database\Query\Builder;
use Illuminate\Support\Facades\DB;

final class SeasonReportService
{
    public function __construct(private SeasonReportSources $sources, private GreenhouseAccess $access, private MutationLedger $ledger) {}

    public function scope(User $actor): Builder
    {
        $this->access->manager($actor);

        return DB::table('report_snapshots')->where('kind', 'season_report')->whereIn('greenhouse_id', $this->access->ids($actor));
    }

    public function preview(User $actor, array $input): array
    {
        return $this->summary($input, $this->sources->collect($actor, $input['season_id']));
    }

    public function sources(User $actor, string $seasonId, string $expectedHash): array
    {
        $collected = $this->sources->collect($actor, $seasonId);
        $this->compare($expectedHash, $collected['sources_hash']);

        return $collected;
    }

    public function create(User $actor, array $operation): array
    {
        return $this->ledger->run($actor, $operation, function (User $current) use ($operation) {
            $this->access->manager($current);
            DB::table('seasons')->whereIn('greenhouse_id', $this->access->ids($current))->where('id', $operation['payload']['season_id'])->first() ?? abort(404);
        }, function (User $current) use ($operation) {
            $input = $operation['payload'];
            if ($input['expected_formula_version'] !== SeasonEvaluation::VERSION) {
                throw new DomainFailure('report_formula_changed', 'Versi rumus berubah. Tinjau ulang pratinjau.');
            }
            $data = $this->sources->collect($current, $input['season_id']);
            $this->compare($input['expected_sources_hash'], $data['sources_hash']);
            $time = now()->toISOString();
            $snapshot = [...$this->summary($input, $data), 'id' => $operation['entity_id'], 'kind' => 'season_report', 'version' => 1,
                'recorded_by' => $current->public_id, 'recorded_by_name' => $current->name, 'created_at' => $time, 'generated_at' => $time, 'sources' => $data['sources']];
            DB::table('report_snapshots')->insert(['id' => $operation['entity_id'], 'kind' => 'season_report', 'version' => 1,
                'greenhouse_id' => $snapshot['greenhouse_id'], 'season_id' => $input['season_id'], 'recorded_by' => $current->id,
                'content' => json_encode($snapshot, JSON_THROW_ON_ERROR), 'snapshot_hash' => CanonicalJson::hash($snapshot),
                'sources_hash' => $data['sources_hash'], 'source_count' => count($data['sources']), 'created_at' => now()]);
            $row = DB::table('report_snapshots')->where('id', $operation['entity_id'])->first();

            return ['before' => null, 'after' => $this->publicRow($row), 'greenhouse_id' => $snapshot['greenhouse_id'], 'audience' => 'manager'];
        });
    }

    public function full(object $row): array
    {
        $snapshot = json_decode($row->content, true, flags: JSON_THROW_ON_ERROR);
        if (! hash_equals($row->snapshot_hash, CanonicalJson::hash($snapshot)) || ($snapshot['id'] ?? null) !== $row->id
            || ($snapshot['greenhouse_id'] ?? null) !== $row->greenhouse_id || ($snapshot['season_id'] ?? null) !== $row->season_id
            || ($snapshot['kind'] ?? null) !== $row->kind || ($snapshot['version'] ?? null) !== $row->version || ($snapshot['sources_hash'] ?? null) !== $row->sources_hash
            || count($snapshot['sources'] ?? []) !== $row->source_count) {
            throw new DomainFailure('report_snapshot_integrity', 'Integritas snapshot laporan perlu diperiksa; hasil tidak dihitung ulang.', 500);
        }

        return $snapshot;
    }

    public function publicRow(object $row): array
    {
        $snapshot = $this->full($row);
        unset($snapshot['sources']);

        return [...$snapshot, 'snapshot_hash' => $row->snapshot_hash];
    }

    private function compare(string $expected, string $actual): void
    {
        if (! hash_equals($actual, $expected)) {
            throw new DomainFailure('report_sources_changed', 'Sumber atau versi musim berubah. Muat dan tinjau pratinjau baru sebelum membuat laporan.');
        }
    }

    private function summary(array $input, array $data): array
    {
        $coverage = $input['coverage'];
        $counts = $data['counts'];
        $totals = $data['totals'];
        $season = $data['scope']['season'];
        $final = $input['requested_state'] === 'reviewed_final';
        $reasons = [];
        if ($final) {
            if ($season['status'] !== 'closed') {
                $reasons[] = 'Musim harus ditutup sebelum laporan ditinjau sebagai akhir.';
            }
            if ($coverage['costs'] !== 'complete') {
                $reasons[] = 'Biaya dan alokasi harus ditinjau lengkap.';
            }
            if ($coverage['sales'] !== 'complete') {
                $reasons[] = 'Penjualan harus ditinjau lengkap.';
            }
            if ($input['known_pending_count'] !== 0) {
                $reasons[] = 'Antrean yang diketahui harus dinyatakan nol setelah peninjauan.';
            }
            if ($reasons) {
                throw new DomainFailure('report_review_incomplete', implode(' ', $reasons));
            }
        }
        $fc = $counts['FC'] > 0 || $coverage['costs'] === 'complete' ? $totals['FC'] : null;
        $vc = $counts['VC'] > 0 || $coverage['costs'] === 'complete' ? $totals['VC'] : null;
        $saleKnown = $counts['sales'] > 0 || $coverage['sales'] === 'complete';

        return ['greenhouse_id' => $data['scope']['greenhouse']['id'], 'season_id' => $season['id'], 'scope' => $data['scope'],
            'formula_version' => SeasonEvaluation::VERSION, 'currency' => 'IDR', 'timezone' => 'Asia/Jakarta', 'report_state' => $final ? 'reviewed_final' : 'provisional',
            'coverage' => $coverage, 'known_pending_count' => $input['known_pending_count'], 'review_note' => $input['review_note'],
            'metrics' => SeasonEvaluation::calculate($fc, $vc, $saleKnown ? $totals['TR'] : null, $saleKnown ? $totals['Q'] : null, $coverage),
            'sources_hash' => $data['sources_hash'], 'source_count' => count($data['sources']), 'source_counts' => $counts,
            'assumptions' => [
                'Hanya revisi keuangan yang diterima server pada snapshot ini; draf dan antrean perangkat tidak masuk agregasi.',
                'Biaya mencakup alokasi tercatat; pembayaran yang belum dialokasikan dapat belum tercakup dan perlu ditinjau pengelola.',
                'Pernyataan lengkap dan jumlah antrean adalah tinjauan pengelola, bukan bukti semua transaksi/perangkat offline terdeteksi.',
                'Pembelian aset dan catatan pemakaian bahan tidak menjadi pembayaran atau biaya musim kedua.',
                'TR memakai total pembayaran lunas yang telah dibulatkan per transaksi sesuai kontrak; panen belum terjual bukan penerimaan.',
                'Laporan adalah evaluasi manajerial dalam cakupan sistem, bukan kesimpulan kelayakan otomatis.',
            ]];
    }
}
