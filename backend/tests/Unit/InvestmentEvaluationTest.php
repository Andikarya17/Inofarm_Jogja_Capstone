<?php

namespace Tests\Unit;

use App\Domain\Finance\InvestmentEvaluation;
use PHPUnit\Framework\TestCase;

class InvestmentEvaluationTest extends TestCase
{
    public function test_proposal_numbers_and_both_explicit_payback_conventions(): void
    {
        $result = InvestmentEvaluation::calculate('100000000.00', ['60000000.00', '60000000.00'], '0.1', 'interpolation', true);
        foreach (['NPV' => '4132231.40', 'IRR' => '13.06623863', 'PP' => '1.66666667', 'DPP' => '1.91666667'] as $key => $value) {
            $this->assertSame($value, $result[$key]['value']);
        }
        $fraction = $result['NPV']['exact_fraction'];
        $this->assertSame(0, bccomp(bcmul($fraction['numerator'], '121', 0), bcmul($fraction['denominator'], '500000000', 0), 0));
        $this->assertNull($result['IRR']['exact_fraction']);
        $this->assertTrue($result['IRR']['calculation_detail']['converged']);
        $end = InvestmentEvaluation::calculate('100000000.00', ['60000000.00', '60000000.00'], '0.1', 'end_of_year', true);
        $this->assertSame('2.00000000', $end['PP']['value']);
        $this->assertSame('2.00000000', $end['DPP']['value']);
    }

    public function test_missing_rate_and_convention_do_not_disable_independent_metrics(): void
    {
        $result = InvestmentEvaluation::calculate('100.00', ['100.00'], null, 'interpolation', true);
        $this->assertSame('unavailable', $result['NPV']['status']);
        $this->assertSame('unavailable', $result['DPP']['status']);
        $this->assertSame('1.00000000', $result['PP']['value']);
        $this->assertSame('0.00000000', $result['IRR']['value']);
        $result = InvestmentEvaluation::calculate('100.00', ['110.00'], '0', null, true);
        $this->assertSame('10.00', $result['NPV']['value']);
        $this->assertNull($result['PP']['value']);
        $this->assertNull($result['DPP']['value']);
    }

    public function test_unknown_and_signed_later_cash_never_become_zero_or_guaranteed_recovery(): void
    {
        $result = InvestmentEvaluation::calculate('100.00', ['110.00', null], '0', 'interpolation', true);
        $this->assertSame('0.90909091', $result['PP']['value']);
        $this->assertSame('provisional', $result['PP']['status']);
        $this->assertNull($result['NPV']['value']);
        $result = InvestmentEvaluation::calculate('100.00', ['110.00', '-20.00'], '0', 'interpolation', true);
        $this->assertSame('-10.00', $result['NPV']['value']);
        $this->assertSame('unavailable', $result['IRR']['status']);
        $this->assertTrue($result['PP']['calculation_detail']['negative_after_recovery']);
        $result = InvestmentEvaluation::calculate('100.00', ['0.00', '0.00'], '0', 'end_of_year', true);
        $this->assertSame('not_recovered', $result['DPP']['status']);
        $this->assertNull($result['DPP']['value']);
    }

    public function test_structural_horizon_is_bounded_without_an_implicit_default(): void
    {
        foreach ([[], array_fill(0, 101, '0.00')] as $cash) {
            try {
                InvestmentEvaluation::calculate('1.00', $cash, '0', 'interpolation', true);
                $this->fail('Invalid horizon accepted');
            } catch (\InvalidArgumentException) {
                $this->addToAssertionCount(1);
            }
        }
    }
}
