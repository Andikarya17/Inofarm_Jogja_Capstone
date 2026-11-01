<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\DecimalValue;
use InvalidArgumentException;

/** Exact integer fractions, signed HALF_UP; display rounding never feeds another formula. */
final class RationalDecimal
{
    public static function scaled(string $value, int $scale): string
    {
        DecimalValue::parse($value, $scale, 60);

        return bcmul($value, bcpow('10', $scale, 0), 0);
    }

    public static function round(string $numerator, string $denominator, int $scale): string
    {
        if (bccomp($denominator, '0', 0) === 0 || $scale < 0 || $scale > 30) {
            throw new InvalidArgumentException('Pembagi atau skala tidak sah.');
        }
        $negative = (bccomp($numerator, '0', 0) < 0) !== (bccomp($denominator, '0', 0) < 0);
        $n = bcmul(ltrim($numerator, '-'), bcpow('10', $scale, 0), 0);
        $d = ltrim($denominator, '-');
        $whole = bcdiv($n, $d, 0);
        if (bccomp(bcmul(bcmod($n, $d, 0), '2', 0), $d, 0) >= 0) {
            $whole = bcadd($whole, '1', 0);
        }
        $digits = str_pad($whole, $scale + 1, '0', STR_PAD_LEFT);
        $result = $scale === 0 ? $digits : substr($digits, 0, -$scale).'.'.substr($digits, -$scale);

        return ($negative && bccomp($whole, '0', 0) !== 0 ? '-' : '').$result;
    }
}
