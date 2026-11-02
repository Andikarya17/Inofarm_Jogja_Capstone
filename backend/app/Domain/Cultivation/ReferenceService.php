<?php

namespace App\Domain\Cultivation;

use App\Domain\Contracts\DecimalValue;
use App\Domain\Contracts\DomainFailure;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;
use InvalidArgumentException;

final class ReferenceService
{
    public function __construct(private GroupContext $context, private GreenhouseAccess $access, private MasterService $masters, private MutationLedger $ledger) {}

    public function scope(User $actor, string $table)
    {
        return DB::table($table)->whereIn('planting_group_id', $this->masters->scope($actor, 'planting_groups')->select('id'));
    }

    public function publicRow(object $row): array
    {
        $result = (array) $row;
        $result['active'] = (bool) $row->active;
        foreach (['planned_at', 'effective_from', 'effective_until', 'created_at', 'updated_at'] as $key) {
            if (isset($result[$key])) {
                $result[$key] = CarbonImmutable::parse($result[$key], 'UTC')->toISOString();
            }
        }

        return $result;
    }

    public function mutate(User $user, string $table, array $op): array
    {
        return $this->ledger->run($user, $op, function (User $actor) use ($table, $op) {
            $this->access->manager($actor);
            $group = $op['action'] === 'create' ? $op['payload']['planting_group_id'] : ($this->scope($actor, $table)->where('id', $op['entity_id'])->value('planting_group_id') ?? abort(404));
            $this->context->get($actor, $group);
        }, function (User $actor) use ($table, $op) {
            $before = null;
            if ($op['action'] !== 'create') {
                $row = $this->scope($actor, $table)->where('id', $op['entity_id'])->first() ?? abort(404);
                $before = $this->publicRow($row);
                $this->ledger->version($row, $op['base_version'], $before);
            } elseif (DB::table($table)->where('id', $op['entity_id'])->exists()) {
                throw new DomainFailure('entity_exists', 'Identitas acuan sudah digunakan.');
            }
            $p = $op['payload'];
            unset($p['reason']);
            $groupId = $before['planting_group_id'] ?? $p['planting_group_id'];
            $ctx = $this->context->get($actor, $groupId, true);
            foreach (['planned_at', 'effective_from', 'effective_until'] as $key) {
                if (isset($p[$key])) {
                    $p[$key] = CarbonImmutable::parse($p[$key])->utc()->format('Y-m-d H:i:s.u');
                }
            }
            if ($table === 'targets') {
                $this->validateTarget($p);
                if ($p['active'] ?? true) {
                    $overlap = DB::table('targets')->where('planting_group_id', $groupId)->where('activity_type', $p['activity_type'])
                        ->where('parameter', $p['parameter'])->where('active', true)->where('id', '!=', $op['entity_id'])
                        ->where(fn ($q) => $q->whereNull('effective_until')->orWhere('effective_until', '>', $p['effective_from']))
                        ->when($p['effective_until'] !== null, fn ($q) => $q->where('effective_from', '<', $p['effective_until']))->exists();
                    abort_if($overlap, 422, 'Masa berlaku target tumpang tindih untuk parameter yang sama.');
                }
            }
            $p['version'] = $before === null ? 1 : $before['version'] + 1;
            $p['updated_at'] = now();
            if ($before === null) {
                DB::table($table)->insert([...$p, 'id' => $op['entity_id'], 'created_at' => now()]);
            } else {
                DB::table($table)->where('id', $op['entity_id'])->update($p);
            }
            $after = $this->publicRow(DB::table($table)->where('id', $op['entity_id'])->first());
            DB::table('cultivation_reference_versions')->insert(['entity_type' => $table, 'entity_id' => $op['entity_id'], 'version' => $after['version'], 'body' => json_encode($after, JSON_THROW_ON_ERROR)]);

            return ['before' => $before, 'after' => $after, 'greenhouse_id' => $ctx['greenhouse']->id];
        });
    }

    private function validateTarget(array $p): void
    {
        foreach (['target_value', 'minimum', 'maximum', 'tolerance'] as $key) {
            if ($p[$key] !== null) {
                try {
                    DecimalValue::parse($p[$key]);
                } catch (InvalidArgumentException $e) {
                    throw ValidationException::withMessages([$key => $e->getMessage()]);
                }
                abort_if(bccomp($p[$key], '0', 6) < 0, 422, 'Target atau toleransi tidak boleh negatif.');
            }
        }
        abort_if(($p['parameter'] === 'manual_ppm' ? 'ppm' : 'pH') !== $p['unit'], 422, 'Satuan target tidak sesuai parameter.');
        abort_if($p['effective_until'] !== null && $p['effective_until'] <= $p['effective_from'], 422, 'Akhir berlaku harus sesudah awal.');
        abort_if($p['target_value'] !== null && ($p['minimum'] !== null || $p['maximum'] !== null), 422, 'Pilih target tunggal atau rentang, bukan keduanya.');
        abort_if($p['target_value'] === null && $p['tolerance'] !== null, 422, 'Toleransi memerlukan target tunggal.');
        abort_if(($p['minimum'] === null) !== ($p['maximum'] === null), 422, 'Rentang memerlukan batas minimum dan maksimum.');
        abort_if($p['minimum'] !== null && bccomp($p['minimum'], $p['maximum'], 6) > 0, 422, 'Minimum melebihi maksimum.');
        if ($p['parameter'] === 'manual_ph') {
            foreach (['target_value', 'minimum', 'maximum'] as $key) {
                abort_if($p[$key] !== null && bccomp($p[$key], '14', 6) > 0, 422, 'Nilai target pH harus 0 sampai 14.');
            }
        }
    }

    public function snapshot(object $group, string $type, string $occurredAt, array $actual): array
    {
        $at = CarbonImmutable::parse($occurredAt)->utc()->format('Y-m-d H:i:s.u');
        $checks = [];
        foreach (['manual_ppm', 'manual_ph'] as $parameter) {
            $targets = DB::table('targets')->where('planting_group_id', $group->id)->where('activity_type', $type)->where('parameter', $parameter)
                ->where('active', true)->where('effective_from', '<=', $at)->where(fn ($q) => $q->whereNull('effective_until')->orWhere('effective_until', '>', $at))->limit(2)->get();
            $checks[$parameter] = $targets->count() > 1
                ? ['actual' => $actual[$parameter] ?? null, 'target' => null, 'delta' => null, 'status' => 'unavailable', 'reasons' => ['Target ambigu; pengelola perlu meninjau masa berlaku.']]
                : TargetComparator::compare($actual[$parameter] ?? null, $targets->count() === 1 ? $this->publicRow($targets[0]) : null);
        }

        return ['comparator_version' => '1', 'planting_group_version' => $group->version,
            'sowing_date' => $group->sowing_date, 'transplant_date' => $group->transplant_date,
            'age' => PlantAge::calculate($group->sowing_date, $group->transplant_date, $occurredAt), 'checks' => $checks];
    }
}
