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
            return $metric['status'] === 'not_recovered' ? 'Belum kembali dalam horizon' : 'Belum dapat dihitung';
        }
        $negative = str_starts_with($metric['value'], '-');
        $parts = explode('.', ltrim($metric['value'], '-'));
        $number = ($negative ? '-' : '').preg_replace('/\B(?=(\d{3})+(?!\d))/', '.', $parts[0]).(isset($parts[1]) ? ','.$parts[1] : '');

        return match ($metric['unit']) {
            'IDR' => 'Rp'.$number,'percent' => $number.'%','year' => $number.' tahun',default => $number.' '.$metric['unit']
        };
    }

    public function html(array $document): string
    {
        $investment = $document['kind'] === 'investment_report';
        $scope = $document['scope'];
        $title = $investment ? 'Evaluasi Investasi' : 'Evaluasi Musim';
        $html = '<!doctype html><html lang="id"><head><meta charset="utf-8"><style>@page{margin:38px 42px 48px}body{font-family:"DejaVu Sans",sans-serif;font-size:12px;line-height:1.45;color:#172a24}h1{font-size:23px;color:#14532d;margin:4px 0 14px}h2{font-size:14px;color:#14532d;page-break-after:avoid;margin:18px 0 7px}h3{font-size:11px;page-break-after:avoid;margin:12px 0 5px}p{margin:5px 0;word-wrap:break-word}.brand{font-size:10px;letter-spacing:2px;color:#527060}.value{font-size:17px;font-weight:bold}.detail{font-size:10px;white-space:normal}.source-compact{page-break-inside:avoid}.section{page-break-before:always}.rule{border-top:1px solid #c8d9ce;margin:12px 0}.muted{color:#527060}</style></head><body><p class="brand">INOFARM · LAPORAN TERSIMPAN</p><h1>'.$title.'</h1>';
        $html .= $this->detail($scope['greenhouse']['name'], 'Greenhouse');
        $html .= $this->detail($investment ? $scope['scenario']['name'] : $scope['season']['name'], $investment ? 'Skenario' : 'Musim');
        foreach (['id' => 'Nomor laporan', 'version' => 'Versi laporan', 'generated_at' => 'Diterbitkan (UTC)', 'recorded_by_name' => 'Pengelola', 'report_state' => 'Status tersimpan', 'review_note' => 'Catatan tinjauan', 'currency' => 'Mata uang', 'timezone' => 'Zona waktu sumber', 'formula_version' => 'Versi rumus', 'snapshot_hash' => 'SHA256 snapshot', 'sources_hash' => 'SHA256 sumber', 'source_count' => 'Jumlah sumber'] as $key => $label) {
            $value = $document[$key] ?? null;
            if ($key === 'report_state') {
                $value = match ($value) {
                    'provisional' => 'Sementara', 'reviewed_final' => 'Tinjauan final tersimpan', default => $value
                };
            }
            $html .= $this->detail($value, $label);
        }
        $html .= '<div class="rule"></div><p class="muted">Ekspor memakai snapshot yang sama dengan laporan yang dibuka. Perubahan sumber setelah penerbitan tidak menghitung ulang laporan ini. Nilai kosong tidak diubah menjadi nol.</p>';
        $ordered = array_replace(array_intersect_key(self::LABELS, $document['metrics']), array_diff_key(array_fill_keys(array_keys($document['metrics']), ''), self::LABELS));
        $html .= '<h2>Ringkasan indikator</h2>';
        foreach ($ordered as $key => $label) {
            $html .= '<p><strong>'.self::escape($key.' · '.($label ?: $key)).':</strong> '.self::printable($this->metricValue($document['metrics'][$key])).'</p>';
        }
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
                            'available' => 'Terhitung dari input yang dinyatakan','provisional' => 'Sementara','not_recovered' => 'Belum kembali dalam horizon',default => 'Belum dapat dihitung'
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
