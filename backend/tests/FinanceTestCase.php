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
