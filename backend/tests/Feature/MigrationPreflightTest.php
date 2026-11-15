<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\DB;
use Tests\FinanceTestCase;

class MigrationPreflightTest extends FinanceTestCase
{
    // Checklist rilis memakai `php artisan migrate:status --pending=1`; tes ini mengunci exit code-nya terhadap upgrade framework.
    public function test_release_preflight_fails_only_while_a_migration_is_pending(): void
    {
        $this->artisan('migrate:status', ['--pending' => 1])->assertExitCode(0);

        DB::table('migrations')->where('migration', '2026_11_10_000020_create_harvest_reviews')->delete();

        $this->artisan('migrate:status', ['--pending' => 1])->assertExitCode(1);
    }
}
