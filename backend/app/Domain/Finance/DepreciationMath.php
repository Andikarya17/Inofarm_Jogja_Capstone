<?php

namespace App\Domain\Finance;

use Carbon\CarbonImmutable;
use Illuminate\Validation\ValidationException;

/** anniversary-cumulative-half-up-v1; no daily allocation or automatic season split. */
final class DepreciationMath
{
    public static function base(string $cost, string $residual): string
    {
        $cost = Money::amount($cost, 'cost');
        $residual = Money::amount($residual, 'residual');
        if (bccomp($residual, $cost, 2) > 0) {
            throw ValidationException::withMessages(['residual' => 'Nilai sisa melebihi harga aset.']);
        }

        return bcsub($cost, $residual, 2);
    }

    public static function year(string $base, int $life, int $year, string $start): array
    {
        if ($life < 1 || $life > 1000 || $year < 1 || $year > $life) {
            throw ValidationException::withMessages(['usage_year' => 'Tahun pemakaian harus dalam umur aset (1–1000 tahun).']);
        }
        // Integer cents: floor((2 * cents * k + n) / (2*n)) is exact HALF_UP.
        $cents = bcmul($base, '100', 0);
        $cumulative = fn (int $k) => bcdiv(bcadd(bcmul(bcmul($cents, (string) $k, 0), '2', 0), (string) $life, 0), (string) (2 * $life), 0);
        $date = CarbonImmutable::createFromFormat('!Y-m-d', $start, 'Asia/Jakarta');

        return ['start' => $date->addYearsNoOverflow($year - 1)->toDateString(),
            'end' => $date->addYearsNoOverflow($year)->toDateString(),
            'budget' => bcdiv(bcsub($cumulative($year), $cumulative($year - 1), 0), '100', 2),
            'formula_version' => 'anniversary-cumulative-half-up-v1'];
    }
}
