<?php

namespace App\Domain\Harvest;

use App\Domain\Contracts\DecimalValue;
use App\Domain\Contracts\DomainFailure;
use App\Domain\Cultivation\GroupContext;
use App\Domain\Cultivation\MasterService;
use App\Domain\Cultivation\PlantAge;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;
use InvalidArgumentException;

final class HarvestService
{
    public function __construct(private GroupContext $context, private MasterService $masters, private MutationLedger $ledger, private HarvestBalance $balance) {}

    public function scope(User $actor)
    {
        return DB::table('harvests')->whereIn('planting_group_id', $this->masters->scope($actor, 'planting_groups')->select('id'));
    }

    public function publicRow(object $row): array
    {
        $result = (array) $row;
        $recorder = User::findOrFail($row->recorded_by);
        $result['recorded_by'] = $recorder->public_id;
        $result['recorded_by_name'] = $recorder->name;
        foreach (['occurred_at', 'created_at', 'updated_at'] as $key) {
            $result[$key] = CarbonImmutable::parse($result[$key], 'UTC')->toISOString();
        }
        $result['items'] = DB::table('harvest_items')->where('harvest_id', $row->id)->where('active', true)->orderBy('id')
            ->get(['id', 'grade_id', 'grade_name', 'grade_version', 'fruit_count', 'weight_kg'])->map(fn ($item) => (array) $item)->all();
        $snapshot = DB::table('harvest_reference_snapshots')->where('harvest_id', $row->id)->where('harvest_version', $row->version)->value('body');
        $result['reference_snapshot'] = $snapshot === null ? null : json_decode($snapshot, true, flags: JSON_THROW_ON_ERROR);
        $result['completeness'] = 'ready';

        return $result;
    }

