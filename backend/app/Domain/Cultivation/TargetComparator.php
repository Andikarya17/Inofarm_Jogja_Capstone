<?php

namespace App\Domain\Cultivation;

final class TargetComparator
{
    public static function compare(?string $actual, ?array $target): array
    {
        $base = ['actual' => $actual, 'target' => $target, 'delta' => null, 'status' => 'unavailable', 'reasons' => []];
        if ($actual === null || $target === null) {
            return [...$base, 'reasons' => [...($actual === null ? ['Nilai aktual belum diisi.'] : []),
                ...($target === null ? ['Target pada waktu kegiatan belum tersedia.'] : [])]];
        }
        $value = $target['target_value'];
        $min = $target['minimum'];
        $max = $target['maximum'];
        if ($value !== null) {
            $base['delta'] = bcsub($actual, $value, 6);
            if ($target['tolerance'] === null) {
                return [...$base, 'status' => 'delta_only', 'reasons' => ['Target tunggal tanpa toleransi; hanya selisih, belum dapat menyimpulkan kesesuaian.']];
            }
            $min = bcsub($value, $target['tolerance'], 6);
            $max = bcadd($value, $target['tolerance'], 6);
        }
        if ($min === null || $max === null) {
            return [...$base, 'reasons' => ['Aturan pembanding belum lengkap.']];
        }
        $within = bccomp($actual, $min, 6) >= 0 && bccomp($actual, $max, 6) <= 0;

        return [...$base, 'status' => $within ? 'within_range' : 'outside_range',
            'reasons' => $within ? [] : ['Nilai aktual berada di luar rentang acuan.']];
    }
}
