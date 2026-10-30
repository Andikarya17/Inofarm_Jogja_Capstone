<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\DomainFailure;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;

final class AssetService
{
    public function __construct(private GreenhouseAccess $access, private MutationLedger $ledger, private ExpenseService $expenses) {}

    public function table(string $kind): string
    {
        return match ($kind) {
            'asset' => 'assets', 'depreciation_allocation' => 'depreciation_allocations'
        };
    }

    public function scope(User $actor, string $kind)
    {
        $this->access->manager($actor);

        return DB::table($this->table($kind))->whereIn('greenhouse_id', $this->access->ids($actor));
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
        $kind = $op['entity_type'];

        return $this->ledger->run($actor, $op, function (User $current) use ($op, $kind) {
            $this->access->manager($current);
            if ($op['action'] !== 'create') {
                $this->scope($current, $kind)->where('id', $op['entity_id'])->first() ?? abort(404);
            } elseif ($kind === 'asset') {
                $this->expenses->scope($current)->where('id', $op['payload']['expense_id'])->first() ?? abort(404);
            } else {
                $asset = $this->scope($current, 'asset')->where('id', $op['payload']['asset_id'])->first() ?? abort(404);
                DB::table('seasons')->where('id', $op['payload']['season_id'])->where('greenhouse_id', $asset->greenhouse_id)->first() ?? abort(404);
            }
        }, function (User $current) use ($op, $kind) {
            $table = $this->table($kind);
            $row = $op['action'] === 'create' ? null : ($this->scope($current, $kind)->where('id', $op['entity_id'])->lockForUpdate()->first() ?? abort(404));
            $before = $row ? $this->publicRow($row) : null;
            if ($row) {
                $this->ledger->version($row, $op['base_version'], $before);
                abort_unless($row->record_state === 'active', 422, 'Catatan telah dibatalkan.');
            }
            $data = $kind === 'asset' ? $this->asset($current, $op, $row) : $this->allocation($current, $op, $row);
            $data['version'] = ($row->version ?? 0) + 1;
            $data['updated_at'] = now();
            if ($row) {
                DB::table($table)->where('id', $row->id)->update($data);
            } else {
                DB::table($table)->insert([...$data, 'id' => $op['entity_id'], 'recorded_by' => $current->id, 'created_at' => now()]);
            }
            $saved = DB::table($table)->where('id', $op['entity_id'])->first();

            return ['before' => $before, 'after' => $this->publicRow($saved), 'greenhouse_id' => $saved->greenhouse_id, 'audience' => 'manager'];
        });
    }

    private function asset(User $actor, array $op, ?object $row): array
    {
        $p = $op['payload'];
        $expense = $this->expenses->scope($actor)->where('id', $row->expense_id ?? $p['expense_id'])->lockForUpdate()->first() ?? abort(404);
        $this->expenses->writableGreenhouse($actor, $expense->greenhouse_id);
        abort_unless($expense->record_state === 'active' && $expense->payment_kind === 'asset_acquisition', 422, 'Aset memerlukan pembayaran pembelian aset aktif.');
        $used = $row && DB::table('depreciation_allocations')->where('asset_id', $row->id)->exists();
        if ($op['action'] === 'cancel') {
            if (DB::table('depreciation_allocations')->where('asset_id', $row->id)->where('record_state', 'active')->exists()) {
                throw new DomainFailure('asset_in_use', 'Batalkan alokasi penyusutan aktif terlebih dahulu.');
            }

            return ['record_state' => 'cancelled'];
        }
        abort_if($row && $p['expense_id'] !== $row->expense_id, 422, 'Sumber pembayaran aset tidak dapat dipindah.');
        $cost = Money::amount($p['cost'], 'cost');
        $residual = Money::amount($p['residual'], 'residual');
        DepreciationMath::base($cost, $residual);
        abort_if((int) substr($p['in_service_date'], 0, 4) < 1 || (int) substr($p['in_service_date'], 0, 4) + $p['life_years'] > 9999, 422, 'Akhir umur aset di luar rentang tanggal.');
        $last = DepreciationMath::year(bcsub($cost, $residual, 2), $p['life_years'], $p['life_years'], $p['in_service_date']);
        abort_if($last['end'] > '9999-12-31', 422, 'Akhir umur aset di luar rentang tanggal.');
        if ($used && ($row->cost !== $cost || $row->residual !== $residual || $row->life_years !== $p['life_years'] || $row->in_service_date !== $p['in_service_date'])) {
            throw new DomainFailure('asset_in_use', 'Dasar penyusutan yang telah mempunyai histori tidak boleh diubah.');
        }
        $reserved = (string) DB::table('assets')->where('expense_id', $expense->id)->where('record_state', 'active')->when($row, fn ($q) => $q->where('id', '<>', $row->id))->sum('cost');
        if (bccomp(bcadd($reserved, $cost, 2), $expense->amount, 2) > 0) {
            throw new DomainFailure('asset_exceeds_payment', 'Harga aset aktif melebihi pembayaran sumber.');
        }

        return ['expense_id' => $expense->id, 'greenhouse_id' => $expense->greenhouse_id, 'name' => $p['name'],
            'cost' => $cost, 'residual' => $residual, 'life_years' => $p['life_years'], 'in_service_date' => $p['in_service_date'],
            'usage_basis' => $p['usage_basis'], 'formula_version' => 'anniversary-cumulative-half-up-v1'];
    }

