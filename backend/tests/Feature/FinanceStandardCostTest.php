<?php

namespace Tests\Feature;

use App\Domain\Finance\StandardCostMigration;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;

class FinanceStandardCostTest extends FinanceTestCase
{
    public function test_sequence_backfill_breaks_ties_by_id(): void
    {
        $ctx = $this->context($this->account());
        $second = $this->createMaster('seasons', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Musim kembar', 'start_date' => '2026-10-01', 'end_date' => null]);
        DB::table('seasons')->update(['sequence' => null]);

        StandardCostMigration::backfillSequences();

        $ids = [$ctx['season']['id'], $second['id']];
        sort($ids);
        $this->assertSame(1, (int) DB::table('seasons')->where('id', $ids[0])->value('sequence'));
        $this->assertSame(2, (int) DB::table('seasons')->where('id', $ids[1])->value('sequence'));
    }

    /** Inofarm's own table (20 Nov 2026), used only as test values; production never seeds amounts. */
    public const INOFARM = ['Biaya penyusutan' => '5806100.00', 'Sewa tanah' => '275000.00', 'Tenaga kerja' => '1500000.00',
        'Cocopeat/rockwool' => '180000.00', 'Nutrisi AB Mix' => '1600000.00', 'Nutrisi tambahan dan pestisida' => '350000.00', 'Benih' => '1300000.00'];

    private function sheet(int $sequence = 1): array
    {
        return $this->getJson('/api/v2/season-cost-sheets?sequence='.$sequence)->assertOk()->json('data.0');
    }

    private function lines(array $sheet, array $amounts = self::INOFARM): array
    {
        return array_map(fn ($line) => ['id' => $line['id'], 'name' => $line['name'], 'cost_class' => $line['cost_class'],
            'amount' => array_key_exists($line['name'], $amounts) ? $amounts[$line['name']] : $line['amount'], 'is_required' => $line['is_required']], $sheet['lines']);
    }

    private function save(array $sheet, array $lines)
    {
        return $this->patchJson('/api/v2/season-cost-sheets/'.$sheet['id'],
            $this->operation('season_cost_sheet', ['lines' => $lines], 'update', $sheet['id'], $sheet['version']));
    }

    public function test_sheet_totals_match_inofarm_table(): void
    {
        $this->context($this->account());
        $sheet = $this->sheet();
        $this->assertCount(7, $sheet['lines']);
        $this->assertNull($sheet['totals']['TC']);

        $saved = $this->save($sheet, $this->lines($sheet))->assertOk()->json('data');

        $this->assertSame(['FC' => '7581100.00', 'VC' => '3430000.00', 'TC' => '11011100.00', 'complete' => true], $saved['totals']);
        $this->assertSame(1, $saved['greenhouse_count']);
    }

    public function test_empty_required_amount_is_incomplete_not_zero(): void
    {
        $this->context($this->account());
        $sheet = $this->sheet();
        $saved = $this->save($sheet, $this->lines($sheet, [...self::INOFARM, 'Sewa tanah' => null]))->assertOk()->json('data');

        $this->assertNull($saved['totals']['FC']);
        $this->assertSame('3430000.00', $saved['totals']['VC']);
        $this->assertNull($saved['totals']['TC']);
        $this->assertFalse($saved['totals']['complete']);
    }

    public function test_required_line_cannot_be_renamed_or_removed(): void
    {
        $this->context($this->account());
        $sheet = $this->sheet();
        $renamed = $this->lines($sheet);
        $renamed[1]['name'] = 'Sewa lahan';
        $this->save($sheet, $renamed)->assertUnprocessable();
        $this->save($sheet, array_slice($this->lines($sheet), 1))->assertUnprocessable();
    }

    public function test_extra_line_can_be_added_and_removed(): void
    {
        $this->context($this->account());
        $sheet = $this->sheet();
        $extra = ['id' => (string) Str::uuid(), 'name' => 'Listrik pompa', 'cost_class' => 'VC', 'amount' => '150000.00', 'is_required' => false];
        $saved = $this->save($sheet, [...$this->lines($sheet), $extra])->assertOk()->json('data');
        $this->assertSame('3580000.00', $saved['totals']['VC']);
        $this->assertCount(8, $saved['lines']);

        $removed = $this->save($saved, $this->lines($sheet))->assertOk()->json('data');
        $this->assertSame('3430000.00', $removed['totals']['VC']);
        $this->assertCount(7, $removed['lines']);
    }

    public function test_duplicate_line_name_rejected_case_insensitive(): void
    {
        $this->context($this->account());
        $sheet = $this->sheet();
        $dup = ['id' => (string) Str::uuid(), 'name' => 'sewa TANAH', 'cost_class' => 'FC', 'amount' => '1.00', 'is_required' => false];
        $this->save($sheet, [...$this->lines($sheet), $dup])->assertUnprocessable();
    }

    public function test_update_rejected_when_any_season_of_sequence_closed(): void
    {
        $ctx = $this->context($this->account());
        $sheet = $this->sheet();
        DB::table('seasons')->where('id', $ctx['season']['id'])->update(['status' => 'closed']);

        $this->save($sheet, $this->lines($sheet))->assertStatus(409)->assertJsonPath('code', 'season_closed');
    }

    public function test_new_sequence_copies_previous_sheet(): void
    {
        $ctx = $this->context($this->account());
        $sheet = $this->sheet();
        $this->save($sheet, $this->lines($sheet))->assertOk();
        $this->createMaster('seasons', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Musim lanjut', 'start_date' => '2027-01-01', 'end_date' => null]);

        $second = $this->sheet(2);
        $this->assertSame(2, $second['sequence']);
        $this->assertSame('11011100.00', $second['totals']['TC']);
        $this->assertNotSame($sheet['id'], $second['id']);
    }

    public function test_worker_gets_403(): void
    {
        $this->context($this->account());
        $sheet = $this->sheet();
        $this->asUser($this->account('worker'));
        $this->getJson('/api/v2/season-cost-sheets?sequence=1')->assertForbidden();
        $this->save($sheet, $this->lines($sheet))->assertForbidden();
    }

    public function test_ledger_skips_feed_without_greenhouse(): void
    {
        $this->context($this->account());
        $sheet = $this->sheet();
        $before = DB::table('change_feed')->count();
        $this->save($sheet, $this->lines($sheet))->assertOk();

        $this->assertSame($before, DB::table('change_feed')->count());
        $this->assertSame(1, DB::table('audit_logs')->where('entity_type', 'season_cost_sheet')->where('action', 'update')->count());
    }

    /** A season with a full Inofarm sheet, a real-cost allocation of 4.999.990 and one paid sale of 500 kg x 30.900. */
    private function reportSeason(): array
    {
        $ctx = $this->context($this->account());
        $sheet = $this->sheet();
        $this->save($sheet, $this->lines($sheet))->assertOk();
        $category = $this->createMaster('expense_categories', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Biaya nyata sintetis']);
        $payment = $this->operation('expense', ['greenhouse_id' => $ctx['gh']['id'], 'category_id' => $category['id'], 'amount' => '4999990.00',
            'paid_at' => '2026-10-30T08:00:00+07:00', 'payment_kind' => 'operating', 'notes' => '']);
        $this->postJson('/api/v2/expenses', $payment)->assertCreated();
        $this->postJson('/api/v2/cost-allocations', $this->operation('cost_allocation', ['expense_id' => $payment['entity_id'], 'season_id' => $ctx['season']['id'],
            'amount' => '4999990.00', 'cost_class' => 'FC', 'allocation_basis' => 'Dasar sintetis']))->assertCreated();
        $item = (string) Str::uuid();
        $this->postJson('/api/v2/harvests', $this->operation('harvest', ['planting_group_id' => $ctx['group']['id'], 'occurred_at' => '2026-10-29T12:00:00+07:00', 'notes' => '',
            'items' => [['id' => $item, 'quality' => 'saleable', 'fruit_count' => null, 'weight_kg' => '500']]]))->assertCreated();
        $this->postJson('/api/v2/sales', $this->operation('sale', ['season_id' => $ctx['season']['id'], 'paid_at' => '2026-10-30T09:00:00+07:00',
            'payment_status' => 'paid', 'customer_name' => '', 'notes' => '',
            'items' => [['id' => (string) Str::uuid(), 'harvest_item_id' => $item, 'quantity_kg' => '500', 'price_per_kg' => '30900']]]))->assertCreated();

        return [...$ctx, 'sheet' => $this->sheet(), 'input' => ['season_id' => $ctx['season']['id'], 'coverage' => ['costs' => 'complete', 'sales' => 'complete'],
            'known_pending_count' => 0, 'requested_state' => 'provisional', 'review_note' => 'Tinjauan sintetis']];
    }

    public function test_report_uses_standard_costs_not_allocations(): void
    {
        $ctx = $this->reportSeason();
        $preview = $this->postJson('/api/v2/season-reports/preview', $ctx['input'])->assertOk()->json();

        foreach (['FC' => '7581100.00', 'VC' => '3430000.00', 'TC' => '11011100.00', 'TR' => '15450000.00', 'L' => '4438900.00'] as $key => $value) {
            $this->assertSame($value, $preview['metrics'][$key]['value'], $key);
        }
        $this->assertSame('standard', $preview['cost_basis']);
        $this->assertSame(['id' => $ctx['sheet']['id'], 'sequence' => 1, 'version' => $ctx['sheet']['version']], $preview['cost_sheet']);
    }

    public function test_allocations_listed_as_reference_only(): void
    {
        $ctx = $this->reportSeason();
        $hash = $this->postJson('/api/v2/season-reports/preview', $ctx['input'])->assertOk()->json('sources_hash');
        $sources = $this->getJson('/api/v2/season-reports/preview/sources?'.http_build_query(['season_id' => $ctx['season']['id'], 'sources_hash' => $hash, 'limit' => 100]))
            ->assertOk()->json('data');
        $allocation = collect($sources)->firstWhere('kind', 'cost_allocation');
        $sheet = collect($sources)->firstWhere('kind', 'season_cost_sheet');

        $this->assertFalse($allocation['included']);
        $this->assertSame('reference_only', $allocation['role']);
        $this->assertTrue($sheet['included']);
        $this->assertSame(['FC' => '7581100.00', 'VC' => '3430000.00'], $sheet['contribution']);
    }

    public function test_incomplete_sheet_keeps_report_provisional(): void
    {
        $ctx = $this->reportSeason();
        $this->save($ctx['sheet'], $this->lines($ctx['sheet'], [...self::INOFARM, 'Benih' => null]))->assertOk();
        $preview = $this->postJson('/api/v2/season-reports/preview', $ctx['input'])->assertOk()->json();

        $this->assertNull($preview['metrics']['VC']['value']);
        $this->assertNull($preview['metrics']['TC']['value']);
        $this->assertSame('7581100.00', $preview['metrics']['FC']['value']);
        $this->assertSame(['Benih'], $preview['cost_gaps']);
    }

    public function test_published_report_is_unchanged_by_cost_sheet_edit(): void
    {
        $ctx = $this->reportSeason();
        $preview = $this->postJson('/api/v2/season-reports/preview', $ctx['input'])->assertOk()->json();
        $op = $this->operation('season_report', [...$ctx['input'], 'expected_sources_hash' => $preview['sources_hash'], 'expected_formula_version' => $preview['formula_version']]);
        $published = $this->postJson('/api/v2/season-reports', $op)->assertCreated()->json('data');

        $this->save($ctx['sheet'], $this->lines($ctx['sheet'], [...self::INOFARM, 'Sewa tanah' => '300000.00']))->assertOk();

        $this->getJson('/api/v2/reports/'.$op['entity_id'])->assertExactJson($published);
        $this->assertSame('7606100.00', $this->postJson('/api/v2/season-reports/preview', $ctx['input'])->json('metrics.FC.value'));
    }

    public function test_new_season_gets_next_sequence(): void
    {
        $ctx = $this->context($this->account());
        $this->assertSame(1, $ctx['season']['sequence']);
        $next = $this->createMaster('seasons', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Musim lanjut', 'start_date' => '2027-01-01', 'end_date' => null]);

        $this->assertSame(2, $next['sequence']);
    }
}
