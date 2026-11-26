<?php

namespace App\Domain\Finance;

/** A cell written as a number; plain strings in a sheet are always text. */
final readonly class XlsxNumber
{
    private function __construct(public string $value, public string $format) {}

    /** Decimal string or int; null stays a blank cell. $format: general, int (#,##0) or money (#,##0.00 only when cents matter). */
    public static function of(string|int|null $value, string $format = 'general'): ?self
    {
        if ($value === null) {
            return null;
        }
        $text = (string) $value;
        if (str_contains($text, '.')) {
            $text = rtrim(rtrim($text, '0'), '.');
        }
        if ($text === '-0' || $text === '') {
            $text = '0';
        }

        return new self($text, $format);
    }

    public static function money(?string $value): ?self
    {
        $number = self::of($value);

        return $number === null ? null : new self($number->value, str_contains($number->value, '.') ? 'cents' : 'int');
    }
}
