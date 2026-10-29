<?php

namespace App\Domain\Contracts;

use InvalidArgumentException;

/** Contract v2: sorted ASCII object keys, UTF-8 strings, ordered arrays, integer numbers. */
final class CanonicalJson
{
    public static function encode(mixed $value): string
    {
        if (is_float($value)) {
            throw new InvalidArgumentException('Decimal values must be strings.');
        }
        if (is_array($value)) {
            if (array_is_list($value)) {
                return '['.implode(',', array_map(self::encode(...), $value)).']';
            }
            ksort($value, SORT_STRING);
            $pairs = [];
            foreach ($value as $key => $item) {
                $pairs[] = self::encode((string) $key).':'.self::encode($item);
            }

            return '{'.implode(',', $pairs).'}';
        }
        if (! is_null($value) && ! is_scalar($value)) {
            throw new InvalidArgumentException('Unsupported canonical value.');
        }

        return json_encode($value, JSON_THROW_ON_ERROR | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_LINE_TERMINATORS);
    }

    public static function hash(array $operation): string
    {
        return hash('sha256', self::encode($operation));
    }
}
