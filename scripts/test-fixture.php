<?php

use App\Models\Device;
use App\Models\Tank;
use App\Models\User;
use App\Support\TestDatabaseGuard;
use Illuminate\Contracts\Console\Kernel;

// Explicit integration fixtures, never production defaults.
putenv('APP_ENV=testing');
require __DIR__.'/../backend/vendor/autoload.php';
$app = require __DIR__.'/../backend/bootstrap/app.php';
TestDatabaseGuard::install($app, required: true);
$app->make(Kernel::class)->bootstrap();
TestDatabaseGuard::assertSafe($app['config']->all());
if (User::where('email', 'integration@example.invalid')->exists()) {
    throw new RuntimeException('Integration fixture exists; run test-backend first to reset testing schema.');
}
$tank = Tank::create(['name' => 'INTEGRATION TEST ONLY']);
User::create(['name' => 'TEST ONLY', 'email' => 'integration@example.invalid', 'password' => 'integration-password-only', 'role' => 'owner', 'tank_id' => $tank->id]);
Device::create(['id' => '11111111-1111-4111-8111-111111111111', 'name' => 'TEST ONLY', 'tank_id' => $tank->id, 'token_hash' => hash('sha256', 'integration-device-only')]);
echo "Testing fixture created in inofarm_test; production untouched.\n";
