<?php

namespace Tests\Feature;

use App\Domain\Contracts\CanonicalJson;
use App\Domain\Finance\ReportDocumentRenderer;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;

class FinanceReportExportTest extends FinanceTestCase
{
    private function prepared(string $kind = 'investment'): array
    {
        $actor = $this->account();
        $ctx = $this->context($actor);
        if ($kind === 'investment') {
            $p = ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Skenario α <img src="file:///never-read">', 'base_date' => '2026-01-01', 'horizon_years' => 2, 'initial_outlay' => '100000000.00', 'initial_source' => ['kind' => 'actual', 'reference' => 'Rekap sintetis', 'basis' => 'Awal sekali'], 'discount_rate' => '0.10', 'payback_convention' => 'interpolation', 'cash_flows' => array_map(fn ($year) => ['year' => $year, 'amount' => '60000000.00', 'source_kind' => 'projection', 'source_reference' => 'Sumber sintetis', 'basis' => 'Kas neto tahunan'], [1, 2]), 'notes' => "\t=HYPERLINK(\"https://example.invalid\")\n<script>not executable</script>"];
            $scenario = $this->postJson('/api/v2/investment-scenarios', $this->operation('investment_scenario', $p))->assertCreated()->json('data');
            $input = ['scenario_id' => $scenario['id'], 'review_note' => 'Tinjauan α sintetis'];
            $preview = $this->postJson('/api/v2/investment-reports/preview', $input)->assertOk()->json();
            $op = $this->operation('investment_report', [...$input, 'expected_scenario_version' => $preview['scenario_version'], 'expected_sources_hash' => $preview['sources_hash'], 'expected_formula_version' => $preview['formula_version']]);
            $receipt = $this->postJson('/api/v2/investment-reports', $op)->assertCreated()->json();
            $ctx += ['scenario' => $scenario, 'scenario_payload' => $p];
        } else {
            $cat = $this->createMaster('expense_categories', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Sumber ekspor']);
            $payment = $this->operation('expense', ['greenhouse_id' => $ctx['gh']['id'], 'category_id' => $cat['id'], 'amount' => '100.00', 'paid_at' => '2026-10-30T08:00:00+07:00', 'payment_kind' => 'operating', 'notes' => 'Sumber sintetis']);
            $this->postJson('/api/v2/expenses', $payment)->assertCreated();
            $allocation = $this->operation('cost_allocation', ['expense_id' => $payment['entity_id'], 'season_id' => $ctx['season']['id'], 'amount' => '40.00', 'cost_class' => 'FC', 'allocation_basis' => 'Alokasi manual']);
            $this->postJson('/api/v2/cost-allocations', $allocation)->assertCreated();
            $input = ['season_id' => $ctx['season']['id'], 'coverage' => ['costs' => 'complete', 'sales' => 'unknown'], 'known_pending_count' => null, 'requested_state' => 'provisional', 'review_note' => 'Laporan musim sintetis'];
            $preview = $this->postJson('/api/v2/season-reports/preview', $input)->assertOk()->json();
            $receipt = $this->postJson('/api/v2/season-reports', $this->operation('season_report', [...$input, 'expected_sources_hash' => $preview['sources_hash'], 'expected_formula_version' => $preview['formula_version']]))->assertCreated()->json();
            $ctx += ['allocation' => $allocation];
        }

        return [...$ctx, 'actor' => $actor, 'receipt' => $receipt, 'data' => $receipt['data']];
    }

    private function path(array $ctx, string $format = 'csv'): string
    {
        return '/api/v2/report-exports/'.$ctx['data']['id'].'/'.$format.'?expected_snapshot_hash='.$ctx['data']['snapshot_hash'];
    }

    private function cells(string $csv): array
    {
        $stream = fopen('php://temp', 'w+b');
        fwrite($stream, substr($csv, 3));
        rewind($stream);
        $header = fgetcsv($stream, escape: '');
        $rows = [];
        while (($row = fgetcsv($stream, escape: '')) !== false) {
            $rows[$row[0]] = array_combine($header, $row);
        }
        fclose($stream);

        return $rows;
    }

    public function test_real_pdf_and_csv_both_report_kinds_match_saved_document(): void
    {
        foreach (['investment', 'season'] as $kind) {
            $ctx = $this->prepared($kind);
            $csv = $this->get($this->path($ctx))->assertOk()->assertHeader('X-Report-Snapshot-Hash', $ctx['data']['snapshot_hash']);
            $this->assertSame(hash('sha256', $csv->getContent()), $csv->headers->get('X-Content-SHA256'));
            $this->assertSame((string) strlen($csv->getContent()), $csv->headers->get('Content-Length'));
            $rows = $this->cells($csv->getContent());
            $key = $kind === 'investment' ? 'NPV' : 'TC';
            $expected = $kind === 'investment' ? '4132231.40' : '40.00';
            $this->assertSame($expected, json_decode($rows['/metrics/'.$key.'/value']['json_value'], true));
            $this->assertSame($ctx['data']['snapshot_hash'], json_decode($rows['/snapshot_hash']['json_value'], true));
            $pdf = $this->get($this->path($ctx, 'pdf'))->assertOk()->assertHeader('Content-Type', 'application/pdf')->assertHeader('X-Content-Type-Options', 'nosniff');
            $this->assertSame((string) strlen($pdf->getContent()), $pdf->headers->get('Content-Length'));
            $this->assertStringStartsWith('%PDF-', $pdf->getContent());
            $this->assertGreaterThan(2000, strlen($pdf->getContent()));
            $this->assertSame(hash('sha256', $pdf->getContent()), $pdf->headers->get('X-Content-SHA256'));
            $this->assertStringContainsString('private', $pdf->headers->get('Cache-Control'));
            $this->assertStringContainsString('no-store', $pdf->headers->get('Cache-Control'));
            file_put_contents(base_path('../tmp/fin14-'.$kind.'.pdf'), $pdf->getContent());
            file_put_contents(base_path('../tmp/fin14-'.$kind.'.csv'), $csv->getContent());
            $row = DB::table('report_snapshots')->where('id', $ctx['data']['id'])->first();
            $full = json_decode($row->content, true);
            $this->assertSame($row->snapshot_hash, CanonicalJson::hash($full));
            file_put_contents(base_path('../tmp/fin14-'.$kind.'-document.json'), json_encode([...$full, 'snapshot_hash' => $row->snapshot_hash, 'export_schema_version' => ReportDocumentRenderer::VERSION], JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR));
        }
    }

    public function test_source_correction_never_changes_old_export(): void
    {
        $ctx = $this->prepared('season');
        $first = $this->get($this->path($ctx))->assertOk()->getContent();
        $allocation = $ctx['allocation'];
        $this->patchJson('/api/v2/cost-allocations/'.$allocation['entity_id'], $this->operation('cost_allocation', [...$allocation['payload'], 'amount' => '60.00', 'reason' => 'Koreksi sumber setelah laporan'], 'update', $allocation['entity_id'], 1))->assertOk();
        $again = $this->get($this->path($ctx))->assertOk()->getContent();
        $this->assertSame($first, $again);
        $this->assertSame('40.00', json_decode($this->cells($again)['/metrics/TC/value']['json_value'], true));
    }

    public function test_current_worker_foreign_revoked_scope_and_bad_hash_cannot_download(): void
    {
        $ctx = $this->prepared();
        $this->get('/api/v2/report-exports/'.$ctx['data']['id'].'/csv')->assertUnprocessable();
        $this->get('/api/v2/report-exports/'.$ctx['data']['id'].'/csv?expected_snapshot_hash='.str_repeat('0', 64))->assertConflict();
        $this->get($this->path($ctx).'&filename=bad')->assertUnprocessable();
        $this->get(str_replace('/csv?', '/exe?', $this->path($ctx)))->assertNotFound();
        $this->asUser($this->account('worker'));
        foreach (['pdf', 'csv'] as $f) {
            $this->get($this->path($ctx, $f))->assertForbidden();
        }
        $this->asUser($this->account());
        $this->get($this->path($ctx))->assertNotFound();
        $this->asUser($ctx['actor']);
        DB::table('greenhouse_assignments')->where('user_id', $ctx['actor']->id)->update(['active' => false]);
        $this->get($this->path($ctx))->assertNotFound();
    }

    public function test_permission_and_token_are_checked_again_after_rendering(): void
    {
        $ctx = $this->prepared();
        $real = new ReportDocumentRenderer;
        $renderer = \Mockery::mock(ReportDocumentRenderer::class);
        $renderer->shouldReceive('csv')->once()->andReturnUsing(function ($document) use ($ctx, $real) {
            DB::table('personal_access_tokens')->where('tokenable_id', $ctx['actor']->id)->delete();

            return $real->csv($document);
        });
        $this->app->instance(ReportDocumentRenderer::class, $renderer);
        $this->get($this->path($ctx))->assertUnauthorized();
    }

    public function test_epoch_change_and_corrupt_snapshot_do_not_return_a_file(): void
    {
        $ctx = $this->prepared();
        $real = new ReportDocumentRenderer;
        $renderer = \Mockery::mock(ReportDocumentRenderer::class);
        $renderer->shouldReceive('csv')->once()->andReturnUsing(function ($document) use ($real) {
            DB::table('sync_state')->where('id', 1)->update(['epoch' => (string) Str::uuid()]);

            return $real->csv($document);
        });
        $this->app->instance(ReportDocumentRenderer::class, $renderer);
        $this->get($this->path($ctx))->assertConflict();
        $this->app->instance(ReportDocumentRenderer::class, $real);
        $row = DB::table('report_snapshots')->where('id', $ctx['data']['id'])->first();
        $body = json_decode($row->content, true);
        $body['metrics']['NPV']['value'] = '999.00';
        DB::table('report_snapshots')->where('id', $row->id)->update(['content' => json_encode($body)]);
        $this->get($this->path($ctx))->assertStatus(500);
    }
}
