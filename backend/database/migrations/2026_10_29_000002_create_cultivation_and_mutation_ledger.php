<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Str;

return new class extends Migration
{
    public function up(): void
    {
        foreach (['varieties', 'units', 'materials', 'grades', 'expense_categories'] as $table) {
            Schema::create($table, function (Blueprint $t) use ($table) {
                $t->uuid('id')->primary();
                $t->foreignUuid('greenhouse_id')->constrained()->restrictOnDelete();
                $t->string('name', 150);
                if (in_array($table, ['units', 'materials'], true)) {
                    $t->string('dimension', 30);
                }
                $t->boolean('active')->default(true);
                $t->unsignedInteger('version')->default(1);
                $t->timestamps(6);
                $t->index(['greenhouse_id', 'active']);
            });
        }
        Schema::create('seasons', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignUuid('greenhouse_id')->constrained()->restrictOnDelete();
            $t->string('name', 150);
            $t->date('start_date');
            $t->date('end_date')->nullable();
            $t->string('status', 20)->default('open');
            $t->unsignedInteger('version')->default(1);
            $t->timestamps(6);
            $t->index(['greenhouse_id', 'status']);
        });
        Schema::create('planting_groups', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignUuid('season_id')->constrained()->restrictOnDelete();
            $t->foreignUuid('variety_id')->constrained()->restrictOnDelete();
            $t->string('name', 150);
            $t->unsignedInteger('population');
            $t->date('sowing_date');
            $t->date('transplant_date')->nullable();
            $t->boolean('active')->default(true);
            $t->unsignedInteger('version')->default(1);
            $t->timestamps(6);
        });
        Schema::create('sync_state', function (Blueprint $t) {
            $t->unsignedTinyInteger('id')->primary();
            $t->uuid('epoch');
        });
        DB::table('sync_state')->insert(['id' => 1, 'epoch' => (string) Str::uuid()]);
        Schema::create('sync_operations', function (Blueprint $t) {
            $t->bigIncrements('id');
            $t->foreignId('actor_id')->constrained('users')->restrictOnDelete();
            $t->uuid('operation_id');
            $t->uuid('entity_id');
            $t->string('entity_type', 40);
            $t->string('request_hash', 64);
            $t->json('receipt');
            $t->unsignedSmallInteger('http_status');
            $t->timestamp('committed_at', 6);
            $t->unique(['actor_id', 'operation_id']);
        });
        Schema::create('audit_logs', function (Blueprint $t) {
            $t->bigIncrements('id');
            $t->foreignId('actor_id')->constrained('users')->restrictOnDelete();
            $t->uuid('operation_id');
            $t->string('entity_type', 40);
            $t->uuid('entity_id');
            $t->string('action', 20);
            $t->unsignedInteger('version');
            $t->text('reason')->nullable();
            $t->json('changes');
            $t->timestamp('server_at', 6);
            $t->index(['entity_type', 'entity_id', 'version']);
        });
        Schema::create('change_feed', function (Blueprint $t) {
            $t->bigIncrements('sequence');
            $t->foreignUuid('greenhouse_id')->constrained()->restrictOnDelete();
            $t->string('audience', 20)->default('all');
            $t->string('entity_type', 40);
            $t->uuid('entity_id');
            $t->unsignedInteger('version');
            $t->boolean('tombstone')->default(false);
            $t->json('body');
            $t->timestamp('committed_at', 6);
            $t->index(['greenhouse_id', 'sequence']);
        });
    }

    public function down(): void
    {
        foreach (['change_feed', 'audit_logs', 'sync_operations', 'sync_state', 'planting_groups', 'seasons', 'expense_categories', 'grades', 'materials', 'units', 'varieties'] as $table) {
            Schema::dropIfExists($table);
        }
    }
};
