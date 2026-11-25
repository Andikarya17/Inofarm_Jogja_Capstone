<?php

namespace App\Domain\Finance;

use App\Domain\Identity\GreenhouseAccess;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;

/**
 * Per season number across active greenhouses: the standard cost sheet, paid sales by buyer type and by week.
 * Plain arrays with string decimals so the workbook export can reuse it. No profit or ratios here by decision.
 */
final class SeasonOverview
{
    public const TYPES = ['consumer', 'retail', 'partner', 'unspecified'];

    public function __construct(private GreenhouseAccess $access, private SeasonCostSheetService $sheets) {}

    /** Seasons of active greenhouses that have a sequence; every sequence's sheet is ensured on the way. */
    private function seasons(User $actor): array
    {
        $this->access->manager($actor);
        $rows = DB::table('seasons')->join('greenhouses', 'greenhouses.id', '=', 'seasons.greenhouse_id')
            ->where('greenhouses.active', true)->whereNotNull('seasons.sequence')->orderBy('greenhouses.name')
            ->get(['seasons.id', 'seasons.greenhouse_id', 'greenhouses.name', 'seasons.status', 'seasons.start_date', 'seasons.end_date', 'seasons.sequence'])->all();
        foreach (array_unique(array_map(fn ($row) => (int) $row->sequence, $rows)) as $sequence) {
            $this->sheets->ensureFor($sequence, $actor);
        }

        return $rows;
    }

    private static function slot(): array
    {
        return ['amount' => '0.00', 'kg' => '0.000000', 'count' => 0];
    }

    private static function add(array &$slot, object $sale): void
    {
        $slot['amount'] = bcadd($slot['amount'], $sale->total_amount, 2);
        $slot['kg'] = bcadd($slot['kg'], $sale->total_weight_kg, 6);
        $slot['count']++;
    }

    private static function typeOf(object $sale): string
    {
        return in_array($sale->customer_type, ['consumer', 'retail', 'partner'], true) ? $sale->customer_type : 'unspecified';
    }

    private static function emptyByType(): array
    {
        return array_fill_keys(self::TYPES, self::slot());
    }

    /** Sheet totals times the active greenhouses of that season number; null while a line is blank. */
    private static function farm(array $totals, int $count): array
    {
        $times = fn (?string $value) => $value === null ? null : bcmul($value, (string) $count, 2);

        return ['fc' => $times($totals['FC']), 'vc' => $times($totals['VC']), 'tc' => $times($totals['TC'])];
    }

    /** Paid, active sales grouped by season id. */
    private function sales(array $seasonIds): array
    {
        $grouped = [];
        foreach (DB::table('sales')->whereIn('season_id', $seasonIds)->where('record_state', 'active')->where('payment_status', 'paid')
            ->orderBy('paid_at')->get(['season_id', 'paid_at', 'total_amount', 'total_weight_kg', 'customer_type']) as $sale) {
            $grouped[$sale->season_id][] = $sale;
        }

        return $grouped;
    }

    public function sequences(User $actor): array
    {
        $seasons = $this->seasons($actor);
        $bySequence = [];
        foreach ($seasons as $season) {
            $bySequence[(int) $season->sequence][] = $season;
        }
        krsort($bySequence);
        $sales = $this->sales(array_column($seasons, 'id'));
        $out = [];
        foreach ($bySequence as $sequence => $group) {
            $sold = self::slot();
            foreach ($group as $season) {
                foreach ($sales[$season->id] ?? [] as $sale) {
                    self::add($sold, $sale);
                }
            }
            $totals = $this->sheets->publicSheet($this->sheets->ensureFor($sequence, $actor))['totals'];
            $farm = self::farm($totals, count($group));
            $out[] = ['sequence' => $sequence, 'status' => in_array('open', array_column($group, 'status'), true) ? 'running' : 'closed',
                'greenhouse_count' => count($group), ...$farm, 'sales_amount' => $sold['amount'], 'sales_kg' => $sold['kg']];
        }

        return $out;
    }

