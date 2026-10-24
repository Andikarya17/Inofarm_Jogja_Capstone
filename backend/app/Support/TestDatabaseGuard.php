<?php

namespace App\Support;

use Illuminate\Foundation\Application;
use Illuminate\Foundation\Bootstrap\LoadConfiguration;
use Illuminate\Support\ConfigurationUrlParser;
use RuntimeException;

/** No connection or SQL is opened while checking the effective configuration. */
final class TestDatabaseGuard
{
    public static function install(Application $app, bool $required = false): void
    {
        $app->afterBootstrapping(LoadConfiguration::class, function ($app) use ($required) {
            if ($required || $app['config']->get('app.env') === 'testing' || getenv('APP_ENV') === 'testing' || in_array('--env=testing', $_SERVER['argv'] ?? [], true)) {
                self::assertSafe($app['config']->all());
            }
        });
    }

    public static function assertSafe(array $config): void
    {
        if (($config['app']['env'] ?? null) !== 'testing' || ($config['database']['default'] ?? null) !== 'mysql') {
            throw new RuntimeException('Tests require testing environment and dedicated MySQL inofarm_test.');
        }
        $raw = $config['database']['connections']['mysql'] ?? [];
        $effective = (new ConfigurationUrlParser)->parseConfiguration($raw);
        self::assertConnection($effective);
        // Read/write overrides can redirect a connection after URL resolution.
        foreach (['read', 'write'] as $mode) {
            if (isset($effective[$mode])) {
                if (! is_array($effective[$mode]) || ! empty($effective[$mode]['url'])) {
                    throw new RuntimeException('Refusing ambiguous test read/write connection.');
                }
                self::assertConnection(array_merge($effective, $effective[$mode]));
            }
        }
    }

    private static function assertConnection(array $config): void
    {
        if (($config['driver'] ?? null) !== 'mysql' || ($config['database'] ?? null) !== 'inofarm_test') {
            throw new RuntimeException('Refusing tests outside effective MySQL inofarm_test schema.');
        }
    }
}
