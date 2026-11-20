<?php

namespace Tests\Feature;

use App\Domain\Finance\StandardCostMigration;
use Illuminate\Support\Facades\DB;
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

    public function test_new_season_gets_next_sequence(): void
    {
        $ctx = $this->context($this->account());
        $this->assertSame(1, $ctx['season']['sequence']);
        $next = $this->createMaster('seasons', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Musim lanjut', 'start_date' => '2027-01-01', 'end_date' => null]);

        $this->assertSame(2, $next['sequence']);
    }
}
