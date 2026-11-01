<?php

use App\Domain\Finance\SeasonEvaluation;

// Pure arithmetic bridge: no Laravel bootstrap, SQL, production environment or seed.
require dirname(__DIR__).'/backend/vendor/autoload.php';

$cases = json_decode(stream_get_contents(STDIN), true, flags: JSON_THROW_ON_ERROR);
$results = [];
foreach ($cases as $case) {
    $results[] = SeasonEvaluation::calculate($case['FC'], $case['VC'], $case['TR'], $case['Q'], $case['coverage']);
}
echo json_encode($results, JSON_THROW_ON_ERROR | JSON_UNESCAPED_UNICODE);
