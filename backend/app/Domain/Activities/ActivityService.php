<?php

namespace App\Domain\Activities;

use App\Domain\Contracts\DecimalValue;
use App\Domain\Contracts\DomainFailure;
use App\Domain\Cultivation\GroupContext;
use App\Domain\Cultivation\MasterService;
use App\Domain\Cultivation\ReferenceService;
use App\Domain\Cultivation\TargetComparator;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;
use InvalidArgumentException;

final class ActivityService
{
    public function __construct(private GroupContext $context, private MasterService $masters, private ReferenceService $references, private MutationLedger $ledger) {}

    public function scope(User $actor)
    {
        return DB::table('activities')->whereIn('planting_group_id', $this->masters->scope($actor, 'planting_groups')->select('id'));
    }

    public function publicRow(object $row): array
    {
        $result = (array) $row;
        $recorder = User::findOrFail($row->recorded_by);
        $result['recorded_by'] = $recorder->public_id;
        $result['recorded_by_name'] = $recorder->name;
        $result['observations'] = $row->observations === null ? null : json_decode($row->observations, true, flags: JSON_THROW_ON_ERROR);
        foreach (['occurred_at', 'created_at', 'updated_at'] as $key) {
            $result[$key] = CarbonImmutable::parse($result[$key], 'UTC')->toISOString();
        }
        $result['materials'] = DB::table('activity_materials')->where('activity_id', $row->id)->orderBy('id')->get(['material_id', 'unit_id', 'quantity'])->map(fn ($r) => (array) $r)->all();
        $snapshot = DB::table('activity_reference_snapshots')->where('activity_id', $row->id)->where('activity_version', $row->version)->value('body');
        $result['reference_snapshot'] = $snapshot === null ? null : json_decode($snapshot, true, flags: JSON_THROW_ON_ERROR);
        $result['completeness'] = 'ready';

        return $result;
    }

