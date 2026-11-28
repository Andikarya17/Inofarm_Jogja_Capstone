<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\CanonicalJson;
use App\Domain\Contracts\DomainFailure;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\AcceptedSnapshot;
use App\Models\User;
use Illuminate\Support\Facades\DB;

/** Source selection only; caller holds the sync-state read/mutation lock. */
final class SeasonReportSources
{
    public const MAX_SOURCES = 10000;

    public function __construct(private GreenhouseAccess $access, private AcceptedSnapshot $snapshots, private SeasonCostSheetService $sheets) {}

    public function collect(User $actor, string $seasonId): array
    {
        $this->access->manager($actor);
        $season = DB::table('seasons')->whereIn('greenhouse_id', $this->access->ids($actor))->where('id', $seasonId)->first() ?? abort(404);
        $gh = $this->access->require($actor, $season->greenhouse_id);
        $scope = ['greenhouse' => $this->snapshots->read('greenhouses', $gh), 'season' => $this->snapshots->read('seasons', $season)];
        $sources = [];
        $expenseIds = [];
        $assetIds = [];
        $totals = ['FC' => '0.00', 'VC' => '0.00', 'TR' => '0.00', 'Q' => '0.000000'];
        $counts = ['FC' => 0, 'VC' => 0, 'sales' => 0, 'cancelled' => 0, 'allocations' => 0];
        foreach (['cost_allocation' => 'cost_allocations', 'depreciation_allocation' => 'depreciation_allocations', 'sale' => 'sales'] as $kind => $table) {
            $query = DB::table($table)->where('season_id', $seasonId);
            // Cost allocations may come from a shared expense paid under another greenhouse; the season already scopes them.
            if ($kind !== 'cost_allocation') {
                $query->where('greenhouse_id', $gh->id);
            }
            $rows = $query->orderBy('id')->limit(self::MAX_SOURCES + 1)->get();
            $this->capacity(count($sources) + $rows->count());
            $bodies = $this->snapshots->readMany($kind, $rows->all());
            foreach ($rows as $row) {
                $body = $bodies[$row->id];
                if ($kind === 'cost_allocation') {
                    if (($body['expense_id'] ?? null) !== $row->expense_id) {
                        throw new DomainFailure('report_source_integrity', 'Identitas pembayaran sumber tidak konsisten.', 500);
                    }
                } else {
                    $this->parent($body, $gh->id);
                }
                if (($body['season_id'] ?? null) !== $seasonId) {
                    throw new DomainFailure('report_source_integrity', 'Musim sumber laporan tidak konsisten.', 500);
                }
                if (! in_array($body['record_state'] ?? null, ['active', 'cancelled'], true) || $body['record_state'] !== $row->record_state) {
                    throw new DomainFailure('report_source_integrity', 'Status revisi sumber tidak konsisten.', 500);
                }
                $included = $body['record_state'] === 'active';
                $contribution = null;
                if (! $included) {
                    $counts['cancelled']++;
                }
                if ($kind === 'sale') {
                    $included = $included && ($body['payment_status'] ?? null) === 'paid';
                    if ($included) {
                        $totals['TR'] = bcadd($totals['TR'], $body['total_amount'], 2);
                        $totals['Q'] = bcadd($totals['Q'], $body['total_weight_kg'], 6);
                        $counts['sales']++;
                        $contribution = ['TR' => $body['total_amount'], 'Q' => $body['total_weight_kg']];
                    }
                } else {
                    $class = $kind === 'depreciation_allocation' ? 'FC' : $body['cost_class'];
                    if (! in_array($class, ['FC', 'VC'], true)) {
                        throw new DomainFailure('report_source_integrity', 'Kelas biaya sumber tidak dikenal.', 500);
                    }
                    if ($kind === 'cost_allocation') {
                        $expenseIds[$body['expense_id']] = true;
                    } else {
                        $assetIds[$body['asset_id']] = true;
                    }
                    // Real costs stay visible but are never summed: the season uses its standard cost sheet.
                    if ($included) {
                        $counts['allocations']++;
                    }
                    $sources[] = ['kind' => $kind, 'id' => $row->id, 'version' => $row->version, 'included' => false, 'role' => 'reference_only',
                        'contribution' => null, 'reason' => 'Biaya nyata; laporan memakai biaya standar.', 'data' => $body];

                    continue;
                }
                $sources[] = ['kind' => $kind, 'id' => $row->id, 'version' => $row->version, 'included' => $included,
                    'role' => 'season_transaction', 'contribution' => $contribution, 'reason' => $included ? 'Ikut dihitung dalam biaya/penjualan.' : 'Dibatalkan atau bukan penjualan lunas; dikeluarkan dari agregasi.', 'data' => $body];
            }
        }
        $sheet = $this->standardCosts($actor, $season, $sources, $totals, $counts);
        if ($assetIds) {
            $rows = DB::table('assets')->where('greenhouse_id', $gh->id)->whereIn('id', array_keys($assetIds))->orderBy('id')->get();
            if ($rows->count() !== count($assetIds)) {
                throw new DomainFailure('report_source_integrity', 'Sumber aset tidak lengkap pada greenhouse.', 500);
            }
            $this->capacity(count($sources) + $rows->count());
            foreach ($this->snapshots->readMany('asset', $rows->all()) as $body) {
                $this->parent($body, $gh->id);
                $expenseIds[$body['expense_id']] = true;
                $sources[] = ['kind' => 'asset', 'id' => $body['id'], 'version' => $body['version'], 'included' => false, 'role' => 'reference_only',
                    'contribution' => null, 'reason' => 'Harga aset tidak ditambahkan lagi ke biaya musim; hanya alokasi penyusutannya.', 'data' => $body];
            }
        }
        if ($expenseIds) {
            $rows = DB::table('expenses')->whereIn('id', array_keys($expenseIds))->orderBy('id')->get();
            if ($rows->count() !== count($expenseIds)) {
                throw new DomainFailure('report_source_integrity', 'Pembayaran sumber tidak lengkap.', 500);
            }
            $this->capacity(count($sources) + $rows->count());
            foreach ($this->snapshots->readMany('expense', $rows->all()) as $body) {
                $sources[] = ['kind' => 'expense', 'id' => $body['id'], 'version' => $body['version'], 'included' => false, 'role' => 'reference_only',
                    'contribution' => null, 'reason' => 'Pembayaran sumber saja; biaya masuk melalui alokasi, bukan pembayaran kedua.', 'data' => $body];
            }
        }
        usort($sources, fn ($a, $b) => strcmp($a['kind'].':'.$a['id'], $b['kind'].':'.$b['id']));

        return ['scope' => $scope, 'sources' => $sources, 'totals' => $totals, 'counts' => $counts, 'cost_sheet' => $sheet['ref'], 'cost_gaps' => $sheet['gaps'],
            'sources_hash' => CanonicalJson::hash(['scope' => $scope, 'sources' => $sources])];
    }

