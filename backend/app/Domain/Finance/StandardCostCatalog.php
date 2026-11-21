<?php

namespace App\Domain\Finance;

/** The cost lines Inofarm requires on every season sheet (20 Nov 2026). Names only: amounts are never seeded. */
final class StandardCostCatalog
{
    public const REQUIRED = [
        ['name' => 'Biaya penyusutan', 'cost_class' => 'FC'],
        ['name' => 'Sewa tanah', 'cost_class' => 'FC'],
        ['name' => 'Tenaga kerja', 'cost_class' => 'FC'],
        ['name' => 'Cocopeat/rockwool', 'cost_class' => 'VC'],
        ['name' => 'Nutrisi AB Mix', 'cost_class' => 'VC'],
        ['name' => 'Nutrisi tambahan dan pestisida', 'cost_class' => 'VC'],
        ['name' => 'Benih', 'cost_class' => 'VC'],
    ];
}
