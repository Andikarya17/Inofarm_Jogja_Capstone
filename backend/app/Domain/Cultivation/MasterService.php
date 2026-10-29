<?php

namespace App\Domain\Cultivation;

use App\Domain\Contracts\DomainFailure;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

final class MasterService
{
    public const TABLES = ['varieties', 'units', 'materials', 'grades', 'expense_categories', 'seasons', 'planting_groups', 'greenhouses', 'greenhouse_assignments'];

    public function __construct(private GreenhouseAccess $access, private MutationLedger $ledger) {}

    public function scope(User $actor, string $table)
    {
        $query = DB::table($table);
        if ($table === 'greenhouses') {
            return $this->access->query($actor);
        }
        if ($table === 'planting_groups') {
            return $query->whereIn('season_id', DB::table('seasons')->select('id')->whereIn('greenhouse_id', $this->access->ids($actor)));
        }
        if ($table === 'greenhouse_assignments') {
            $this->access->manager($actor);
        }
        if ($table === 'expense_categories') {
            $this->access->manager($actor);
        }

        return $query->whereIn('greenhouse_id', $this->access->ids($actor));
    }

    public function greenhouse(string $table, object|array $row): string
    {
        $row = (array) $row;
        if ($table === 'greenhouses') {
            return $row['id'];
        }
        if ($table === 'planting_groups') {
            return DB::table('seasons')->where('id', $row['season_id'])->value('greenhouse_id') ?? abort(404);
        }

        return $row['greenhouse_id'];
    }

    public function publicRow(string $table, object $row): array
    {
        $value = (array) $row;
        if (array_key_exists('active', $value)) {
            $value['active'] = (bool) $value['active'];
        }
        if ($table === 'greenhouse_assignments') {
            $value['user_id'] = User::findOrFail($row->user_id)->public_id;
        }
        unset($value['created_by']);
        foreach (['created_at', 'updated_at', 'effective_from', 'effective_until'] as $key) {
            if (isset($value[$key])) {
                $value[$key] = CarbonImmutable::parse($value[$key], 'UTC')->toISOString();
            }
        }

        return $value;
    }

