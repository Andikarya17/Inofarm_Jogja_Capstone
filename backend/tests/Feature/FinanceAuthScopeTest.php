<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\TestCase;

class FinanceAuthScopeTest extends TestCase
{
    use RefreshDatabase;

    private function account(string $role = 'worker'): User
    {
        return User::create(['public_id' => (string) Str::uuid(), 'name' => 'Akun sintetis',
            'email' => Str::uuid().'@example.invalid', 'password' => 'fixture-only-password', 'role' => $role]);
    }

    private function greenhouse(User $manager, string $name): string
    {
        $id = (string) Str::uuid();
        DB::table('greenhouses')->insert(['id' => $id, 'name' => $name, 'created_by' => $manager->id]);

        return $id;
    }

    private function assign(User $user, string $gh, User $manager): void
    {
        DB::table('greenhouse_assignments')->insert(['id' => (string) Str::uuid(), 'user_id' => $user->id,
            'greenhouse_id' => $gh, 'effective_from' => now()->subDay(), 'created_by' => $manager->id]);
    }

    private function token(User $actor, array $abilities = ['finance-v2']): string
    {
        $this->app['auth']->forgetGuards();

        return $actor->createToken('fixture-v2', $abilities, now()->addDay())->plainTextToken;
    }

    public function test_two_roles_receive_public_identity_and_only_assigned_greenhouses(): void
    {
        $manager = $this->account('manager');
        $worker = $this->account();
        $a = $this->greenhouse($manager, 'GH A sintetis');
        $b = $this->greenhouse($manager, 'GH B sintetis');
        $this->assign($worker, $a, $manager);
        $this->assign($manager, $b, $manager);
        foreach ([$worker, $manager] as $user) {
            $this->app['auth']->forgetGuards();
            $login = $this->postJson('/api/v2/auth/login', ['email' => $user->email, 'password' => 'fixture-only-password'])
                ->assertOk()->assertJsonPath('user.id', $user->public_id)->assertJsonPath('user.role', $user->role)->json();
            $this->withToken($login['token'])->getJson('/api/v2/me')->assertOk()->assertJsonCount(1, 'greenhouses');
            $this->withToken($login['token'])->getJson('/api/v2/greenhouses')->assertOk()->assertJsonCount(1, 'data');
        }
        $this->app['auth']->forgetGuards();
        $this->withToken($this->token($worker))->getJson('/api/v2/greenhouses/'.$b)->assertNotFound()->assertJsonPath('code', 'not_found');
        $this->getJson('/api/v2/greenhouses?greenhouse_id='.$b)->assertJsonPath('data.0.id', $a);
    }

    public function test_scope_revocation_inactive_and_effective_window_are_checked_each_request(): void
    {
        $manager = $this->account('manager');
        $worker = $this->account();
        $a = $this->greenhouse($manager, 'A');
        $this->assign($worker, $a, $manager);
        $token = $this->token($worker);
        $this->withToken($token)->getJson('/api/v2/greenhouses/'.$a)->assertOk();
        DB::table('greenhouse_assignments')->where('user_id', $worker->id)->update(['active' => false]);
        $this->app['auth']->forgetGuards();
        $this->withToken($token)->getJson('/api/v2/greenhouses/'.$a)->assertNotFound();
        DB::table('greenhouse_assignments')->where('user_id', $worker->id)->update(['active' => true, 'effective_from' => now()->addDay()]);
        $this->getJson('/api/v2/greenhouses')->assertJsonCount(0, 'data');
        DB::table('greenhouse_assignments')->where('user_id', $worker->id)->update(['effective_from' => now()->subDay(), 'effective_until' => now()->subSecond()]);
        $this->getJson('/api/v2/greenhouses')->assertJsonCount(0, 'data');
        $worker->update(['active' => false]);
        $this->app['auth']->forgetGuards();
        $this->withToken($token)->getJson('/api/v2/me')->assertForbidden()->assertJsonPath('code', 'access_denied');
    }

    public function test_real_token_logout_expiry_ability_and_legacy_boundaries(): void
    {
        $worker = $this->account();
        $token = $this->token($worker);
        $this->withToken($token)->getJson('/api/v1/dashboard')->assertForbidden();
        $this->withToken($token)->postJson('/api/v2/auth/logout')->assertNoContent();
        $this->app['auth']->forgetGuards();
        $this->withToken($token)->getJson('/api/v2/me')->assertUnauthorized()->assertJsonPath('code', 'authentication_required');
        $expired = $worker->createToken('fixture-expired', ['finance-v2'], now()->subSecond())->plainTextToken;
        $this->app['auth']->forgetGuards();
        $this->withToken($expired)->getJson('/api/v2/me')->assertUnauthorized();
        $this->withToken($this->token($worker, ['owner']))->getJson('/api/v2/me')->assertForbidden();
        $legacy = $this->account('owner');
        $this->app['auth']->forgetGuards();
        $this->withToken($this->token($legacy))->getJson('/api/v2/me')->assertForbidden();
        $this->assertDatabaseCount('tanks', 0);
    }

    public function test_login_errors_do_not_disclose_role_or_activation_and_rate_limit_is_enforced(): void
    {
        $user = $this->account();
        $user->update(['active' => false]);
        for ($i = 0; $i < 5; $i++) {
            $this->postJson('/api/v2/auth/login', ['email' => $user->email, 'password' => 'fixture-only-password'])
                ->assertUnprocessable()->assertJsonPath('code', 'validation_failed')->assertJsonPath('field_errors.email.0', 'Email atau password tidak sesuai.');
        }
        $this->postJson('/api/v2/auth/login', ['email' => $user->email, 'password' => 'fixture-only-password'])
            ->assertStatus(429)->assertJsonPath('code', 'rate_limited')->assertHeader('Retry-After');
        $this->postJson('/api/v2/auth/login', ['email' => str_repeat('x', 17000), 'password' => 'fixture'])
            ->assertStatus(413)->assertJsonPath('code', 'payload_too_large');
    }

    public function test_context_pagination_is_bounded_and_manager_has_no_global_access(): void
    {
        $manager = $this->account('manager');
        $a = $this->greenhouse($manager, 'A');
        $b = $this->greenhouse($manager, 'B');
        $this->withToken($this->token($manager))->getJson('/api/v2/greenhouses')->assertJsonCount(0, 'data');
        $this->assign($manager, $a, $manager);
        $this->assign($manager, $b, $manager);
        $first = $this->getJson('/api/v2/greenhouses?limit=1')->assertJsonCount(1, 'data')->json();
        $second = $this->getJson('/api/v2/greenhouses?limit=1&cursor='.$first['next_cursor'])->assertJsonCount(1, 'data')->json();
        $this->assertNotSame($first['data'][0]['id'], $second['data'][0]['id']);
        $this->getJson('/api/v2/greenhouses?limit=101')->assertUnprocessable();
    }
}
