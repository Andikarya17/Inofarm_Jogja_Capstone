<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('events', function (Blueprint $t) {
            $t->boolean('clock_trusted')->default(false);
        });
    }

    public function down(): void
    {
        Schema::table('events', function (Blueprint $t) {
            $t->dropColumn('clock_trusted');
        });
    }
};
