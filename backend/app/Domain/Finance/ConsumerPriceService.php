<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\DomainFailure;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;

/**
 * Farm-wide price per kg for individual (consumer) buyers, one active price per variety and quality. Prices belong to
 * no greenhouse, so they never enter the change feed; every change still writes an audit row. No seed prices.
 */
final class ConsumerPriceService
{
    public function __construct(private GreenhouseAccess $access, private MutationLedger $ledger) {}

    /** Lookup key: lower case, trimmed, inner runs of whitespace collapsed, so "golden " and "GOLDEN" share a price. */
    public static function key(string $name): string
    {
        return mb_strtolower(trim(preg_replace('/\s+/u', ' ', $name)));
    }

    public function list(User $actor, bool $all): array
    {
        $this->access->manager($actor);
        $rows = DB::table('consumer_prices')->when(! $all, fn ($q) => $q->where('record_state', 'active'))
            ->orderBy('variety_name')->orderBy('quality')->orderBy('id')->get();

        return ['data' => $rows->map(fn ($row) => $this->publicRow($row))->values()];
    }

    public function publicRow(object $row): array
    {
        return ['id' => $row->id, 'variety_name' => $row->variety_name, 'quality' => $row->quality, 'price_per_kg' => (string) $row->price_per_kg,
            'version' => (int) $row->version, 'record_state' => $row->record_state === 'active' ? 'active' : 'inactive',
            'updated_at' => CarbonImmutable::parse($row->updated_at, 'UTC')->toISOString()];
    }

    public function mutate(User $actor, array $op): array
    {
        return $this->ledger->run($actor, $op, fn (User $current) => $this->access->manager($current), function () use ($op) {
            $p = $op['payload'];
            $name = trim(preg_replace('/\s+/u', ' ', $p['variety_name']));
            $key = self::key($name);
            $state = ($p['record_state'] ?? 'active') === 'inactive' ? 'cancelled' : 'active';
            $now = now();
            if ($op['action'] === 'create') {
                if ($state === 'active') {
                    $this->assertFree($key, $p['quality'], null);
                }
                DB::table('consumer_prices')->insert(['id' => $op['entity_id'], 'variety_key' => $key, 'variety_name' => $name, 'quality' => $p['quality'],
                    'price_per_kg' => $p['price_per_kg'], 'record_state' => $state, 'version' => 1, 'created_at' => $now, 'updated_at' => $now]);

                return ['before' => null, 'after' => $this->publicRow(DB::table('consumer_prices')->where('id', $op['entity_id'])->first()), 'greenhouse_id' => null];
            }
            $row = DB::table('consumer_prices')->where('id', $op['entity_id'])->lockForUpdate()->first() ?? abort(404);
            $before = $this->publicRow($row);
            $this->ledger->version($row, $op['base_version'], $before);
            if ($state === 'active') {
                $this->assertFree($key, $p['quality'], $row->id);
            }
            DB::table('consumer_prices')->where('id', $row->id)->update(['variety_key' => $key, 'variety_name' => $name, 'quality' => $p['quality'],
                'price_per_kg' => $p['price_per_kg'], 'record_state' => $state, 'version' => $row->version + 1, 'updated_at' => $now]);

            return ['before' => $before, 'after' => $this->publicRow(DB::table('consumer_prices')->where('id', $row->id)->first()), 'greenhouse_id' => null];
        });
    }

    /** Runs under the global sync_state lock taken by the ledger, so the check cannot race another writer. */
    private function assertFree(string $key, string $quality, ?string $exceptId): void
    {
        $taken = DB::table('consumer_prices')->where('variety_key', $key)->where('quality', $quality)->where('record_state', 'active')
            ->when($exceptId, fn ($q) => $q->where('id', '!=', $exceptId))->exists();
        if ($taken) {
            throw new DomainFailure('duplicate', 'Harga untuk varietas dan kualitas ini sudah ada.');
        }
    }
}
