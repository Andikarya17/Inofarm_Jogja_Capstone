<?php

use App\Domain\Identity\EntityIdentity;
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Frozen to schema8: future modules must not change the meaning/order of this historical migration.
    private const SOURCES = [
        'users' => ['users', 'public_id'], 'greenhouses' => ['greenhouses', 'id'], 'greenhouse_assignments' => ['greenhouse_assignments', 'id'],
        'varieties' => ['varieties', 'id'], 'units' => ['units', 'id'], 'materials' => ['materials', 'id'], 'grades' => ['grades', 'id'],
        'expense_categories' => ['expense_categories', 'id'], 'seasons' => ['seasons', 'id'], 'planting_groups' => ['planting_groups', 'id'],
        'schedules' => ['schedules', 'id'], 'targets' => ['targets', 'id'], 'activity' => ['activities', 'id'], 'harvest' => ['harvests', 'id'],
        'harvest_item' => ['harvest_items', 'id'], 'expense' => ['expenses', 'id'], 'expense_balance' => ['expenses', 'balance_id'], 'cost_allocation' => ['cost_allocations', 'id'],
    ];

    public function preflight(): void
    {
        $entries = EntityIdentity::entries(self::SOURCES);
        if (DB::query()->fromSub(clone $entries, 'identities')->select('entity_id')->groupBy('entity_id')->havingRaw('COUNT(*) > 1')->exists()) {
            throw new RuntimeException('Existing v2 UUIDs collide across kinds. Migration stopped; reconcile explicitly without automatic ID conversion.');
        }
        foreach ($entries->orderBy('entity_id')->lazy(500) as $row) {
            if (! preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/D', $row->entity_id)) {
                throw new RuntimeException('Existing v2 identity is not canonical. Migration stopped without converting it.');
            }
        }
    }

    public function up(): void
    {
        $this->preflight(); // Before DDL: conflict never causes a partially converted identity graph.
        Schema::create('entity_identities', function (Blueprint $t) {
            $t->uuid('entity_id')->primary();
            $t->string('entity_type', 40);
            $t->timestamp('reserved_at', 6)->useCurrent();
        });
        $this->backfill();
    }

    /** Apply only in maintenance; MySQL DDL does not share a transaction with the surrounding backfill. */
    public function backfill(): void
    {
        $this->preflight();
        DB::transaction(function () {
            abort_if(DB::table('entity_identities')->exists(), 409, 'Registry must be empty for initial backfill.');
            DB::table('entity_identities')->insertUsing(['entity_id', 'entity_type'], EntityIdentity::entries(self::SOURCES));
        });
    }

    public function down(): void
    {
        throw new LogicException('Removing UUID reservations could permit historical identity reuse. A reviewed forward/recovery plan is required.');
    }
};
