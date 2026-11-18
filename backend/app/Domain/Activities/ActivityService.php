<?php

namespace App\Domain\Activities;

use App\Domain\Contracts\DecimalValue;
use App\Domain\Contracts\DomainFailure;
use App\Domain\Cultivation\GroupContext;
use App\Domain\Cultivation\MasterService;
use App\Domain\Cultivation\ReferenceService;
use App\Domain\Cultivation\SprayMaterialCatalog;
use App\Domain\Cultivation\TargetComparator;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;
use InvalidArgumentException;

final class ActivityService
{
    /** Columns a mutation copies from its payload, besides occurred_at and observations. */
    private const COLUMNS = ['planting_group_id', 'season_id', 'schedule_id', 'activity_type', 'manual_ec', 'manual_ppm', 'manual_ph', 'actual_stage',
        'work_state', 'skipped_reason', 'notes', 'entry_batch_id', 'age_basis', 'age_day', 'spray_date', 'spray_period'];

    public function __construct(private GroupContext $context, private MasterService $masters, private ReferenceService $references,
        private MutationLedger $ledger, private SprayMaterialCatalog $sprays) {}

    public function scope(User $actor)
    {
        return DB::table('activities')->whereIn('season_id', $this->masters->scope($actor, 'seasons')->select('id'));
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
            $this->context->activity($current, $source ?? $op['payload']);
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
            if ($op['action'] !== 'cancel') {
                $p = $this->parent($p, $before);
            }
            $ctx = $this->context->activity($current, $before ?? $p, true);
            if ($op['action'] === 'create' && isset($p['running_period_version'])) {
                app(\App\Domain\Cultivation\RunningPeriod::class)->assertHomeParent($ctx, $p['running_period_version']);
            }
            if ($op['action'] === 'cancel') {
                $data = ['record_state' => 'cancelled'];
                $snapshot = $before['reference_snapshot'];
            } else {
                $p['materials'] = $this->resolveMaterials($current, $p, $ctx['greenhouse']->id);
                $this->validate($p, $ctx['greenhouse']->id, $before);
                if ($before !== null && ($p['reference_policy'] ?? 'preserve') === 'preserve') {
                    abort_if(CarbonImmutable::parse($p['occurred_at'])->ne(CarbonImmutable::parse($before['occurred_at'])) || $p['activity_type'] !== $before['activity_type'], 422, 'Perubahan waktu/jenis memerlukan refresh acuan eksplisit dan alasan.');
                    $snapshot = $before['reference_snapshot'];
                    foreach (['manual_ec', 'manual_ppm', 'manual_ph'] as $parameter) {
                        $snapshot['checks'][$parameter] = TargetComparator::compare($p[$parameter] ?? null, $snapshot['checks'][$parameter]['target'] ?? null);
                    }
                } else {
                    $snapshot = $ctx['group'] === null
                        ? $this->references->seasonSnapshot($ctx['season'], $p['activity_type'], $p['occurred_at'], $p)
                        : $this->references->snapshot($ctx['group'], $p['activity_type'], $p['occurred_at'], $p);
                    // New entries carry the typed HSS/HST, so a computed age is no longer required (K6).
                    if (($p['entry_batch_id'] ?? null) === null && $snapshot['age']['status'] === 'unavailable') {
                        throw ValidationException::withMessages(['occurred_at' => $snapshot['age']['reasons']]);
                    }
                }
                $data = array_intersect_key($p, array_flip(self::COLUMNS));
                if (array_key_exists('observations', $p)) {
                    $observations = array_filter($p['observations'] ?? [], fn ($value) => $value !== null);
                    $data['observations'] = $observations === [] ? null : json_encode($observations, JSON_THROW_ON_ERROR);
                }
                if ($before === null || ($p['reference_policy'] ?? '') === 'refresh' || ($p['schedule_id'] ?? null) !== $before['schedule_id']) {
                    $schedule = DB::table('schedules')->where('id', $p['schedule_id'] ?? null)->first();
                    $snapshot['schedule'] = $schedule === null ? null : $this->references->publicRow($schedule);
                }
                $data['occurred_at'] = CarbonImmutable::parse($p['occurred_at'])->utc()->format('Y-m-d H:i:s.u');
                foreach (['planting_group_id', 'schedule_id', 'manual_ec', 'manual_ppm', 'manual_ph', 'actual_stage', 'skipped_reason',
                    'entry_batch_id', 'age_basis', 'age_day', 'spray_date', 'spray_period'] as $key) {
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
        $spray = $p['activity_type'] === 'spray';
        foreach ($p['materials'] as $item) {
            $unitId = $item['unit_id'] ?? null;
            $quantity = $item['quantity'] ?? null;
            // A spray dose is optional, but half a dose (unit without amount or the reverse) is never accepted.
            if (! $spray || $unitId !== null || $quantity !== null) {
                abort_if($unitId === null || $quantity === null, 422, $spray ? 'Isi jumlah dan satuan dosis bersama, atau kosongkan keduanya.' : 'Isi jumlah dan satuan bahan.');
                $this->nonNegative($quantity, 'materials.quantity');
            }
            $retained = collect($before['materials'] ?? [])->contains(fn ($old) => $old['material_id'] === $item['material_id'] && $old['unit_id'] === $unitId);
            $material = DB::table('materials')->where('id', $item['material_id'])->where('greenhouse_id', $gh)->when(! $retained, fn ($q) => $q->where('active', true))->first();
            $unit = $unitId === null ? null : DB::table('units')->where('id', $unitId)->where('greenhouse_id', $gh)->when(! $retained, fn ($q) => $q->where('active', true))->first();
            // Spray doses use any unit of the greenhouse (e.g. ml/L); nutrition keeps the same-dimension rule.
            abort_unless($material && ($unitId === null || ($unit && ($spray || $material->dimension === $unit->dimension))), 422,
                'Bahan/satuan harus aktif, berdimensi sama dan berasal dari greenhouse yang sama.');
        }
        if (($p['schedule_id'] ?? null) !== null) {
            // A greenhouse-level record (18 Nov 2026) may realise a schedule of any planting group in its own season.
            $group = $p['planting_group_id'] ?? null;
            $schedule = DB::table('schedules')->where('id', $p['schedule_id'])->where('activity_type', $p['activity_type'])
                ->when($group !== null, fn ($q) => $q->where('planting_group_id', $group))
                ->when($group === null, fn ($q) => $q->whereIn('planting_group_id',
                    DB::table('planting_groups')->select('id')->where('season_id', $p['season_id'] ?? null)))
                ->first();
            abort_unless($schedule && ($schedule->active || ($before['schedule_id'] ?? null) === $schedule->id), 422, 'Jadwal tidak cocok atau sudah tidak aktif.');
        }
        $errors = $this->entryErrors($p);
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
            // Field practice: skipped work needs no reason, except nutrition, which must still be given.
            if ($p['activity_type'] === 'nutrition' && trim($p['skipped_reason'] ?? '') === '') {
                $errors['skipped_reason'] = 'Pemberian nutrisi yang dilewati wajib diberi alasan.';
            }
        } else {
            // In the greenhouse entry form the readings live in the measurement section of the same batch.
            if ($p['activity_type'] === 'nutrition' && ($p['entry_batch_id'] ?? null) === null) {
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

    /** Group and season come from the payload (or the stored row); a legacy payload names only its group. */
    private function parent(array $p, ?array $before): array
    {
        $group = $p['planting_group_id'] ?? null;
        $season = $p['season_id'] ?? null;
        if ($group !== null) {
            $groupSeason = DB::table('planting_groups')->where('id', $group)->value('season_id') ?? abort(404);
            abort_if($season !== null && $season !== $groupSeason, 422, 'Kelompok tidak berada di musim ini.');
            $season = $groupSeason;
        }
        abort_if($season === null, 422, 'Pilih greenhouse dan musim.');
        if ($before !== null) {
            abort_if($group !== $before['planting_group_id'] || $season !== $before['season_id'], 422, 'Kelompok aktivitas tidak dapat dipindah.');
        }

        return [...$p, 'planting_group_id' => $group, 'season_id' => $season];
    }

    /** A typed spray name becomes a shared master material; ids are what the activity stores. */
    private function resolveMaterials(User $actor, array $p, string $gh): array
    {
        return array_map(function (array $item) use ($actor, $p, $gh) {
            if (($item['material_id'] ?? null) === null) {
                abort_unless($p['activity_type'] === 'spray' && trim($item['material_name'] ?? '') !== '', 422, 'Pilih bahan dari daftar.');
                $item['material_id'] = $this->sprays->resolve($actor, $gh, $item['material_name']);
            }

            return ['material_id' => $item['material_id'], 'unit_id' => $item['unit_id'] ?? null, 'quantity' => $item['quantity'] ?? null];
        }, $p['materials']);
    }

    /** Rules of the greenhouse entry form (entry_batch_id present); legacy single-type payloads skip the new requirements. */
    private function entryErrors(array $p): array
    {
        $errors = [];
        $entry = ($p['entry_batch_id'] ?? null) !== null;
        $basis = $p['age_basis'] ?? null;
        $day = $p['age_day'] ?? null;
        if (($basis === null) !== ($day === null) || ($entry && $basis === null)) {
            $errors['age_basis'] = 'Isi umur tanaman: HSS atau HST dan hari ke berapa.';
        }
        $spray = $p['activity_type'] === 'spray';
        if (! $spray && (($p['spray_date'] ?? null) !== null || ($p['spray_period'] ?? null) !== null)) {
            $errors['spray_date'] = 'Tanggal dan waktu semprot hanya untuk penyemprotan.';
        }
        if ($entry && $spray) {
            if (($p['planting_group_id'] ?? null) === null) {
                $errors['planting_group_id'] = 'Pilih varietas yang disemprot.';
            }
            if (($p['spray_date'] ?? null) === null || ($p['spray_period'] ?? null) === null) {
                $errors['spray_date'] = 'Isi tanggal semprot dan pilih pagi atau sore.';
            }
        }
        if ($entry && ! $spray && ($p['planting_group_id'] ?? null) !== null) {
            $errors['planting_group_id'] = 'Catatan ini berlaku untuk seluruh greenhouse; varietas hanya dipilih untuk penyemprotan.';
        }

        return $errors;
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
