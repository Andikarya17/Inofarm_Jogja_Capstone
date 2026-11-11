<?php

namespace Tests\Feature;

use Illuminate\Http\Request;
use Tests\FinanceTestCase;

class OpenApiParityTest extends FinanceTestCase
{
    private function specification(): array
    {
        return json_decode(file_get_contents(base_path('../contracts/v2/openapi.json')), true, flags: JSON_THROW_ON_ERROR);
    }

    public function test_published_operations_cover_runtime_routes_in_both_directions(): void
    {
        $runtime = [];
        foreach (app('router')->getRoutes() as $route) {
            if (! str_starts_with($route->uri(), 'api/v2/')) {
                continue;
            }
            $paths = [substr($route->uri(), strlen('api/v2'))];
            foreach ($route->wheres as $parameter => $constraint) {
                if ($parameter === 'id') {
                    continue;
                }
                $expanded = [];
                foreach ($paths as $path) {
                    foreach (explode('|', $constraint) as $value) {
                        $expanded[] = str_replace('{'.$parameter.'}', $value, $path);
                    }
                }
                $paths = $expanded;
            }
            foreach (array_diff($route->methods(), ['HEAD']) as $method) {
                foreach ($paths as $path) {
                    $runtime[] = $method.' '.$path;
                }
            }
        }
        $published = [];
        foreach ($this->specification()['paths'] as $path => $operations) {
            foreach ($operations as $method => $operation) {
                if (! in_array($method, ['get', 'post', 'patch', 'put', 'delete'], true)) {
                    continue;
                }
                foreach (str_contains($path, '{format}') ? ['pdf', 'csv'] : [''] as $format) {
                    $expanded = str_replace('{format}', $format, $path);
                    $published[] = strtoupper($method).' '.$expanded;
                    $url = '/api/v2'.str_replace('{id}', '11111111-1111-4111-8111-111111111111', $expanded);
                    $matched = app('router')->getRoutes()->match(Request::create($url, strtoupper($method)));
                    $this->assertContains('api', $matched->gatherMiddleware());
                }
                foreach ($operation['responses'] as $status => $response) {
                    if ((int) $status >= 200 && (int) $status < 300 && (int) $status !== 204) {
                        $this->assertNotEmpty($response['content'] ?? null, strtoupper($method).' '.$path);
                    }
                }
            }
        }
        sort($runtime);
        sort($published);
        $this->assertSame($runtime, $published);
    }

    public function test_master_reference_and_account_responses_match_published_shapes(): void
    {
        $ctx = $this->context($this->account());
        $resources = ['greenhouses', 'seasons', 'planting-groups', 'varieties'];
        foreach (['materials', 'units'] as $resource) {
            $this->createMaster($resource, ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Sintetis', 'dimension' => 'mass']);
            $resources[] = $resource;
        }
        foreach (['grades', 'expense_categories'] as $resource) {
            $this->createMaster($resource, ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Sintetis']);
            $resources[] = str_replace('_', '-', $resource);
        }
        $this->createMaster('schedules', ['planting_group_id' => $ctx['group']['id'], 'activity_type' => 'measurement', 'planned_at' => '2026-11-10T08:00:00+07:00', 'notes' => '']);
        $this->createMaster('targets', ['planting_group_id' => $ctx['group']['id'], 'activity_type' => 'measurement', 'parameter' => 'manual_ec', 'unit' => 'mS/cm',
            'effective_from' => '2026-11-01T00:00:00+07:00', 'effective_until' => null, 'target_value' => null, 'minimum' => '1', 'maximum' => '2', 'tolerance' => null]);
        array_push($resources, 'assignments', 'schedules', 'targets', 'users');
        $spec = $this->specification();
        $created = $this->postJson('/api/v2/users', $this->operation('users', [
            'greenhouse_id' => $ctx['gh']['id'], 'name' => 'Pekerja sintetis', 'email' => 'contract-worker@example.invalid',
            'role' => 'worker', 'password' => 'contract-fixture-password',
        ]))->assertCreated()->json();
        $this->assertShape($created, $spec['components']['schemas']['AccountReceipt'], $spec);
        $this->assertArrayNotHasKey('password', $created['data']);
        foreach ($resources as $resource) {
            $route = '/'.$resource;
            $body = $this->getJson('/api/v2'.$route.'?greenhouse_id='.$ctx['gh']['id'])->assertOk()->json();
            $schema = $spec['paths'][$route]['get']['responses']['200']['content']['application/json']['schema'];
            $this->assertShape($body, $schema, $spec);
            $this->assertNotEmpty($body['data'], $resource);
        }
    }

    private function assertShape(mixed $value, array $schema, array $spec): void
    {
        if (isset($schema['$ref'])) {
            $name = basename($schema['$ref']);
            $this->assertShape($value, $spec['components']['schemas'][$name], $spec);
            return;
        }
        if (isset($schema['anyOf'])) {
            if ($value === null) {
                $this->assertContains(['type' => 'null'], $schema['anyOf']);
                return;
            }
            $this->assertShape($value, $schema['anyOf'][0], $spec);
            return;
        }
        if (isset($schema['enum'])) {
            $this->assertContains($value, $schema['enum']);
        }
        if (($schema['type'] ?? null) === 'object') {
            $this->assertIsArray($value);
            foreach ($schema['required'] ?? [] as $key) {
                $this->assertArrayHasKey($key, $value);
            }
            foreach ($value as $key => $item) {
                $this->assertArrayHasKey($key, $schema['properties']);
                $this->assertShape($item, $schema['properties'][$key], $spec);
            }
        } elseif (($schema['type'] ?? null) === 'array') {
            $this->assertIsArray($value);
            foreach ($value as $item) {
                $this->assertShape($item, $schema['items'], $spec);
            }
        } elseif (($schema['type'] ?? null) === 'integer') {
            $this->assertIsInt($value);
        } elseif (($schema['type'] ?? null) === 'boolean') {
            $this->assertIsBool($value);
        } elseif (($schema['type'] ?? null) === 'string') {
            $this->assertIsString($value);
            if (isset($schema['pattern'])) {
                $this->assertMatchesRegularExpression('~'.$schema['pattern'].'~', $value);
            }
        }
    }
}
