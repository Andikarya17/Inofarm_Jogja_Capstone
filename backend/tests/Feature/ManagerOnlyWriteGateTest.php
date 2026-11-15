<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\Route;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;

class ManagerOnlyWriteGateTest extends FinanceTestCase
{
    /** Writes a worker may legitimately send; every other v2 POST/PATCH must carry the manager gate. */
    private const WORKER_WRITES = ['POST api/v2/auth/login', 'POST api/v2/auth/logout', 'POST api/v2/sync/operations',
        'POST api/v2/sync/reconcile', 'POST api/v2/attachments', 'POST api/v2/activities', 'PATCH api/v2/activities/{id}',
        'POST api/v2/activities/{id}/cancel', 'POST api/v2/harvests', 'PATCH api/v2/harvests/{id}', 'POST api/v2/harvests/{id}/cancel'];

    private function managerOnlyWrites(): array
    {
        $id = (string) Str::uuid();
        $writes = [];
        foreach (['greenhouses', 'seasons', 'planting-groups', 'varieties', 'materials', 'units', 'grades', 'expense-categories',
            'assignments', 'schedules', 'targets', 'expenses', 'cost-allocations', 'assets', 'depreciation-allocations', 'sales',
            'investment-scenarios', 'users'] as $resource) {
            $writes[] = ['POST', $resource];
            $writes[] = ['PATCH', $resource.'/'.$id];
        }
        foreach (['expenses', 'cost-allocations', 'assets', 'depreciation-allocations', 'sales', 'investment-scenarios', 'attachments'] as $resource) {
            $writes[] = ['POST', $resource.'/'.$id.'/cancel'];
        }

        return [...$writes, ['POST', 'seasons/'.$id.'/close'], ['POST', 'seasons/'.$id.'/reopen'],
            ['POST', 'greenhouses/'.$id.'/running-period'], ['POST', 'planting-groups/'.$id.'/harvest-completion'],
            ['POST', 'activity-reviews'], ['POST', 'harvest-reviews'], ['POST', 'users/assignment-candidate'],
            ['POST', 'season-reports/preview'], ['POST', 'season-reports'], ['POST', 'investment-reports/preview'],
            ['POST', 'investment-reports'], ['POST', 'recovery/backups'], ['POST', 'recovery/backups/'.$id.'/validate']];
    }

    public function test_worker_with_an_empty_payload_gets_403_on_every_manager_only_write(): void
    {
        $this->asUser($this->account('worker'));
        foreach ($this->managerOnlyWrites() as [$method, $path]) {
            $response = $this->json($method, '/api/v2/'.$path, []);
            $this->assertSame(403, $response->status(), $method.' '.$path);
            $this->assertSame([], $response->json('field_errors') ?? [], $method.' '.$path.' must not describe the payload');
        }
    }

    public function test_manager_still_reaches_validation_on_gated_writes(): void
    {
        $this->asUser($this->account());
        foreach ([['POST', 'expenses'], ['POST', 'sales'], ['POST', 'users'], ['POST', 'greenhouses'], ['POST', 'season-reports/preview']] as [$method, $path]) {
            $this->assertSame(422, $this->json($method, '/api/v2/'.$path, [])->status(), $method.' '.$path);
        }
    }

    public function test_every_v2_write_route_is_either_gated_or_a_known_worker_write(): void
    {
        $ungated = [];
        foreach (Route::getRoutes() as $route) {
            $write = array_values(array_intersect($route->methods(), ['POST', 'PATCH', 'PUT', 'DELETE']));
            if ($write && str_starts_with($route->uri(), 'api/v2/') && ! in_array('finance-manager', $route->gatherMiddleware(), true)) {
                $ungated[] = $write[0].' '.$route->uri();
            }
        }
        sort($ungated);
        $expected = self::WORKER_WRITES;
        sort($expected);
        $this->assertSame($expected, $ungated);
    }
}
