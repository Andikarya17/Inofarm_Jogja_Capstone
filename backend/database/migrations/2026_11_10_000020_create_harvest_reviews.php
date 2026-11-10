<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('harvests', function (Blueprint $table) {
            $table->string('recorded_by_role', 20)->nullable();
            $table->index(['recorded_by_role', 'record_state', 'occurred_at', 'id'], 'harvest_review_inbox');
        });
        Schema::create('harvest_reviews', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->foreignUuid('harvest_id')->constrained()->restrictOnDelete();
            $table->unsignedInteger('harvest_version');
            $table->foreignId('reviewer_id')->constrained('users')->restrictOnDelete();
            $table->string('reviewer_name');
            $table->string('decision', 12);
            $table->text('reason')->nullable();
            $table->dateTime('reviewed_at', 6);
            $table->unique(['harvest_id', 'harvest_version']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('harvest_reviews');
        Schema::table('harvests', function (Blueprint $table) {
            $table->dropIndex('harvest_review_inbox');
            $table->dropColumn('recorded_by_role');
        });
    }
};
