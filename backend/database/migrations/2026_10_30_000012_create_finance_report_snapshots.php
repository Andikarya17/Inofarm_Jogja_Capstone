<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('report_snapshots', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->string('kind', 40);
            $t->foreignUuid('greenhouse_id')->constrained()->restrictOnDelete();
            $t->foreignUuid('season_id')->nullable()->constrained()->restrictOnDelete();
            $t->foreignId('recorded_by')->constrained('users')->restrictOnDelete();
            $t->unsignedInteger('version')->default(1);
            $t->string('snapshot_hash', 64);
            $t->string('sources_hash', 64);
            $t->unsignedInteger('source_count');
            $t->json('content');
            $t->timestamp('created_at', 6);
            $t->index(['greenhouse_id', 'kind', 'season_id', 'created_at']);
        });
        DB::statement("ALTER TABLE report_snapshots ADD CONSTRAINT report_snapshot_version CHECK (version = 1 AND kind IN ('season_report','investment_report'))");
    }

    public function down(): void
    {
        throw new LogicException('Published report snapshots require a reviewed recovery plan.');
    }
};
