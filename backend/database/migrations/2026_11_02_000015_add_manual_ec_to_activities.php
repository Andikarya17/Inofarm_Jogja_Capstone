<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('activities', function (Blueprint $table) {
            $table->decimal('manual_ec', 20, 6)->nullable();
            $table->index(['planting_group_id', 'record_state', 'work_state', 'occurred_at', 'id'], 'activities_latest_measurement');
        });
    }

    public function down(): void
    {
        // Historical and newly recorded values must survive code rollback.
        throw new RuntimeException('Manual measurements require a separately authorized data rollback plan.');
    }
};
