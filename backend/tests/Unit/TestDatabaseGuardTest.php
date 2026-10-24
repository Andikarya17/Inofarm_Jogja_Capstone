<?php

namespace Tests\Unit;

use App\Support\TestDatabaseGuard;
use Illuminate\Config\Repository;
use Illuminate\Foundation\Application;
use Illuminate\Foundation\Bootstrap\LoadConfiguration;
use PHPUnit\Framework\TestCase;

class TestDatabaseGuardTest extends TestCase
{
    private function configuration(array $connection = []): array
    {
        return ['app' => ['env' => 'testing'], 'database' => ['default' => 'mysql', 'connections' => ['mysql' => array_merge(['driver' => 'mysql', 'database' => 'inofarm_test'], $connection)]]];
    }

    public function test_safe_configuration_requires_no_database_connection(): void
    {
        TestDatabaseGuard::assertSafe($this->configuration());
        TestDatabaseGuard::assertSafe($this->configuration(['url' => 'mysql://localhost/inofarm_test']));
        $this->addToAssertionCount(2);
    }

    public function test_url_path_query_and_read_write_overrides_are_rejected(): void
    {
        foreach ([['database' => 'inofarm_local'], ['url' => 'mysql://localhost/inofarm_local'], ['url' => 'mysql://localhost/inofarm_test?database=inofarm_local'], ['url' => 'sqlite://localhost/inofarm_test'], ['read' => ['database' => 'inofarm_local']], ['write' => ['database' => 'inofarm_local']], ['write' => ['url' => 'mysql://localhost/inofarm_local']]] as $unsafe) {
            try {
                TestDatabaseGuard::assertSafe($this->configuration($unsafe));
                $this->fail('Unsafe configuration was accepted.');
            } catch (\RuntimeException) {
                $this->addToAssertionCount(1);
            }
        }
    }

    public function test_guard_fails_after_configuration_and_before_provider_or_mutation(): void
    {
        $app = new Application;
        $app->instance('config', new Repository($this->configuration(['database' => 'inofarm_local'])));
        TestDatabaseGuard::install($app, required: true);
        $mutated = false;
        try {
            // The same event is fired for cached and environment-loaded configuration.
            $app['events']->dispatch('bootstrapped: '.LoadConfiguration::class, [$app]);
            $mutated = true;
        } catch (\RuntimeException) {
            $this->assertFalse($mutated);
        }
        $this->assertFalse($mutated);
    }

    public function test_cached_local_environment_cannot_bypass_requested_test_mode(): void
    {
        $config = $this->configuration();
        $config['app']['env'] = 'local';
        $app = new Application;
        $app->instance('config', new Repository($config));
        TestDatabaseGuard::install($app);
        $this->expectException(\RuntimeException::class);
        $app['events']->dispatch('bootstrapped: '.LoadConfiguration::class, [$app]);
    }
}
