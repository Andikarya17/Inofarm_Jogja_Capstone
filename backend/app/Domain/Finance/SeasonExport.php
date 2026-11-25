<?php

namespace App\Domain\Finance;

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
                $sheets[] = match ($part) {
                    'summary' => ['name' => 'Ringkasan per GH', 'rows' => [[$title], ...$this->summary($details)]],
                    'costs' => ['name' => 'Rincian biaya', 'rows' => [[$title], ...$this->costs($details)]],
                    'sales' => ['name' => 'Daftar penjualan', 'rows' => [[$title], ...$this->sales($actor, $selected)]],
                };
            }
        }

        return ['bytes' => SeasonWorkbook::render($sheets), 'label' => $sequence === null ? 'semua' : (string) $sequence];
    }

    private function summary(array $details): array
    {
        $rows = [['Musim', 'GH', 'FC (Rp)', 'VC (Rp)', 'TC (Rp)', 'Penjualan (Rp)', 'Terjual (kg)', 'Transaksi']];
        foreach ($details as $d) {
            $per = $d['cost_sheet']['per_greenhouse'];
            foreach ($d['by_greenhouse'] as $line) {
                $rows[] = [$d['sequence'], $line['name'], $per['fc'], $per['vc'], $per['tc'], $line['amount'], $line['kg'], $line['count']];
            }
            $farm = $d['cost_sheet']['farm'];
            $rows[] = [$d['sequence'], count($d['by_greenhouse']).' GH', $farm['fc'], $farm['vc'], $farm['tc'], $d['sales']['amount'], $d['sales']['kg'], $d['sales']['count']];
        }

        return $rows;
    }

    private function costs(array $details): array
    {
        $rows = [['Musim', 'Kelas', 'Baris biaya', 'Wajib', 'Per GH (Rp)', 'Jumlah GH', 'Total kebun (Rp)']];
        foreach ($details as $d) {
            $count = count($d['by_greenhouse']);
            foreach ($d['cost_sheet']['lines'] as $line) {
                $amount = $line['amount'];
                $rows[] = [$d['sequence'], $line['cost_class'], $line['name'], $line['is_required'] ? 'Ya' : 'Tidak', $amount, $count, $amount === null ? null : bcmul($amount, (string) $count, 2)];
            }
            $rows[] = [$d['sequence'], null, 'TC', null, $d['cost_sheet']['per_greenhouse']['tc'], $count, $d['cost_sheet']['farm']['tc']];
        }

        return $rows;
    }

    private function sales(User $actor, array $sequences): array
    {
        $rows = [['Tanggal', 'GH', 'Pembeli', 'Tipe', 'Varietas', 'Kualitas', 'Grade', 'kg', 'Buah', 'Harga/kg (Rp)', 'Total (Rp)']];
        $lines = DB::table('sale_lines')->join('sales', 'sales.id', '=', 'sale_lines.sale_id')
            ->join('seasons', 'seasons.id', '=', 'sales.season_id')->join('greenhouses', 'greenhouses.id', '=', 'seasons.greenhouse_id')
            ->where('greenhouses.active', true)->whereIn('seasons.sequence', $sequences ?: [0])
            ->where('sales.record_state', 'active')->where('sales.payment_status', 'paid')->where('sale_lines.active', true)
            ->orderByDesc('seasons.sequence')->orderBy('sales.paid_at')->orderBy('sale_lines.created_at')->orderBy('sale_lines.id')
            ->get(['sales.paid_at', 'greenhouses.name as greenhouse', 'sales.customer_name', 'sales.customer_type', 'sale_lines.variety_name', 'sale_lines.grade_name',
                'sale_lines.buyer_grade', 'sale_lines.quantity_kg', 'sale_lines.fruit_count', 'sale_lines.price_per_kg', 'sale_lines.exact_amount']);
        foreach ($lines as $line) {
            $date = CarbonImmutable::parse($line->paid_at, 'UTC')->setTimezone('Asia/Jakarta');
            $rows[] = [$date->day.' '.FieldReports::MONTHS[$date->month - 1].' '.$date->format('Y'), $line->greenhouse, $line->customer_name ?: null,
                self::TYPES[$line->customer_type] ?? 'Tanpa tipe', $line->variety_name, stripos($line->grade_name, 'cacat') !== false ? 'Cacat' : 'Layak',
                $line->buyer_grade, $line->quantity_kg, $line->fruit_count, $line->price_per_kg, $this->money((string) $line->exact_amount)];
        }

        return $rows;
    }

    /** Half-up to whole rupiah cents; amounts are never negative. */
    private function money(string $exact): string
    {
        return bcadd($exact, '0.005', 2);
    }
}
