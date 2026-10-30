<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\DecimalValue;
use Illuminate\Validation\ValidationException;
use InvalidArgumentException;

/** Exact line products; HALF_UP exactly once on the transaction total, never on individual lines. */
final class SaleMath
{
    public static function quote(array $items): array
    {
        if (count($items) < 1 || count($items) > 30) {
            throw ValidationException::withMessages(['items' => 'Sertakan1–30 rincian sumber panen per transaksi.']);
        }
        $sum = '0.000000000000';$weight = '0.000000';$lines = [];
        foreach ($items as $index => $item) {
            foreach (['quantity_kg', 'price_per_kg'] as $field) {
                try {
                    DecimalValue::parse($item[$field] ?? null);
                    if (str_starts_with($item[$field], '-') || ($field === 'quantity_kg' && bccomp($item[$field], '0', 6) <= 0)) {
                        throw new InvalidArgumentException($field === 'quantity_kg' ? 'Bobot penjualan harus lebih dari nol.' : 'Harga tidak boleh negatif.');
                    }
                } catch (InvalidArgumentException $error) {
                    throw ValidationException::withMessages(["items.$index.$field" => $error->getMessage()]);
                }
            }
            $quantity = bcadd($item['quantity_kg'], '0', 6);
            $price = bcadd($item['price_per_kg'], '0', 6);
            $exact = bcmul($quantity, $price, 12);
            $sum = bcadd($sum, $exact, 12);$weight = bcadd($weight, $quantity, 6);
            $lines[] = [...$item, 'quantity_kg' => $quantity, 'price_per_kg' => $price, 'exact_amount' => $exact];
        }
        // All inputs are nonnegative. Adding half a cent then truncating implements HALF_UP.
        $total = bcadd(bcadd($sum, '0.005', 12), '0', 2);
        try {DecimalValue::parse($total, 2);DecimalValue::parse($weight, 6);} catch (InvalidArgumentException $error) {
            throw ValidationException::withMessages(['items' => 'Total transaksi atau bobot melebihi rentang angka yang didukung.']);
        }

        return ['items' => $lines, 'exact_total' => $sum, 'total_amount' => $total, 'total_weight_kg' => $weight, 'currency' => 'IDR', 'rounding' => 'HALF_UP_TRANSACTION'];
    }
}
