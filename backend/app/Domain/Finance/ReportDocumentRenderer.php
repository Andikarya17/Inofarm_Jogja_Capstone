<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\CanonicalJson;
use Dompdf\Dompdf;
use Dompdf\Options;

/** Formatting only: every financial value comes from the verified immutable document. */
class ReportDocumentRenderer
{
    public const VERSION = 'finance-snapshot-export-v1';

    private const LABELS = ['FC' => 'Biaya tetap teralokasi', 'VC' => 'Biaya variabel teralokasi', 'TC' => 'Total biaya tercatat', 'TR' => 'Penerimaan lunas', 'Q' => 'Berat terjual', 'L' => 'Surplus / pendapatan usaha', 'RC' => 'R/C', 'BC' => 'B/C sederhana', 'P' => 'Harga rata-rata tertimbang', 'BEP_Q' => 'BEP kuantitas', 'BEP_PRICE' => 'BEP harga', 'NPV' => 'Nilai kini bersih', 'IRR' => 'Tingkat pengembalian internal', 'PP' => 'Waktu pengembalian', 'DPP' => 'Waktu pengembalian terdiskonto'];

    public function csv(array $document): string
    {
        $stream = fopen('php://temp', 'w+b');
        if ($stream === false) {
            throw new \RuntimeException('CSV buffer unavailable');
        }
        try {
            if (fwrite($stream, "\xEF\xBB\xBF") !== 3) {
                throw new \RuntimeException('CSV header write failed');
            }
            $this->csvRow($stream, ['field', 'type', 'value', 'json_value', 'text_escaped']);
            foreach ($this->leaves($document) as [$path,$value]) {
                $text = match (true) {
                    $value === null => 'Belum diketahui',is_bool($value) => $value ? 'true' : 'false',is_array($value) => CanonicalJson::encode($value),default => (string) $value
                };
                $safe = self::csvText($text);
                $this->csvRow($stream, [self::csvText($path), get_debug_type($value), $safe, CanonicalJson::encode($value), $safe === $text ? 'false' : 'true']);
            }
            rewind($stream);
            $bytes = stream_get_contents($stream);
            if ($bytes === false) {
                throw new \RuntimeException('CSV read failed');
            }

            return $bytes;
        } finally {
            fclose($stream);
        }
    }

    private function csvRow(mixed $stream, array $columns): void
    {
        if (fputcsv($stream, $columns, ',', '"', '', "\r\n") === false) {
            throw new \RuntimeException('CSV row write failed');
        }
    }

    /** Protect every human text cell, including leading controls/Unicode spaces. */
    public static function csvText(string $text): string
    {
        return preg_match('/^[\s\p{Z}\p{C}]*[=+\-@]/u', $text) || preg_match('/^[\t\r\n]/', $text) ? "'".$text : $text;
    }

    private function leaves(mixed $value, string $path = ''): iterable
    {
        if (! is_array($value) || $value === []) {
            yield [$path, $value];

            return;
        }
        foreach ($value as $key => $item) {
            $token = str_replace(['~', '/'], ['~0', '~1'], (string) $key);
            yield from $this->leaves($item, $path.'/'.$token);
        }
    }

    private static function escape(string $value): string
    {
        return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
    }

    /** Break long identifiers/numeric strings for pagination, without rounding or truncation. */
    private static function printable(string $value): string
    {
        $escaped = self::escape($value);

        return nl2br(preg_replace('/([A-Za-z0-9._\-]{32})(?=[A-Za-z0-9._\-])/u', '$1&#8203;', $escaped));
    }

    private function detail(mixed $value, string $label): string
    {
        $text = match (true) {
            $value === null => 'Belum diketahui',is_bool($value) => $value ? 'Ya' : 'Tidak',is_array($value) => json_encode($value, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR),default => (string) $value
        };

        return '<p class="detail"><strong>'.self::escape($label).':</strong> '.self::printable($text).'</p>';
    }

