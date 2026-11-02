<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\CanonicalJson;
use App\Domain\Contracts\DomainFailure;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\AcceptedSnapshot;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;

final class InvestmentReportService
{
    public function __construct(private GreenhouseAccess $access, private InvestmentScenarioService $scenarios, private AcceptedSnapshot $snapshots, private MutationLedger $ledger, private SavedReportStorage $saved) {}

    public function scope(User $actor)
    {
        $this->access->manager($actor);

        return DB::table('report_snapshots')->where('kind', 'investment_report')->whereIn('greenhouse_id', $this->access->ids($actor));
    }

    public function publicRow(object $row): array
    {
        return $this->saved->publicRow($row);
    }

    public function full(object $row): array
    {
        return $this->saved->full($row);
    }

    public function preview(User $actor, array $input): array
    {
        return $this->summary($input, $this->collect($actor, $input['scenario_id']));
    }

    private function collect(User $actor, string $id): array
    {
        $row = $this->scenarios->scope($actor)->where('id', $id)->first() ?? abort(404);
        abort_unless($row->record_state === 'active', 422, 'Skenario telah dibatalkan; laporan lama tetap tersimpan.');
        $scenario = $this->snapshots->read('investment_scenario', $row);
        $gh = $this->access->require($actor, $row->greenhouse_id);
        if (($scenario['greenhouse_id'] ?? null) !== $gh->id || ($scenario['record_state'] ?? null) !== $row->record_state) {
            throw new DomainFailure('scenario_source_integrity', 'Identitas atau status versi skenario tidak konsisten.', 500);
        }
        $scope = ['greenhouse' => $this->snapshots->read('greenhouses', $gh), 'scenario' => array_intersect_key($scenario, array_flip(['id', 'version', 'name', 'base_date', 'horizon_years', 'discount_rate', 'payback_convention', 'notes', 'recorded_by', 'recorded_by_name', 'created_at', 'updated_at']))];
        $sources = [['source_key' => 'initial_outlay', 'kind' => 'initial_outlay', 'scenario_id' => $id, 'version' => $scenario['version'], 'year' => 0, 'known' => $scenario['initial_outlay'] !== null,
            'data' => ['amount' => $scenario['initial_outlay'], 'source_kind' => $scenario['initial_source']['kind'], 'source_reference' => $scenario['initial_source']['reference'], 'basis' => $scenario['initial_source']['basis'], 'at_date' => $scenario['base_date']]]];
        $base = CarbonImmutable::parse($scenario['base_date'], 'UTC');
        foreach ($scenario['cash_flows'] as $flow) {
            $year = $flow['year'];
            $sources[] = ['source_key' => 'year:'.$year, 'kind' => 'annual_cash_flow', 'scenario_id' => $id, 'version' => $scenario['version'], 'year' => $year, 'known' => $flow['amount'] !== null,
                'data' => [...$flow, 'period_start' => $base->addYearsNoOverflow($year - 1)->toDateString(), 'period_end' => $base->addYearsNoOverflow($year)->toDateString(), 'timing' => 'end_of_annual_period']];
        }

        return ['scope' => $scope, 'scenario' => $scenario, 'sources' => $sources, 'sources_hash' => CanonicalJson::hash(['scope' => $scope, 'sources' => $sources])];
    }

    private function summary(array $input, array $collected): array
    {
        $scenario = $collected['scenario'];
        $counts = ['actual' => 0, 'projection' => 0, 'unknown' => 0];
        foreach ($collected['sources'] as $source) {
            $kind = $source['data']['source_kind'] ?? 'unknown';
            $counts[$kind]++;
        }

        return ['greenhouse_id' => $scenario['greenhouse_id'], 'season_id' => null, 'scenario_id' => $scenario['id'], 'scenario_version' => $scenario['version'], 'scope' => $collected['scope'], 'formula_version' => InvestmentEvaluation::VERSION,
            'currency' => 'IDR', 'timezone' => 'Asia/Jakarta', 'report_state' => 'provisional', 'review_note' => $input['review_note'], 'source_count' => count($collected['sources']), 'sources_hash' => $collected['sources_hash'], 'source_summary' => $counts,
            'metrics' => InvestmentEvaluation::calculate($scenario['initial_outlay'], array_column($scenario['cash_flows'], 'amount'), $scenario['discount_rate'], $scenario['payback_convention'], $scenario['source_provenance'] === 'declared_complete'),
            'assumptions' => ['Input aktual/proyeksi adalah pernyataan pengelola dengan basis tercatat; periksa kesesuaiannya terhadap bukti bisnis.',
                'Tahun dihitung relatif tanggal acuan; arus kas pada akhir periode tahunan, investasi awal sekali pada tahun0.',
                'Kas neto tidak dihitung otomatis dari laba musim atau penyusutan. Realisasi nilai sisa harus dinyatakan dalam CF akhir jika digunakan.',
                'Indikator merupakan analisis input skenario, bukan rekomendasi kelayakan otomatis; konvensi waktu pengembalian dipilih eksplisit.']];
    }

    public function create(User $actor, array $op): array
    {
        return $this->ledger->run($actor, $op, function (User $current) use ($op) {
            $this->scenarios->scope($current)->where('id', $op['payload']['scenario_id'])->first() ?? abort(404);
        }, function (User $current) use ($op) {
            $p = $op['payload'];
            if ($p['expected_formula_version'] !== InvestmentEvaluation::VERSION) {
                throw new DomainFailure('report_formula_changed', 'Versi rumus berubah. Tinjau ulang skenario.');
            }
            $collected = $this->collect($current, $p['scenario_id']);
            if ($collected['scenario']['version'] !== $p['expected_scenario_version'] || ! hash_equals($collected['sources_hash'], $p['expected_sources_hash'])) {
                throw new DomainFailure('report_sources_changed', 'Skenario atau sumber berubah. Muat pratinjau dan tinjau lagi.');
            }
            $time = now()->toISOString();
            $body = [...$this->summary($p, $collected), 'id' => $op['entity_id'], 'kind' => 'investment_report', 'version' => 1, 'recorded_by' => $current->public_id, 'recorded_by_name' => $current->name, 'created_at' => $time, 'generated_at' => $time, 'sources' => $collected['sources']];
            DB::table('report_snapshots')->insert(['id' => $op['entity_id'], 'kind' => 'investment_report', 'version' => 1, 'greenhouse_id' => $body['greenhouse_id'], 'season_id' => null, 'scenario_id' => $p['scenario_id'], 'recorded_by' => $current->id,
                'content' => json_encode($body, JSON_THROW_ON_ERROR), 'snapshot_hash' => CanonicalJson::hash($body), 'sources_hash' => $body['sources_hash'], 'source_count' => $body['source_count'], 'created_at' => now()]);
            $row = DB::table('report_snapshots')->where('id', $op['entity_id'])->first();

            return ['before' => null, 'after' => $this->publicRow($row), 'greenhouse_id' => $row->greenhouse_id, 'audience' => 'manager'];
        });
    }
}
