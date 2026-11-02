<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('record_attachments', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->string('owner_type', 20);
            $t->uuid('owner_id');
            $t->foreign('owner_id')->references('entity_id')->on('entity_identities')->restrictOnDelete();
            $t->unsignedInteger('owner_version');
            $t->foreignUuid('greenhouse_id')->constrained()->restrictOnDelete();
            $t->foreignId('recorded_by')->constrained('users')->restrictOnDelete();
            $t->string('recorded_by_name', 100);
            $t->string('original_name', 200);
            $t->string('mime', 80);
            $t->unsignedInteger('bytes');
            $t->string('sha256', 64);
            $t->string('record_state', 20)->default('active');
            $t->unsignedInteger('version')->default(1);
            $t->timestamps(6);
            $t->index(['owner_type', 'owner_id', 'record_state', 'id'], 'record_attachment_owner');
            $t->index(['greenhouse_id', 'owner_type', 'id'], 'record_attachment_scope');
        });
        DB::statement("ALTER TABLE record_attachments ADD CONSTRAINT attachment_owner_kind CHECK (owner_type IN ('activity','harvest','expense') AND record_state IN ('active','cancelled'))");
        DB::statement('ALTER TABLE record_attachments ADD CONSTRAINT attachment_size_supported CHECK (bytes > 0 AND bytes <= 33554432 AND owner_version > 0)');
    }

    public function down(): void
    {
        throw new LogicException('Private evidence metadata must not be erased automatically. A reviewed recovery/retention plan is required.');
    }
};
