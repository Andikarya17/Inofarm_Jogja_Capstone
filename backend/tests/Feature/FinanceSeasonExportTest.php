<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;
use ZipArchive;

/** Season workbook: three sheets, numbers as numeric cells, blank costs left empty. */
class FinanceSeasonExportTest extends FinanceTestCase
{
    private array $temp = [];

    protected function tearDown(): void
    {
        foreach ($this->temp as $file) {
            @unlink($file);
        }
        parent::tearDown();
    }

    private function export(string $query = '?sequence=1&parts=summary,costs,sales')
    {
        return $this->get('/api/v2/reports/season-export'.$query);
    }

    /** @return array<string,string> entry name => xml */
    private function unzip(string $bytes): array
    {
        $path = tempnam(sys_get_temp_dir(), 'xlsx');
        $this->temp[] = $path;
        file_put_contents($path, $bytes);
        $zip = new ZipArchive;
        $this->assertTrue($zip->open($path) === true);
        $entries = [];
        for ($i = 0; $i < $zip->numFiles; $i++) {
            $entries[$zip->getNameIndex($i)] = $zip->getFromIndex($i);
        }
        $zip->close();

        return $entries;
    }

    private function workbook(string $query = '?sequence=1&parts=summary,costs,sales'): array
    {
        $response = $this->export($query);
        $response->assertOk();

        return $this->unzip($response->getContent());
    }

    private function sold(array $ctx, string $kg, string $price, int $fruit, string $variety = 'Golden'): void
    {
        DB::table('varieties')->where('id', $ctx['variety']['id'])->update(['name' => $variety]);
        $item = (string) Str::uuid();
        $harvest = $this->operation('harvest', ['planting_group_id' => $ctx['group']['id'], 'occurred_at' => '2026-11-17T12:00:00+07:00', 'notes' => '',
            'items' => [['id' => $item, 'quality' => 'saleable', 'fruit_count' => null, 'weight_kg' => '200']]]);
        $this->postJson('/api/v2/harvests', $harvest)->assertCreated();
        $grade = DB::table('harvest_items')->where('id', $item)->value('grade_id');
        $this->postJson('/api/v2/sales', $this->operation('sale', ['season_id' => $ctx['season']['id'], 'paid_at' => '2026-11-20T10:00:00+07:00',
            'payment_status' => 'paid', 'customer_name' => 'Toko Sari', 'notes' => '', 'customer_type' => 'retail',
            'lines' => [['id' => (string) Str::uuid(), 'planting_group_id' => $ctx['group']['id'], 'grade_id' => $grade, 'quantity_kg' => $kg,
                'fruit_count' => $fruit, 'price_per_kg' => $price, 'buyer_grade' => null]]]))->assertCreated();
    }

    public function test_workbook_is_valid_zip_with_three_sheets(): void
    {
        $this->context($this->account());

        $entries = $this->workbook();

        foreach (['[Content_Types].xml', '_rels/.rels', 'xl/workbook.xml', 'xl/_rels/workbook.xml.rels', 'xl/styles.xml', 'xl/worksheets/sheet1.xml', 'xl/worksheets/sheet2.xml', 'xl/worksheets/sheet3.xml'] as $name) {
            $this->assertArrayHasKey($name, $entries);
        }
        foreach (['Ringkasan per GH', 'Rincian biaya', 'Daftar penjualan'] as $sheet) {
            $this->assertStringContainsString('name="'.$sheet.'"', $entries['xl/workbook.xml']);
        }
        $this->assertStringContainsString('Musim 1 · diunduh', $entries['xl/worksheets/sheet1.xml']);
        $this->assertStringContainsString('WIB', $entries['xl/worksheets/sheet1.xml']);
    }

    public function test_response_headers(): void
    {
        $this->context($this->account());

        $response = $this->export();

        $response->assertOk();
        $this->assertSame('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', $response->headers->get('Content-Type'));
        $this->assertMatchesRegularExpression('/^attachment; filename="?inofarm-keuangan-musim-1-\d{8}\.xlsx"?$/', $response->headers->get('Content-Disposition'));
        $this->assertSame(hash('sha256', $response->getContent()), $response->headers->get('X-Content-SHA256'));
    }

    public function test_summary_numbers_are_numeric_cells(): void
    {
        $this->context($this->account());
        $this->standardCosts('7581100.00', '3430000.00');

        $sheet = $this->workbook()['xl/worksheets/sheet1.xml'];

        $this->assertMatchesRegularExpression('/<c [^>]*t="n"[^>]*><v>11011100<\/v><\/c>/', $sheet);
        $this->assertStringContainsString('GH sintetis', $sheet);
        $this->assertStringContainsString('1 GH', $sheet);
    }

    public function test_workbook_leaves_blank_cost_cells_empty(): void
    {
        $this->context($this->account());

        $sheet = $this->workbook('?sequence=1&parts=summary')['xl/worksheets/sheet1.xml'];

        $this->assertStringContainsString('<c r="B3"', $sheet);
        $this->assertDoesNotMatchRegularExpression('/<c r="[CDE][34]"/', $sheet);
    }

    public function test_sales_sheet_lists_lines_with_variety_quality_and_fruit(): void
    {
        $ctx = $this->context($this->account());
        $this->sold($ctx, '50', '28000', 38);

        $sheet = $this->workbook()['xl/worksheets/sheet3.xml'];

        foreach (['Golden', 'Layak', 'Toko Sari', 'Retail', '20 Nov 2026'] as $text) {
            $this->assertStringContainsString($text, $sheet);
        }
        foreach (['50', '38', '28000', '1400000'] as $number) {
            $this->assertMatchesRegularExpression('/t="n"[^>]*><v>'.$number.'<\/v>/', $sheet);
        }
    }

    public function test_parts_filter_and_validation(): void
    {
        $this->context($this->account());

        $entries = $this->workbook('?sequence=1&parts=summary');
        $this->assertArrayHasKey('xl/worksheets/sheet1.xml', $entries);
        $this->assertArrayNotHasKey('xl/worksheets/sheet2.xml', $entries);
        $this->assertStringContainsString('Ringkasan per GH', $entries['xl/workbook.xml']);

        $this->export('?sequence=1&parts=foo')->assertStatus(422);
        $this->export('?sequence=1&parts=')->assertStatus(422);
        $this->export('?sequence=1')->assertStatus(422);
        $this->export('?sequence=x&parts=summary')->assertStatus(422);
        $this->export('?sequence=9&parts=summary')->assertNotFound();
    }

    public function test_export_all_sequences(): void
    {
        $manager = $this->account();
        $this->context($manager);
        $b = $this->context($manager);
        DB::table('seasons')->where('id', $b['season']['id'])->update(['sequence' => 2, 'status' => 'closed']);

        $response = $this->export('?sequence=all&parts=summary');
        $response->assertOk();
        $sheet = $this->unzip($response->getContent())['xl/worksheets/sheet1.xml'];

        $this->assertStringContainsString('Semua musim · diunduh', $sheet);
        $this->assertMatchesRegularExpression('/<c r="A3"[^>]*><v>2<\/v>/', $sheet);
        $this->assertMatchesRegularExpression('/<c r="A5"[^>]*><v>1<\/v>/', $sheet);
        $this->assertStringContainsString('filename="inofarm-keuangan-musim-semua-', $response->headers->get('Content-Disposition'));
    }

    public function test_worker_forbidden_export(): void
    {
        $this->context($this->account());
        $this->asUser($this->account('worker'))->get('/api/v2/reports/season-export?sequence=1&parts=summary')->assertForbidden();
    }
}
