<?php

use App\Domain\Finance\SaleLineMigration;
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

/*
 * User decisions 25 Nov 2026: a sale is a list of lines (variety, grade, kg, price) taken from stock first-in first-out,
 * retail buyers join consumers and partners, and consumer prices are a list per variety and quality. Additive only.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('sale_lines', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignUuid('sale_id')->constrained('sales')->restrictOnDelete();
            $t->foreignUuid('planting_group_id')->constrained('planting_groups')->restrictOnDelete();
            $t->foreignUuid('grade_id')->constrained('grades')->restrictOnDelete();
            $t->string('grade_name', 150);
            $t->string('variety_name', 150);
            $t->char('buyer_grade', 1)->nullable();
            $t->decimal('quantity_kg', 20, 6);
            $t->unsignedInteger('fruit_count')->nullable();
            $t->decimal('price_per_kg', 20, 6);
            $t->decimal('exact_amount', 40, 12);
            $t->boolean('active')->default(true);
            $t->timestamps(6);
            $t->index(['sale_id', 'active']);
        });
        DB::statement("ALTER TABLE sale_lines ADD CONSTRAINT sale_line_values CHECK (quantity_kg > 0 AND price_per_kg >= 0 AND exact_amount >= 0 AND (buyer_grade IS NULL OR buyer_grade IN ('A','B','C')) AND (fruit_count IS NULL OR fruit_count >= 1))");

        Schema::table('sale_items', function (Blueprint $t) {
            $t->uuid('sale_line_id')->nullable();
            $t->foreign('sale_line_id')->references('id')->on('sale_lines')->restrictOnDelete();
            $t->index('sale_line_id');
        });

        DB::statement('ALTER TABLE customers DROP CHECK customer_values');
        DB::statement("ALTER TABLE customers ADD CONSTRAINT customer_values CHECK (type IN ('consumer','retail','partner') AND source IN ('manual','import') AND record_state IN ('active','cancelled'))");

        Schema::create('consumer_prices', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->string('variety_key', 150);
            $t->string('variety_name', 150);
            $t->string('quality', 10);
            $t->decimal('price_per_kg', 20, 6);
            $t->string('record_state', 20)->default('active');
            $t->unsignedInteger('version')->default(1);
            $t->timestamps(6);
        });
        DB::statement("ALTER TABLE consumer_prices ADD COLUMN active_key VARCHAR(170) GENERATED ALWAYS AS (CASE WHEN record_state = 'active' THEN CONCAT(variety_key, '|', quality) ELSE NULL END) STORED");
        DB::statement('ALTER TABLE consumer_prices ADD UNIQUE INDEX consumer_price_active (active_key)');
        DB::statement("ALTER TABLE consumer_prices ADD CONSTRAINT consumer_price_values CHECK (quality IN ('saleable','defective') AND price_per_kg >= 0 AND record_state IN ('active','cancelled'))");

        SaleLineMigration::backfill();
    }

    public function down(): void
    {
        throw new LogicException('Sale line, retail buyer and consumer price history requires a reviewed recovery plan, not automatic removal.');
    }
};
