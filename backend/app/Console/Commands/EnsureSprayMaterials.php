<?php

namespace App\Console\Commands;

use App\Domain\Cultivation\SprayMaterialCatalog;
use App\Models\User;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;

/**
 * Adds the starting spray list to every existing greenhouse, once, after migration 22.
 * New greenhouses get it on creation. Each created material is audited under the named active manager.
 */
final class EnsureSprayMaterials extends Command
{
    protected $signature = 'inofarm:spray-materials {manager_email : Active manager account recorded as the actor}';
    protected $description = 'Idempotently add the starting spray materials to every greenhouse.';

    public function handle(SprayMaterialCatalog $catalog): int
    {
        $actor = User::where('email', mb_strtolower(trim($this->argument('manager_email'))))->where('role', 'manager')->where('active', true)->first();
        if ($actor === null) {
            $this->error('Akun pengelola aktif tidak ditemukan.');

            return self::FAILURE;
        }
        $created = DB::transaction(function () use ($catalog, $actor) {
            DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();

            return DB::table('greenhouses')->orderBy('id')->pluck('id')->mapWithKeys(fn ($gh) => [$gh => $catalog->ensure($actor, $gh)])->all();
        });
        $this->line(json_encode(['greenhouses' => count($created), 'created' => array_sum($created)], JSON_THROW_ON_ERROR));

        return self::SUCCESS;
    }
}
