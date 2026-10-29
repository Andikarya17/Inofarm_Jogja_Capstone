<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\DomainFailure;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;

final class CostAllocationService
{
    public function __construct(private GreenhouseAccess $access, private MutationLedger $ledger, private ExpenseService $expenses, private ExpenseBalance $balance) {}

    public function scope(User $actor)
    {
        $this->access->manager($actor);

        return DB::table('cost_allocations')->whereIn('expense_id', $this->expenses->scope($actor)->select('id'));
    }

    public function publicRow(object $row): array
    {
        $data = (array) $row;
        $creator = User::findOrFail($row->recorded_by);
        $data['recorded_by'] = $creator->public_id;
        $data['recorded_by_name'] = $creator->name;
        foreach (['created_at', 'updated_at'] as $key) {
            $data[$key] = CarbonImmutable::parse($data[$key], 'UTC')->toISOString();
        }

        return $data;
    }

    public function mutate(User $actor, array $op): array
    {
        return $this->ledger->run($actor, $op, function (User $current) use ($op) {
            $this->access->manager($current);
            $source = $op['action'] === 'create' ? (object) $op['payload'] : ($this->scope($current)->where('id', $op['entity_id'])->first() ?? abort(404));
            $expense = $this->expenses->scope($current)->where('id', $source->expense_id)->first() ?? abort(404);
            DB::table('seasons')->where('id', $source->season_id)->where('greenhouse_id', $expense->greenhouse_id)->first() ?? abort(404);
        }, function (User $current) use ($op) {
            $p = $op['payload'];
            $row = null;
            $before = null;
            if ($op['action'] !== 'create') {
                $row = $this->scope($current)->where('id', $op['entity_id'])->lockForUpdate()->first() ?? abort(404);
                $before = $this->publicRow($row);
                $this->ledger->version($row, $op['base_version'], $before);
                abort_if($row->record_state !== 'active', 422, 'Alokasi telah dibatalkan.');
            } elseif (DB::table('cost_allocations')->where('id', $op['entity_id'])->exists()) {
                throw new DomainFailure('entity_exists', 'Identitas alokasi sudah digunakan.');
            }
            $expense = $this->expenses->scope($current)->where('id', $row->expense_id ?? $p['expense_id'])->lockForUpdate()->first() ?? abort(404);
            $this->balance->assertConsistent($expense);
            $season = DB::table('seasons')->where('id', $row->season_id ?? $p['season_id'])->where('greenhouse_id', $expense->greenhouse_id)->lockForUpdate()->first() ?? abort(404);
            $this->expenses->writableGreenhouse($current, $expense->greenhouse_id);
            $this->expenses->openSeason($season);
            abort_unless($expense->record_state === 'active' && $expense->payment_kind === 'operating', 422, 'Alokasi biaya operasi memerlukan pembayaran operasi aktif; pembelian aset tidak dihitung ulang sebagai biaya operasi.');
            $amount = $op['action'] === 'cancel' ? '0.00' : Money::amount($p['amount']);
            $newTotal = bcadd(bcsub($expense->allocated_amount, $row->amount ?? '0', 2), $amount, 2);
            if (bccomp($newTotal, $expense->amount, 2) > 0) {
                throw new DomainFailure('allocation_exceeds_source', 'Jumlah alokasi aktif melebihi pembayaran sumber. Tinjau sisa biaya terbaru.');
            }
            if ($op['action'] === 'cancel') {
                $data = ['record_state' => 'cancelled'];
            } else {
                abort_if($row !== null && ($p['expense_id'] !== $row->expense_id || $p['season_id'] !== $row->season_id), 422, 'Sumber dan musim alokasi tidak dapat dipindah. Batalkan lalu buat alokasi baru dengan alasan.');
                $data = ['expense_id' => $expense->id, 'season_id' => $season->id, 'amount' => $amount, 'currency' => 'IDR',
                    'cost_class' => $p['cost_class'], 'allocation_basis' => $p['allocation_basis'],
                    'season_name' => $row->season_name ?? $season->name, 'season_version' => $row->season_version ?? $season->version];
            }
            $data['version'] = ($row->version ?? 0) + 1;
            $data['updated_at'] = now();
            if ($row === null) {
                DB::table('cost_allocations')->insert([...$data, 'id' => $op['entity_id'], 'recorded_by' => $current->id, 'created_at' => now()]);
            } else {
                DB::table('cost_allocations')->where('id', $row->id)->update($data);
            }
            DB::table('expenses')->where('id', $expense->id)->update(['allocated_amount' => $newTotal, 'balance_version' => $expense->balance_version + 1]);
            $this->balance->publish(DB::table('expenses')->where('id', $expense->id)->first());

            return ['before' => $before, 'after' => $this->publicRow(DB::table('cost_allocations')->where('id', $op['entity_id'])->first()),
                'greenhouse_id' => $expense->greenhouse_id, 'audience' => 'manager'];
        });
    }
}
