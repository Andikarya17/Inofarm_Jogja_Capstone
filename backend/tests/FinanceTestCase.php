<?php

namespace Tests;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

abstract class FinanceTestCase extends TestCase
{
    use RefreshDatabase;

    protected function account(string $role = 'manager'): User
    {
        return User::create(['public_id' => (string) Str::uuid(), 'name' => 'Akun sintetis',
            'email' => Str::uuid().'@example.invalid', 'password' => 'fixture-only-password', 'role' => $role]);
    }

    protected function asUser(User $actor): static
    {
        $this->app['auth']->forgetGuards();

        return $this->withToken($actor->createToken('synthetic-v2', ['finance-v2'], now()->addDay())->plainTextToken);
    }

    protected function operation(string $type, array $payload, string $action = 'create', ?string $id = null, ?int $version = null): array
    {
        return ['schema_version' => '2', 'sync_epoch' => DB::table('sync_state')->where('id', 1)->value('epoch'),
            'operation_id' => (string) Str::uuid(), 'entity_id' => $id ?? (string) Str::uuid(),
            'entity_type' => $type, 'action' => $action, 'base_version' => $version, 'payload' => $payload];
    }

    protected function createMaster(string $type, array $payload): array
    {
        $path = ['planting_groups' => 'planting-groups', 'greenhouse_assignments' => 'assignments', 'expense_categories' => 'expense-categories'][$type] ?? $type;

        return $this->postJson('/api/v2/'.$path, $this->operation($type, $payload))->assertCreated()->json('data');
    }

    /**
     * Fills standard cost sheet n so FC and VC per greenhouse equal the given totals (first line of each class carries
     * the amount, the other required lines are zero). Season reports read costs from this sheet since 21 Nov 2026.
     */
    protected function standardCosts(string $fc, string $vc, int $sequence = 1): array
    {
        $sheet = $this->getJson('/api/v2/season-cost-sheets?sequence='.$sequence)->assertOk()->json('data.0');
        $seen = [];
        $lines = array_map(function ($line) use ($fc, $vc, &$seen) {
            $first = ! isset($seen[$line['cost_class']]);
            $seen[$line['cost_class']] = true;

            return ['id' => $line['id'], 'name' => $line['name'], 'cost_class' => $line['cost_class'], 'is_required' => $line['is_required'],
                'amount' => $first ? ($line['cost_class'] === 'FC' ? $fc : $vc) : '0.00'];
        }, $sheet['lines']);

        return $this->patchJson('/api/v2/season-cost-sheets/'.$sheet['id'],
            $this->operation('season_cost_sheet', ['lines' => $lines], 'update', $sheet['id'], $sheet['version']))->assertOk()->json('data');
    }

    protected function context(User $manager): array
    {
        $this->asUser($manager);
        $gh = $this->createMaster('greenhouses', ['name' => 'GH sintetis']);
        $variety = $this->createMaster('varieties', ['greenhouse_id' => $gh['id'], 'name' => 'Varietas sintetis']);
        $season = $this->createMaster('seasons', ['greenhouse_id' => $gh['id'], 'name' => 'Musim sintetis', 'start_date' => '2026-10-01', 'end_date' => null]);
        $group = $this->createMaster('planting_groups', ['season_id' => $season['id'], 'variety_id' => $variety['id'],
            'name' => 'Kelompok sintetis', 'population' => 100, 'sowing_date' => '2026-10-01', 'transplant_date' => '2026-10-10']);

        return compact('gh', 'variety', 'season', 'group');
    }
}
