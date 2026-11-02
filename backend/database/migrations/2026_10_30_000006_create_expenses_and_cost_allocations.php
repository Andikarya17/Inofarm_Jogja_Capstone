<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('expenses', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->uuid('balance_id')->unique();
            $t->foreignUuid('greenhouse_id')->constrained()->restrictOnDelete();
            $t->foreignUuid('category_id')->constrained('expense_categories')->restrictOnDelete();
            $t->string('category_name', 120);
            $t->unsignedInteger('category_version');
            $t->foreignId('recorded_by')->constrained('users')->restrictOnDelete();
            $t->dateTime('paid_at', 6);
            $t->string('currency', 3)->default('IDR');
            $t->decimal('amount', 20, 2);
            $t->string('payment_kind', 24);
            $t->text('notes');
            $t->string('record_state', 20)->default('active');
            $t->unsignedInteger('version')->default(1);
            $t->decimal('allocated_amount', 20, 2)->default('0');
            $t->unsignedInteger('balance_version')->default(1);
            $t->timestamps(6);
            $t->index(['greenhouse_id', 'paid_at', 'id'], 'expense_history');
        });
        DB::statement("ALTER TABLE expenses ADD CONSTRAINT expense_amount_balance CHECK (amount >= 0 AND allocated_amount >= 0 AND allocated_amount <= amount AND (payment_kind = 'operating' OR (payment_kind = 'asset_acquisition' AND allocated_amount = 0)))");
        DB::statement("ALTER TABLE expenses ADD CONSTRAINT expense_cancel_unused CHECK (record_state = 'active' OR (record_state = 'cancelled' AND allocated_amount = 0))");
        Schema::create('cost_allocations', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignUuid('expense_id')->constrained()->restrictOnDelete();
            $t->foreignUuid('season_id')->constrained()->restrictOnDelete();
            $t->foreignId('recorded_by')->constrained('users')->restrictOnDelete();
            $t->decimal('amount', 20, 2);
            $t->string('currency', 3)->default('IDR');
            $t->string('cost_class', 2);
            $t->text('allocation_basis');
            $t->string('season_name', 120);
            $t->unsignedInteger('season_version');
            $t->string('record_state', 20)->default('active');
            $t->unsignedInteger('version')->default(1);
            $t->timestamps(6);
            $t->index(['expense_id', 'record_state']);
            $t->index(['season_id', 'record_state', 'id']);
        });
        DB::statement("ALTER TABLE cost_allocations ADD CONSTRAINT cost_allocation_amount CHECK (amount >= 0 AND cost_class IN ('FC','VC'))");
    }

    public function down(): void
    {
        Schema::dropIfExists('cost_allocations');
        Schema::dropIfExists('expenses');
    }
};
