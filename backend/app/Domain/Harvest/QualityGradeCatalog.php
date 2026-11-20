<?php

namespace App\Domain\Harvest;

use App\Domain\Cultivation\MasterService;
use App\Domain\Identity\EntityIdentity;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

/**
 * Inofarm 20 Nov 2026: Inofarm does not grade its fruit (the buyer does), so a harvest records layak and cacat only.
 * Each line is still filed under a grade of its greenhouse, so sales and reports keep working: an active grade named
 * "Layak" or "Cacat" (any case) is reused, else created there. Callers hold the sync_state row lock (MutationLedger).
 */
final class QualityGradeCatalog
{
    public const NAMES = ['saleable' => 'Layak', 'defective' => 'Cacat'];

    public function __construct(private MasterService $masters, private EntityIdentity $identities) {}

    public function resolve(User $actor, string $greenhouseId, string $quality): string
    {
        $name = self::NAMES[$quality];
        $found = DB::table('grades')->where('greenhouse_id', $greenhouseId)->where('active', true)
            ->whereRaw('LOWER(TRIM(name)) = ?', [mb_strtolower($name)])->orderBy('created_at')->value('id');
        if ($found !== null) {
            return $found;
        }
        $id = (string) Str::uuid();
        $this->identities->claim('grades', $id);
        $now = now();
        DB::table('grades')->insert(['id' => $id, 'greenhouse_id' => $greenhouseId, 'name' => $name, 'active' => true, 'version' => 1,
            'created_at' => $now, 'updated_at' => $now]);
        $after = $this->masters->publicRow('grades', DB::table('grades')->where('id', $id)->first());
        DB::table('audit_logs')->insert(['actor_id' => $actor->id, 'operation_id' => (string) Str::uuid(), 'entity_type' => 'grades',
            'entity_id' => $id, 'action' => 'create', 'version' => 1, 'reason' => null,
            'changes' => json_encode(['before' => null, 'after' => $after], JSON_THROW_ON_ERROR), 'server_at' => $now]);
        DB::table('change_feed')->insert(['greenhouse_id' => $greenhouseId, 'audience' => 'all', 'entity_type' => 'grades',
            'entity_id' => $id, 'version' => 1, 'tombstone' => false, 'body' => json_encode($after, JSON_THROW_ON_ERROR), 'committed_at' => $now]);

        return $id;
    }
}
