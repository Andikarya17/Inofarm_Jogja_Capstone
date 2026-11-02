<?php

namespace Tests\Unit;

use App\Domain\Finance\ReportDocumentRenderer;
use PHPUnit\Framework\TestCase;

class ReportDocumentRendererTest extends TestCase
{
    public function test_csv_formula_text_and_unicode_controls_are_escaped(): void
    {
        foreach (['=1+1', '+cmd', '-1+2', '@SUM(A1)', "\t=cmd", "\u{feff}=cmd", "\u{200b} @cmd", "\x01=cmd"] as $value) {
            $this->assertSame("'".$value, ReportDocumentRenderer::csvText($value));
        }
        foreach (['0.00', 'α referensi', 'https://example.invalid', 'text =1'] as $value) {
            $this->assertSame($value, ReportDocumentRenderer::csvText($value));
        }
    }

    public function test_html_user_content_is_text_and_metrics_are_never_recalculated(): void
    {
        $f = json_decode(file_get_contents(dirname(__DIR__, 3).'/contracts/v2/fixtures/investment-report-public.json'), true);
        $document = [...$f['full'], 'snapshot_hash' => $f['receipt']['data']['snapshot_hash'], 'export_schema_version' => ReportDocumentRenderer::VERSION];
        $document['review_note'] = '<script>alert(1)</script><img src="https://example.invalid">';
        $html = (new ReportDocumentRenderer)->html($document);
        $this->assertStringNotContainsString('<script>', $html);
        $this->assertStringNotContainsString('<img ', $html);
        $this->assertStringContainsString('&lt;script&gt;', $html);
        $this->assertStringContainsString('Rp4.132.231,40', $html);
        $this->assertStringContainsString($document['snapshot_hash'], str_replace('&#8203;', '', $html));
    }
}
