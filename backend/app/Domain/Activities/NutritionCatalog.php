<?php

namespace App\Domain\Activities;

use App\Domain\Cultivation\MasterService;
use App\Domain\Identity\EntityIdentity;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

/**
 * Inofarm 20 Nov 2026: nutrition is AB Mix plus extra nutrients typed by hand, measured in mL, L or gram.
 * Typed names and the three units become masters of that greenhouse only. Callers hold the sync_state row lock
 * (MutationLedger), which serializes creation, so the case-insensitive name guard cannot race.
 */
final class NutritionCatalog
{
    public const AB_MIX = 'AB Mix';

    public const PURPOSE = 'nutrition';

    /** Unit name to its dimension. */
    public const UNITS = ['mL' => 'volume', 'L' => 'volume', 'gram' => 'mass'];

    public function __construct(private MasterService $masters, private EntityIdentity $identities) {}

    /** Material id for a typed nutrient name in this greenhouse, created there when it is new. */
    public function material(User $actor, string $greenhouseId, string $name): string
    {
        $name = trim($name);

        return $this->find('materials', $greenhouseId, $name, fn ($q) => $q->where('purpose', self::PURPOSE))
            ?? $this->create($actor, 'materials', $greenhouseId, ['name' => $name, 'dimension' => self::PURPOSE, 'purpose' => self::PURPOSE]);
    }

    /** Unit id for mL, L or gram in this greenhouse; an active unit with the same name is reused. */
    public function unit(User $actor, string $greenhouseId, string $name): string
    {
        return $this->find('units', $greenhouseId, $name, fn ($q) => $q)
            ?? $this->create($actor, 'units', $greenhouseId, ['name' => $name, 'dimension' => self::UNITS[$name]]);
    }

    private function find(string $table, string $greenhouseId, string $name, callable $filter): ?string
    {
        return $filter(DB::table($table)->where('greenhouse_id', $greenhouseId)->where('active', true))
            ->whereRaw('LOWER(TRIM(name)) = ?', [mb_strtolower(trim($name))])->orderBy('created_at')->value('id');
    }

    private function create(User $actor, string $table, string $greenhouseId, array $values): string
    {
        $id = (string) Str::uuid();
        $this->identities->claim($table, $id);
        $now = now();
        DB::table($table)->insert(['id' => $id, 'greenhouse_id' => $greenhouseId, ...$values, 'active' => true, 'version' => 1,
            'created_at' => $now, 'updated_at' => $now]);
        $after = $this->masters->publicRow($table, DB::table($table)->where('id', $id)->first());
        DB::table('audit_logs')->insert(['actor_id' => $actor->id, 'operation_id' => (string) Str::uuid(), 'entity_type' => $table,
            'entity_id' => $id, 'action' => 'create', 'version' => 1, 'reason' => null,
            'changes' => json_encode(['before' => null, 'after' => $after], JSON_THROW_ON_ERROR), 'server_at' => $now]);
        DB::table('change_feed')->insert(['greenhouse_id' => $greenhouseId, 'audience' => 'all', 'entity_type' => $table,
            'entity_id' => $id, 'version' => 1, 'tombstone' => false, 'body' => json_encode($after, JSON_THROW_ON_ERROR), 'committed_at' => $now]);

        return $id;
    }
}
