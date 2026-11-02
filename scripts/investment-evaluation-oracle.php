<?php

use App\Domain\Finance\InvestmentEvaluation;

require dirname(__DIR__).'/backend/vendor/autoload.php';
$cases = json_decode(stream_get_contents(STDIN), true, flags: JSON_THROW_ON_ERROR);
$out = [];
foreach ($cases as $case) {
    $out[] = InvestmentEvaluation::calculate($case['initial'], $case['flows'], $case['rate'], $case['convention'], $case['sources_complete']);
}
echo json_encode($out, JSON_THROW_ON_ERROR);
