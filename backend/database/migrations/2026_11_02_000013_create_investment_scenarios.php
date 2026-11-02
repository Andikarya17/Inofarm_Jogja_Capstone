<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('investment_scenarios', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignUuid('greenhouse_id')->constrained()->restrictOnDelete();
            $t->foreignId('recorded_by')->constrained('users')->restrictOnDelete();
            $t->unsignedInteger('version');
            $t->string('record_state', 16)->default('active');
            $t->string('name', 120);
            $t->date('base_date');
            $t->unsignedSmallInteger('horizon_years');
            $t->decimal('initial_outlay', 20, 2)->nullable();
            $t->json('initial_source');
            $t->decimal('discount_rate', 20, 8)->nullable();
            $t->string('payback_convention', 24)->nullable();
            $t->text('notes');
            $t->timestamps(6);
            $t->index(['greenhouse_id', 'record_state', 'updated_at']);
        });
        Schema::create('investment_cash_flow_versions', function (Blueprint $t) {
            $t->foreignUuid('scenario_id')->constrained('investment_scenarios')->restrictOnDelete();
            $t->unsignedInteger('scenario_version');
            $t->unsignedSmallInteger('year');
            $t->decimal('amount', 20, 2)->nullable();
            $t->string('source_kind', 16)->nullable();
            $t->string('source_reference', 200);
            $t->text('basis');
            $t->primary(['scenario_id', 'scenario_version', 'year']);
        });
        Schema::table('report_snapshots', function (Blueprint $t) {
            $t->foreignUuid('scenario_id')->nullable()->constrained('investment_scenarios')->restrictOnDelete();
            $t->index(['greenhouse_id', 'kind', 'scenario_id', 'created_at'], 'report_scenario_history');
        });
        DB::statement("ALTER TABLE report_snapshots ADD CONSTRAINT report_scenario_kind CHECK ((kind = 'season_report' AND scenario_id IS NULL) OR (kind = 'investment_report' AND scenario_id IS NOT NULL AND season_id IS NULL))");
        DB::statement("ALTER TABLE investment_scenarios ADD CONSTRAINT investment_scenario_values CHECK (version >= 1 AND horizon_years BETWEEN 1 AND 100 AND (initial_outlay IS NULL OR initial_outlay >= 0) AND (discount_rate IS NULL OR discount_rate > -1) AND record_state IN ('active','cancelled') AND (payback_convention IS NULL OR payback_convention IN ('interpolation','end_of_year')))");
        DB::statement("ALTER TABLE investment_cash_flow_versions ADD CONSTRAINT investment_cash_year CHECK (scenario_version >= 1 AND year BETWEEN 1 AND 100 AND (source_kind IS NULL OR source_kind IN ('actual','projection')))");
    }

    public function down(): void
    {
        throw new LogicException('Investment history requires a reviewed recovery plan.');
    }
};
