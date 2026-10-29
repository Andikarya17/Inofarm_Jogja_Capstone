<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('schedules', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignUuid('planting_group_id')->constrained()->restrictOnDelete();
            $t->string('activity_type', 30);
            $t->timestamp('planned_at', 6);
            $t->text('notes');
            $t->boolean('active')->default(true);
            $t->unsignedInteger('version')->default(1);
            $t->timestamps(6);
            $t->index(['planting_group_id', 'planned_at']);
        });
        Schema::create('targets', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignUuid('planting_group_id')->constrained()->restrictOnDelete();
            $t->string('activity_type', 30);
            $t->string('parameter', 30);
            $t->string('unit', 10);
            foreach (['target_value', 'minimum', 'maximum', 'tolerance'] as $field) {
                $t->decimal($field, 20, 6)->nullable();
            }
            $t->timestamp('effective_from', 6);
            $t->timestamp('effective_until', 6)->nullable();
            $t->boolean('active')->default(true);
            $t->unsignedInteger('version')->default(1);
            $t->timestamps(6);
            $t->index(['planting_group_id', 'activity_type', 'parameter', 'effective_from'], 'targets_selection');
        });
        Schema::create('cultivation_reference_versions', function (Blueprint $t) {
            $t->bigIncrements('id');
            $t->string('entity_type', 20);
            $t->uuid('entity_id');
            $t->unsignedInteger('version');
            $t->json('body');
            $t->unique(['entity_type', 'entity_id', 'version'], 'reference_version_identity');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('cultivation_reference_versions');
        Schema::dropIfExists('targets');
        Schema::dropIfExists('schedules');
    }
};
