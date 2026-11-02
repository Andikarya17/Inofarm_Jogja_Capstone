<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('sales', function (Blueprint $t) {
            $t->uuid('id')->primary();$t->foreignUuid('greenhouse_id')->constrained()->restrictOnDelete();
            $t->foreignUuid('season_id')->constrained()->restrictOnDelete();$t->string('season_name', 150);$t->unsignedInteger('season_version');
            $t->foreignId('recorded_by')->constrained('users')->restrictOnDelete();$t->string('recorded_by_name', 100);
            $t->timestamp('paid_at', 6);$t->string('payment_status', 20);$t->string('customer_name', 200);$t->string('currency', 3);
            $t->decimal('total_amount', 20, 2);$t->decimal('total_weight_kg', 20, 6);$t->decimal('exact_total', 40, 12);
            $t->string('rounding', 40);$t->text('notes');$t->string('record_state', 20)->default('active');$t->unsignedInteger('version')->default(1);$t->timestamps(6);
            $t->index(['greenhouse_id', 'season_id', 'paid_at', 'id'], 'sale_scope_date');
        });
        Schema::create('sale_items', function (Blueprint $t) {
            $t->uuid('id')->primary();$t->foreignUuid('sale_id')->constrained('sales')->restrictOnDelete();
            $t->foreignUuid('harvest_item_id')->constrained('harvest_items')->restrictOnDelete();
            $t->uuid('harvest_id');$t->unsignedInteger('harvest_version');$t->uuid('grade_id');$t->string('grade_name', 150);
            $t->decimal('quantity_kg', 20, 6);$t->decimal('price_per_kg', 20, 6);$t->decimal('exact_amount', 40, 12);$t->boolean('active')->default(true);
            $t->index(['sale_id', 'harvest_item_id']);$t->index(['harvest_item_id', 'active', 'sale_id']);
        });
        DB::statement("ALTER TABLE sales ADD CONSTRAINT sale_paid_state CHECK (payment_status = 'paid' AND currency = 'IDR' AND record_state IN ('active','cancelled') AND total_amount >= 0 AND total_weight_kg > 0 AND exact_total >= 0)");
        DB::statement('ALTER TABLE sale_items ADD CONSTRAINT sale_positive_weight CHECK (quantity_kg > 0 AND price_per_kg >= 0 AND exact_amount >= 0)');
    }

    public function down(): void
    {
        throw new LogicException('Paid sales history requires a reviewed recovery plan, not automatic removal.');
    }
};
