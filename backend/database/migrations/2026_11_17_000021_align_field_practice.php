<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

// Field answers 15 Nov 2026: harvests are weighed only; sales are traced through the QRIS/payment reference.
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('harvest_items', fn (Blueprint $t) => $t->unsignedInteger('fruit_count')->nullable()->change());
        Schema::table('sales', function (Blueprint $t) {
            $t->string('payment_method', 20)->nullable();
            $t->string('payment_reference', 100)->nullable();
            $t->string('customer_type', 20)->nullable();
        });
    }

    public function down(): void
    {
        Schema::table('sales', fn (Blueprint $t) => $t->dropColumn(['payment_method', 'payment_reference', 'customer_type']));
        // Weight-only rows have no honest count; rollback is refused instead of inventing zero.
        abort_if(DB::table('harvest_items')->whereNull('fruit_count')->exists(), 500, 'Ada rincian panen tanpa jumlah buah; rollback dibatalkan.');
        Schema::table('harvest_items', fn (Blueprint $t) => $t->unsignedInteger('fruit_count')->nullable(false)->change());
    }
};
