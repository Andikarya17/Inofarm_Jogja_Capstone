<?php

namespace App\Domain\Finance;

use App\Domain\Cultivation\MasterService;
use App\Domain\Identity\GreenhouseAccess;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;

/** Live read-only views the field asked for (15 Nov 2026); unlike season reports they are not immutable snapshots. */
final class FieldReports
{
    public function __construct(private GreenhouseAccess $access, private MasterService $masters, private SeasonReportService $seasonReports) {}

    /** Monthly cash in (paid sales) and cash out (all payments, assets included) by WIB month and greenhouse. */
    public function cashFlow(User $actor, string $from, string $until, ?string $greenhouseId): array
    {
        $this->access->manager($actor);
        $greenhouses = $this->access->query($actor)->when($greenhouseId, fn ($q, $id) => $q->where('id', $id))->orderBy('name')->get(['id', 'name']);
        abort_if($greenhouseId !== null && $greenhouses->isEmpty(), 404, 'Data tidak ditemukan.');
        $ids = $greenhouses->pluck('id')->all();
        $start = CarbonImmutable::parse($from.'-01', 'Asia/Jakarta');
        $end = CarbonImmutable::parse($until.'-01', 'Asia/Jakarta')->addMonth();
        // WIB is a fixed UTC+7 without daylight saving, so shifting the stored UTC time is exact.
        $sum = fn (string $table, string $amount) => DB::table($table)->whereIn('greenhouse_id', $ids)->where('record_state', 'active')
            ->when($table === 'sales', fn ($q) => $q->where('payment_status', 'paid'))
            ->where('paid_at', '>=', $start->utc()->format('Y-m-d H:i:s.u'))->where('paid_at', '<', $end->utc()->format('Y-m-d H:i:s.u'))
            ->groupBy('month', 'greenhouse_id')
            ->selectRaw("DATE_FORMAT(DATE_ADD(paid_at, INTERVAL 7 HOUR), '%Y-%m') AS month, greenhouse_id, CAST(SUM($amount) AS CHAR) AS amount")
            ->get()->keyBy(fn ($row) => $row->month.'|'.$row->greenhouse_id);
        $receipts = $sum('sales', 'total_amount');
        $payments = $sum('expenses', 'amount');
        $months = [];
        $grand = ['receipts' => '0.00', 'payments' => '0.00'];
        for ($month = $start; $month->lt($end); $month = $month->addMonth()) {
            $key = $month->format('Y-m');
            $line = ['month' => $key, 'receipts' => '0.00', 'payments' => '0.00', 'greenhouses' => []];
            foreach ($greenhouses as $gh) {
                $in = bcadd($receipts->get("$key|$gh->id")->amount ?? '0', '0', 2);
                $out = bcadd($payments->get("$key|$gh->id")->amount ?? '0', '0', 2);
                if (bccomp($in, '0', 2) === 0 && bccomp($out, '0', 2) === 0) {
                    continue;
                }
                $line['greenhouses'][] = ['greenhouse_id' => $gh->id, 'greenhouse_name' => $gh->name, 'receipts' => $in, 'payments' => $out, 'net' => bcsub($in, $out, 2)];
                $line['receipts'] = bcadd($line['receipts'], $in, 2);
                $line['payments'] = bcadd($line['payments'], $out, 2);
            }
            $line['net'] = bcsub($line['receipts'], $line['payments'], 2);
            $grand['receipts'] = bcadd($grand['receipts'], $line['receipts'], 2);
            $grand['payments'] = bcadd($grand['payments'], $line['payments'], 2);
            $months[] = $line;
        }

        return ['from' => $from, 'until' => $until, 'greenhouse_id' => $greenhouseId, 'currency' => 'IDR', 'timezone' => 'Asia/Jakarta',
            'months' => $months, 'totals' => [...$grand, 'net' => bcsub($grand['receipts'], $grand['payments'], 2)],
            'basis' => 'Uang masuk dari penjualan lunas aktif; uang keluar dari semua pembayaran aktif termasuk pembelian aset. Bukan laba musim.',
            'as_of' => CarbonImmutable::now('UTC')->toISOString()];
    }

