<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

/*
 * User decision 17 Nov 2026: one nutrient tank per greenhouse, so measurement, nutrition, stage and general
 * work are recorded once per greenhouse season. Only spraying names a variety (planting group).
 * Plant age (HSS/HST + day) is typed by Inofarm, never computed. Additive: existing values are kept;
 * season_id is a new column derived from each row's existing group.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('activities', function (Blueprint $t) {
            $t->uuid('season_id')->nullable()->after('id');
            $t->uuid('entry_batch_id')->nullable();
            $t->string('age_basis', 3)->nullable();
            $t->unsignedSmallInteger('age_day')->nullable();
            $t->date('spray_date')->nullable();
            $t->string('spray_period', 10)->nullable();
        });
        DB::table('activities')->whereNull('season_id')->update([
            'season_id' => DB::raw('(SELECT planting_groups.season_id FROM planting_groups WHERE planting_groups.id = activities.planting_group_id)'),
        ]);
        Schema::table('activities', function (Blueprint $t) {
            $t->uuid('season_id')->nullable(false)->change();
            $t->foreign('season_id')->references('id')->on('seasons')->restrictOnDelete();
            $t->uuid('planting_group_id')->nullable()->change();
            $t->index(['season_id', 'occurred_at', 'id'], 'activities_season_history');
            $t->index('entry_batch_id');
        });
        // Spray doses are optional; nutrition still requires both, enforced by ActivityService.
        Schema::table('activity_materials', function (Blueprint $t) {
            $t->uuid('unit_id')->nullable()->change();
            $t->decimal('quantity', 20, 6)->nullable()->change();
        });
        Schema::table('materials', fn (Blueprint $t) => $t->string('purpose', 20)->nullable());
    }

    public function down(): void
    {
        // Greenhouse-level rows and dose-less sprays have no honest group/unit; rollback is refused instead of inventing one.
        abort_if(DB::table('activities')->whereNull('planting_group_id')->exists(), 500, 'Ada kegiatan tingkat greenhouse; rollback dibatalkan.');
        abort_if(DB::table('activity_materials')->whereNull('unit_id')->orWhereNull('quantity')->exists(), 500, 'Ada bahan tanpa dosis; rollback dibatalkan.');
        Schema::table('materials', fn (Blueprint $t) => $t->dropColumn('purpose'));
        Schema::table('activity_materials', function (Blueprint $t) {
            $t->uuid('unit_id')->nullable(false)->change();
            $t->decimal('quantity', 20, 6)->nullable(false)->change();
        });
        Schema::table('activities', function (Blueprint $t) {
            $t->dropForeign(['season_id']);
            $t->dropIndex('activities_season_history');
            $t->dropIndex(['entry_batch_id']);
            $t->uuid('planting_group_id')->nullable(false)->change();
            $t->dropColumn(['season_id', 'entry_batch_id', 'age_basis', 'age_day', 'spray_date', 'spray_period']);
        });
    }
};
