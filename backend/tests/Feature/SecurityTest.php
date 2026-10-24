<?php

namespace Tests\Feature;

use App\Models\Device;
use App\Models\Tank;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Str;
use Tests\TestCase;

class SecurityTest extends TestCase
{
    use RefreshDatabase;

    public function test_real_tokens_expire_revoke_and_keep_tank_scope_server_authoritative(): void
    {
        $a = Tank::create(['name' => 'TEST A']);
        $b = Tank::create(['name' => 'TEST B']);
        $owner = User::create(['name' => 'A', 'email' => 'token-a@example.invalid', 'password' => 'fixture-only-password', 'tank_id' => $a->id, 'role' => 'owner']);
        $other = User::create(['name' => 'B', 'email' => 'token-b@example.invalid', 'password' => 'fixture-only-password', 'tank_id' => $b->id, 'role' => 'owner']);
        $login = $this->postJson('/api/v1/login', ['email' => $owner->email, 'password' => 'fixture-only-password'])->assertOk()->assertJsonPath('user.id', $owner->id)->assertJsonPath('user.tank_id', $a->id)->json();
        $token = $login['token'];
        $worker = $this->withToken($token)->postJson('/api/v1/workers', ['name' => 'Scoped fixture'])->assertCreated()->json();
        $this->app['auth']->forgetGuards();
        $otherToken = $other->createToken('fixture', ['owner'], now()->addDay())->plainTextToken;
        $this->withToken($otherToken)->getJson('/api/v1/workers?tank_id='.$a->id)->assertJsonCount(0, 'data');
        $this->withToken($otherToken)->deleteJson('/api/v1/workers/'.$worker['id'], ['baseVersion' => $worker['version']])->assertNotFound();
        $this->app['auth']->forgetGuards();
        $this->withToken($token)->postJson('/api/v1/logout')->assertNoContent();
        $this->app['auth']->forgetGuards();
        $this->withToken($token)->getJson('/api/v1/dashboard')->assertUnauthorized();
        $this->app['auth']->forgetGuards();
        $expired = $owner->createToken('expired-fixture', ['owner'], now()->subSecond())->plainTextToken;
        $this->withToken($expired)->getJson('/api/v1/config')->assertUnauthorized();
    }

    public function test_disabled_device_and_device_limiter(): void
    {
        $tank = Tank::create(['name' => 'TEST limiter']);
        $device = Device::create(['id' => (string) Str::uuid(), 'tank_id' => $tank->id, 'name' => 'TEST', 'token_hash' => hash('sha256', 'limiter-fixture')]);
        for ($i = 0; $i < 120; $i++) {
            $this->withToken('limiter-fixture')->getJson('/api/v1/device/config')->assertOk();
        }
        $this->withToken('limiter-fixture')->getJson('/api/v1/device/config')->assertStatus(429);
        $device->update(['enabled' => false]);
        $this->travel(61)->seconds();
        $this->withToken('limiter-fixture')->getJson('/api/v1/device/config')->assertUnauthorized();
    }

    public function test_brute_force_payload_limit_and_scope_isolation(): void
    {
        $tank = Tank::create(['name' => 'TEST A']);
        $otherTank = Tank::create(['name' => 'TEST B']);
        $owner = User::create(['name' => 'A', 'email' => 'a@example.invalid', 'password' => 'fixture-only-password', 'tank_id' => $tank->id, 'role' => 'owner']);
        $other = User::create(['name' => 'B', 'email' => 'b@example.invalid', 'password' => 'fixture-only-password', 'tank_id' => $otherTank->id, 'role' => 'leader']);
        for ($i = 0; $i < 5; $i++) {
            $this->postJson('/api/v1/login', ['email' => $owner->email, 'password' => 'bad'])->assertUnprocessable();
        }
        $this->postJson('/api/v1/login', ['email' => $owner->email, 'password' => 'fixture-only-password'])->assertStatus(429);
        $this->postJson('/api/v1/login', ['email' => str_repeat('x', 17000).'@example.invalid', 'password' => 'bad'])->assertStatus(413);
        $worker = $this->actingAs($owner, 'sanctum')->postJson('/api/v1/workers', ['name' => '=TEST fixture'])->assertCreated()->json();
        $this->actingAs($other, 'sanctum')->putJson('/api/v1/workers/'.$worker['id'], ['name' => 'Changed', 'active' => true, 'baseVersion' => $worker['version']])->assertNotFound();
        $this->getJson('/api/v1/workers')->assertJsonCount(0, 'data');
        $producer = Device::create(['id' => (string) Str::uuid(), 'tank_id' => $tank->id, 'name' => 'TEST', 'token_hash' => hash('sha256', 'producer-fixture')]);
        $this->app['auth']->forgetGuards();
        $this->withHeader('Authorization', 'Bearer producer-fixture')->getJson('/api/v1/config')->assertUnauthorized();
    }
}
