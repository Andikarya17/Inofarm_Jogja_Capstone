<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\DomainFailure;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Identity\EntityIdentity;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

final class ExpenseService
{
    public function __construct(private GreenhouseAccess $access, private MutationLedger $ledger, private ExpenseBalance $balance) {}

    public function scope(User $actor)
    {
        $this->access->manager($actor);

        return DB::table('expenses')->whereIn('greenhouse_id', $this->access->ids($actor));
    }

    public function publicRow(object $row): array
    {
        $data = (array) $row;
        unset($data['allocated_amount'], $data['balance_version']);
        $creator = User::findOrFail($row->recorded_by);
        $data['recorded_by'] = $creator->public_id;
        $data['recorded_by_name'] = $creator->name;
        foreach (['paid_at', 'created_at', 'updated_at'] as $key) {
            $data[$key] = CarbonImmutable::parse($data[$key], 'UTC')->toISOString();
        }

        return $data;
    }

    public function writableGreenhouse(User $actor, string $id): void
    {
        abort_unless($this->access->require($actor, $id)->active, 422, 'Greenhouse tidak aktif.');
    }

    public function openSeason(object $season): void
    {
        if ($season->status !== 'open') {
            throw new DomainFailure('season_closed', 'Musim ditutup. Buka kembali dengan alasan sebelum mengubah biaya terkait.');
        }
    }

    public function mutate(User $actor, array $op): array
    {
        return $this->ledger->run($actor, $op, function (User $current) use ($op) {
            $this->access->manager($current);
            $gh = $op['action'] === 'create' ? $op['payload']['greenhouse_id'] : ($this->scope($current)->where('id', $op['entity_id'])->first() ?? abort(404))->greenhouse_id;
            $this->access->require($current, $gh);
        }, function (User $current) use ($op) {
            $before = null;
            $row = null;
            if ($op['action'] !== 'create') {
                $row = $this->scope($current)->where('id', $op['entity_id'])->lockForUpdate()->first() ?? abort(404);
                $before = $this->publicRow($row);
                $this->ledger->version($row, $op['base_version'], $before);
                abort_if($row->record_state !== 'active', 422, 'Pengeluaran telah dibatalkan.');
            } elseif (DB::table('expenses')->where('id', $op['entity_id'])->exists()) {
                throw new DomainFailure('entity_exists', 'Identitas pengeluaran sudah digunakan.');
            }
            $p = $op['payload'];
            $gh = $row->greenhouse_id ?? $p['greenhouse_id'];
            $this->writableGreenhouse($current, $gh);
            if ($row !== null) {
                $this->balance->assertConsistent($row);
                foreach (DB::table('seasons')->whereIn('id', DB::table('cost_allocations')->where('expense_id', $row->id)->where('record_state', 'active')->select('season_id'))->orderBy('id')->lockForUpdate()->get() as $season) {
                    $this->openSeason($season);
                }
            }
            if ($op['action'] === 'cancel') {
                if (DB::table('cost_allocations')->where('expense_id', $row->id)->where('record_state', 'active')->exists()) {
                    throw new DomainFailure('expense_in_use', 'Batalkan seluruh alokasi terkait sebelum membatalkan pembayaran sumber.');
                }
                $data = ['record_state' => 'cancelled'];
            } else {
                abort_if($row !== null && ($p['greenhouse_id'] !== $gh || $p['payment_kind'] !== $row->payment_kind), 422, 'Greenhouse dan jenis pembayaran tidak dapat dipindah; tinjau pembatalan lalu catatan baru.');
                $amount = Money::amount($p['amount']);
                if ($row !== null && bccomp($amount, $row->allocated_amount, 2) < 0) {
                    throw new DomainFailure('expense_in_use', 'Nominal pembayaran tidak boleh lebih kecil dari total alokasi aktif.');
                }
                $category = DB::table('expense_categories')->where('id', $p['category_id'])->where('greenhouse_id', $gh)
                    ->when(($row->category_id ?? null) !== $p['category_id'], fn ($q) => $q->where('active', true))->first() ?? abort(422, 'Kategori harus berasal dari greenhouse yang sama; kategori baru harus aktif.');
                $sameCategory = ($row->category_id ?? null) === $p['category_id'];
                $data = ['greenhouse_id' => $gh, 'category_id' => $p['category_id'], 'category_name' => $sameCategory ? $row->category_name : $category->name,
                    'category_version' => $sameCategory ? $row->category_version : $category->version, 'amount' => $amount, 'currency' => 'IDR',
                    'paid_at' => CarbonImmutable::parse($p['paid_at'])->utc()->format('Y-m-d H:i:s.u'), 'payment_kind' => $p['payment_kind'], 'notes' => $p['notes']];
            }
            $data['version'] = ($row->version ?? 0) + 1;
            $data['updated_at'] = now();
            if ($row === null) {
                $balanceId = (string) Str::uuid();
                app(EntityIdentity::class)->claim('expense_balance', $balanceId);
                DB::table('expenses')->insert([...$data, 'id' => $op['entity_id'], 'balance_id' => $balanceId, 'recorded_by' => $current->id, 'created_at' => now()]);
            } else {
                DB::table('expenses')->where('id', $row->id)->update([...$data, 'balance_version' => $row->balance_version + 1]);
            }
            $saved = DB::table('expenses')->where('id', $op['entity_id'])->first();
            $this->balance->publish($saved);

            return ['before' => $before, 'after' => $this->publicRow($saved), 'greenhouse_id' => $gh, 'audience' => 'manager'];
        });
    }
}
