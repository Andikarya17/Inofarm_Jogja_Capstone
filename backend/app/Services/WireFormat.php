<?php

namespace App\Services;

final class WireFormat
{
    public static function configuration(?array $body): ?array
    {
        if ($body === null) {
            return null;
        }
        // JSON maps remain objects even when PHP decodes an empty {} as [].
        foreach (['ppmRanges', 'sensorBounds'] as $key) {
            if (array_key_exists($key, $body)) {
                $body[$key] = (object) $body[$key];
            }
        }

        return $body;
    }
}
