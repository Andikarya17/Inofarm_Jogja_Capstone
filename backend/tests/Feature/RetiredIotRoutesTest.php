<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\Route;
use Illuminate\Support\Facades\Artisan;
use Tests\TestCase;

class RetiredIotRoutesTest extends TestCase
{
    public function test_only_v2_product_routes_and_finance_provisioning_remain(): void
    {
        foreach (Route::getRoutes() as $route) {
            $this->assertFalse(str_starts_with($route->uri(), 'api/v1'), $route->uri());
            $this->assertNotContains('device', $route->gatherMiddleware());
            $this->assertNotContains('owner', $route->gatherMiddleware());
        }
        foreach (['login','dashboard','history','telemetry','device/events/telemetry','device/config','device/config-applied','config','workers','shifts'] as $path) {
            $this->getJson('/api/v1/'.$path)->assertNotFound();
            $this->postJson('/api/v1/'.$path, [])->assertNotFound();
        }
        $this->getJson('/api/v2/me')->assertUnauthorized();
        $commands = Artisan::all();
        $this->assertArrayHasKey('inofarm:finance-user', $commands);
        foreach (['inofarm:device', 'inofarm:owner', 'inofarm:demo-history'] as $command) $this->assertArrayNotHasKey($command, $commands);
    }
}
