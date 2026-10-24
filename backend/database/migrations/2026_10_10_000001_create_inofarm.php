<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('tanks', function (Blueprint $t) {
            $t->id();
            $t->string('name');
            $t->timestamps(6);
        });
        Schema::table('users', function (Blueprint $t) {
            $t->foreignId('tank_id')->nullable()->constrained();
            $t->string('role')->default('owner');
        });
        Schema::create('devices', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignId('tank_id')->constrained();
            $t->string('name');
            $t->string('token_hash', 64)->unique();
            $t->boolean('enabled')->default(true);
            $t->timestamp('last_contact_at', 6)->nullable();
            $t->unsignedBigInteger('applied_config_version')->nullable();
            $t->timestamps(6);
        });
        Schema::create('personal_access_tokens', function (Blueprint $t) {
            $t->id();
            $t->morphs('tokenable');
            $t->text('name');
            $t->string('token', 64)->unique();
            $t->text('abilities')->nullable();
            $t->timestamp('last_used_at')->nullable();
            $t->timestamp('expires_at')->nullable()->index();
            $t->timestamps();
        });
        foreach (['config_versions', 'phase_versions'] as $table) {
            Schema::create($table, function (Blueprint $t) {
                $t->id();
                $t->foreignId('tank_id')->constrained();
                $t->foreignId('created_by')->constrained('users');
                $t->json('body');
                $t->timestamp('effective_from', 6);
                $t->index(['tank_id', 'effective_from']);
            });
        }
        Schema::create('workers', function (Blueprint $t) {
            $t->id();
            $t->foreignId('tank_id')->constrained();
            $t->timestamps(6);
        });
        Schema::create('worker_versions', function (Blueprint $t) {
            $t->id();
            $t->foreignId('worker_id')->constrained();
            $t->string('name');
            $t->boolean('active');
            $t->timestamp('effective_from', 6);
            $t->index(['worker_id', 'effective_from']);
        });
        Schema::create('shift_versions', function (Blueprint $t) {
            $t->id();
            $t->uuid('shift_key')->index();
            $t->foreignId('tank_id')->constrained();
            $t->foreignId('worker_id')->constrained();
            $t->json('body');
            $t->timestamp('effective_from', 6);
            $t->timestamp('effective_until', 6)->nullable();
            $t->foreignId('created_by')->constrained('users');
            $t->index(['tank_id', 'effective_from', 'effective_until']);
        });
        Schema::create('events', function (Blueprint $t) {
            $t->id();
            $t->uuid('event_id');
            $t->foreignUuid('device_id')->constrained();
            $t->foreignId('tank_id')->constrained();
            $t->string('kind');
            $t->string('payload_hash', 64);
            $t->json('body');
            $t->timestamp('source_at', 6)->nullable();
            $t->timestamp('received_at', 6);
            $t->string('phase')->nullable();
            $t->decimal('manual_ppm', 12, 3)->nullable();
            $t->json('validation_result')->nullable();
            $t->json('assignment')->nullable();
            $t->foreignId('config_version_id')->nullable()->constrained('config_versions');
            $t->foreignId('phase_version_id')->nullable()->constrained('phase_versions');
            $t->index(['tank_id', 'kind', 'source_at', 'id']);
            $t->unique(['device_id', 'event_id']);
        });
    }

    public function down(): void
    {
        foreach (['events', 'shift_versions', 'worker_versions', 'workers', 'phase_versions', 'config_versions', 'personal_access_tokens', 'devices'] as $table) {
            Schema::dropIfExists($table);
        }
        Schema::table('users', function (Blueprint $t) {
            $t->dropConstrainedForeignId('tank_id');
            $t->dropColumn('role');
        });
        Schema::dropIfExists('tanks');
    }
};
