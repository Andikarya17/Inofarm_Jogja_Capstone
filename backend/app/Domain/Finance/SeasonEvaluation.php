<?php

namespace App\Domain\Finance;

/** Known recorded amounts only; null means unknown, not zero. */
final class SeasonEvaluation
{
    public const VERSION = 'season-recorded-cash-rational-half-up-v1';

    public static function calculate(?string $fixed, ?string $variable, ?string $revenue, ?string $quantity, array $coverage): array
    {
        $fc = $fixed === null ? null : RationalDecimal::scaled($fixed, 2);
        $vc = $variable === null ? null : RationalDecimal::scaled($variable, 2);
        $tr = $revenue === null ? null : RationalDecimal::scaled($revenue, 2);
        $q = $quantity === null ? null : RationalDecimal::scaled($quantity, 6);
        foreach ([$fc, $vc, $tr, $q] as $number) {
            if ($number !== null && bccomp($number, '0', 0) < 0) {
                throw new \InvalidArgumentException('Sumber evaluasi musim tidak boleh negatif.');
            }
        }
        $tc = $fc !== null && $vc !== null ? bcadd($fc, $vc, 0) : null;
        $profit = $tr !== null && $tc !== null ? bcsub($tr, $tc, 0) : null;
        $cost = ['costs'];
        $sale = ['sales'];
        $both = ['costs', 'sales'];
        $metric = function (string $formula, string $unit, array $inputs, array $groups, ?string $n, string $d = '1', int $scale = 2, array $reasons = [], array $assumptions = []) use ($coverage): array {
            foreach ($inputs as $key => $value) {
                if ($value === null) {
                    $reasons[] = 'Input '.$key.' belum diketahui; kosong tidak dianggap nol.';
                }
            }
            $partial = false;
            foreach ($groups as $group) {
                if (($coverage[$group] ?? 'unknown') !== 'complete') {
                    $partial = true;
                    $reasons[] = $group === 'costs' ? 'Kelengkapan seluruh biaya/alokasi belum dinyatakan lengkap.' : 'Kelengkapan seluruh penjualan belum dinyatakan lengkap.';
                }
            }
            $available = $n !== null && ! in_array(null, $inputs, true) && bccomp($d, '0', 0) > 0;
            $value = $available ? RationalDecimal::round($n, $d, $scale) : null;
            if ($available && bccomp($n, '0', 0) !== 0 && bccomp($value, '0', $scale) === 0) {
                $reasons[] = 'Nilai bukan nol tetapi lebih kecil dari presisi tampilan; pecahan eksak tetap disimpan.';
            }

            return ['value' => $value, 'unit' => $unit, 'display_scale' => $scale, 'rounding' => 'HALF_UP',
                'exact_fraction' => $available ? ['numerator' => $n, 'denominator' => $d] : null,
                'status' => ! $available ? 'unavailable' : ($partial ? 'provisional' : 'available'), 'reasons' => array_values(array_unique($reasons)),
                'formula' => $formula, 'inputs' => $inputs, 'assumptions' => $assumptions];
        };
        $money = fn (?string $cents) => $cents === null ? null : RationalDecimal::round($cents, '100', 2);
        $weight = $q === null ? null : RationalDecimal::round($q, '1000000', 6);
        $costInputs = ['FC' => $money($fc), 'VC' => $money($vc)];
        $base = ['TC' => $money($tc), 'TR' => $money($tr)];
        $rcReasons = $tc !== null && bccomp($tc, '0', 0) <= 0 ? ['R/C dan B/C sederhana memerlukan TC lebih dari nol.'] : [];
        $qReasons = $q !== null && bccomp($q, '0', 0) <= 0 ? ['Indikator ini memerlukan Q terjual lebih dari nol.'] : [];
        $bepReasons = [...$qReasons, ...($tr !== null && bccomp($tr, '0', 0) <= 0 ? ['BEP kuantitas memerlukan harga rata-rata positif (TR>0 dan Q>0).'] : [])];

        return [
            'FC' => $metric('FC=alokasi FC aktif+alokasi penyusutan aktif', 'IDR', ['FC' => $money($fc)], $cost, $fc, '100'),
            'VC' => $metric('VC=alokasi VC aktif', 'IDR', ['VC' => $money($vc)], $cost, $vc, '100'),
            'TC' => $metric('TC=FC+VC', 'IDR', $costInputs, $cost, $tc, '100'),
            'TR' => $metric('TR=sum(total transaksi lunas aktif; HALF_UP per transaksi)', 'IDR', ['TR' => $money($tr)], $sale, $tr, '100'),
            'Q' => $metric('Q=sum(kg detail penjualan lunas aktif)', 'kg', ['Q' => $weight], $sale, $q, '1000000', 6),
            'L' => $metric('L=TR-TC', 'IDR', $base, $both, $profit, '100'),
            'RC' => $metric('R/C=TR/TC; TC>0', 'ratio', $base, $both, $tr, $tc ?? '0', 8, $rcReasons),
            'BC' => $metric('B/C sederhana=(TR-TC)/TC; TC>0', 'ratio', $base, $both, $profit, $tc ?? '0', 8, $rcReasons, ['Rasio surplus terhadap biaya, bukan discounted benefit-cost.']),
            'P' => $metric('P=TR/Q; Q>0', 'IDR/kg', ['TR' => $money($tr), 'Q' => $weight], $sale, $tr === null ? null : bcmul($tr, '10000', 0), $q ?? '0', 6, $qReasons),
            'BEP_Q' => $metric('BEP kuantitas biaya tercatat=TC*Q/TR; P>0', 'kg', [...$base, 'Q' => $weight], $both,
                $tc === null || $q === null || bccomp($q, '0', 0) <= 0 ? null : bcmul($tc, $q, 0), $tr === null ? '0' : bcmul($tr, '1000000', 0), 6, $bepReasons, ['Total biaya tercatat dianggap tetap pada skenario perbandingan; harga tidak dibulatkan sebelum pembagian.']),
            'BEP_PRICE' => $metric('BEP harga=TC/Q; Q>0', 'IDR/kg', ['TC' => $money($tc), 'Q' => $weight], $both,
                $tc === null ? null : bcmul($tc, '10000', 0), $q ?? '0', 6, $qReasons),
        ];
    }
}
