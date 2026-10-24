<?php

namespace App\Services;

use App\Models\Tank;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;

final class ConfigurationStore
{
    public function at(string $model, int $tankId, CarbonImmutable $time)
    {
        return $model::where('tank_id', $tankId)->where('effective_from', '<=', $time->format('Y-m-d H:i:s.u'))->orderByDesc('effective_from')->orderByDesc('id')->first();
    }

    public function latest(string $model, int $tankId)
    {
        return $this->at($model, $tankId, CarbonImmutable::now());
    }

    public function save(string $model, int $tankId, int $userId, ?int $baseVersion, array $body)
    {
        return DB::transaction(function () use ($model, $tankId, $userId, $baseVersion, $body) {
            Tank::whereKey($tankId)->lockForUpdate()->firstOrFail();
            $latest = $this->latest($model, $tankId);
            abort_unless($latest?->id === $baseVersion, 409, 'Versi berubah. Muat ulang konfigurasi.');

            return $model::create(['tank_id' => $tankId, 'created_by' => $userId, 'body' => $body, 'effective_from' => now()]);
        });
    }
}
