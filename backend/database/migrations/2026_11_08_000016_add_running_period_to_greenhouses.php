<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('greenhouses', function (Blueprint $table) {
            $table->uuid('running_group_id')->nullable();
            $table->boolean('running_period_reviewed')->default(false);
        });
    }

    public function down(): void
    {
        throw new RuntimeException('Running-period designations require a separately authorized data rollback plan.');
    }
};
