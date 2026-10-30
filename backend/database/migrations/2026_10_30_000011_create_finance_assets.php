<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('assets', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignUuid('expense_id')->constrained()->restrictOnDelete();
            $t->foreignUuid('greenhouse_id')->constrained()->restrictOnDelete();
            $t->foreignId('recorded_by')->constrained('users')->restrictOnDelete();
            $t->string('name', 150);
            $t->decimal('cost', 20, 2);
            $t->decimal('residual', 20, 2);
            $t->unsignedInteger('life_years');
            $t->date('in_service_date');
            $t->text('usage_basis');
            $t->string('formula_version', 60);
            $t->string('record_state', 20)->default('active');
            $t->unsignedInteger('version')->default(1);
            $t->timestamps(6);
            $t->index(['expense_id', 'record_state']);
        });
        Schema::create('depreciation_allocations', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignUuid('asset_id')->constrained()->restrictOnDelete();
            $t->foreignUuid('greenhouse_id')->constrained()->restrictOnDelete();
            $t->foreignUuid('season_id')->constrained()->restrictOnDelete();
            $t->foreignId('recorded_by')->constrained('users')->restrictOnDelete();
            $t->unsignedInteger('asset_version');
            $t->unsignedInteger('usage_year');
            $t->date('period_start');
            $t->date('period_end');
            $t->uuid('usage_reference');
            $t->decimal('amount', 20, 2);
            $t->decimal('year_budget', 20, 2);
            $t->text('allocation_basis');
            $t->string('formula_version', 60);
            $t->string('record_state', 20)->default('active');
            $t->unsignedInteger('version')->default(1);
            $t->timestamps(6);
            $t->unique(['asset_id', 'usage_reference'], 'asset_unique_usage');
            $t->unique(['asset_id', 'period_start', 'period_end'], 'asset_unique_period');
            $t->index(['asset_id', 'usage_year', 'record_state']);
        });
        DB::statement("ALTER TABLE assets ADD CONSTRAINT asset_values CHECK (cost >= residual AND residual >= 0 AND life_years BETWEEN 1 AND 1000 AND record_state IN ('active','cancelled'))");
        DB::statement("ALTER TABLE depreciation_allocations ADD CONSTRAINT depreciation_values CHECK (amount > 0 AND amount <= year_budget AND period_end > period_start AND record_state IN ('active','cancelled'))");
    }

    public function down(): void
    {
        throw new LogicException('Asset history requires a reviewed recovery plan.');
    }
};
