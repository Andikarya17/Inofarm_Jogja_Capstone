<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('harvests', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignUuid('planting_group_id')->constrained()->restrictOnDelete();
            $t->foreignId('recorded_by')->constrained('users')->restrictOnDelete();
            $t->dateTime('occurred_at', 6);
            $t->text('notes');
            $t->string('record_state', 20)->default('active');
            $t->unsignedInteger('version')->default(1);
            $t->timestamps(6);
            $t->index(['planting_group_id', 'occurred_at', 'id'], 'harvest_history');
        });
        Schema::create('harvest_items', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignUuid('harvest_id')->constrained()->restrictOnDelete();
            $t->foreignUuid('grade_id')->constrained()->restrictOnDelete();
            $t->string('grade_name', 120);
            $t->unsignedInteger('grade_version');
            $t->unsignedInteger('fruit_count');
            $t->decimal('weight_kg', 20, 6);
            // Usage is a server-only balance. FIN10 sales will adjust it transactionally.
            $t->decimal('sold_weight_kg', 20, 6)->default('0');
            $t->unsignedInteger('balance_version')->default(1);
            $t->boolean('active')->default(true);
            $t->index(['harvest_id', 'active', 'grade_id']);
        });
        DB::statement('ALTER TABLE harvest_items ADD CONSTRAINT harvest_nonnegative CHECK (weight_kg >= 0 AND sold_weight_kg >= 0 AND sold_weight_kg <= weight_kg AND fruit_count <= 2147483647)');
        DB::statement('ALTER TABLE harvest_items ADD CONSTRAINT harvest_inactive_unused CHECK (active = 1 OR sold_weight_kg = 0)');
        Schema::create('harvest_reference_snapshots', function (Blueprint $t) {
            $t->bigIncrements('id');
            $t->foreignUuid('harvest_id')->constrained()->restrictOnDelete();
            $t->unsignedInteger('harvest_version');
            $t->json('body');
            $t->unique(['harvest_id', 'harvest_version'], 'harvest_reference_identity');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('harvest_reference_snapshots');
        Schema::dropIfExists('harvest_items');
        Schema::dropIfExists('harvests');
    }
};