    private function metricValue(array $metric): string
    {
        if ($metric['value'] === null) {
            return $metric['status'] === 'not_recovered' ? 'Belum kembali dalam periode skenario' : 'Belum dapat dihitung';
        }
        $negative = str_starts_with($metric['value'], '-');
        $parts = explode('.', ltrim($metric['value'], '-'));
        $number = ($negative ? '-' : '').preg_replace('/\B(?=(\d{3})+(?!\d))/', '.', $parts[0]).(isset($parts[1]) ? ','.$parts[1] : '');

        return match ($metric['unit']) {
            'IDR' => 'Rp'.$number,'percent' => $number.'%','year' => $number.' tahun',default => $number.' '.$metric['unit']
        };
    }

    private const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des'];

    private static function day(?string $date): string
    {
        if ($date === null || $date === '') {
            return 'Belum diisi';
        }
        $at = \Carbon\CarbonImmutable::parse($date, 'Asia/Jakarta');

        return $at->day.' '.self::MONTHS[$at->month - 1].' '.$at->year;
    }

    private static function moment(?string $value): string
    {
        if ($value === null || $value === '') {
            return 'Belum diketahui';
        }
        $at = \Carbon\CarbonImmutable::parse($value)->setTimezone('Asia/Jakarta');

        return $at->day.' '.self::MONTHS[$at->month - 1].' '.$at->year.' '.$at->format('H.i').' WIB';
    }

    private function fact(string $label, string $value, bool $strong = false): string
    {
        return '<tr><td class="label">'.self::escape($label).'</td><td'.($strong ? ' class="value"' : '').'>'.self::escape($value).'</td></tr>';
    }

    /** "Rp4.500.000", "−Rp4.150.000", "9,70%", "2,5 tahun", "10 kg": what a person reads, not the stored precision. */
    private function plainValue(array $metric): string
    {
        if ($metric['value'] === null) {
            return $metric['status'] === 'not_recovered' ? 'Belum kembali dalam periode skenario' : 'Belum dapat dihitung';
        }
        $value = (float) $metric['value'];
        $digits = match ($metric['unit']) { 'IDR' => (fmod(abs($value), 1.0) > 0 ? 2 : 0), 'percent' => 2, 'year' => 1, default => 2 };
        $number = number_format(abs($value), $digits, ',', '.');
        if ($metric['unit'] !== 'IDR' && $digits > 0) {
            $number = rtrim(rtrim($number, '0'), ',');
        }
        $sign = $value < 0 ? '−' : '';

        return match ($metric['unit']) {
            'IDR' => $sign.'Rp'.$number, 'percent' => $sign.$number.'%', 'year' => $sign.$number.' tahun', 'kg' => $sign.$number.' kg', default => $sign.$number.' '.$metric['unit']
        };
    }