    /** Without a sequence the largest running one is selected, else the largest closed one; unknown sequences are 404. */
    public function build(User $actor, ?int $sequence = null): array
    {
        $sequences = $this->sequences($actor);
        if ($sequence === null && $sequences !== []) {
            $running = array_values(array_filter($sequences, fn ($row) => $row['status'] === 'running'));
            $sequence = ($running[0] ?? $sequences[0])['sequence'];
        }
        abort_if($sequence !== null && ! in_array($sequence, array_column($sequences, 'sequence'), true), 404, 'Data tidak ditemukan.');

        return ['sequences' => $sequences, 'selected' => $sequence === null ? null : $this->selected($actor, $sequence)];
    }

    private function selected(User $actor, int $sequence): array
    {
        $seasons = array_values(array_filter($this->seasons($actor), fn ($row) => (int) $row->sequence === $sequence));
        $sheet = $this->sheets->publicSheet($this->sheets->ensureFor($sequence, $actor));
        $totals = $sheet['totals'];
        $running = in_array('open', array_column($seasons, 'status'), true);
        $today = CarbonImmutable::now('Asia/Jakarta')->startOfDay();
        $ends = array_filter(array_column($seasons, 'end_date'));
        $until = $running || $ends === [] ? $today : CarbonImmutable::parse(max($ends), 'Asia/Jakarta');

        $sales = $this->sales(array_column($seasons, 'id'));
        $all = self::slot();
        $byType = self::emptyByType();
        $byGreenhouse = [];
        $weeks = [];
        foreach ($seasons as $season) {
            $line = ['greenhouse_id' => $season->greenhouse_id, 'name' => $season->name, ...self::slot()];
            foreach ($sales[$season->id] ?? [] as $sale) {
                $type = self::typeOf($sale);
                self::add($all, $sale);
                self::add($byType[$type], $sale);
                self::add($line, $sale);
                $start = FieldReports::bucketStart(CarbonImmutable::parse($sale->paid_at, 'UTC')->setTimezone('Asia/Jakarta'), 'week');
                $key = $start->format('Y-m-d');
                $weeks[$key] ??= ['start' => $start, 'amount' => '0.00', 'kg' => '0.000000', 'by_type' => self::emptyByType()];
                $weeks[$key]['amount'] = bcadd($weeks[$key]['amount'], $sale->total_amount, 2);
                $weeks[$key]['kg'] = bcadd($weeks[$key]['kg'], $sale->total_weight_kg, 6);
                self::add($weeks[$key]['by_type'][$type], $sale);
            }
            $byGreenhouse[] = $line;
        }

        return [
            'sequence' => $sequence,
            'date_range' => ['from' => min(array_column($seasons, 'start_date')), 'until' => $until->format('Y-m-d'), 'running' => $running],
            'cost_sheet' => ['id' => $sheet['id'], 'version' => $sheet['version'],
                'lines' => array_map(fn ($line) => ['name' => $line['name'], 'cost_class' => $line['cost_class'], 'amount' => $line['amount'], 'is_required' => $line['is_required']], $sheet['lines']),
                'per_greenhouse' => ['fc' => $totals['FC'], 'vc' => $totals['VC'], 'tc' => $totals['TC']],
                'farm' => self::farm($totals, count($seasons))],
            'sales' => [...$all, 'by_type' => $byType],
            'by_greenhouse' => $byGreenhouse,
            'weekly' => self::weekly($weeks, $until),
        ];
    }

    /** Continuous Monday weeks from the first sale to the end of the season range; empty while nothing was sold. */
    private static function weekly(array $weeks, CarbonImmutable $until): array
    {
        if ($weeks === []) {
            return [];
        }
        ksort($weeks);
        $first = $weeks[array_key_first($weeks)]['start'];
        $end = FieldReports::bucketStart($until, 'week');
        $last = $weeks[array_key_last($weeks)]['start'];
        if ($last->gt($end)) {
            $end = $last;
        }
        $out = [];
        for ($start = $first; $start->lte($end); $start = $start->addWeek()) {
            $key = $start->format('Y-m-d');
            $week = $weeks[$key] ?? ['amount' => '0.00', 'kg' => '0.000000', 'by_type' => self::emptyByType()];
            $out[] = ['key' => $key, 'label' => $start->day.' '.FieldReports::MONTHS[$start->month - 1], 'start' => $key,
                'amount' => $week['amount'], 'kg' => $week['kg'] ?? '0.000000', 'by_type' => $week['by_type']];
        }

        return $out;
    }
}
