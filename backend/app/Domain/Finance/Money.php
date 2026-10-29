<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\DecimalValue;
use Illuminate\Validation\ValidationException;
use InvalidArgumentException;

final class Money
{
    public static function amount(mixed $value, string $field = 'amount'): string
    {
        try {
            DecimalValue::parse($value, 2);
            if (str_starts_with($value, '-')) {
                throw new InvalidArgumentException('Nominal tidak boleh negatif.');
            }
        } catch (InvalidArgumentException $error) {
            throw ValidationException::withMessages([$field => $error->getMessage()]);
        }

        return bcadd($value, '0', 2);
    }
}
