<?php

namespace App\Domain\Finance;

/** Annual manual-input analysis. Exact rational NPV/payback; bounded conventional-flow IRR. */
final class InvestmentEvaluation
{
    public const VERSION = 'annual-relative-rational-bisection-v1';

    private const PRECISION = 80;

    private const TOLERANCE = '0.00000000000001';

    public static function calculate(?string $initial, array $flows, ?string $rate, ?string $convention, bool $sourcesComplete): array
    {
        if (count($flows) < 1 || count($flows) > 100 || ! array_is_list($flows) || ! in_array($convention, [null, 'interpolation', 'end_of_year'], true)) {
            throw new \InvalidArgumentException('Horizon atau konvensi tidak sah.');
        }
        $i0 = $initial === null ? null : RationalDecimal::scaled($initial, 2);
        if ($i0 !== null && bccomp($i0, '0', 0) < 0) {
            throw new \InvalidArgumentException('Investasi awal tidak boleh negatif.');
        }
        $cash = array_map(fn ($v) => $v === null ? null : RationalDecimal::scaled($v, 2), $flows);
        $r = $rate === null ? null : RationalDecimal::scaled($rate, 8);
        $a = $r === null ? null : bcadd('100000000', $r, 0);
        $rateValid = $a !== null && bccomp($a, '0', 0) > 0;
        $inputs = ['initial_outlay' => $initial, 'annual_cash_flows' => $flows, 'discount_rate' => $rate, 'horizon_years' => count($flows), 'payback_convention' => $convention];
        $make = function (string $formula, string $unit, int $scale, ?string $n, ?string $d, array $reasons = [], array $detail = [], bool $partial = false, string $missing = 'unavailable') use ($inputs, $sourcesComplete) {
            if (! $sourcesComplete) {
                $reasons[] = 'Sumber/basis input belum seluruhnya dinyatakan lengkap oleh pengelola.';
            }

            return ['formula' => $formula, 'unit' => $unit, 'status' => $n === null ? $missing : (($partial || ! $sourcesComplete) ? 'provisional' : 'available'),
                'value' => $n === null ? null : RationalDecimal::round($n, $d, $scale), 'display_scale' => $scale, 'rounding' => 'HALF_UP',
                'exact_fraction' => $n === null ? null : ['numerator' => $n, 'denominator' => $d], 'reasons' => array_values(array_unique($reasons)), 'inputs' => $inputs,
                'assumptions' => ['I0 dihitung sekali pada tahun0; CF adalah kas neto tahunan yang dinyatakan pengguna, bukan otomatis laba musim.',
                    'Tidak ada depresiasi/nilai sisa yang ditambahkan otomatis; periksa basis setiap input dan asumsi realisasi.'], 'calculation_detail' => $detail ?: ['method' => $n === null ? 'not_calculated' : 'exact-rational-half-up-v1']];
        };
        $npvReasons = [];
        if ($i0 === null) {
            $npvReasons[] = 'Investasi awal belum diketahui.';
        }
        if (! $rateValid) {
            $npvReasons[] = $rate === null ? 'Diskonto belum dipilih.' : 'Faktor1+r harus positif.';
        }
        if (in_array(null, $cash, true)) {
            $npvReasons[] = 'Ada arus kas tahunan yang belum diketahui.';
        }
        $n = '0';
        $den = '1';
        $bp = '1';
        if (! $npvReasons) {
            foreach ($cash as $cf) {
                $n = bcadd(bcmul($n, $a, 0), bcmul($cf, $bp = bcmul($bp, '100000000', 0), 0), 0);
                $den = bcmul($den, $a, 0);
            }
        }
        $npv = $make('-I0 + sum(CFt / (1+r)^t)', 'IDR', 2, $npvReasons ? null : bcsub($n, bcmul($i0, $den, 0), 0), bcmul($den, '100', 0), $npvReasons);
        $payback = function (bool $discounted) use ($i0, $cash, $rateValid, $a, $convention, $make) {
            $reasons = [];
            if ($i0 === null || bccomp($i0, '0', 0) <= 0) {
                $reasons[] = 'Waktu pengembalian memerlukan investasi awal positif yang diketahui.';
            }
            if ($convention === null) {
                $reasons[] = 'Pilih konvensi interpolasi atau akhir tahun secara eksplisit.';
            }
            if ($discounted && ! $rateValid) {
                $reasons[] = 'DPP memerlukan diskonto yang diketahui dan faktor1+r positif.';
            }
            $formula = $discounted ? 'first recovery of cumulative CFt/(1+r)^t against I0' : 'first recovery of cumulative CFt against I0';
            if ($reasons) {
                return $make($formula, 'year', 8, null, null, $reasons);
            }
            $num = '0';
            $den = '1';
            $bp = '1';
            $recovery = null;
            $rn = null;
            $rd = null;
            $unknown = false;
            $negativeAfter = false;
            foreach ($cash as $index => $cf) {
                if ($recovery !== null) {
                    if ($cf === null) {
                        $unknown = true;
                    } elseif (bccomp($cf, '0', 0) < 0) {
                        $negativeAfter = true;
                    }

                    continue;
                }
                if ($cf === null) {
                    return $make($formula, 'year', 8, null, null, ['Arus kas sebelum pemulihan belum diketahui; pemulihan pertama belum dapat ditentukan.']);
                }
                $previous = $num;
                $previousDen = $den;
                $factor = $discounted ? $a : '1';
                $bp = $discounted ? bcmul($bp, '100000000', 0) : '1';
                $yearN = bcmul($cf, $bp, 0);
                $num = bcadd(bcmul($num, $factor, 0), $yearN, 0);
                $den = bcmul($den, $factor, 0);
                if (bccomp($num, bcmul($i0, $den, 0), 0) >= 0) {
                    $recovery = $index + 1;
                    if ($convention === 'end_of_year') {
                        $rn = (string) $recovery;
                        $rd = '1';
                    } else {
                        $rd = $yearN;
                        $remaining = bcmul(bcsub(bcmul($i0, $previousDen, 0), $previous, 0), $factor, 0);
                        $rn = bcadd(bcmul((string) $index, $rd, 0), $remaining, 0);
                    }
                }
            }
            if ($negativeAfter) {
                $reasons[] = 'Ada kas negatif setelah pemulihan pertama; hasil tidak menjamin investasi tetap pulih setelahnya.';
            }
            if ($unknown) {
                $reasons[] = 'Arus kas setelah pemulihan pertama belum seluruhnya diketahui.';
            }
            if ($recovery === null) {
                $reasons[] = 'Belum kembali dalam horizon yang diketahui; bukan nilai nol.';
            }

            return $make($formula, 'year', 8, $rn, $rd, $reasons, ['convention' => $convention, 'recovery_year' => $recovery, 'negative_after_recovery' => $negativeAfter], $unknown, $recovery === null ? 'not_recovered' : 'unavailable');
        };
        $irrReasons = [];
        if ($i0 === null || bccomp($i0, '0', 0) <= 0) {
            $irrReasons[] = 'IRR memerlukan investasi awal positif yang diketahui.';
        }
        if (in_array(null, $cash, true)) {
            $irrReasons[] = 'IRR memerlukan seluruh arus kas tahunan diketahui.';
        }
        $negative = false;
        $positive = false;
        foreach ($cash as $cf) {
            if ($cf !== null) {
                $negative = $negative || bccomp($cf, '0', 0) < 0;
                $positive = $positive || bccomp($cf, '0', 0) > 0;
            }
        }
        if ($negative || ! $positive) {
            $irrReasons[] = 'Solver awal hanya mendukung CF setelah investasi>=0 dengan setidaknya satu positif; seri ini di luar cakupan.';
        }
        $irr = $make('root(-I0 + sum(CFt/(1+IRR)^t))', 'percent', 8, null, null, $irrReasons);
        if (! $irrReasons) {
            $solved = self::irr($i0, $cash);
            if ($solved === null) {
                $irr['reasons'][] = 'Solver tidak memperoleh akar dalam batas bracket/iterasi; indikator lain tetap independen.';
            } else {
                $irr = $make('root(-I0 + sum(CFt/(1+IRR)^t))', 'percent', 8, '0', '1', [], $solved);
                $scaled = bcmul($solved['rate_fraction'], bcpow('10', self::PRECISION, 0), 0);
                $irr['value'] = RationalDecimal::round(bcmul($scaled, '100', 0), bcpow('10', self::PRECISION, 0), 8);
                $irr['exact_fraction'] = null;
                $irr['reasons'][] = 'IRR merupakan pendekatan numerik dalam batas interval solver, bukan pecahan eksak.';
                if ($irr['value'] === '-100.00000000') {
                    $irr['reasons'][] = 'Angka tampilan dibulatkan ke-100%; akar tetap di atas-100%. Lihat batas solver.';
                }
            }
        }

        return ['NPV' => $npv, 'IRR' => $irr, 'PP' => $payback(false), 'DPP' => $payback(true)];
    }

