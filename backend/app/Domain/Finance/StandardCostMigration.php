<?php

namespace App\Domain\Finance;

use Illuminate\Support\Facades\DB;

/** Data steps of migration 23, kept here so tests can run them against existing rows. */
final class StandardCostMigration
{
    /** Numbers each greenhouse's seasons 1..n by start date; equal dates keep a stable order by id. */
    public static function backfillSequences(): void
    {
        $seasons = DB::table('seasons')->orderBy('greenhouse_id')->orderBy('start_date')->orderBy('id')->get(['id', 'greenhouse_id']);
        $next = [];
        foreach ($seasons as $season) {
            $next[$season->greenhouse_id] = ($next[$season->greenhouse_id] ?? 0) + 1;
            DB::table('seasons')->where('id', $season->id)->update(['sequence' => $next[$season->greenhouse_id]]);
        }
    }
}
