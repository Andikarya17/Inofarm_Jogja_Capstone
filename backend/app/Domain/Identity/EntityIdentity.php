<?php

namespace App\Domain\Identity;

use App\Domain\Contracts\DomainFailure;
use Illuminate\Database\Query\Builder;
use Illuminate\Support\Facades\DB;

/** UUIDs are global across v2 records, users, inline children and live balance identities. */
final class EntityIdentity
{
    public const SOURCES = [
        'users' => ['users', 'public_id'], 'greenhouses' => ['greenhouses', 'id'],
        'greenhouse_assignments' => ['greenhouse_assignments', 'id'], 'varieties' => ['varieties', 'id'],
        'units' => ['units', 'id'], 'materials' => ['materials', 'id'], 'grades' => ['grades', 'id'],
        'expense_categories' => ['expense_categories', 'id'], 'seasons' => ['seasons', 'id'],
        'planting_groups' => ['planting_groups', 'id'], 'schedules' => ['schedules', 'id'], 'targets' => ['targets', 'id'],
        'activity' => ['activities', 'id'], 'harvest' => ['harvests', 'id'], 'harvest_item' => ['harvest_items', 'id'],
        'expense' => ['expenses', 'id'], 'expense_balance' => ['expenses', 'balance_id'], 'cost_allocation' => ['cost_allocations', 'id'],
        'sale' => ['sales', 'id'], 'sale_item' => ['sale_items', 'id'],
        'activity_attachment' => ['record_attachments', 'id', ['owner_type' => 'activity']],
        'harvest_attachment' => ['record_attachments', 'id', ['owner_type' => 'harvest']],
        'expense_attachment' => ['record_attachments', 'id', ['owner_type' => 'expense']],
    ];

    /** Includes historical root IDs even if an external writer removed a physical row. */
    public static function entries(array $sources = self::SOURCES, ?string $id = null): Builder
    {
        $union = null;
        foreach ($sources as $kind => $source) {
            [$table, $column] = $source;
            $part = DB::table($table)->select($column.' as entity_id')->selectRaw('? as entity_type', [$kind])->whereNotNull($column)
                ->when($id !== null, fn ($q) => $q->where($column, $id));
            foreach ($source[2] ?? [] as $field => $value) {
                $part->where($field, $value);
            }
            $union = $union === null ? $part : $union->unionAll($part);
        }
        $history = DB::table('audit_logs')->select('entity_id', 'entity_type')->whereIn('entity_type', array_keys($sources))
            ->when($id !== null, fn ($q) => $q->where('entity_id', $id));

        return DB::query()->fromSub($union->unionAll($history), 'identity_sources')->select('entity_id', 'entity_type')->distinct();
    }

    /** Caller owns sync_state row1 FOR UPDATE; reservation rolls back with its mutation. */
    public function claim(string $kind, string $id): void
    {
        if (DB::transactionLevel() < 1 || ! isset(self::SOURCES[$kind]) || ! preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/D', $id)) {
            throw new \LogicException('Identity claim requires a known kind, canonical UUID and authorized transaction.');
        }
        if (DB::table('entity_identities')->where('entity_id', $id)->exists() || self::entries(id: $id)->exists()) {
            // Never disclose the colliding type, owner, greenhouse or financial data.
            throw new DomainFailure('entity_exists', 'Identitas catatan sudah digunakan. Tinjau catatan dan antrean sebelum membuat identitas baru.');
        }
        DB::table('entity_identities')->insert(['entity_id' => $id, 'entity_type' => $kind]);
    }
}
