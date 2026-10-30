<?php

namespace Tests\Unit;

use App\Domain\Finance\SaleMath;
use Illuminate\Validation\ValidationException;
use Tests\TestCase;

class SaleMathTest extends TestCase
{
    public function test_exact_multiline_products_are_rounded_once_instead_of_each_line(): void
    {
        $result = SaleMath::quote([['quantity_kg' => '1', 'price_per_kg' => '0.004'], ['quantity_kg' => '1', 'price_per_kg' => '0.004']]);
        $this->assertSame('0.008000000000', $result['exact_total']);$this->assertSame('0.01', $result['total_amount']);
        $this->assertSame('2.000000', $result['total_weight_kg']);$this->assertSame('0.004000000000', $result['items'][0]['exact_amount']);
        $this->assertSame('IDR', $result['currency']);
        $this->assertSame('0.01', SaleMath::quote([['quantity_kg' => '1.000001', 'price_per_kg' => '0.005']])['total_amount']);
        $this->assertSame('0.00', SaleMath::quote([['quantity_kg' => '1', 'price_per_kg' => '0']])['total_amount']);
    }
    public function test_money_and_weight_overflow_are_rejected_without_float_or_truncation(): void
    {
        foreach ([['quantity_kg' => '99999999999999.999999', 'price_per_kg' => '99999999999999.999999'], ['quantity_kg' => '1', 'price_per_kg' => '100000000000000'], ['quantity_kg' => '0', 'price_per_kg' => '1'], ['quantity_kg' => '-0', 'price_per_kg' => '1'], ['quantity_kg' => '1e2', 'price_per_kg' => '1'], ['quantity_kg' => '1', 'price_per_kg' => '-0'], ['quantity_kg' => '1', 'price_per_kg' => 1.5]] as $invalid) {
            try {SaleMath::quote([$invalid]);$this->fail('Invalid sale arithmetic accepted.');} catch (ValidationException) {$this->addToAssertionCount(1);}
        }
        $this->expectException(ValidationException::class);
        SaleMath::quote([['quantity_kg' => '99999999999999.999999', 'price_per_kg' => '0'], ['quantity_kg' => '0.000001', 'price_per_kg' => '0']]);
    }
}