    public function mutate(User $actor, string $table, array $op): array
    {
        return $this->ledger->run($actor, $op, function (User $current) use ($table, $op) {
            $this->access->manager($current);
            if ($table !== 'greenhouses' || $op['action'] !== 'create') {
                $source = $op['action'] === 'create' ? $op['payload'] : ($this->scope($current, $table)->where('id', $op['entity_id'])->first() ?? abort(404));
                $this->access->require($current, $this->greenhouse($table, $source));
            } elseif (DB::table('greenhouses')->where('id', $op['entity_id'])->exists()) {
                $this->access->require($current, $op['entity_id']);
            }
        }, function (User $current) use ($table, $op) {
            $id = $op['entity_id'];
            $before = null;
            $payload = $op['payload'];
            $action = $op['action'];
            if ($action !== 'create') {
                $row = $this->scope($current, $table)->where('id', $id)->first() ?? abort(404);
                $before = $this->publicRow($table, $row);
                $this->ledger->version($row, $op['base_version'], $before);
                $gh = $this->greenhouse($table, $row);
            } else {
                if (DB::table($table)->where('id', $id)->exists()) {
                    throw new DomainFailure('entity_exists', 'Identitas catatan sudah digunakan.');
                }
                $gh = $table === 'greenhouses' ? $id : $this->greenhouse($table, $payload);
            }
            if ($table !== 'greenhouses' || $action !== 'create') {
                $context = $this->access->require($current, $gh);
                abort_if(! $context->active && ! ($table === 'greenhouses' && $action === 'update'), 422, 'Greenhouse tidak aktif.');
            }
            $data = $payload;
            unset($data['reason']);
            if (isset($before['dimension'])) {
                abort_if($data['dimension'] !== $before['dimension'], 422, 'Dimensi satuan/bahan tidak dapat diubah; buat master baru.');
            }
            if (in_array($action, ['close', 'reopen'], true)) {
                $newStatus = $action === 'close' ? 'closed' : 'open';
                abort_if($before['status'] === $newStatus, 422, 'Status musim sudah sesuai permintaan.');
                $data = ['status' => $newStatus];
            } elseif ($table === 'planting_groups') {
                $seasonId = $before['season_id'] ?? $data['season_id'];
                $season = DB::table('seasons')->where('id', $seasonId)->first() ?? abort(404);
                if ($season->status !== 'open') {
                    throw new DomainFailure('season_closed', 'Musim ditutup. Buka kembali dengan alasan sebelum mengubah kelompok.');
                }
                $variety = DB::table('varieties')->where('id', $data['variety_id'])->where('greenhouse_id', $gh)
                    ->when(($before['variety_id'] ?? null) !== $data['variety_id'], fn ($q) => $q->where('active', true))->first();
                abort_unless($variety, 422, 'Varietas harus aktif dan berasal dari greenhouse yang sama.');
                abort_if(($data['transplant_date'] ?? null) !== null && $data['transplant_date'] < $data['sowing_date'], 422, 'Tanggal pindah tidak boleh sebelum semai.');
            } elseif ($table === 'seasons') {
                if (($before['status'] ?? null) === 'closed') {
                    throw new DomainFailure('season_closed', 'Buka kembali musim sebelum mengoreksi data musim.');
                }
                abort_if(($data['end_date'] ?? null) !== null && $data['end_date'] < $data['start_date'], 422, 'Tanggal akhir tidak boleh sebelum mulai.');
            } elseif ($table === 'greenhouse_assignments') {
                if ($action === 'create') {
                    $user = User::where('public_id', $data['user_id'])->whereIn('role', ['worker', 'manager'])->where('active', true)->first() ?? abort(422, 'Akun operasional aktif diperlukan.');
                    $data['user_id'] = $user->id;
                    abort_if(DB::table($table)->where('user_id', $user->id)->where('greenhouse_id', $gh)->exists(), 422, 'Penugasan sudah ada; perbarui versi yang ada.');
                }
                abort_if(isset($data['effective_until']) && strtotime($data['effective_until']) <= strtotime($data['effective_from']), 422, 'Akhir penugasan harus sesudah awal.');
                if ($action === 'update' && $row->user_id === $current->id) {
                    abort_if(! $data['active'] || strtotime($data['effective_from']) > time() || (isset($data['effective_until']) && strtotime($data['effective_until']) <= time()), 422, 'Pengelola tidak dapat mencabut penugasannya sendiri.');
                }
                foreach (['effective_from', 'effective_until'] as $key) {
                    if (isset($data[$key])) {
                        $data[$key] = CarbonImmutable::parse($data[$key])->utc()->format('Y-m-d H:i:s.u');
                    }
                }
            }
            $data['version'] = $before === null ? 1 : $before['version'] + 1;
            $data['updated_at'] = now();
            if ($action === 'create') {
                $data['id'] = $id;
                $data['created_at'] = now();
                if (in_array($table, ['greenhouses', 'greenhouse_assignments'], true)) {
                    $data['created_by'] = $current->id;
                }
                DB::table($table)->insert($data);
                if ($table === 'greenhouses') {
                    DB::table('greenhouse_assignments')->insert(['id' => (string) Str::uuid(), 'greenhouse_id' => $id,
                        'user_id' => $current->id, 'created_by' => $current->id, 'effective_from' => now(), 'created_at' => now(), 'updated_at' => now()]);
                }
            } else {
                DB::table($table)->where('id', $id)->update($data);
            }
            $after = $this->publicRow($table, DB::table($table)->where('id', $id)->first());

            return ['before' => $before, 'after' => $after, 'greenhouse_id' => $gh,
                'audience' => in_array($table, ['greenhouse_assignments', 'expense_categories'], true) ? 'manager' : 'all'];
        });
    }
}
