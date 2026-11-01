<?php

namespace Tests\Unit;

use App\Domain\Finance\RationalDecimal;
use App\Domain\Finance\SeasonEvaluation;
use PHPUnit\Framework\TestCase;

class SeasonEvaluationTest extends TestCase
{
    public function test_proposal_synthetic_values_and_exact_fraction_rounding(): void
    {
        $metrics = SeasonEvaluation::calculate('5000000', '3000000', '12000000', '400', ['costs' => 'complete', 'sales' => 'complete']);
        foreach (['TC' => '8000000.00', 'TR' => '12000000.00', 'L' => '4000000.00', 'RC' => '1.50000000', 'BC' => '0.50000000', 'P' => '30000.000000', 'BEP_Q' => '266.666667', 'BEP_PRICE' => '20000.000000'] as $key => $value) {
            $this->assertSame($value, $metrics[$key]['value']);
            $this->assertSame('available', $metrics[$key]['status']);
        }
        $this->assertSame('-0.01', RationalDecimal::round('-1', '200', 2));
        $this->assertSame('0.01', RationalDecimal::round('1', '200', 2));
        $this->assertSame('0.00', RationalDecimal::round('-1', '201', 2));
    }

    public function test_unknown_zero_and_missing_denominators_are_independent(): void
    {
        $unknown = SeasonEvaluation::calculate(null, '10.00', null, null, ['costs' => 'partial', 'sales' => 'unknown']);
        $this->assertSame('10.00', $unknown['VC']['value']);
        $this->assertSame('provisional', $unknown['VC']['status']);
        $this->assertNull($unknown['TC']['value']);
        $this->assertNull($unknown['TR']['value']);
        $this->assertNotEmpty($unknown['TR']['reasons']);
        $zero = SeasonEvaluation::calculate('0', '0', '0', '0', ['costs' => 'complete', 'sales' => 'complete']);
        $this->assertSame('0.00', $zero['L']['value']);
        $this->assertNull($zero['RC']['value']);
        $this->assertNull($zero['BEP_PRICE']['value']);
        $loss = SeasonEvaluation::calculate('1', '0', '0', '1', ['costs' => 'complete', 'sales' => 'complete']);
        $this->assertSame('-1.00', $loss['L']['value']);
        $this->assertSame('-1.00000000', $loss['BC']['value']);
        $this->assertNull($loss['BEP_Q']['value']);
    }

    public function test_bep_uses_unrounded_weighted_price_and_aggregates_can_exceed_one_payment(): void
    {
        $m = SeasonEvaluation::calculate('0.01', '0', '0.01', '3', ['costs' => 'complete', 'sales' => 'complete']);
        $this->assertSame('0.003333', $m['P']['value']);
        $this->assertSame('3.000000', $m['BEP_Q']['value']);
        $large = SeasonEvaluation::calculate('999999999999999999.99', '999999999999999999.99', '1', '0.000001', ['costs' => 'complete', 'sales' => 'complete']);
        $this->assertSame('1999999999999999999.98', $large['TC']['value']);
        $this->assertSame('1000000.000000', $large['P']['value']);
        $tiny = SeasonEvaluation::calculate('1', '0', '0.01', '999999999999999999', ['costs' => 'complete', 'sales' => 'complete']);
        $this->assertSame('0.000000', $tiny['P']['value']);
        $this->assertNotEmpty($tiny['P']['reasons']);
        $this->assertNotNull($tiny['P']['exact_fraction']);
    }
}
