<?php

namespace Tests\Unit;

use App\Domain\Contracts\CanonicalJson;
use App\Domain\Contracts\DecimalValue;
use InvalidArgumentException;
use PHPUnit\Framework\TestCase;

class FinanceWireContractTest extends TestCase
{
    public function test_shared_fixture_has_the_independent_canonical_hash(): void
    {
        $fixture = json_decode(file_get_contents(__DIR__.'/../../../contracts/v2/fixtures/activity-operation.json'), true, flags: JSON_THROW_ON_ERROR);
        $this->assertSame($fixture['canonical_sha256'], CanonicalJson::hash($fixture['operation']));
        $reordered = array_reverse($fixture['operation'], true);
        $this->assertSame(CanonicalJson::hash($fixture['operation']), CanonicalJson::hash($reordered));
    }

    public function test_decimal_zero_null_locale_scale_and_overflow_are_distinct(): void
    {
        $this->assertSame('0.00', DecimalValue::parse('0.00', 2));
        $this->assertSame('999999999999999999.99', DecimalValue::parse('999999999999999999.99', 2));
        foreach ([null, 0, 1.5, '', 'NaN', 'Infinity', '1e2', '1,5', '01', '1.001', '1000000000000000000.00'] as $invalid) {
            try {
                DecimalValue::parse($invalid, 2);
                $this->fail('Invalid wire decimal accepted.');
            } catch (InvalidArgumentException) {
                $this->addToAssertionCount(1);
            }
        }
    }
}
