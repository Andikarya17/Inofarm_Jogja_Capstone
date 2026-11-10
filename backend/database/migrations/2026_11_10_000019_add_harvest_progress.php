<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('planting_groups', function (Blueprint $table) {
            $table->json('harvest_target')->nullable();
            $table->json('harvest_completion')->nullable();
        });
        Schema::table('harvest_items', fn (Blueprint $table) => $table->string('quality', 12)->nullable());
    }

    public function down(): void
    {
        Schema::table('harvest_items', fn (Blueprint $table) => $table->dropColumn('quality'));
        Schema::table('planting_groups', fn (Blueprint $table) => $table->dropColumn(['harvest_target', 'harvest_completion']));
    }
};
