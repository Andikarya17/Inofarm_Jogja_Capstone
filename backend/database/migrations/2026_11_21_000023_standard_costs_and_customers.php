<?php

use App\Domain\Finance\StandardCostMigration;
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

/*
 * User decisions 20-21 Nov 2026: fixed and variable costs are one standard sheet per season number for the
 * whole farm (the same for every greenhouse), sales name their buyer (consumer list importable from Excel,
 * or a partner who grades fruit A/B/C per kg). Additive only.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('seasons', function (Blueprint $t) {
            $t->unsignedInteger('sequence')->nullable();
            $t->index(['greenhouse_id', 'sequence']);
        });
        StandardCostMigration::backfillSequences();

        Schema::create('season_cost_sheets', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->unsignedInteger('sequence')->unique();
            $t->string('record_state', 20)->default('active');
            $t->unsignedInteger('version')->default(1);
            $t->timestamps(6);
        });
        Schema::create('season_cost_lines', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignUuid('sheet_id')->constrained('season_cost_sheets')->restrictOnDelete();
            $t->string('name', 150);
            $t->string('cost_class', 2);
            $t->decimal('amount', 20, 2)->nullable();
            $t->boolean('is_required');
            $t->unsignedInteger('sort_order');
            $t->boolean('active')->default(true);
            $t->index(['sheet_id', 'active', 'sort_order']);
        });
        DB::statement("ALTER TABLE season_cost_lines ADD CONSTRAINT cost_line_values CHECK (cost_class IN ('FC','VC') AND (amount IS NULL OR amount >= 0))");

        Schema::create('customers', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->string('type', 10);
            $t->string('name', 200);
            $t->string('whatsapp', 20)->nullable();
            $t->string('whatsapp_display', 30)->nullable();
            $t->string('source', 10)->default('manual');
            $t->string('record_state', 20)->default('active');
            $t->unsignedInteger('version')->default(1);
            $t->timestamps(6);
            $t->unique(['type', 'whatsapp']);
            $t->index(['type', 'name']);
        });
        DB::statement("ALTER TABLE customers ADD CONSTRAINT customer_values CHECK (type IN ('consumer','partner') AND source IN ('manual','import') AND record_state IN ('active','cancelled'))");

        Schema::table('sales', function (Blueprint $t) {
            $t->foreignUuid('customer_id')->nullable()->constrained('customers')->restrictOnDelete();
        });
        Schema::table('sale_items', function (Blueprint $t) {
            $t->char('buyer_grade', 1)->nullable();
        });
        DB::statement("ALTER TABLE sale_items ADD CONSTRAINT sale_item_buyer_grade CHECK (buyer_grade IS NULL OR buyer_grade IN ('A','B','C'))");
    }

    public function down(): void
    {
        throw new LogicException('Standard cost and customer history requires a reviewed recovery plan, not automatic removal.');
    }
};