    public function cashFlowCsv(array $report): string
    {
        $stream = fopen('php://temp', 'r+');
        $row = fn (array $columns) => fputcsv($stream, $columns, ',', '"', '', "\r\n");
        $row(['bulan', 'greenhouse', 'uang_masuk', 'uang_keluar', 'selisih']);
        foreach ($report['months'] as $month) {
            foreach ($month['greenhouses'] as $gh) {
                $row([$month['month'], ReportDocumentRenderer::csvText($gh['greenhouse_name']), $gh['receipts'], $gh['payments'], $gh['net']]);
            }
            $row([$month['month'], 'Semua greenhouse', $month['receipts'], $month['payments'], $month['net']]);
        }
        rewind($stream);
        $csv = stream_get_contents($stream);
        fclose($stream);

        return $csv;
    }

    /** Side-by-side recorded metrics of several seasons, reusing the provisional season-report calculation. */
    public function compareSeasons(User $actor, array $seasonIds): array
    {
        return array_map(function (string $seasonId) use ($actor) {
            $preview = $this->seasonReports->preview($actor, ['season_id' => $seasonId, 'coverage' => ['costs' => 'unknown', 'sales' => 'unknown'],
                'known_pending_count' => null, 'requested_state' => 'provisional', 'review_note' => 'Perbandingan musim']);
            $harvested = DB::table('harvest_items as item')->join('harvests as harvest', 'harvest.id', '=', 'item.harvest_id')
                ->join('planting_groups as grp', 'grp.id', '=', 'harvest.planting_group_id')->where('grp.season_id', $seasonId)
                ->where('harvest.record_state', 'active')->where('item.active', true)
                ->selectRaw('CAST(COALESCE(SUM(item.weight_kg), 0) AS DECIMAL(20,6)) AS kg')->value('kg');
            $season = $preview['scope']['season'];

            return ['season_id' => $seasonId, 'season_name' => $season['name'], 'season_status' => $season['status'],
                'start_date' => $season['start_date'] ?? null, 'end_date' => $season['end_date'] ?? null,
                'greenhouse_id' => $preview['greenhouse_id'], 'greenhouse_name' => $preview['scope']['greenhouse']['name'],
                'metrics' => $preview['metrics'], 'source_counts' => $preview['source_counts'],
                'harvest_weight_kg' => (string) $harvested, 'unsold_weight_kg' => MasterService::unsoldWeight($seasonId)];
        }, $seasonIds);
    }

    /** Recorded material use per season, summed per material and unit; different units are not converted. */
    public function materialUsage(User $actor, string $seasonId): array
    {
        $season = $this->masters->scope($actor, 'seasons')->where('id', $seasonId)->first() ?? abort(404, 'Data tidak ditemukan.');
        $items = DB::table('activity_materials as used')->join('activities as activity', 'activity.id', '=', 'used.activity_id')
            ->join('planting_groups as grp', 'grp.id', '=', 'activity.planting_group_id')
            ->join('materials as material', 'material.id', '=', 'used.material_id')->join('units as unit', 'unit.id', '=', 'used.unit_id')
            ->where('grp.season_id', $seasonId)->where('activity.record_state', 'active')->where('activity.work_state', 'completed')
            ->groupBy('used.material_id', 'material.name', 'used.unit_id', 'unit.name')->orderBy('material.name')->orderBy('unit.name')
            ->selectRaw('used.material_id, material.name AS material_name, used.unit_id, unit.name AS unit_name, CAST(SUM(used.quantity) AS CHAR) AS quantity, COUNT(DISTINCT activity.id) AS activities')
            ->get()->map(fn ($row) => [...(array) $row, 'activities' => (int) $row->activities])->all();

        return ['season_id' => $season->id, 'season_name' => $season->name, 'greenhouse_id' => $season->greenhouse_id, 'items' => $items,
            'basis' => 'Kegiatan aktif yang selesai; jumlah dijumlahkan per bahan dan satuan tanpa konversi satuan.'];
    }
}
