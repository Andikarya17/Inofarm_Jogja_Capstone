<?php

namespace Tests\Feature;

use App\Domain\Cultivation\SprayMaterialCatalog;
use App\Models\User;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;

/**
 * User decisions of 17 Nov 2026: one nutrient tank per greenhouse, so readings, nutrition, stage and general work are
 * one record per greenhouse season; only spraying names a variety. Plant age is typed (HSS/HST), workers reach every
 * active greenhouse without an assignment, and spray materials typed by anyone become a shared master list.
 */
class RecordPerGreenhouseTest extends FinanceTestCase
{
    private function twoVarieties(User $manager): array
    {
        $ctx = $this->context($manager);
        $second = $this->createMaster('varieties', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Varietas kedua']);
        $ctx['group2'] = $this->createMaster('planting_groups', ['season_id' => $ctx['season']['id'], 'variety_id' => $second['id'],
            'name' => 'Varietas kedua', 'population' => 80, 'sowing_date' => '2026-10-01', 'transplant_date' => '2026-10-10']);
        $ctx['unit'] = $this->createMaster('units', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'gram', 'dimension' => 'mass']);
        $ctx['ml'] = $this->createMaster('units', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'ml', 'dimension' => 'volume']);
        $ctx['mix'] = $this->createMaster('materials', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'AB mix', 'dimension' => 'mass']);

        return $ctx;
    }

    private function entry(array $ctx, string $batch, array $overrides = []): array
    {
        return [...['season_id' => $ctx['season']['id'], 'planting_group_id' => null, 'entry_batch_id' => $batch, 'schedule_id' => null,
            'activity_type' => 'measurement', 'occurred_at' => '2026-11-17T08:00:00+07:00', 'age_basis' => 'hst', 'age_day' => 30,
            'spray_date' => null, 'spray_period' => null, 'manual_ec' => '1.8', 'manual_ppm' => '900', 'manual_ph' => '6.1',
            'observations' => ['measurement_moment' => 'after_injection'], 'actual_stage' => null, 'work_state' => 'completed',
            'skipped_reason' => null, 'materials' => [], 'notes' => ''], ...$overrides];
    }

    private function spray(array $ctx, string $batch, string $group, array $materials, array $overrides = []): array
    {
        return $this->entry($ctx, $batch, ['activity_type' => 'spray', 'planting_group_id' => $group, 'manual_ec' => null, 'manual_ppm' => null,
            'manual_ph' => null, 'observations' => null, 'spray_date' => '2026-11-17', 'spray_period' => 'morning', 'materials' => $materials, ...$overrides]);
    }

    public function test_one_entry_stores_greenhouse_records_once_and_spray_per_variety(): void
    {
        $manager = $this->account();
        $ctx = $this->twoVarieties($manager);
        $worker = $this->account('worker');
        $batch = (string) Str::uuid();
        $this->asUser($worker);
        $measurement = $this->postJson('/api/v2/activities', $this->operation('activity', $this->entry($ctx, $batch)))->assertCreated()
            ->assertJsonPath('data.planting_group_id', null)->assertJsonPath('data.season_id', $ctx['season']['id'])
            ->assertJsonPath('data.age_basis', 'hst')->assertJsonPath('data.age_day', 30)->assertJsonPath('data.entry_batch_id', $batch)
            ->assertJsonPath('data.reference_snapshot.scope', 'greenhouse')
            ->assertJsonPath('data.reference_snapshot.checks.manual_ppm.status', 'unavailable')->json('data');
        $nutrition = $this->entry($ctx, $batch, ['activity_type' => 'nutrition', 'manual_ec' => null, 'manual_ppm' => null, 'manual_ph' => null,
            'observations' => null, 'materials' => [['material_id' => $ctx['mix']['id'], 'unit_id' => $ctx['unit']['id'], 'quantity' => '250']]]);
        $this->postJson('/api/v2/activities', $this->operation('activity', $nutrition))->assertCreated();
        $stage = $this->entry($ctx, $batch, ['activity_type' => 'stage', 'manual_ec' => null, 'manual_ppm' => null, 'manual_ph' => null,
            'observations' => null, 'actual_stage' => 'Pembesaran buah, teks bebas']);
        $this->postJson('/api/v2/activities', $this->operation('activity', $stage))->assertCreated();
        foreach ([$ctx['group'], $ctx['group2']] as $group) {
            $this->postJson('/api/v2/activities', $this->operation('activity', $this->spray($ctx, $batch, $group['id'],
                [['material_name' => '  bion m ', 'unit_id' => null, 'quantity' => null], ['material_name' => 'Fungisida baru', 'unit_id' => $ctx['ml']['id'], 'quantity' => '15']])))
                ->assertCreated()->assertJsonPath('data.planting_group_id', $group['id'])->assertJsonPath('data.spray_period', 'morning')
                ->assertJsonPath('data.materials.0.quantity', null);
        }

        $this->assertSame(5, DB::table('activities')->where('entry_batch_id', $batch)->count());
        $this->assertSame(3, DB::table('activities')->where('entry_batch_id', $batch)->whereNull('planting_group_id')->count());
        $this->getJson('/api/v2/activities?entry_batch_id='.$batch)->assertJsonCount(5, 'data');
        // "bion m" matched the starting "Bion M"; the new name was created once per greenhouse, not twice.
        $this->assertSame(1, DB::table('materials')->where('greenhouse_id', $ctx['gh']['id'])->whereRaw('LOWER(name) = ?', ['bion m'])->count());
        $this->assertSame(1, DB::table('materials')->where('greenhouse_id', $ctx['gh']['id'])->where('name', 'Fungisida baru')->count());
        $this->assertSame('spray', DB::table('materials')->where('name', 'Fungisida baru')->value('purpose'));

        // The latest reading of either variety is the greenhouse-level one.
        foreach (['planting_group_id='.$ctx['group2']['id'], 'season_id='.$ctx['season']['id']] as $query) {
            $this->getJson('/api/v2/measurements/latest?'.$query)->assertOk()->assertJsonPath('data.id', $measurement['id']);
        }
        $usage = collect($this->asUser($manager)->getJson('/api/v2/seasons/'.$ctx['season']['id'].'/material-usage')->assertOk()->json('data.items'));
        $this->assertSame('250.000000', $usage->firstWhere('material_name', 'AB mix')['quantity']);
        $this->assertSame(1, $usage->firstWhere('material_name', 'AB mix')['activities']);
        $this->assertNull($usage->firstWhere('material_name', 'Bion M')['unit_id']);
        $this->assertSame('30.000000', $usage->firstWhere('material_name', 'Fungisida baru')['quantity']);

        // Either variety may be the running period; greenhouse-level readings follow its season.
        $this->postJson('/api/v2/greenhouses/'.$ctx['gh']['id'].'/running-period', $this->operation('greenhouses', ['planting_group_id' => $ctx['group2']['id']],
            'designate_period', $ctx['gh']['id'], (int) DB::table('greenhouses')->where('id', $ctx['gh']['id'])->value('version')))->assertOk();
        $home = $this->getJson('/api/v2/manager-home?greenhouse_id='.$ctx['gh']['id'])->assertOk();
        $home->assertJsonPath('greenhouses.0.period.manual_age.basis', 'hst')->assertJsonPath('greenhouses.0.period.manual_age.day', 30)
            ->assertJsonPath('greenhouses.0.period.phase.value', 'Pembesaran buah, teks bebas');
    }

    /** Found on the emulator against the real API, 18 Nov 2026: the review inbox answered 500 for a greenhouse-level row. */
    public function test_manager_reviews_greenhouse_level_rows_and_running_period_drafts_accept_them(): void
    {
        $manager = $this->account();
        $ctx = $this->twoVarieties($manager);
        $worker = $this->account('worker');
        $batch = (string) Str::uuid();
        $this->asUser($worker);
        $measurement = $this->postJson('/api/v2/activities', $this->operation('activity', $this->entry($ctx, $batch)))->assertCreated()->json('data');
        $this->postJson('/api/v2/activities', $this->operation('activity', $this->spray($ctx, $batch, $ctx['group']['id'],
            [['material_name' => 'Bion M', 'unit_id' => null, 'quantity' => null]])))->assertCreated();

        $this->asUser($manager);
        $inbox = collect($this->getJson('/api/v2/activity-reviews')->assertOk()->assertJsonCount(2, 'data')->json('data'));
        $greenhouseRow = $inbox->firstWhere('activity.id', $measurement['id']);
        $this->assertNull($greenhouseRow['context']['group']);
        $this->assertNull($greenhouseRow['context']['variety']);
        $this->assertSame($ctx['season']['name'], $greenhouseRow['context']['season']);
        $this->assertNotNull($inbox->firstWhere('activity.activity_type', 'spray')['context']['group']);
        $this->getJson('/api/v2/activities/'.$measurement['id'].'/verification')->assertOk()
            ->assertJsonPath('context.group', null)->assertJsonPath('status', 'pending');
        $this->postJson('/api/v2/activity-reviews', $this->operation('activity_review',
            ['activity_id' => $measurement['id'], 'activity_version' => 1, 'decision' => 'valid']))->assertCreated();
        $this->getJson('/api/v2/activity-reviews')->assertOk()->assertJsonCount(1, 'data');

        // A manager's entry opened from Beranda carries the running period version; greenhouse rows have no group to compare.
        $version = (int) DB::table('greenhouses')->where('id', $ctx['gh']['id'])->value('version');
        $this->postJson('/api/v2/greenhouses/'.$ctx['gh']['id'].'/running-period', $this->operation('greenhouses',
            ['planting_group_id' => $ctx['group']['id']], 'designate_period', $ctx['gh']['id'], $version))->assertOk();
        $current = (int) DB::table('greenhouses')->where('id', $ctx['gh']['id'])->value('version');
        $this->postJson('/api/v2/activities', $this->operation('activity', $this->entry($ctx, (string) Str::uuid(),
            ['running_period_version' => $current])))->assertCreated();
        $this->postJson('/api/v2/activities', $this->operation('activity', $this->entry($ctx, (string) Str::uuid(),
            ['running_period_version' => $current - 1])))->assertUnprocessable()->assertJsonPath('code', 'running_period_changed');
    }

    public function test_a_typed_spray_name_reaches_every_greenhouse_and_other_users_through_sync(): void
    {
        $manager = $this->account();
        $a = $this->twoVarieties($manager);
        $b = $this->context($manager);
        $other = $this->account('worker');
        $cursor = $this->asUser($other)->getJson('/api/v2/sync/changes?limit=100')->assertOk()->json('next_cursor');
        $op = $this->operation('activity', $this->spray($a, (string) Str::uuid(), $a['group']['id'], [['material_name' => 'Pestisida ketik', 'unit_id' => null, 'quantity' => null]]));
        $this->asUser($this->account('worker'))->postJson('/api/v2/activities', $op)->assertCreated();
        $this->postJson('/api/v2/activities', $op)->assertCreated();
        foreach ([$a, $b] as $ctx) {
            $this->assertSame(1, DB::table('materials')->where('greenhouse_id', $ctx['gh']['id'])->where('name', 'Pestisida ketik')->count());
        }
        $feed = $this->asUser($other)->getJson('/api/v2/sync/changes?'.http_build_query(['cursor' => $cursor, 'limit' => 100]))->assertOk()->json('data');
        $names = collect($feed)->where('entity_type', 'materials')->pluck('data.name');
        $this->assertSame(2, $names->filter(fn ($name) => $name === 'Pestisida ketik')->count());
        $this->assertSame(1, DB::table('audit_logs')->where('entity_type', 'materials')->where('entity_id', DB::table('materials')
            ->where('greenhouse_id', $b['gh']['id'])->where('name', 'Pestisida ketik')->value('id'))->count());
    }

    public function test_entry_rules_are_enforced_and_legacy_payloads_still_send(): void
    {
        $manager = $this->account();
        $ctx = $this->twoVarieties($manager);
        $batch = (string) Str::uuid();
        $reject = fn (array $payload, string $field) => $this->postJson('/api/v2/activities', $this->operation('activity', $payload))
            ->assertUnprocessable()->assertJsonValidationErrors([$field], 'field_errors');
        $refuse = fn (array $payload) => $this->postJson('/api/v2/activities', $this->operation('activity', $payload))->assertUnprocessable();

        $reject($this->entry($ctx, $batch, ['age_basis' => null, 'age_day' => null]), 'age_basis');
        $reject($this->entry($ctx, $batch, ['age_day' => null]), 'age_basis');
        $reject($this->entry($ctx, $batch, ['planting_group_id' => $ctx['group']['id']]), 'planting_group_id');
        $reject($this->entry($ctx, $batch, ['spray_period' => 'morning']), 'spray_date');
        $reject($this->spray($ctx, $batch, $ctx['group']['id'], [['material_name' => 'Bion M']], ['spray_period' => null]), 'spray_date');
        $refuse($this->spray($ctx, $batch, $ctx['group']['id'], [['material_name' => 'Bion M', 'unit_id' => $ctx['ml']['id'], 'quantity' => null]]));
        $refuse($this->entry($ctx, $batch, ['activity_type' => 'nutrition', 'manual_ec' => null, 'manual_ppm' => null, 'manual_ph' => null,
            'materials' => [['material_id' => $ctx['mix']['id'], 'unit_id' => null, 'quantity' => null]]]));
        $refuse($this->entry($ctx, $batch, ['activity_type' => 'nutrition', 'manual_ec' => null, 'manual_ppm' => null, 'manual_ph' => null,
            'materials' => [['material_name' => 'AB mix baru', 'unit_id' => $ctx['unit']['id'], 'quantity' => '1']]]));
        $refuse($this->entry($ctx, $batch, ['manual_ppm' => null]));
        $refuse($this->entry($ctx, $batch, ['season_id' => null]));
        $refuse($this->spray($ctx, $batch, $ctx['group']['id'], [['material_name' => 'Bion M']], ['season_id' => $this->context($manager)['season']['id']]));
        $this->assertSame(0, DB::table('activities')->count());

        // An outbox entry written before the update names only its group and has no typed age; it still sends.
        $legacy = $this->postJson('/api/v2/activities', $this->operation('activity', ['planting_group_id' => $ctx['group']['id'],
            'activity_type' => 'general', 'occurred_at' => '2026-11-16T08:00:00+07:00', 'work_state' => 'completed', 'materials' => [],
            'notes' => 'Draf lama']))->assertCreated()->assertJsonPath('data.season_id', $ctx['season']['id'])->assertJsonPath('data.age_basis', null)->json('data');
        $edit = $this->operation('activity', ['planting_group_id' => null, 'season_id' => $ctx['season']['id'], 'activity_type' => 'general',
            'occurred_at' => '2026-11-16T08:00:00+07:00', 'work_state' => 'completed', 'materials' => [], 'notes' => 'Pindah', 'reference_policy' => 'preserve'],
            'update', $legacy['id'], 1);
        $this->patchJson('/api/v2/activities/'.$legacy['id'], $edit)->assertUnprocessable();
    }

    public function test_worker_without_assignment_records_on_active_greenhouses_but_never_reaches_finance(): void
    {
        $manager = $this->account();
        $ctx = $this->twoVarieties($manager);
        $season = $ctx['season']['id'];
        $expense = $this->createMaster('expense_categories', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Biaya rahasia']);
        $worker = $this->account('worker');
        $this->assertSame(0, DB::table('greenhouse_assignments')->where('user_id', $worker->id)->count());
        $this->asUser($worker)->postJson('/api/v2/activities', $this->operation('activity', $this->entry($ctx, (string) Str::uuid())))->assertCreated();
        $id = (string) Str::uuid();
        foreach (['expenses', 'cost-allocations', 'sales', 'assets', 'depreciation-allocations', 'investment-scenarios', 'investment-reports',
            'season-reports', 'reports/cash-flow', 'season-reports/compare?season_ids[]='.$season, 'manager-home', 'expense-categories',
            'expenses/'.$id, 'expenses/'.$id.'/availability', 'sales/'.$id, 'assets/'.$id, 'investment-scenarios/'.$id,
            'report-exports/'.$id.'/pdf?expected_snapshot_hash='.str_repeat('a', 64)] as $path) {
            $status = $this->getJson('/api/v2/'.$path)->status();
            $this->assertContains($status, [403, 404], $path);
            $this->assertNotSame(200, $status, $path);
        }
        $feed = $this->getJson('/api/v2/sync/changes?limit=100')->assertOk()->json('data');
        $types = array_unique(array_column($feed, 'entity_type'));
        foreach (['expense', 'cost_allocation', 'sale', 'asset', 'depreciation_allocation', 'investment_scenario', 'season_report', 'expense_categories', 'greenhouse_assignments'] as $finance) {
            $this->assertNotContains($finance, $types, $finance);
        }
        $this->assertNotContains($expense['id'], array_column($feed, 'entity_id'));
    }

    public function test_spray_starting_list_is_seeded_once_per_greenhouse_by_creation_and_by_the_command(): void
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $this->assertSame(count(SprayMaterialCatalog::DEFAULTS), DB::table('materials')->where('greenhouse_id', $ctx['gh']['id'])->where('purpose', 'spray')->count());
        // A greenhouse inserted outside the API (e.g. before migration 22) gets the list from the command, idempotently.
        $old = (string) Str::uuid();
        DB::table('greenhouses')->insert(['id' => $old, 'name' => 'GH lama', 'created_by' => $manager->id, 'created_at' => now(), 'updated_at' => now()]);
        DB::table('entity_identities')->insert(['entity_id' => $old, 'entity_type' => 'greenhouses']);
        $this->assertSame(0, Artisan::call('inofarm:spray-materials', ['manager_email' => $manager->email]));
        $this->assertSame(0, Artisan::call('inofarm:spray-materials', ['manager_email' => strtoupper($manager->email)]));
        $this->assertSame(10, DB::table('materials')->where('greenhouse_id', $old)->where('purpose', 'spray')->count());
        $this->assertSame(10, DB::table('materials')->where('greenhouse_id', $ctx['gh']['id'])->where('purpose', 'spray')->count());
        $this->assertSame(10, DB::table('change_feed')->where('greenhouse_id', $old)->where('entity_type', 'materials')->count());
        $this->assertSame(1, Artisan::call('inofarm:spray-materials', ['manager_email' => $this->account('worker')->email]));
        // Every created material holds a reserved global identity (test accounts from User::create are not reserved).
        $this->assertSame(0, DB::table('materials')->whereNotIn('id', DB::table('entity_identities')->where('entity_type', 'materials')->select('entity_id'))->count());
    }
}
