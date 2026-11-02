<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('users', function (Blueprint $t) {
            $t->uuid('public_id')->nullable()->unique();
            $t->boolean('active')->default(true);
            $t->unsignedInteger('version')->default(1);
        });
        Schema::create('greenhouses', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->string('name', 150);
            $t->boolean('active')->default(true);
            $t->unsignedInteger('version')->default(1);
            $t->foreignId('created_by')->constrained('users')->restrictOnDelete();
            $t->timestamps(6);
        });
        Schema::create('greenhouse_assignments', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignId('user_id')->constrained()->restrictOnDelete();
            $t->foreignUuid('greenhouse_id')->constrained()->restrictOnDelete();
            $t->boolean('active')->default(true);
            $t->timestamp('effective_from', 6);
            $t->timestamp('effective_until', 6)->nullable();
            $t->unsignedInteger('version')->default(1);
            $t->foreignId('created_by')->constrained('users')->restrictOnDelete();
            $t->timestamps(6);
            $t->unique(['user_id', 'greenhouse_id']);
            $t->index(['user_id', 'active', 'effective_from']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('greenhouse_assignments');
        Schema::dropIfExists('greenhouses');
        Schema::table('users', function (Blueprint $t) {
            $t->dropUnique(['public_id']);
            $t->dropColumn(['public_id', 'active', 'version']);
        });
    }
};
