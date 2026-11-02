<?php

namespace App\Domain\Recovery;

use App\Domain\Identity\EntityIdentity;
use App\Models\User;
use Illuminate\Support\Facades\DB;

final class BackupService
{
    public function __construct(private RecoveryPackage $packages) {}

    public function authorize(User $actor): void
    {
        abort_unless($actor->active && $actor->role === 'manager' && in_array($actor->public_id, config('recovery.operators', []), true), 403, 'Akun ini belum diberi kewenangan pencadangan sistem oleh operator server.');
    }

    public function publicRow(object $row): array
    {
        return ['id' => $row->id, 'state' => $row->state, 'reason' => $row->reason, 'manifest_hash' => $row->manifest_hash,
            'summary' => $row->summary === null ? null : json_decode($row->summary, true, flags: JSON_THROW_ON_ERROR), 'created_at' => $row->created_at, 'updated_at' => $row->updated_at];
    }

    public function request(User $actor, array $input): object
    {
        $this->authorize($actor);
        $row = DB::table('backup_runs')->where('id', $input['request_id'])->first();
        if ($row) {
            abort_unless($row->actor_id === $actor->id && $row->reason === $input['reason'], 409, 'Identitas permintaan cadangan sudah digunakan.');

            return $row;
        }
        app(EntityIdentity::class)->claim('backup_run', $input['request_id']);
        DB::table('backup_runs')->insert(['id' => $input['request_id'], 'actor_id' => $actor->id, 'reason' => $input['reason'], 'state' => 'requested', 'created_at' => now()->format('Y-m-d H:i:s.u'), 'updated_at' => now()->format('Y-m-d H:i:s.u')]);

        return DB::table('backup_runs')->where('id', $input['request_id'])->first();
    }

    public function capture(User $actor, string $id): void
    {
        $this->authorize($actor);
        $row = DB::table('backup_runs')->where('id', $id)->first() ?? abort(404);
        if ($row->state !== 'requested') {
            return;
        }
        $result = $this->packages->create($id);
        $m = $result['manifest'];
        DB::table('backup_runs')->where('id', $id)->update(['state' => 'completed', 'manifest_hash' => $result['hash'],
            'summary' => json_encode(['tables' => count($m['tables']), 'rows' => array_sum(array_column($m['tables'], 'rows')), 'files' => count($m['files']), 'source_epoch' => $m['source_epoch'], 'restore_verified' => false], JSON_THROW_ON_ERROR), 'updated_at' => now()]);
    }
}
