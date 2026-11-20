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

    public function test_new_season_gets_next_sequence(): void
    {
        $ctx = $this->context($this->account());
        $this->assertSame(1, $ctx['season']['sequence']);
        $next = $this->createMaster('seasons', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Musim lanjut', 'start_date' => '2027-01-01', 'end_date' => null]);

        $this->assertSame(2, $next['sequence']);
    }
}