    private static function irr(string $initial, array $cash): ?array
    {
        $sign = function (string $x) use ($initial, $cash): int {
            $pv = '0';
            $grows = bccomp($x, '1', self::PRECISION) < 0;
            foreach (array_reverse($cash) as $cf) {
                $pv = bcdiv(bcadd($pv, $cf, self::PRECISION), $x, self::PRECISION);
                if ($grows && bccomp($pv, $initial, self::PRECISION) > 0) {
                    return 1;
                }
            }

            return bccomp($pv, $initial, self::PRECISION);
        };
        $lo = '0.'.str_repeat('0', 39).'1';
        $hi = '2';
        $expansions = 0;
        while ($sign($hi) > 0 && $expansions < 128) {
            $hi = bcmul($hi, '2', self::PRECISION);
            $expansions++;
        }
        if ($sign($lo) <= 0 || $sign($hi) > 0) {
            return null;
        }
        $iterations = 0;
        $converged = false;
        while ($iterations < 256) {
            $iterations++;
            $mid = bcdiv(bcadd($lo, $hi, self::PRECISION), '2', self::PRECISION);
            $s = $sign($mid);
            if ($s === 0) {
                $lo = $hi = $mid;
                $converged = true;
                break;
            }
            if ($s > 0) {
                $lo = $mid;
            } else {
                $hi = $mid;
            }
            if (bccomp(bcsub($hi, $lo, self::PRECISION), self::TOLERANCE, self::PRECISION) <= 0) {
                $converged = true;
                break;
            }
        }
        if (! $converged) {
            return null;
        }

        return ['method' => 'conventional-horner-decimal80-bisection-v1', 'precision' => self::PRECISION, 'iterations' => $iterations, 'max_iterations' => 256, 'bracket_expansions' => $expansions,
            'tolerance_fraction' => self::TOLERANCE, 'lower_rate_fraction' => bcsub($lo, '1', self::PRECISION), 'upper_rate_fraction' => bcsub($hi, '1', self::PRECISION),
            'rate_fraction' => bcsub(bcdiv(bcadd($lo, $hi, self::PRECISION), '2', self::PRECISION), '1', self::PRECISION), 'converged' => true];
    }
}
