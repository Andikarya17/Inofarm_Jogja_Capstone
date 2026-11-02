<?php

namespace App\Domain\Contracts;

use InvalidArgumentException;

/** Wire values must be plain decimal strings; no coercion or rounding on input. */
final class DecimalValue
{
    public static function parse(mixed $value, int $scale = 6, int $precision = 20): string
    {
        if (! is_string($value) || ! preg_match('/^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?$/D', $value)) {
            throw new InvalidArgumentException('Gunakan string desimal tanpa pemisah ribuan atau eksponen.');
        }
        $parts = explode('.', ltrim($value, '-'));
        if (strlen($parts[0]) > $precision - $scale || strlen($parts[1] ?? '') > $scale) {
            throw new InvalidArgumentException('Angka melebihi presisi atau skala yang diizinkan.');
        }

        return $value;
    }
}