    public function mutate(User $actor, array $op): array
    {
        return $this->ledger->run($actor, $op, function (User $current) use ($op) {
            $source = $op['action'] === 'create' ? null : ($this->scope($current)->where('id', $op['entity_id'])->first() ?? abort(404));
            // Replays still require current scope/role. Closed/inactive state blocks only a new mutation.
            $this->context->get($current, $source->planting_group_id ?? $op['payload']['planting_group_id']);
            if ($source !== null) {
                abort_unless($current->role === 'manager' || ($source->recorded_by === $current->id && $op['action'] !== 'cancel'), 403, 'Koreksi hanya untuk catatan sendiri; pembatalan memerlukan pengelola.');
            }
        }, function (User $current) use ($op) {
            $before = null;
            if ($op['action'] !== 'create') {
                $row = $this->scope($current)->where('id', $op['entity_id'])->first() ?? abort(404);
                $before = $this->publicRow($row);
                $this->ledger->version($row, $op['base_version'], $before);
                abort_if($row->record_state === 'cancelled', 422, 'Catatan telah dibatalkan.');
            } elseif (DB::table('activities')->where('id', $op['entity_id'])->exists()) {
                throw new DomainFailure('entity_exists', 'Identitas aktivitas sudah digunakan.');
            }
            $p = $op['payload'];
            $ctx = $this->context->get($current, $before['planting_group_id'] ?? $p['planting_group_id'], true);
            if ($op['action'] === 'create' && isset($p['running_period_version'])) {
                app(\App\Domain\Cultivation\RunningPeriod::class)->assertHomeParent($ctx, $p['running_period_version']);
            }
            if ($op['action'] === 'cancel') {
                $data = ['record_state' => 'cancelled'];
                $snapshot = $before['reference_snapshot'];
            } else {
                abort_if($before !== null && $p['planting_group_id'] !== $before['planting_group_id'], 422, 'Kelompok aktivitas tidak dapat dipindah.');
                $this->validate($p, $ctx['greenhouse']->id, $before);
                if ($before !== null && ($p['reference_policy'] ?? 'preserve') === 'preserve') {
                    abort_if(CarbonImmutable::parse($p['occurred_at'])->ne(CarbonImmutable::parse($before['occurred_at'])) || $p['activity_type'] !== $before['activity_type'], 422, 'Perubahan waktu/jenis memerlukan refresh acuan eksplisit dan alasan.');
                    $snapshot = $before['reference_snapshot'];
                    foreach (['manual_ec', 'manual_ppm', 'manual_ph'] as $parameter) {
                        $snapshot['checks'][$parameter] = TargetComparator::compare($p[$parameter] ?? null, $snapshot['checks'][$parameter]['target'] ?? null);
                    }
                } else {
                    $snapshot = $this->references->snapshot($ctx['group'], $p['activity_type'], $p['occurred_at'], $p);
                    if ($snapshot['age']['status'] === 'unavailable') {
                        throw ValidationException::withMessages(['occurred_at' => $snapshot['age']['reasons']]);
                    }
                }
                $data = array_intersect_key($p, array_flip(['planting_group_id', 'schedule_id', 'activity_type', 'manual_ec', 'manual_ppm', 'manual_ph', 'actual_stage', 'work_state', 'skipped_reason', 'notes']));
                if (array_key_exists('observations', $p)) {
                    $observations = array_filter($p['observations'] ?? [], fn ($value) => $value !== null);
                    $data['observations'] = $observations === [] ? null : json_encode($observations, JSON_THROW_ON_ERROR);
                }
                if ($before === null || ($p['reference_policy'] ?? '') === 'refresh' || ($p['schedule_id'] ?? null) !== $before['schedule_id']) {
                    $schedule = DB::table('schedules')->where('id', $p['schedule_id'] ?? null)->first();
                    $snapshot['schedule'] = $schedule === null ? null : $this->references->publicRow($schedule);
                }
                $data['occurred_at'] = CarbonImmutable::parse($p['occurred_at'])->utc()->format('Y-m-d H:i:s.u');
                foreach (['schedule_id', 'manual_ec', 'manual_ppm', 'manual_ph', 'actual_stage', 'skipped_reason'] as $key) {
                    $data[$key] = $data[$key] ?? null;
                }
            }
            $data['version'] = $before === null ? 1 : $before['version'] + 1;
            $data['updated_at'] = now();
            if ($before === null) {
                DB::table('activities')->insert([...$data, 'id' => $op['entity_id'], 'recorded_by' => $current->id, 'recorded_by_role' => $current->role, 'created_at' => now()]);
            } else {
                DB::table('activities')->where('id', $op['entity_id'])->update($data);
            }
            if ($op['action'] !== 'cancel') {
                // Old material sets remain in immutable version snapshots and audit changes.
                DB::table('activity_materials')->where('activity_id', $op['entity_id'])->delete();
                foreach ($p['materials'] as $material) {
                    DB::table('activity_materials')->insert([...$material, 'activity_id' => $op['entity_id']]);
                }
            }
            DB::table('activity_reference_snapshots')->insert(['activity_id' => $op['entity_id'], 'activity_version' => $data['version'], 'body' => json_encode($snapshot, JSON_THROW_ON_ERROR)]);
            $after = $this->publicRow(DB::table('activities')->where('id', $op['entity_id'])->first());

            return ['before' => $before, 'after' => $after, 'greenhouse_id' => $ctx['greenhouse']->id];
        });
    }

