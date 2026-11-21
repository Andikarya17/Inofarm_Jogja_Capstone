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

    public const MAX_BUCKETS = 104;

    private const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des'];

    /** WIB bucket of a day: weeks start on Monday, months on the 1st, years on 1 January. */
    public static function bucketStart(CarbonImmutable $day, string $granularity): CarbonImmutable
    {
        return match ($granularity) {
            'week' => $day->startOfDay()->subDays($day->dayOfWeekIso - 1),
            'month' => $day->startOfMonth(),
            'year' => $day->startOfYear(),
        };
    }

    /** Paid, active sales per week/month/year (WIB), split by buyer type for the stacked income chart. */
    public function income(User $actor, string $granularity, string $from, string $until, ?string $greenhouseId): array
    {
        $this->access->manager($actor);
        $ids = $this->access->query($actor)->when($greenhouseId, fn ($q, $id) => $q->where('id', $id))->pluck('id')->all();
        abort_if($greenhouseId !== null && $ids === [], 404, 'Data tidak ditemukan.');
        $first = self::bucketStart(CarbonImmutable::parse($from, 'Asia/Jakarta'), $granularity);
        $end = CarbonImmutable::parse($until, 'Asia/Jakarta')->addDay();
        $step = ['week' => 'addWeek', 'month' => 'addMonth', 'year' => 'addYear'][$granularity];
        $empty = fn () => ['receipts' => '0.00', 'weight_kg' => '0.000000'];
        $buckets = [];
        for ($start = $first; $start->lt($end); $start = $start->{$step}()) {
            abort_if(count($buckets) >= self::MAX_BUCKETS, 422, 'Rentang terlalu panjang; maksimal '.self::MAX_BUCKETS.' batang.');
            $key = ['week' => $start->format('Y-m-d'), 'month' => $start->format('Y-m'), 'year' => $start->format('Y')][$granularity];
            $label = ['week' => $start->day.' '.self::MONTHS[$start->month - 1], 'month' => self::MONTHS[$start->month - 1], 'year' => $start->format('Y')][$granularity];
            $buckets[$key] = ['key' => $key, 'label' => $label, 'start' => $start->format('Y-m-d'), ...$empty(),
                'by_type' => ['consumer' => $empty(), 'partner' => $empty(), 'unspecified' => $empty()]];
        }
        $totals = [...$empty(), 'by_type' => ['consumer' => $empty(), 'partner' => $empty(), 'unspecified' => $empty()]];
        $rows = DB::table('sales')->whereIn('greenhouse_id', $ids)->where('record_state', 'active')->where('payment_status', 'paid')
            ->where('paid_at', '>=', $first->utc()->format('Y-m-d H:i:s.u'))->where('paid_at', '<', $end->utc()->format('Y-m-d H:i:s.u'))
            ->get(['paid_at', 'total_amount', 'total_weight_kg', 'customer_type']);
        foreach ($rows as $row) {
            $day = CarbonImmutable::parse($row->paid_at, 'UTC')->setTimezone('Asia/Jakarta');
            $start = self::bucketStart($day, $granularity);
            $key = ['week' => $start->format('Y-m-d'), 'month' => $start->format('Y-m'), 'year' => $start->format('Y')][$granularity];
            $type = in_array($row->customer_type, ['consumer', 'partner'], true) ? $row->customer_type : 'unspecified';
            foreach ([&$buckets[$key], &$buckets[$key]['by_type'][$type], &$totals, &$totals['by_type'][$type]] as &$slot) {
                $slot['receipts'] = bcadd($slot['receipts'], $row->total_amount, 2);
                $slot['weight_kg'] = bcadd($slot['weight_kg'], $row->total_weight_kg, 6);
            }
            unset($slot);
        }

        return ['granularity' => $granularity, 'buckets' => array_values($buckets), 'totals' => $totals];
    }

    /**
     * Live profit on standard costs (21 Nov 2026): every greenhouse season, the open ones together, and each season
     * number across greenhouses. Not a published report; values follow the season-report calculation.
     */
    public function profitSummary(User $actor, ?string $greenhouseId): array
    {
        $this->access->manager($actor);
        $greenhouses = $this->access->query($actor)->where('active', true)->when($greenhouseId, fn ($q, $id) => $q->where('id', $id))->orderBy('name')->get(['id', 'name']);
        abort_if($greenhouseId !== null && $greenhouses->isEmpty(), 404, 'Data tidak ditemukan.');
        $rows = [];
        foreach (DB::table('seasons')->whereIn('greenhouse_id', $greenhouses->pluck('id'))->orderBy('sequence')->orderBy('id')->get() as $season) {
            $preview = $this->seasonReports->preview($actor, ['season_id' => $season->id, 'coverage' => ['costs' => 'complete', 'sales' => 'complete'],
                'known_pending_count' => null, 'requested_state' => 'provisional', 'review_note' => 'Ringkasan laba']);
            $m = $preview['metrics'];
            $rows[] = ['greenhouse_id' => $season->greenhouse_id, 'name' => $greenhouses->firstWhere('id', $season->greenhouse_id)->name, 'season_id' => $season->id,
                'sequence' => $season->sequence === null ? null : (int) $season->sequence, 'status' => $season->status,
                'FC' => $m['FC']['value'], 'VC' => $m['VC']['value'], 'TR' => $m['TR']['value'], 'Q' => $m['Q']['value']];
        }
        $open = array_values(array_filter($rows, fn ($row) => $row['status'] === 'open'));
        $bySequence = [];
        foreach (array_unique(array_filter(array_column($rows, 'sequence'), fn ($n) => $n !== null)) as $n) {
            $group = array_values(array_filter($rows, fn ($row) => $row['sequence'] === $n));
            $bySequence[] = ['sequence' => $n, 'greenhouse_count' => count($group), ...self::figures($group)];
        }
        return [
            'open' => ['greenhouse_count' => count($open), ...self::figures($open, true)],
            'by_sequence' => $bySequence,
            'by_greenhouse' => array_map(fn ($row) => ['greenhouse_id' => $row['greenhouse_id'], 'name' => $row['name'], 'season_id' => $row['season_id'],
                'sequence' => $row['sequence'], ...self::figures([$row])], $rows),
        ];
    }

    /** Sums seasons (a missing FC/VC makes costs unknown; no sales count as zero) and reuses SeasonEvaluation. */
    private static function figures(array $rows, bool $withBep = false): array
    {
        $fc = $vc = '0.00';
        $tr = '0.00';
        $q = '0.000000';
        foreach ($rows as $row) {
            $fc = $fc === null || $row['FC'] === null ? null : bcadd($fc, $row['FC'], 2);
            $vc = $vc === null || $row['VC'] === null ? null : bcadd($vc, $row['VC'], 2);
            $tr = bcadd($tr, $row['TR'] ?? '0', 2);
            $q = bcadd($q, $row['Q'] ?? '0', 6);
        }
        $m = SeasonEvaluation::calculate($fc, $vc, $tr, $q, ['costs' => 'complete', 'sales' => 'complete']);
        $out = ['TR' => $tr, 'TC' => $m['TC']['value'], 'profit' => $m['L']['value'], 'rc' => $m['RC']['value'], 'complete' => $m['TC']['value'] !== null];
        if ($withBep) {
            $out += ['bep_price' => $m['BEP_PRICE']['value'], 'bep_kg' => $m['BEP_Q']['value'], 'sold_kg' => $q];
        }

        return $out;
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
        // Greenhouse-level activities have no group, so the season is read from the activity row; a spray without a dose has no unit.
        $items = DB::table('activity_materials as used')->join('activities as activity', 'activity.id', '=', 'used.activity_id')
            ->join('materials as material', 'material.id', '=', 'used.material_id')->leftJoin('units as unit', 'unit.id', '=', 'used.unit_id')
            ->where('activity.season_id', $seasonId)->where('activity.record_state', 'active')->where('activity.work_state', 'completed')
            ->groupBy('used.material_id', 'material.name', 'used.unit_id', 'unit.name')->orderBy('material.name')->orderBy('unit.name')
            ->selectRaw('used.material_id, material.name AS material_name, used.unit_id, unit.name AS unit_name, CAST(SUM(used.quantity) AS CHAR) AS quantity, COUNT(DISTINCT activity.id) AS activities')
            ->get()->map(fn ($row) => [...(array) $row, 'activities' => (int) $row->activities])->all();

        return ['season_id' => $season->id, 'season_name' => $season->name, 'greenhouse_id' => $season->greenhouse_id, 'items' => $items,
            'basis' => 'Kegiatan aktif yang selesai; jumlah dijumlahkan per bahan dan satuan tanpa konversi satuan.'];
    }
}
