<?php

namespace App\Domain\Cultivation;

use Carbon\CarbonImmutable;
use DateTimeImmutable;

final class PlantAge
{
    public static function calculate(string $sowingDate, ?string $transplantDate, string $occurredAt): array
    {
        $day = CarbonImmutable::parse($occurredAt)->setTimezone('Asia/Jakarta')->toDateString();
        $difference = fn (string $start): int => (int) (new DateTimeImmutable($start))->diff(new DateTimeImmutable($day))->format('%r%a');
        if ($day < $sowingDate) {
            return ['hss' => null, 'hst' => null, 'status' => 'unavailable', 'reasons' => ['Kegiatan sebelum semai memerlukan kebijakan pengelola.']];
        }

        return ['hss' => $difference($sowingDate) + 1,
            'hst' => $transplantDate !== null && $day >= $transplantDate ? $difference($transplantDate) + 1 : null,
            'status' => 'available', 'reasons' => $transplantDate === null || $day < $transplantDate ? ['HST belum berlaku sebelum pindah tanam.'] : []];
    }
}
