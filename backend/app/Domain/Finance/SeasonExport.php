<?php

namespace App\Domain\Finance;

use App\Domain\Harvest\QualityGradeCatalog;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;

/** Builds the season workbook from SeasonOverview and the active sale lines. Blank cost lines stay empty cells. */
final class SeasonExport
{
    public const PARTS = ['summary', 'costs', 'sales'];

    private const TYPES = ['consumer' => 'Perorangan', 'retail' => 'Retail', 'partner' => 'Mitra'];

    public function __construct(private SeasonOverview $overview) {}

    /** @param list<string> $parts @return array{bytes: string, label: string} */
    public function build(User $actor, ?int $sequence, array $parts): array
    {
        $now = CarbonImmutable::now('Asia/Jakarta');
        $selected = $sequence === null
            ? array_column($this->overview->sequences($actor), 'sequence')
            : [$this->overview->build($actor, $sequence)['selected']['sequence']];
        sort($selected);
        $selected = array_reverse($selected);
        $details = array_map(fn (int $n) => $this->overview->build($actor, $n)['selected'], $selected);
        $title = ($sequence === null ? 'Semua musim' : 'Musim '.$sequence).' · diunduh '.$now->day.' '.FieldReports::MONTHS[$now->month - 1].' '.$now->format('Y H.i').' WIB';

        $sheets = [];
        foreach (self::PARTS as $part) {
            if (in_array($part, $parts, true)) {
                [$name, [$body, $bold]] = match ($part) {
                    'summary' => ['Ringkasan per GH', $this->summary($details)],
                    'costs' => ['Rincian biaya', $this->costs($details)],
                    'sales' => ['Daftar penjualan', $this->sales($selected)],
                };
                $sheets[] = ['name' => $name, 'rows' => [[$title], ...$body], 'bold' => array_map(fn (int $i) => $i + 2, $bold)];
            }
        }

        return ['bytes' => SeasonWorkbook::render($sheets), 'label' => $sequence === null ? 'semua' : (string) $sequence];
    }

    /** @return array{0: list<list<mixed>>, 1: list<int>} rows and the body indexes of Total rows (header is 0) */
    private function summary(array $details): array
    {
        $m = XlsxNumber::money(...);
        $rows = [['Musim', 'GH', 'FC (Rp)', 'VC (Rp)', 'TC (Rp)', 'Penjualan (Rp)', 'Terjual (kg)', 'Transaksi']];
        $bold = [];
        foreach ($details as $d) {
            $per = $d['cost_sheet']['per_greenhouse'];
            foreach ($d['by_greenhouse'] as $line) {
                $rows[] = [XlsxNumber::of($d['sequence']), $line['name'], $m($per['fc']), $m($per['vc']), $m($per['tc']), $m($line['amount']), XlsxNumber::of($line['kg']), XlsxNumber::of($line['count'])];
            }
            $farm = $d['cost_sheet']['farm'];
            $bold[] = count($rows);
            $rows[] = ['Total', count($d['by_greenhouse']).' GH', $m($farm['fc']), $m($farm['vc']), $m($farm['tc']), $m($d['sales']['amount']), XlsxNumber::of($d['sales']['kg']), XlsxNumber::of($d['sales']['count'])];
        }

        return [$rows, $bold];
    }

    private function costs(array $details): array
    {
        $m = XlsxNumber::money(...);
        $rows = [['Musim', 'Kelas', 'Baris biaya', 'Wajib', 'Per GH (Rp)', 'Jumlah GH', 'Total kebun (Rp)']];
        $bold = [];
        foreach ($details as $d) {
            $count = count($d['by_greenhouse']);
            foreach ($d['cost_sheet']['lines'] as $line) {
                $amount = $line['amount'];
                $rows[] = [XlsxNumber::of($d['sequence']), $line['cost_class'], $line['name'], $line['is_required'] ? 'Ya' : 'Tidak', $m($amount), XlsxNumber::of($count), $m($amount === null ? null : bcmul($amount, (string) $count, 2))];
            }
            $bold[] = count($rows);
            $rows[] = [XlsxNumber::of($d['sequence']), null, 'TC', null, $m($d['cost_sheet']['per_greenhouse']['tc']), XlsxNumber::of($count), $m($d['cost_sheet']['farm']['tc'])];
        }

        return [$rows, $bold];
    }

    /** Line totals are each line's exact amount rounded half up; the footer is the sum of the sales' own totals, as the screen shows. */
    private function sales(array $sequences): array
    {
        $m = XlsxNumber::money(...);
        $rows = [['Musim', 'Tanggal', 'GH', 'Pembeli', 'Tipe', 'Varietas', 'Kualitas', 'Grade', 'kg', 'Buah', 'Harga/kg (Rp)', 'Total (Rp)']];
        $scope = fn () => DB::table('sales')->join('seasons', 'seasons.id', '=', 'sales.season_id')->join('greenhouses', 'greenhouses.id', '=', 'seasons.greenhouse_id')
            ->where('greenhouses.active', true)->whereIn('seasons.sequence', $sequences ?: [0])
            ->where('sales.record_state', 'active')->where('sales.payment_status', 'paid');
        $lines = $scope()->join('sale_lines', 'sale_lines.sale_id', '=', 'sales.id')->where('sale_lines.active', true)
            ->orderByDesc('seasons.sequence')->orderBy('sales.paid_at')->orderBy('sale_lines.created_at')->orderBy('sale_lines.id')
            ->get(['seasons.sequence', 'sales.paid_at', 'greenhouses.name as greenhouse', 'sales.customer_name', 'sales.customer_type', 'sale_lines.variety_name', 'sale_lines.grade_name',
                'sale_lines.buyer_grade', 'sale_lines.quantity_kg', 'sale_lines.fruit_count', 'sale_lines.price_per_kg', 'sale_lines.exact_amount']);
        foreach ($lines as $line) {
            $date = CarbonImmutable::parse($line->paid_at, 'UTC')->setTimezone('Asia/Jakarta');
            $rows[] = [XlsxNumber::of((int) $line->sequence), $date->day.' '.FieldReports::MONTHS[$date->month - 1].' '.$date->format('Y'), $line->greenhouse, $line->customer_name ?: null,
                self::TYPES[$line->customer_type] ?? 'Tanpa tipe', $line->variety_name, self::quality($line->grade_name),
                $line->buyer_grade, XlsxNumber::of($line->quantity_kg), XlsxNumber::of($line->fruit_count), $m((string) $line->price_per_kg), $m($this->money((string) $line->exact_amount))];
        }
        $bold = [count($rows)];
        $total = (string) $scope()->sum('sales.total_amount');
        $rows[] = ['Total', ...array_fill(0, 10, null), $m(bcadd($total === '' ? '0' : $total, '0', 2))];

        return [$rows, $bold];
    }

    private static function quality(string $gradeName): string
    {
        foreach (QualityGradeCatalog::NAMES as $name) {
            if (strcasecmp(trim($gradeName), $name) === 0) {
                return $name;
            }
        }

        return $gradeName;
    }

    /** Half-up to two decimals; amounts are never negative. */
    private function money(string $exact): string
    {
        return bcadd($exact, '0.005', 2);
    }
}