    public function html(array $document): string
    {
        $investment = $document['kind'] === 'investment_report';
        $scope = $document['scope'];
        $title = $investment ? 'Evaluasi Investasi' : 'Evaluasi Musim';
        $html = '<!doctype html><html lang="id"><head><meta charset="utf-8"><style>@page{margin:38px 42px 48px}body{font-family:"DejaVu Sans",sans-serif;font-size:12px;line-height:1.45;color:#172a24}h1{font-size:23px;color:#14532d;margin:4px 0 14px}h2{font-size:14px;color:#14532d;page-break-after:avoid;margin:18px 0 7px}h3{font-size:11px;page-break-after:avoid;margin:12px 0 5px}p{margin:5px 0;word-wrap:break-word}.brand{font-size:10px;letter-spacing:2px;color:#527060}.value{font-size:17px;font-weight:bold}.detail{font-size:10px;white-space:normal}.source-compact{page-break-inside:avoid}.section{page-break-before:always}.rule{border-top:1px solid #c8d9ce;margin:12px 0}.muted{color:#527060}table.facts{border-collapse:collapse;width:100%;margin:6px 0}table.facts td{padding:5px 6px;border-bottom:1px solid #e1ebe4;vertical-align:top}table.facts td.label{color:#527060;width:42%}</style></head><body><p class="brand">INOFARM · LAPORAN</p><h1>'.$title.'</h1>';
        // Page 1 is for the owner or investor (QA 28 Nov 2026): plain names, rupiah, kg and WIB dates, and for a season
        // only the figures the app shows (no profit or ratios). Everything stored stays in the appendix that follows.
        $html .= '<table class="facts">';
        $html .= $this->fact('Greenhouse', $scope['greenhouse']['name']);
        if ($investment) {
            $html .= $this->fact('Skenario', $scope['scenario']['name']);
            $html .= $this->fact('Tanggal dasar', self::day($scope['scenario']['base_date'] ?? null).' · '.($scope['scenario']['horizon_years'] ?? '').' tahun');
        } else {
            $html .= $this->fact('Musim', $scope['season']['name']);
            $end = $scope['season']['end_date'] ?? null;
            $html .= $this->fact('Periode', self::day($scope['season']['start_date'] ?? null).' sampai '.($end ? self::day($end) : 'sekarang'));
        }
        $state = match ($document['report_state'] ?? null) {
            'provisional' => 'Sementara', 'reviewed_final' => 'Final', default => (string) ($document['report_state'] ?? '')
        };
        $html .= $this->fact('Status', $state);
        $html .= $this->fact('Diterbitkan', self::moment($document['generated_at'] ?? null).' oleh '.($document['recorded_by_name'] ?? ''));
        if (($document['review_note'] ?? '') !== '') {
            $html .= $this->fact('Catatan', (string) $document['review_note']);
        }
        $html .= '</table>';
        $shown = $investment ? ['NPV' => 'Nilai kini bersih (NPV)', 'IRR' => 'Tingkat pengembalian (IRR)', 'PP' => 'Waktu pengembalian', 'DPP' => 'Waktu pengembalian terdiskonto']
            : ['FC' => 'Biaya tetap (FC)', 'VC' => 'Biaya variabel (VC)', 'TC' => 'Biaya total (TC)', 'TR' => 'Penjualan lunas', 'Q' => 'Berat terjual'];
        $html .= '<h2>Ringkasan</h2><table class="facts">';
        foreach ($shown as $key => $label) {
            if (isset($document['metrics'][$key])) {
                $html .= $this->fact($label, $this->plainValue($document['metrics'][$key]), true);
            }
        }
        $html .= '</table>';
        if ($investment && isset($document['metrics']['NPV']['value'])) {
            $positive = ! str_starts_with((string) $document['metrics']['NPV']['value'], '-');
            $html .= '<p>'.self::escape($positive ? 'Nilai kas masa depan menutup investasi awal pada diskonto skenario: layak dari sisi NPV.'
                : 'Nilai kas masa depan belum menutup investasi awal pada diskonto skenario: belum layak dari sisi NPV.').'</p>';
        }
        if (($document['report_state'] ?? null) === 'provisional') {
            $html .= '<p class="muted">Laporan sementara: bisa berubah bila ada biaya atau penjualan yang belum tercatat.</p>';
        }
        $html .= '<p class="muted">Angka di atas diambil dari laporan yang sama dengan di aplikasi. Lampiran berikut berisi seluruh data tersimpan untuk pemeriksaan.</p>';
        $html .= '<div class="section"><h2>Lampiran untuk pemeriksa</h2>';
        foreach (['id' => 'Nomor laporan', 'version' => 'Versi laporan', 'generated_at' => 'Diterbitkan (UTC)', 'currency' => 'Mata uang', 'timezone' => 'Zona waktu sumber', 'formula_version' => 'Versi rumus', 'snapshot_hash' => 'SHA256 snapshot', 'sources_hash' => 'SHA256 sumber', 'source_count' => 'Jumlah sumber'] as $key => $label) {
            $html .= $this->detail($document[$key] ?? null, $label);
        }
        $ordered = array_replace(array_intersect_key(self::LABELS, $document['metrics']), array_diff_key(array_fill_keys(array_keys($document['metrics']), ''), self::LABELS));
        $html .= '<h2>Seluruh indikator tersimpan</h2>';
        foreach ($ordered as $key => $label) {
            $html .= '<p><strong>'.self::escape($key.' · '.($label ?: $key)).':</strong> '.self::printable($this->metricValue($document['metrics'][$key])).'</p>';
        }
        $html .= '</div>';
        $html .= '<div class="section"><h2>Rincian perhitungan tersimpan</h2>';
        foreach ($ordered as $key => $label) {
            $metric = $document['metrics'][$key];
            $html .= '<h2>'.self::escape($key.' · '.(self::LABELS[$key] ?? $key)).'</h2>';
            $value = $this->metricValue($metric);
            $html .= '<p class="value">'.self::printable($value).'</p>';
            $html .= $this->detail($metric['value'], 'Nilai tersimpan (desimal API)');
            foreach (['status' => 'Status indikator', 'reasons' => 'Alasan', 'formula' => 'Rumus', 'inputs' => 'Input tersimpan', 'assumptions' => 'Asumsi indikator', 'display_scale' => 'Skala tampilan', 'rounding' => 'Pembulatan', 'exact_fraction' => 'Pecahan sebelum pembulatan', 'calculation_detail' => 'Rincian metode'] as $field => $label) {
                if (array_key_exists($field, $metric)) {
                    $shown = $metric[$field];
                    if ($field === 'status') {
                        $shown = match ($shown) {
                            'available' => 'Terhitung dari input yang dinyatakan','provisional' => 'Sementara','not_recovered' => 'Belum kembali dalam periode skenario',default => 'Belum dapat dihitung'
                        };
                    }
                    if ($field === 'reasons' && $shown === []) {
                        $shown = 'Tidak ada alasan tambahan.';
                    }
                    $html .= $this->detail($shown, $label);
                }
            }
        }
        $html .= '</div><div class="section"><h2>Cakupan, filter dan asumsi tersimpan</h2>';
        foreach ($document as $key => $value) {
            if (! in_array($key, ['metrics', 'sources'], true)) {
                $html .= $this->detail($value, $key);
            }
        }
        $html .= '</div><div class="section"><h2>Seluruh sumber dan versi tersimpan</h2>';
        foreach ($document['sources'] as $index => $source) {
            $html .= '<div class="'.(strlen(json_encode($source, JSON_THROW_ON_ERROR)) < 2000 ? 'source-compact' : 'source-flow').'">';
            $html .= '<h3>Sumber '.($index + 1).' · '.self::escape($source['kind']).' · versi '.self::escape((string) $source['version']).'</h3>';
            foreach ($source as $key => $value) {
                $html .= $this->detail($value, $key);
            }
            $html .= '</div>';
        }

        return $html.'</div></body></html>';
    }

