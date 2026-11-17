<?php

namespace App\Domain\Cultivation;

use App\Domain\Identity\EntityIdentity;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

/**
 * Spray materials are one shared list across greenhouses: a name typed in any greenhouse becomes a master
 * material in every active greenhouse, so it shows in everyone's dropdown after sync.
 * Callers hold the sync_state row lock (MutationLedger or the artisan command), which serializes creation,
 * so the case-insensitive name guard cannot race.
 */
final class SprayMaterialCatalog
{
    /** Starting list from the user's decision of 17 November 2026, not from the paper. */
    public const DEFAULTS = ['Nisozin', 'Demolish', 'Regent', 'Antracol', 'Agrept', 'Amistartop', 'Bion M', 'Daconil', 'Besromil', 'Sop 3g/L'];

    public const PURPOSE = 'spray';

    public function __construct(private MasterService $masters, private EntityIdentity $identities) {}

    public static function key(string $name): string
    {
        return mb_strtolower(trim($name));
    }

    /** Adds the defaults and every spray name known elsewhere to one greenhouse; idempotent. Returns the number created. */
    public function ensure(User $actor, string $greenhouseId): int
    {
        $names = collect(self::DEFAULTS)->merge(DB::table('materials')->where('purpose', self::PURPOSE)->where('active', true)->orderBy('created_at')->pluck('name'))
            ->unique(fn ($name) => self::key($name));
        $created = 0;
        foreach ($names as $name) {
            if ($this->find($greenhouseId, $name) === null) {
                $this->create($actor, $greenhouseId, $name);
                $created++;
            }
        }

        return $created;
    }

    /** Material id for a typed spray name in this greenhouse, creating it in every active greenhouse when it is new. */
    public function resolve(User $actor, string $greenhouseId, string $name): string
    {
        $name = trim($name);
        if ($found = $this->find($greenhouseId, $name)) {
            return $found;
        }
        $targets = DB::table('greenhouses')->where('active', true)->pluck('id')->push($greenhouseId)->unique();
        foreach ($targets as $gh) {
            if ($this->find($gh, $name) === null) {
                $this->create($actor, $gh, $name);
            }
        }

        return $this->find($greenhouseId, $name);
    }

    private function find(string $greenhouseId, string $name): ?string
    {
        return DB::table('materials')->where('greenhouse_id', $greenhouseId)->where('purpose', self::PURPOSE)
            ->whereRaw('LOWER(TRIM(name)) = ?', [self::key($name)])->orderBy('created_at')->value('id');
    }

    private function create(User $actor, string $greenhouseId, string $name): void
    {
        $id = (string) Str::uuid();
        $this->identities->claim('materials', $id);
        $now = now();
        DB::table('materials')->insert(['id' => $id, 'greenhouse_id' => $greenhouseId, 'name' => $name, 'dimension' => self::PURPOSE,
            'purpose' => self::PURPOSE, 'active' => true, 'version' => 1, 'created_at' => $now, 'updated_at' => $now]);
        $after = $this->masters->publicRow('materials', DB::table('materials')->where('id', $id)->first());
        DB::table('audit_logs')->insert(['actor_id' => $actor->id, 'operation_id' => (string) Str::uuid(), 'entity_type' => 'materials',
            'entity_id' => $id, 'action' => 'create', 'version' => 1, 'reason' => null,
            'changes' => json_encode(['before' => null, 'after' => $after], JSON_THROW_ON_ERROR), 'server_at' => $now]);
        DB::table('change_feed')->insert(['greenhouse_id' => $greenhouseId, 'audience' => 'all', 'entity_type' => 'materials',
            'entity_id' => $id, 'version' => 1, 'tombstone' => false, 'body' => json_encode($after, JSON_THROW_ON_ERROR), 'committed_at' => $now]);
    }
}