    public function mutate(User $actor, array $op): array
    {
        return $this->ledger->run($actor, $op, function (User $current) use ($op) {
            $source = $op['action'] === 'create' ? null : ($this->scope($current)->where('id', $op['entity_id'])->first() ?? abort(404));
            $this->context->get($current, $source->planting_group_id ?? $op['payload']['planting_group_id']);
            if ($source !== null) {
                abort_unless($current->role === 'manager' || ($source->recorded_by === $current->id && $op['action'] !== 'cancel'), 403, 'Koreksi hanya untuk panen sendiri; pembatalan memerlukan pengelola.');
            }
        }, function (User $current) use ($op) {
            $before = null;
            if ($op['action'] !== 'create') {
                $row = $this->scope($current)->where('id', $op['entity_id'])->lockForUpdate()->first() ?? abort(404);
                $before = $this->publicRow($row);
                $this->ledger->version($row, $op['base_version'], $before);
                abort_if($row->record_state === 'cancelled', 422, 'Panen telah dibatalkan.');
            } elseif (DB::table('harvests')->where('id', $op['entity_id'])->exists()) {
                throw new DomainFailure('entity_exists', 'Identitas panen sudah digunakan.');
            }
            $p = $op['payload'];
            $ctx = $this->context->get($current, $before['planting_group_id'] ?? $p['planting_group_id'], true);
            $oldItems = DB::table('harvest_items')->where('harvest_id', $op['entity_id'])->orderBy('id')->lockForUpdate()->get()->keyBy('id');
            if ($op['action'] === 'cancel') {
                foreach ($oldItems as $item) {
                    $this->balance->assertWeight($item, '0');
                }
                $data = ['record_state' => 'cancelled'];
                $snapshot = $before['reference_snapshot'];
            } else {
                abort_if($before !== null && $p['planting_group_id'] !== $before['planting_group_id'], 422, 'Kelompok panen tidak dapat dipindah.');
                $preserve = $before !== null && $p['reference_policy'] === 'preserve';
                if ($preserve) {
                    abort_if(CarbonImmutable::parse($p['occurred_at'])->ne(CarbonImmutable::parse($before['occurred_at'])), 422, 'Perubahan waktu panen memerlukan refresh acuan eksplisit.');
                    $snapshot = $before['reference_snapshot'];
                } else {
                    $group = $ctx['group'];
                    $snapshot = ['group_version' => $group->version, 'sowing_date' => $group->sowing_date, 'transplant_date' => $group->transplant_date,
                        'age' => PlantAge::calculate($group->sowing_date, $group->transplant_date, $p['occurred_at'])];
                    if ($snapshot['age']['status'] === 'unavailable') {
                        throw ValidationException::withMessages(['occurred_at' => $snapshot['age']['reasons']]);
                    }
                }
                $submitted = [];
                foreach ($p['items'] as $index => $line) {
                    $old = $oldItems->get($line['id']);
                    abort_if($old === null && DB::table('harvest_items')->where('id', $line['id'])->exists(), 422, 'Identitas rincian panen sudah digunakan.');
                    abort_if($old !== null && $old->grade_id !== $line['grade_id'], 422, 'Grade rincian tidak dapat diganti. Buat rincian baru setelah meninjau saldo.');
                    $grade = DB::table('grades')->where('id', $line['grade_id'])->where('greenhouse_id', $ctx['greenhouse']->id)
                        ->when($old === null, fn ($q) => $q->where('active', true))->first() ?? abort(422, 'Grade harus berasal dari greenhouse yang sama; grade baru harus aktif.');
                    if (! is_int($line['fruit_count'])) {
                        throw ValidationException::withMessages(["items.$index.fruit_count" => 'Jumlah buah harus integer JSON.']);
                    }
                    try {
                        DecimalValue::parse($line['weight_kg']);
                    } catch (InvalidArgumentException $error) {
                        throw ValidationException::withMessages(["items.$index.weight_kg" => $error->getMessage()]);
                    }
                    if (bccomp($line['weight_kg'], '0', 6) < 0) {
                        throw ValidationException::withMessages(["items.$index.weight_kg" => 'Bobot harus tidak negatif.']);
                    }
                    if ($old !== null) {
                        $this->balance->assertWeight($old, $line['weight_kg']);
                    }
                    $submitted[$line['id']] = ['id' => $line['id'], 'harvest_id' => $op['entity_id'], 'grade_id' => $line['grade_id'],
                        'grade_name' => $preserve && $old !== null ? $old->grade_name : $grade->name,
                        'grade_version' => $preserve && $old !== null ? $old->grade_version : $grade->version,
                        'fruit_count' => $line['fruit_count'], 'weight_kg' => $line['weight_kg'], 'active' => true];
                }
                foreach ($oldItems as $id => $item) {
                    if (! isset($submitted[$id])) {
                        $this->balance->assertWeight($item, '0');
                    }
                }
                $data = ['planting_group_id' => $p['planting_group_id'], 'occurred_at' => CarbonImmutable::parse($p['occurred_at'])->utc()->format('Y-m-d H:i:s.u'), 'notes' => $p['notes']];
            }
            $data['version'] = ($before['version'] ?? 0) + 1;
            $data['updated_at'] = now();
            if ($before === null) {
                DB::table('harvests')->insert([...$data, 'id' => $op['entity_id'], 'recorded_by' => $current->id, 'created_at' => now()]);
            } else {
                DB::table('harvests')->where('id', $op['entity_id'])->update($data);
            }
            foreach ($oldItems as $id => $old) {
                if ($op['action'] === 'cancel') {
                    if ($old->active) {
                        DB::table('harvest_items')->where('id', $id)->update(['balance_version' => $old->balance_version + 1]);
                    }

                    continue;
                }
                $replacement = $submitted[$id] ?? null;
                if ($replacement === null) {
                    if ($old->active) {
                        DB::table('harvest_items')->where('id', $id)->update(['active' => false, 'balance_version' => $old->balance_version + 1]);
                    }
                } else {
                    DB::table('harvest_items')->where('id', $id)->update([...$replacement, 'balance_version' => $old->balance_version + 1]);
                }
            }
            foreach ($submitted ?? [] as $id => $item) {
                if (! $oldItems->has($id)) {
                    DB::table('harvest_items')->insert($item);
                }
            }
            DB::table('harvest_reference_snapshots')->insert(['harvest_id' => $op['entity_id'], 'harvest_version' => $data['version'], 'body' => json_encode($snapshot, JSON_THROW_ON_ERROR)]);
            $harvest = DB::table('harvests')->where('id', $op['entity_id'])->first();
            foreach (DB::table('harvest_items')->where('harvest_id', $op['entity_id'])->orderBy('id')->get() as $item) {
                if (! $oldItems->has($item->id) || $oldItems[$item->id]->balance_version !== $item->balance_version) {
                    $this->balance->publish($ctx['greenhouse']->id, $item, $harvest);
                }
            }

            return ['before' => $before, 'after' => $this->publicRow($harvest), 'greenhouse_id' => $ctx['greenhouse']->id];
        });
    }
}