    public function pdf(array $document): string
    {
        $cache = storage_path('framework/cache/report-export');
        if (! is_dir($cache) && ! mkdir($cache, 0770, true) && ! is_dir($cache)) {
            throw new \RuntimeException('PDF cache directory unavailable');
        }
        $font = base_path('vendor/dompdf/dompdf/lib/fonts');
        $options = new Options;
        $options->setIsRemoteEnabled(false);
        $options->setIsPhpEnabled(false);
        $options->setIsJavascriptEnabled(false);
        $options->setChroot($font);
        $options->setFontDir($font);
        $options->setFontCache($cache);
        $options->setTempDir($cache);
        $options->setDefaultFont('DejaVu Sans');
        $options->setAllowedProtocols(['file://']);
        $pdf = new Dompdf($options);
        $pdf->setPaper('A4', 'portrait');
        $pdf->loadHtml($this->html($document), 'UTF-8');
        $pdf->addInfo('Title', 'Inofarm '.$document['kind'].' '.$document['id']);
        $pdf->addInfo('Subject', 'Snapshot SHA256 '.$document['snapshot_hash']);
        $pdf->render();
        $canvas = $pdf->getCanvas();
        $canvas->page_text(42, 811, 'Inofarm · {PAGE_NUM} / {PAGE_COUNT}', $pdf->getFontMetrics()->getFont('DejaVu Sans'), 8, [0.2, 0.3, 0.25]);

        return $pdf->output();
    }
}
