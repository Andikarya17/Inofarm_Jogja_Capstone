<?php

namespace Tests\Feature;

use App\Domain\Contracts\CanonicalJson;
use Illuminate\Database\Events\QueryExecuted;
use Illuminate\Support\Facades\DB;
use Tests\FinanceTestCase;

class FinanceInvestmentTest extends FinanceTestCase
{
    private function prepared(): array
    {
        $actor = $this->account();
        $ctx = $this->context($actor);
        $payload = ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Skenario sintetis terpisah dari laba musim', 'base_date' => '2026-01-01', 'horizon_years' => 2, 'initial_outlay' => '100000000.00',
            'initial_source' => ['kind' => 'actual', 'reference' => 'Rekap investasi sintetis', 'basis' => 'Pengeluaran awal dinyatakan manual satu kali'], 'discount_rate' => '0.10', 'payback_convention' => 'interpolation',
            'cash_flows' => [['year' => 1, 'amount' => '60000000.00', 'source_kind' => 'projection', 'source_reference' => 'Proyeksi1', 'basis' => 'Kas neto tahunan sintetis'], ['year' => 2, 'amount' => '60000000.00', 'source_kind' => 'projection', 'source_reference' => 'Proyeksi2', 'basis' => 'Tanpa penambahan nilai sisa otomatis']], 'notes' => 'Fixture bukan data usaha mitra'];
        $op = $this->operation('investment_scenario', $payload);
        $data = $this->postJson('/api/v2/investment-scenarios', $op)->assertCreated()->json('data');