    private function allocation(User $actor, array $op, ?object $row): array
    {
        $p = $op['payload'];
        $asset = $this->scope($actor, 'asset')->where('id', $row->asset_id ?? $p['asset_id'])->lockForUpdate()->first() ?? abort(404);
        $season = DB::table('seasons')->where('id', $row->season_id ?? $p['season_id'])->where('greenhouse_id', $asset->greenhouse_id)->lockForUpdate()->first() ?? abort(404);
        $this->expenses->writableGreenhouse($actor, $asset->greenhouse_id);
        $this->expenses->openSeason($season);
        abort_unless($asset->record_state === 'active', 422, 'Aset telah dibatalkan.');
        if ($op['action'] === 'cancel') {
            return ['record_state' => 'cancelled'];
        }
        foreach (['asset_id', 'season_id', 'usage_year', 'period_start', 'period_end', 'usage_reference'] as $key) {
            abort_if($row && $p[$key] !== $row->$key, 422, 'Identitas sumber/pemakaian alokasi tidak dapat dipindah.');
        }
        $base = DepreciationMath::base($asset->cost, $asset->residual);
        $year = DepreciationMath::year($base, $asset->life_years, $p['usage_year'], $asset->in_service_date);
        abort_unless($p['period_start'] >= $year['start'] && $p['period_end'] <= $year['end'], 422, 'Periode harus berada dalam tahun pemakaian aset.');
        $amount = Money::amount($p['amount']);
        abort_unless(bccomp($amount, '0', 2) > 0, 422, 'Nominal alokasi harus positif.');
        $other = DB::table('depreciation_allocations')->where('asset_id', $asset->id)->when($row, fn ($q) => $q->where('id', '<>', $row->id));
        if ((clone $other)->where(fn ($q) => $q->where('usage_reference', $p['usage_reference'])->orWhere(fn ($q) => $q->where('period_start', $p['period_start'])->where('period_end', $p['period_end'])))->exists()) {
            throw new DomainFailure('duplicate_asset_usage', 'Pemakaian atau periode telah mempunyai catatan; tinjau histori aslinya.');
        }
        $active = (clone $other)->where('record_state', 'active');
        if (bccomp(bcadd((string) (clone $active)->where('usage_year', $p['usage_year'])->sum('amount'), $amount, 2), $year['budget'], 2) > 0
            || bccomp(bcadd((string) $active->sum('amount'), $amount, 2), $base, 2) > 0) {
            throw new DomainFailure('depreciation_exceeds_budget', 'Alokasi melebihi anggaran tahunan atau sisa penyusutan aset.');
        }

        return ['asset_id' => $asset->id, 'asset_version' => $asset->version, 'greenhouse_id' => $asset->greenhouse_id,
            'season_id' => $season->id, 'usage_year' => $p['usage_year'], 'period_start' => $p['period_start'], 'period_end' => $p['period_end'],
            'usage_reference' => $p['usage_reference'], 'amount' => $amount, 'year_budget' => $year['budget'],
            'allocation_basis' => $p['allocation_basis'], 'formula_version' => $year['formula_version']];
    }
}
