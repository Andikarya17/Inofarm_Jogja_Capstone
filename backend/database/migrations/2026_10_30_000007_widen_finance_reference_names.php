<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        foreach (['harvest_items' => 'grade_name', 'expenses' => 'category_name', 'cost_allocations' => 'season_name'] as $table => $column) {
            Schema::table($table, fn (Blueprint $t) => $t->string($column, 150)->change());
        }
    }

    public function down(): void
    {
        throw new LogicException('Historical names must not be truncated by rollback. Review a forward migration or recovery plan.');
    }
};
