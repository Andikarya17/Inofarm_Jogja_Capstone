<?php

namespace App\Domain\Sync;

use App\Domain\Contracts\CanonicalJson;
use App\Domain\Contracts\DomainFailure;
use App\Domain\Identity\EntityIdentity;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Laravel\Sanctum\PersonalAccessToken;

final class MutationLedger
{
    /** Global metadata lock gives feed sequences commit order and serializes scope revocation.
     * This favors integrity for a small operation. Revisit throughput only with a commit-order proof.
     */
    public function run(User $sessionActor, array $operation, callable $authorize, callable $execute): array
    {
        return DB::transaction(function () use ($sessionActor, $operation, $authorize, $execute) {
            $state = DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
            $token = $sessionActor->currentAccessToken();
            if ($token instanceof PersonalAccessToken) {
                abort_unless(DB::table('personal_access_tokens')->where('id', $token->id)
                    ->where(fn ($q) => $q->whereNull('expires_at')->orWhere('expires_at', '>', now()))->exists(), 401, 'Sesi telah dicabut atau berakhir.');
            }
            $actor = User::findOrFail($sessionActor->id);
            abort_unless($actor->active && in_array($actor->role, ['worker', 'manager'], true), 403, 'Hak akses akun telah berubah.');
            $authorize($actor);
            if ($state->epoch !== $operation['sync_epoch']) {
                throw new DomainFailure('epoch_mismatch', 'Epoch server berubah. Tinjau antrean sebelum mengirim kembali.');
            }
            $hash = CanonicalJson::hash($operation);
            $receipt = DB::table('sync_operations')->where('actor_id', $actor->id)->where('operation_id', $operation['operation_id'])->first();
            if ($receipt) {
                if (! hash_equals($receipt->request_hash, $hash)) {
                    throw new DomainFailure('operation_conflict', 'ID operasi telah dipakai untuk isi berbeda.');
                }

                return ['status' => $receipt->http_status, 'body' => json_decode($receipt->receipt, true, flags: JSON_THROW_ON_ERROR)];
            }
            if ($operation['action'] === 'create') {
                app(EntityIdentity::class)->claim($operation['entity_type'], $operation['entity_id']);
            }
            $change = $execute($actor);
            $now = now();
            $after = $change['after'];
            $body = ['operation_id' => $operation['operation_id'], 'entity_id' => $operation['entity_id'],
                'version' => $after['version'], 'committed_at' => $now->toISOString(), 'data' => $after];
            DB::table('audit_logs')->insert(['actor_id' => $actor->id, 'operation_id' => $operation['operation_id'],
                'entity_type' => $operation['entity_type'], 'entity_id' => $operation['entity_id'], 'action' => $operation['action'],
                'version' => $after['version'], 'reason' => $operation['payload']['reason'] ?? null,
                'changes' => json_encode(['before' => $change['before'], 'after' => $after], JSON_THROW_ON_ERROR), 'server_at' => $now]);
            DB::table('change_feed')->insert(['greenhouse_id' => $change['greenhouse_id'], 'audience' => $change['audience'] ?? 'all',
                'entity_type' => $operation['entity_type'], 'entity_id' => $operation['entity_id'], 'version' => $after['version'],
                'tombstone' => ($after['active'] ?? true) === false || ($after['record_state'] ?? '') === 'cancelled',
                'body' => json_encode($after, JSON_THROW_ON_ERROR), 'committed_at' => $now]);
            $status = $operation['action'] === 'create' ? 201 : 200;
            DB::table('sync_operations')->insert(['actor_id' => $actor->id, 'operation_id' => $operation['operation_id'],
                'entity_id' => $operation['entity_id'], 'entity_type' => $operation['entity_type'], 'request_hash' => $hash,
                'receipt' => json_encode($body, JSON_THROW_ON_ERROR), 'http_status' => $status, 'committed_at' => $now]);

            return ['status' => $status, 'body' => $body];
        }, 3);
    }

    public function version(object $row, ?int $baseVersion, array $public): void
    {
        if ($row->version !== $baseVersion) {
            throw new DomainFailure('version_conflict', 'Data telah berubah. Bandingkan versi lokal dan server.', currentVersion: $row->version, currentData: $public);
        }
    }
}
