<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('activities', function (Blueprint $table) {
            $table->string('recorded_by_role', 20)->nullable();
            $table->index(['recorded_by_role', 'record_state', 'occurred_at', 'id'], 'activity_review_inbox');
        });
        Schema::create('activity_reviews', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->foreignUuid('activity_id')->constrained()->restrictOnDelete();
            $table->unsignedInteger('activity_version');
            $table->foreignId('reviewer_id')->constrained('users')->restrictOnDelete();
            $table->string('reviewer_name');
            $table->string('decision', 12);
            $table->text('reason')->nullable();
            $table->dateTime('reviewed_at', 6);
            $table->unique(['activity_id', 'activity_version']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('activity_reviews');
        Schema::table('activities', function (Blueprint $table) {
            $table->dropIndex('activity_review_inbox');
            $table->dropColumn('recorded_by_role');
        });
    }
};
