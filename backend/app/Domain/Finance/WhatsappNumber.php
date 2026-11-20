<?php

namespace App\Domain\Finance;

/** Indonesian WhatsApp numbers in one canonical form (62...), so 0812-, +62 812 and 62812 are the same buyer. */
final class WhatsappNumber
{
    public static function normalize(?string $raw): ?string
    {
        if ($raw === null || trim($raw) === '') {
            return null;
        }
        if (preg_match('/[^0-9+\s\-().]/u', $raw)) {
            return null;
        }
        $digits = preg_replace('/\D/', '', $raw);
        if (str_starts_with($digits, '0')) {
            $digits = '62'.substr($digits, 1);
        } elseif (str_starts_with($digits, '8')) {
            $digits = '62'.$digits;
        }
        $length = strlen($digits);

        return str_starts_with($digits, '62') && $length >= 10 && $length <= 15 ? $digits : null;
    }

    /** "0812-3456-7890" for display. */
    public static function display(string $canonical): string
    {
        $local = '0'.substr($canonical, 2);

        return implode('-', array_filter([substr($local, 0, 4), substr($local, 4, 4), substr($local, 8)], fn ($part) => $part !== ''));
    }
}
