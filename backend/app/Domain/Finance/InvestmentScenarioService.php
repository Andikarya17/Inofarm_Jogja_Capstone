<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\DecimalValue;
use App\Domain\Contracts\DomainFailure;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;

final class InvestmentScenarioService
{
    public function __construct(private GreenhouseAccess $access, private MutationLedger $ledger) {}

    public function scope(User $actor)
    {
        $this->access->manager($actor);

        return DB::table('investment_scenarios')->whereIn('greenhouse_id', $this->access->ids($actor));
    }

    public function publicRow(object $row): array
    {
        $flows = DB::table('investment_cash_flow_versions')->where('scenario_id', $row->id)->where('scenario_version', $row->version)->orderBy('year')->get()->map(fn ($v) => ['year' => $v->year, 'amount' => $v->amount, 'source_kind' => $v->source_kind, 'source_reference' => $v->source_reference, 'basis' => $v->basis])->all();
        if (count($flows) !== $row->horizon_years || array_column($flows, 'year') !== range(1, $row->horizon_years)) {
            throw new DomainFailure('scenario_source_integrity', 'Versi arus kas skenario tidak lengkap.', 500);
        }
        $source = json_decode($row->initial_source, true, flags: JSON_THROW_ON_ERROR);
        $creator = User::findOrFail($row->recorded_by);
        $complete = self::provenanceComplete($source, $flows);
        $numbers = $row->initial_outlay !== null && ! in_array(null, array_column($flows, 'amount'), true);

        return ['id' => $row->id, 'greenhouse_id' => $row->greenhouse_id, 'version' => $row->version, 'record_state' => $row->record_state, 'name' => $row->name, 'base_date' => $row->base_date, 'horizon_years' => $row->horizon_years,
            'initial_outlay' => $row->initial_outlay, 'initial_source' => $source, 'discount_rate' => $row->discount_rate, 'payback_convention' => $row->payback_convention, 'cash_flows' => $flows, 'notes' => $row->notes,
            'source_provenance' => $complete ? 'declared_complete' : 'incomplete', 'completeness' => $complete && $numbers && $row->discount_rate !== null && $row->payback_convention !== null ? 'evaluation_ready' : 'incomplete',
            'recorded_by' => $creator->public_id, 'recorded_by_name' => $creator->name, 'created_at' => CarbonImmutable::parse($row->created_at, 'UTC')->toISOString(), 'updated_at' => CarbonImmutable::parse($row->updated_at, 'UTC')->toISOString()];
    }

    public static function provenanceComplete(array $initial, array $flows): bool
    {
        if (! in_array($initial['kind'] ?? null, ['actual', 'projection'], true) || trim($initial['basis'] ?? '') === '') {
            return false;
        }
        foreach ($flows as $flow) {
            if (! in_array($flow['source_kind'], ['actual', 'projection'], true) || trim($flow['basis']) === '') {
                return false;
            }
        }

        return true;
    }

    public function mutate(User $actor, array $op): array
    {
        return $this->ledger->run($actor, $op, function (User $current) use ($op) {
            $this->access->manager($current);
            if ($op['action'] === 'create') {
                $this->access->require($current, $op['payload']['greenhouse_id']);
            } else {
                $this->scope($current)->where('id', $op['entity_id'])->first() ?? abort(404);
            }
        }, function (User $current) use ($op) {
            $old = $op['action'] === 'create' ? null : ($this->scope($current)->where('id', $op['entity_id'])->lockForUpdate()->first() ?? abort(404));
            $before = $old ? $this->publicRow($old) : null;
            if ($old) {
                $this->ledger->version($old, $op['base_version'], $before);
                abort_unless($old->record_state === 'active', 422, 'Skenario telah dibatalkan.');
            }
            $gh = $this->access->require($current, $old->greenhouse_id ?? $op['payload']['greenhouse_id']);
            abort_unless($gh->active, 422, 'Greenhouse tidak aktif.');
            $version = ($old->version ?? 0) + 1;
            if ($op['action'] === 'cancel') {
                $data = ['record_state' => 'cancelled'];
                $flows = $before['cash_flows'];
            } else {
                $p = $op['payload'];
                abort_if($old && $p['greenhouse_id'] !== $old->greenhouse_id, 422, 'Greenhouse skenario tidak dapat dipindah.');
                abort_unless(is_int($p['horizon_years']) && $p['horizon_years'] >= 1 && $p['horizon_years'] <= 100, 422, 'Horizon harus integer1..100.');
                abort_if((int) substr($p['base_date'], 0, 4) < 1 || (int) substr($p['base_date'], 0, 4) + $p['horizon_years'] > 9999, 422, 'Periode tahunan di luar rentang tanggal.');
                $flows = $p['cash_flows'];
                abort_unless(array_is_list($flows) && count($flows) === $p['horizon_years'], 422, 'Sediakan tahun1..horizon tepat satu kali.');
                $years = [];
                foreach ($flows as &$flow) {
                    abort_unless(is_int($flow['year']), 422, 'Tahun harus integer JSON.');
                    $years[] = $flow['year'];
                    $flow['amount'] = $this->money($flow['amount']);
                    $flow['source_reference'] = $flow['source_reference'] ?? '';
                    $flow['basis'] = $flow['basis'] ?? '';
                }unset($flow);
                sort($years);
                abort_unless($years === range(1, $p['horizon_years']), 422, 'Tahun tidak boleh hilang atau ganda.');
                usort($flows, fn ($a, $b) => $a['year'] <=> $b['year']);
                $initial = $this->money($p['initial_outlay']);
                abort_if($initial !== null && bccomp($initial, '0', 2) < 0, 422, 'Investasi awal tidak boleh negatif.');
                $rate = $p['discount_rate'];
                if ($rate !== null) {
                    DecimalValue::parse($rate, 8, 20);
                    abort_unless(bccomp($rate, '-1', 8) > 0, 422, 'Faktor1+r harus positif.');
                    $rate = bcadd($rate, '0', 8);
                }
                $source = $p['initial_source'];
                $source['reference'] = $source['reference'] ?? '';
                $source['basis'] = $source['basis'] ?? '';
                $data = ['greenhouse_id' => $gh->id, 'name' => $p['name'], 'base_date' => $p['base_date'], 'horizon_years' => $p['horizon_years'], 'initial_outlay' => $initial, 'initial_source' => json_encode($source, JSON_THROW_ON_ERROR), 'discount_rate' => $rate, 'payback_convention' => $p['payback_convention'], 'notes' => $p['notes'] ?? '', 'record_state' => 'active'];
            }
            $data['version'] = $version;
            $data['updated_at'] = now();
            if ($old) {
                DB::table('investment_scenarios')->where('id', $old->id)->update($data);
            } else {
                DB::table('investment_scenarios')->insert([...$data, 'id' => $op['entity_id'], 'recorded_by' => $current->id, 'created_at' => now()]);
            }
            foreach ($flows as $flow) {
                DB::table('investment_cash_flow_versions')->insert(['scenario_id' => $op['entity_id'], 'scenario_version' => $version, ...$flow]);
            }
            $saved = DB::table('investment_scenarios')->where('id', $op['entity_id'])->first();

            return ['before' => $before, 'after' => $this->publicRow($saved), 'greenhouse_id' => $gh->id, 'audience' => 'manager'];
        });
    }

    private function money(mixed $value): ?string
    {
        if ($value === null) {
            return null;
        }DecimalValue::parse($value, 2, 20);

        return bcadd($value, '0', 2);
    }
}