    /** Adds the season's standard cost sheet (sheet n for season sequence n); FC/VC stay null while a line is blank. */
    private function standardCosts(User $actor, object $season, array &$sources, array &$totals, array &$counts): array
    {
        if ($season->sequence === null) {
            $totals['FC'] = $totals['VC'] = null;

            return ['ref' => null, 'gaps' => ['Nomor musim belum ada']];
        }
        $public = $this->sheets->publicSheet($this->sheets->ensureFor((int) $season->sequence, $actor));
        $totals['FC'] = $public['totals']['FC'];
        $totals['VC'] = $public['totals']['VC'];
        $counts['FC'] = count(array_filter($public['lines'], fn ($line) => $line['cost_class'] === 'FC'));
        $counts['VC'] = count(array_filter($public['lines'], fn ($line) => $line['cost_class'] === 'VC'));
        $gaps = array_values(array_map(fn ($line) => $line['name'], array_filter($public['lines'], fn ($line) => $line['amount'] === null)));
        $sources[] = ['kind' => 'season_cost_sheet', 'id' => $public['id'], 'version' => $public['version'], 'included' => true, 'role' => 'season_cost_basis',
            'contribution' => ['FC' => $public['totals']['FC'], 'VC' => $public['totals']['VC']],
            'reason' => 'Biaya standar Musim '.$public['sequence'].' yang sama untuk setiap greenhouse.', 'data' => SeasonCostSheetService::reportSource($public)];

        return ['ref' => ['id' => $public['id'], 'sequence' => $public['sequence'], 'version' => $public['version']], 'gaps' => $gaps];
    }

    private function capacity(int $count): void
    {
        if ($count > self::MAX_SOURCES) {
            throw new DomainFailure('report_source_limit', 'Jumlah sumber melewati batas teknis10000 per snapshot. Data tidak dipotong atau diubah.');
        }
    }

    private function parent(array $body, string $greenhouseId): void
    {
        if (($body['greenhouse_id'] ?? null) !== $greenhouseId) {
            throw new DomainFailure('report_source_integrity', 'Greenhouse sumber laporan tidak konsisten.', 500);
        }
    }
}