    private function validate(array $p, string $gh, ?array $before): void
    {
        foreach (['manual_ec', 'manual_ppm', 'manual_ph'] as $key) {
            $this->nonNegative($p[$key] ?? null, $key);
        }
        foreach (['dissolved_oxygen_mg_l', 'plant_height_cm', 'leaf_width_cm'] as $field) {
            $this->nonNegative($p['observations'][$field] ?? null, 'observations.'.$field);
        }
        if (isset($p['observations']['water_temperature_c'])) {
            try {
                DecimalValue::parse($p['observations']['water_temperature_c']);
            } catch (InvalidArgumentException $e) {
                throw ValidationException::withMessages(['observations.water_temperature_c' => $e->getMessage()]);
            }
        }
        abort_if(isset($p['manual_ph']) && bccomp($p['manual_ph'], '14', 6) > 0, 422, 'pH harus 0 sampai 14.');
        foreach ($p['materials'] as $item) {
            $this->nonNegative($item['quantity'], 'materials.quantity');
            $retained = collect($before['materials'] ?? [])->contains(fn ($old) => $old['material_id'] === $item['material_id'] && $old['unit_id'] === $item['unit_id']);
            $material = DB::table('materials')->where('id', $item['material_id'])->where('greenhouse_id', $gh)->when(! $retained, fn ($q) => $q->where('active', true))->first();
            $unit = DB::table('units')->where('id', $item['unit_id'])->where('greenhouse_id', $gh)->when(! $retained, fn ($q) => $q->where('active', true))->first();
            abort_unless($material && $unit && $material->dimension === $unit->dimension, 422, 'Bahan/satuan harus aktif, berdimensi sama dan berasal dari greenhouse yang sama.');
        }
        if (($p['schedule_id'] ?? null) !== null) {
            $schedule = DB::table('schedules')->where('id', $p['schedule_id'])->where('planting_group_id', $p['planting_group_id'])->where('activity_type', $p['activity_type'])->first();
            abort_unless($schedule && ($schedule->active || ($before['schedule_id'] ?? null) === $schedule->id), 422, 'Jadwal tidak cocok atau sudah tidak aktif.');
        }
        $errors = [];
        if ($p['activity_type'] === 'measurement') {
            foreach (['manual_ec', 'manual_ppm'] as $field) {
                if (($p[$field] ?? null) === null) {
                    $errors[$field] = 'EC dan PPM manual wajib lengkap; kosong bukan nol.';
                }
            }
            if ($p['work_state'] !== 'completed') {
                $errors['work_state'] = 'Pengukuran harus selesai untuk dikirim.';
            }
            if (count($p['materials']) !== 0) {
                $errors['materials'] = 'Pemberian bahan dicatat sebagai kegiatan nutrisi terpisah.';
            }
        }
        if ($p['work_state'] === 'skipped') {
            if (trim($p['skipped_reason'] ?? '') === '') {
                $errors['skipped_reason'] = 'Alasan kegiatan dilewati wajib diisi.';
            }
        } else {
            if ($p['activity_type'] === 'nutrition') {
                foreach (['manual_ppm', 'manual_ph'] as $field) {
                    if (($p[$field] ?? null) === null) {
                        $errors[$field] = 'Pengukuran manual wajib diisi; kosong bukan nol.';
                    }
                }
            }
            if (in_array($p['activity_type'], ['nutrition', 'spray'], true) && count($p['materials']) === 0) {
                $errors['materials'] = 'Isi sedikitnya satu bahan dan satuan aktual.';
            }
            if ($p['activity_type'] === 'stage' && trim($p['actual_stage'] ?? '') === '') {
                $errors['actual_stage'] = 'Pilih kondisi stadia aktual.';
            }
            if ($p['activity_type'] === 'general' && trim($p['notes']) === '') {
                $errors['notes'] = 'Isi keterangan pekerjaan umum.';
            }
        }
        if ($errors !== []) {
            throw ValidationException::withMessages($errors);
        }
    }

    private function nonNegative(mixed $value, string $field): void
    {
        if ($value === null) {
            return;
        }
        try {
            DecimalValue::parse($value);
        } catch (InvalidArgumentException $e) {
            throw ValidationException::withMessages([$field => $e->getMessage()]);
        }
        if (bccomp($value, '0', 6) < 0) {
            throw ValidationException::withMessages([$field => 'Nilai tidak boleh negatif.']);
        }
    }
}
