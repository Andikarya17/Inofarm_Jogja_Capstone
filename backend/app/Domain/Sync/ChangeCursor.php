<?php

namespace App\Domain\Sync;

use App\Domain\Contracts\DomainFailure;
use App\Models\User;

final class ChangeCursor
{
    public function encode(User $actor, string $epoch, int $after, ?int $upper): string
    {
        $body = rtrim(strtr(base64_encode(json_encode(['actor' => $actor->public_id, 'epoch' => $epoch, 'after' => $after, 'upper' => $upper], JSON_THROW_ON_ERROR)), '+/', '-_'), '=');

        return $body.'.'.hash_hmac('sha256', $body, config('app.key'));
    }

    public function decode(string $token, User $actor, string $epoch): array
    {
        $parts = explode('.', $token);
        abort_unless(count($parts) === 2 && hash_equals(hash_hmac('sha256', $parts[0], config('app.key')), $parts[1]), 422, 'Cursor tidak sah.');
        $body = json_decode(base64_decode(strtr($parts[0], '-_', '+/'), true) ?: '', true);
        abort_unless(is_array($body) && ($body['actor'] ?? null) === $actor->public_id && is_int($body['after'] ?? null), 422, 'Cursor bukan milik sesi ini.');
        if ($body['epoch'] !== $epoch) {
            throw new DomainFailure('epoch_mismatch', 'Epoch server berubah; rekonsiliasi diperlukan.');
        }

        return $body;
    }
}
