<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('backup_runs', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignId('actor_id')->constrained('users')->restrictOnDelete();
            $t->text('reason');
            $t->string('state', 40);
            $t->char('manifest_hash', 64)->nullable();
            $t->json('summary')->nullable();
            $t->timestamps(6);
        });
        Schema::create('restore_runs', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->uuid('backup_id');
            $t->string('operator_reference', 150);
            $t->text('reason');
            $t->string('state', 40);
            $t->char('manifest_hash', 64);
            $t->uuid('old_epoch');
            $t->uuid('new_epoch')->nullable();
            $t->json('verification');
            $t->timestamps(6);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('restore_runs');
        Schema::dropIfExists('backup_runs');
    }
};