        return [...$ctx, 'actor' => $actor, 'payload' => $payload, 'scenario' => $data, 'operation' => $op];
    }

    private function preview(array $ctx): array
    {
        return $this->postJson('/api/v2/investment-reports/preview', ['scenario_id' => $ctx['scenario']['id'], 'review_note' => 'Tinjauan sumber skenario sintetis'])->assertOk()->json();
    }

    private function report(array $preview): array
    {
        return $this->operation('investment_report', ['scenario_id' => $preview['scenario_id'], 'expected_scenario_version' => $preview['scenario_version'], 'expected_sources_hash' => $preview['sources_hash'], 'expected_formula_version' => $preview['formula_version'], 'review_note' => $preview['review_note']]);
    }

    public function test_exact_proposal_snapshot_pagination_and_idempotent_receipt(): void
    {
        $ctx = $this->prepared();
        $this->assertSame('evaluation_ready', $ctx['scenario']['completeness']);
        $preview = $this->preview($ctx);
        foreach (['NPV' => '4132231.40', 'IRR' => '13.06623863', 'PP' => '1.66666667', 'DPP' => '1.91666667'] as $key => $value) {
            $this->assertSame($value, $preview['metrics'][$key]['value']);
        }
        $this->assertSame(['actual' => 1, 'projection' => 2, 'unknown' => 0], $preview['source_summary']);
        $op = $this->report($preview);
        $receipt = $this->postJson('/api/v2/investment-reports', $op)->assertCreated()->json();
        $replay = $this->postJson('/api/v2/investment-reports', $op)->assertCreated()->json();
        $this->assertSame(CanonicalJson::hash($receipt), CanonicalJson::hash($replay));
        $this->assertSame($receipt['data'], $this->getJson('/api/v2/investment-reports/'.$op['entity_id'])->assertOk()->json());
        $keys = [];
        for ($offset = 0; $offset < 3; $offset++) {
            $page = $this->getJson('/api/v2/investment-reports/'.$op['entity_id'].'/sources?limit=1&cursor='.$offset)->assertOk()->json();
            $keys[] = $page['data'][0]['source_key'];
            $this->assertSame(3, $page['source_count']);
        }
        $this->assertSame(['initial_outlay', 'year:1', 'year:2'], $keys);
        foreach (['audit_logs', 'sync_operations', 'change_feed'] as $table) {
            $this->assertSame(1, DB::table($table)->where('entity_type', 'investment_report')->where('entity_id', $op['entity_id'])->count());
        }
        file_put_contents(base_path('../tmp/fin13-public.json'), json_encode(['preview' => $preview, 'receipt' => $receipt, 'full' => json_decode(DB::table('report_snapshots')->where('id', $op['entity_id'])->value('content'), true), 'scenario' => $ctx['scenario']], JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR));
    }

    public function test_scenario_versions_are_append_only_and_old_reports_survive_updates_and_cancel(): void
    {
        $ctx = $this->prepared();
        $preview = $this->preview($ctx);
        $op = $this->report($preview);
        $old = $this->postJson('/api/v2/investment-reports', $op)->assertCreated()->json('data');
        $payload = [...$ctx['payload'], 'initial_outlay' => '150000000.00', 'reason' => 'Perbarui investasi dari bukti sintetis'];
        $this->patchJson('/api/v2/investment-scenarios/'.$ctx['scenario']['id'], $this->operation('investment_scenario', $payload, 'update', $ctx['scenario']['id'], 1))->assertOk()->assertJsonPath('version', 2);
        $this->assertSame(4, DB::table('investment_cash_flow_versions')->where('scenario_id', $ctx['scenario']['id'])->count());
        $this->postJson('/api/v2/investment-reports', $this->report($preview))->assertConflict()->assertJsonPath('code', 'report_sources_changed');
        $newPreview = $this->preview($ctx);
        $this->assertNotSame($preview['sources_hash'], $newPreview['sources_hash']);
        $this->assertSame('not_recovered', $newPreview['metrics']['PP']['status']);
        $next = $this->report($newPreview);
        $this->postJson('/api/v2/investment-reports', $next)->assertCreated();
        $this->assertSame($old, $this->getJson('/api/v2/investment-reports/'.$op['entity_id'])->assertOk()->json());
        $this->postJson('/api/v2/investment-scenarios/'.$ctx['scenario']['id'].'/cancel', $this->operation('investment_scenario', ['reason' => 'Batalkan skenario sintetis'], 'cancel', $ctx['scenario']['id'], 2))->assertOk()->assertJsonPath('version', 3);
        $this->assertSame(6, DB::table('investment_cash_flow_versions')->where('scenario_id', $ctx['scenario']['id'])->count());
        $this->postJson('/api/v2/investment-reports/preview', ['scenario_id' => $ctx['scenario']['id'], 'review_note' => 'Tidak boleh menilai skenario dibatalkan'])->assertUnprocessable();
        $this->postJson('/api/v2/investment-reports', $op)->assertCreated()->assertJsonPath('data.snapshot_hash', $old['snapshot_hash']);
        $this->patchJson('/api/v2/investment-reports/'.$op['entity_id'], [])->assertStatus(405);
    }

    public function test_incomplete_numeric_inputs_and_provenance_remain_explicit_and_independent(): void
    {
        $ctx = $this->prepared();
        $payload = [...$ctx['payload'], 'initial_outlay' => '100.00', 'discount_rate' => null, 'cash_flows' => [['year' => 1, 'amount' => '110.00', 'source_kind' => null, 'source_reference' => '', 'basis' => ''], ['year' => 2, 'amount' => null, 'source_kind' => null, 'source_reference' => '', 'basis' => '']], 'reason' => 'Data tahun kedua belum diketahui'];
        $updated = $this->patchJson('/api/v2/investment-scenarios/'.$ctx['scenario']['id'], $this->operation('investment_scenario', $payload, 'update', $ctx['scenario']['id'], 1))->assertOk()->json('data');
        $this->assertSame('incomplete', $updated['completeness']);
        $this->assertNull($updated['cash_flows'][1]['amount']);
        $preview = $this->preview($ctx);
        $this->assertNull($preview['metrics']['NPV']['value']);
        $this->assertNull($preview['metrics']['DPP']['value']);
        $this->assertSame('0.90909091', $preview['metrics']['PP']['value']);
        $this->assertSame('provisional', $preview['metrics']['PP']['status']);
    }

    public function test_malformed_types_periods_and_duplicate_years_are_rejected(): void
    {
        $ctx = $this->prepared();
        $base = $ctx['payload'];
        $bad = [];
        foreach (['2', true, 0, 101] as $h) {
            $bad[] = [...$base, 'horizon_years' => $h];
        }
        $bad[] = [...$base, 'initial_outlay' => 100.0];
        $bad[] = [...$base, 'initial_outlay' => '-1'];
        $bad[] = [...$base, 'discount_rate' => '-1'];
        $bad[] = [...$base, 'payback_convention' => 'guess'];
        $bad[] = [...$base, 'base_date' => '9999-01-01'];
        $p = $base;
        $p['cash_flows'][0]['year'] = '1';
        $bad[] = $p;
        $p = $base;
        $p['cash_flows'][1]['year'] = 1;
        $bad[] = $p;
        $p = $base;
        $p['cash_flows'][0]['amount'] = false;
        $bad[] = $p;
        $p = $base;
        $p['cash_flows'][0]['surprise'] = 'unknown';
        $bad[] = $p;
        $p = $base;
        $p['cash_flows'] = (object) $p['cash_flows'];
        $bad[] = $p;
        foreach ($bad as $payload) {
            $this->postJson('/api/v2/investment-scenarios', $this->operation('investment_scenario', $payload))->assertUnprocessable();
        }
        $this->assertDatabaseCount('investment_scenarios', 1);
    }

    public function test_current_role_scope_and_assignment_precede_snapshot_replay_and_sources(): void
    {
        $ctx = $this->prepared();
        $op = $this->report($this->preview($ctx));
        $this->postJson('/api/v2/investment-reports', $op)->assertCreated();
        $worker = $this->account('worker');
        $this->asUser($worker)->getJson('/api/v2/investment-scenarios')->assertForbidden();
        $this->getJson('/api/v2/investment-reports/'.$op['entity_id'])->assertForbidden();
        $this->postJson('/api/v2/investment-reports', $op)->assertForbidden();
        $this->assertEmpty(array_filter($this->getJson('/api/v2/sync/changes')->assertOk()->json('data'), fn ($r) => in_array($r['entity_type'], ['investment_scenario', 'investment_report'], true)));
        $this->asUser($this->account())->getJson('/api/v2/investment-reports/'.$op['entity_id'])->assertNotFound();
        $this->asUser($ctx['actor']);
        DB::table('greenhouse_assignments')->where('user_id', $ctx['actor']->id)->update(['active' => false]);
        $this->postJson('/api/v2/investment-reports', $op)->assertNotFound();
        $this->getJson('/api/v2/investment-reports/'.$op['entity_id'].'/sources')->assertNotFound();
    }

    public function test_failure_after_report_receipt_rolls_back_every_ledger_part(): void
    {
        $ctx = $this->prepared();
        $op = $this->report($this->preview($ctx));
        $fired = false;
        DB::listen(function (QueryExecuted $q) use ($op, &$fired) {
            if (! $fired && str_starts_with($q->sql, 'insert into `sync_operations`') && in_array($op['operation_id'], $q->bindings, true)) {
                $fired = true;
                throw new \RuntimeException('Injected investment receipt failure');
            }
        });
        $this->postJson('/api/v2/investment-reports', $op)->assertStatus(500);
        $this->assertTrue($fired);
        foreach (['entity_identities' => 'entity_id', 'report_snapshots' => 'id', 'audit_logs' => 'entity_id', 'change_feed' => 'entity_id', 'sync_operations' => 'entity_id'] as $table => $column) {
            $this->assertSame(0, DB::table($table)->where($column, $op['entity_id'])->count());
        }
        $this->postJson('/api/v2/investment-reports', $op)->assertCreated();
    }

    public function test_relative_annual_periods_remain_anchored_to_original_leap_day(): void
    {
        $ctx = $this->prepared();
        $payload = [...$ctx['payload'], 'base_date' => '2024-02-29', 'horizon_years' => 4, 'cash_flows' => array_map(fn ($year) => ['year' => $year, 'amount' => '60000000.00', 'source_kind' => 'projection', 'source_reference' => '', 'basis' => 'Tahunan sintetis'], range(1, 4)), 'reason' => 'Periksa penanggalan relatif'];
        $this->patchJson('/api/v2/investment-scenarios/'.$ctx['scenario']['id'], $this->operation('investment_scenario', $payload, 'update', $ctx['scenario']['id'], 1))->assertOk();
        $op = $this->report($this->preview($ctx));
        $this->postJson('/api/v2/investment-reports', $op)->assertCreated();
        $rows = $this->getJson('/api/v2/investment-reports/'.$op['entity_id'].'/sources')->assertOk()->json('data');
        $this->assertSame('2025-02-28', $rows[1]['data']['period_end']);
        $this->assertSame('2028-02-29', $rows[4]['data']['period_end']);
    }
}
