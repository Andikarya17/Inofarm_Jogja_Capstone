<?php

namespace App\Console\Commands;

use App\Domain\Identity\EntityIdentity;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

final class CheckFinanceIdentities extends Command
{
    protected $signature = 'inofarm:finance-identities {--json : Print counts only as JSON}';
    protected $description = 'Read-only v2 identity preflight; never renames, deletes or repairs records.';

    public function handle(): int
    {
        $result = DB::transaction(function () {
            DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
            $entries = EntityIdentity::entries();
            $registryPresent = Schema::hasTable('entity_identities');
            $count = DB::query()->fromSub(clone $entries, 'i')->count();
            $collisions = DB::query()->fromSub(clone $entries, 'i')->select('entity_id')->groupBy('entity_id')->havingRaw('COUNT(*) > 1');
            $missing = $registryPresent ? DB::query()->fromSub(clone $entries, 'i')->whereNotExists(fn ($q) => $q->selectRaw('1')->from('entity_identities as r')->whereColumn('r.entity_id', 'i.entity_id')->whereColumn('r.entity_type', 'i.entity_type'))->count() : $count;
            $invalid = 0;
            foreach ((clone $entries)->orderBy('entity_id')->lazy(500) as $row) {
                if (! preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/D', $row->entity_id)) {
                    $invalid++;
                }
            }

            return ['registry_present' => $registryPresent, 'identities' => $count, 'collisions' => DB::query()->fromSub($collisions, 'c')->count(), 'unregistered_or_mismatched' => $missing, 'invalid_uuid' => $invalid];
        });
        $this->line($this->option('json') ? json_encode($result, JSON_THROW_ON_ERROR) : 'Read-only identity check: '.json_encode($result, JSON_THROW_ON_ERROR));

        return $result['registry_present'] && $result['collisions'] + $result['unregistered_or_mismatched'] + $result['invalid_uuid'] === 0 ? self::SUCCESS : self::FAILURE;
    }
}
