<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('activities', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignUuid('planting_group_id')->constrained()->restrictOnDelete();
            $t->foreignUuid('schedule_id')->nullable()->constrained()->restrictOnDelete();
            $t->foreignId('recorded_by')->constrained('users')->restrictOnDelete();
            $t->string('activity_type', 30);
            $t->dateTime('occurred_at', 6);
            $t->decimal('manual_ppm', 20, 6)->nullable();
            $t->decimal('manual_ph', 20, 6)->nullable();
            $t->string('actual_stage', 100)->nullable();
            $t->string('work_state', 20);
            $t->text('skipped_reason')->nullable();
            $t->text('notes');
            $t->string('record_state', 20)->default('active');
            $t->unsignedInteger('version')->default(1);
            $t->timestamps(6);
            $t->index(['planting_group_id', 'occurred_at', 'id'], 'activity_history');
        });
        Schema::create('activity_materials', function (Blueprint $t) {
            $t->bigIncrements('id');
            $t->foreignUuid('activity_id')->constrained()->restrictOnDelete();
            $t->foreignUuid('material_id')->constrained()->restrictOnDelete();
            $t->foreignUuid('unit_id')->constrained()->restrictOnDelete();
            $t->decimal('quantity', 20, 6);
        });
        Schema::create('activity_reference_snapshots', function (Blueprint $t) {
            $t->bigIncrements('id');
            $t->foreignUuid('activity_id')->constrained()->restrictOnDelete();
            $t->unsignedInteger('activity_version');
            $t->json('body');
            $t->unique(['activity_id', 'activity_version'], 'activity_reference_identity');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('activity_reference_snapshots');
        Schema::dropIfExists('activity_materials');
        Schema::dropIfExists('activities');
    }
};
