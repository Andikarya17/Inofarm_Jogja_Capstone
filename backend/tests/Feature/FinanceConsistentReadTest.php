<?php

namespace Tests\Feature;

use App\Domain\Activities\ActivityService;
use App\Domain\Contracts\CanonicalJson;
use App\Domain\Harvest\HarvestService;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\AcceptedSnapshot;
use App\Domain\Sync\ScopedRead;
use App\Models\User;
use App\Support\TestDatabaseGuard;
use Illuminate\Database\Events\QueryExecuted;
use Illuminate\Database\QueryException;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Symfony\Component\HttpKernel\Exception\HttpException;
use Tests\FinanceTestCase;

class FinanceConsistentReadTest extends FinanceTestCase
{
    private function records(): array
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $unit = $this->createMaster('units', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'gram sintetis', 'dimension' => 'mass']);
        $material = $this->createMaster('materials', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Bahan sintetis', 'dimension' => 'mass']);
        $grade = $this->createMaster('grades', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Grade sintetis']);

        return [$manager, [
            ['activity', 'activities', ActivityService::class, ['planting_group_id' => $ctx['group']['id'],
                'activity_type' => 'spray', 'occurred_at' => '2026-10-29T08:00:00+07:00', 'work_state' => 'completed',
                'materials' => [['material_id' => $material['id'], 'unit_id' => $unit['id'], 'quantity' => '1']], 'notes' => 'Versi satu']],
            ['harvest', 'harvests', HarvestService::class, ['planting_group_id' => $ctx['group']['id'],
                'occurred_at' => '2026-10-29T08:00:00+07:00', 'notes' => 'Versi satu',
                'items' => [['id' => (string) Str::uuid(), 'grade_id' => $grade['id'], 'fruit_count' => 1, 'weight_kg' => '1']]]],
        ]];
    }

    private function correction(array $payload): array
    {
        $payload = [...$payload, 'notes' => 'Versi dua', 'reason' => 'Koreksi sintetis', 'reference_policy' => 'preserve'];
        if (isset($payload['items'])) {
            $payload['items'][0]['weight_kg'] = '2';
        } else {
            $payload['materials'][0]['quantity'] = '2';
        }

        return $payload;
    }

    public function test_old_header_projects_its_accepted_children_after_a_new_version_commits(): void
    {
        [, $records] = $this->records();
        foreach ($records as [$kind, $table, , $payload]) {
            $op = $this->operation($kind, $payload);
            $v1 = $this->postJson('/api/v2/'.$table, $op)->assertCreated()->json('data');
            $header = DB::table($table)->where('id', $op['entity_id'])->first();
            $v2 = $this->patchJson('/api/v2/'.$table.'/'.$op['entity_id'], $this->operation($kind, $this->correction($payload), 'update', $op['entity_id'], 1))
                ->assertOk()->json('data');
            $this->assertSame(CanonicalJson::hash($v1), CanonicalJson::hash(app(AcceptedSnapshot::class)->read($kind, $header)));
            $this->getJson('/api/v2/'.$table.'/'.$op['entity_id'])->assertExactJson($v2);
            $listed = $this->getJson('/api/v2/'.$table)->assertOk()->json('data.0');
            $this->assertSame(CanonicalJson::hash($v2), CanonicalJson::hash($listed));
        }
    }

    public function test_controlled_interleave_after_header_query_never_mixes_versions(): void
    {
        [$manager, $records] = $this->records();
        foreach ($records as [$kind, $table, $service, $payload]) {
            $op = $this->operation($kind, $payload);
            $v1 = $this->postJson('/api/v2/'.$table, $op)->assertCreated()->json('data');
            $edit = $this->operation($kind, $this->correction($payload), 'update', $op['entity_id'], 1);
            $armed = true;
            $v2 = null;
            // Deliberate same-connection interleave, separate from the two-connection lock test.
            DB::listen(function (QueryExecuted $event) use (&$armed, &$v2, $manager, $table, $service, $edit) {
                if ($armed && str_starts_with($event->sql, 'select * from `'.$table.'`') && in_array($edit['entity_id'], $event->bindings, true)) {
                    $armed = false;
                    $v2 = app($service)->mutate($manager, $edit)['body']['data'];
                }
            });
            $this->getJson('/api/v2/'.$table.'/'.$op['entity_id'])->assertExactJson($v1);
            $this->assertFalse($armed, 'Synthetic interleave must execute after header SELECT.');
            $this->assertSame(2, $v2['version']);
            $this->getJson('/api/v2/'.$table.'/'.$op['entity_id'])->assertExactJson($v2);
        }
    }

    public function test_missing_snapshot_fails_instead_of_fabricating_a_revision(): void
    {
        [, $records] = $this->records();
        foreach ($records as [$kind, $table, , $payload]) {
            $op = $this->operation($kind, $payload);
            $this->postJson('/api/v2/'.$table, $op)->assertCreated();
            DB::table('audit_logs')->where('entity_type', $kind)->where('entity_id', $op['entity_id'])->delete();
            $this->getJson('/api/v2/'.$table.'/'.$op['entity_id'])->assertStatus(500)->assertJsonPath('code', 'record_snapshot_missing');
        }
    }

    public function test_read_boundary_rechecks_stale_role_active_state_and_revoked_token(): void
    {
        $actor = $this->account();
        $token = $actor->createToken('synthetic-v2', ['finance-v2'], now()->addDay())->accessToken;
        $actor->withAccessToken($token);
        $request = Request::create('/api/v2/activities');
        $request->setUserResolver(fn () => $actor);
        foreach ([['role' => 'worker'], ['active' => false]] as $change) {
            User::whereKey($actor->id)->update($change);
            try {
                app(ScopedRead::class)->run($request, fn (User $fresh) => app(GreenhouseAccess::class)->manager($fresh));
                $this->fail('Stale manager session must not pass.');
            } catch (HttpException $e) {
                $this->assertSame(403, $e->getStatusCode());
            }
        }
        User::whereKey($actor->id)->update(['active' => true, 'role' => 'manager']);
        $token->delete();
        try {
            app(ScopedRead::class)->run($request, fn () => $this->fail('Revoked token callback must not execute.'));
            $this->fail('Revoked token must fail.');
        } catch (HttpException $e) {
            $this->assertSame(401, $e->getStatusCode());
        }
    }

    public function test_read_boundary_owns_the_mutation_lock_on_a_second_mysql_connection(): void
    {
        $actor = $this->account();
        $request = Request::create('/api/v2/activities');
        $request->setUserResolver(fn () => $actor);
        $connection = DB::connection()->getConfig();
        TestDatabaseGuard::assertSafe(['app' => ['env' => 'testing'], 'database' => ['default' => 'mysql', 'connections' => ['mysql' => $connection]]]);
        config(['database.connections.read_probe' => $connection]);
        $probe = DB::connection('read_probe');
        try {
            $probe->statement('SET SESSION innodb_lock_wait_timeout = 1');
            $primaryId = DB::selectOne('SELECT CONNECTION_ID() AS id')->id;
            $this->assertNotSame($primaryId, $probe->selectOne('SELECT CONNECTION_ID() AS id')->id);
            app(ScopedRead::class)->run($request, function () use ($probe) {
                $probe->beginTransaction();
                try {
                    $probe->table('sync_state')->where('id', 1)->lockForUpdate()->first();
                    $this->fail('Independent writer must wait while scoped read holds the metadata row.');
                } catch (QueryException $e) {
                    $this->assertSame(1205, $e->errorInfo[1]);
                } finally {
                    $probe->rollBack();
                }
            });
        } finally {
            DB::purge('read_probe');
        }
    }
}
