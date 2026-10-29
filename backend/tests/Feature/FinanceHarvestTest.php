<?php

namespace Tests\Feature;

use Illuminate\Database\Events\QueryExecuted;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use RuntimeException;
use Tests\FinanceTestCase;

class FinanceHarvestTest extends FinanceTestCase
{
    private function prepared(): array
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $grades = [];
        foreach (['A sintetis', 'B sintetis'] as $name) {
            $grades[] = $this->createMaster('grades', ['greenhouse_id' => $ctx['gh']['id'], 'name' => $name]);
        }
        $worker = $this->account('worker');
        $this->createMaster('greenhouse_assignments', ['greenhouse_id' => $ctx['gh']['id'], 'user_id' => $worker->public_id,
            'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);

        return [...$ctx, 'manager' => $manager, 'worker' => $worker, 'grades' => $grades];
    }

    private function payload(array $ctx): array
    {
        return ['planting_group_id' => $ctx['group']['id'], 'occurred_at' => '2026-10-29T08:12:34+07:00', 'notes' => 'Panen dua grade sintetis',
            'items' => [['id' => (string) Str::uuid(), 'grade_id' => $ctx['grades'][0]['id'], 'fruit_count' => 10, 'weight_kg' => '15.25'],
                ['id' => (string) Str::uuid(), 'grade_id' => $ctx['grades'][1]['id'], 'fruit_count' => 0, 'weight_kg' => '0']]];
    }

    private function correction(array $payload): array
    {
        return [...$payload, 'reason' => 'Koreksi panen sintetis', 'reference_policy' => 'preserve'];
    }

    public function test_multigrade_worker_receipt_balance_feed_and_every_read_are_scoped(): void
    {
        $ctx = $this->prepared();
        $op = $this->operation('harvest', $this->payload($ctx));
        $receipt = $this->asUser($ctx['worker'])->postJson('/api/v2/harvests', $op)->assertCreated()->assertJsonPath('data.recorded_by', $ctx['worker']->public_id)
            ->assertJsonCount(2, 'data.items')->assertJsonPath('data.reference_snapshot.age.hss', 29)->json();
        $this->postJson('/api/v2/harvests', $op)->assertCreated()->assertExactJson($receipt);
        $this->assertDatabaseCount('harvests', 1);
        $this->assertDatabaseCount('harvest_items', 2);
        $this->assertDatabaseCount('harvest_reference_snapshots', 1);
        $this->assertSame(1, DB::table('audit_logs')->where('entity_type', 'harvest')->count());
        $this->assertSame(2, DB::table('change_feed')->where('entity_type', 'harvest_balance')->count());
        $availability = $this->getJson('/api/v2/harvests/'.$op['entity_id'].'/availability')->assertOk()->json('data');
        $this->assertSame('15.250000', collect($availability)->firstWhere('id', $op['payload']['items'][0]['id'])['available_weight_kg']);
        $feed = $this->getJson('/api/v2/sync/changes')->assertOk()->json('data');
        $this->assertCount(2, array_filter($feed, fn ($row) => $row['entity_type'] === 'harvest_balance'));
        $this->asUser($ctx['manager'])->getJson('/api/v2/harvests?from=2026-10-29&until=2026-10-29')->assertJsonCount(1, 'data');
        $this->getJson('/api/v2/harvests/'.$op['entity_id'].'/audit')->assertJsonPath('data.0.actor_id', $ctx['worker']->public_id);
        $this->context($this->account());
        foreach (['', '/audit', '/availability'] as $suffix) {
            $this->getJson('/api/v2/harvests/'.$op['entity_id'].$suffix)->assertNotFound();
        }
        $this->getJson('/api/v2/harvests')->assertJsonCount(0, 'data');
        $this->postJson('/api/v2/harvests', $this->operation('harvest', $op['payload']))->assertNotFound();
    }

    public function test_invalid_missing_float_negative_duplicate_and_foreign_grade_never_become_ready(): void
    {
        $ctx = $this->prepared();
        $payload = $this->payload($ctx);
        foreach (['NaN', '1e2', '01', '-0.000001', '1.0000001', 1.5, null] as $invalid) {
            $p = $payload;
            $p['items'][0]['weight_kg'] = $invalid;
            $this->postJson('/api/v2/harvests', $this->operation('harvest', $p))->assertUnprocessable();
        }
        foreach ([-1, '2', 1.5, true, 2147483648, null] as $invalid) {
            $p = $payload;
            $p['items'][0]['fruit_count'] = $invalid;
            $this->postJson('/api/v2/harvests', $this->operation('harvest', $p))->assertUnprocessable();
        }
        foreach (['id', 'grade_id'] as $key) {
            $p = $payload;
            $p['items'][1][$key] = $p['items'][0][$key];
            $this->postJson('/api/v2/harvests', $this->operation('harvest', $p))->assertUnprocessable();
        }
        $p = $payload;
        $p['items'][0]['sold_weight_kg'] = '0';
        $this->postJson('/api/v2/harvests', $this->operation('harvest', $p))->assertUnprocessable();
        $this->postJson('/api/v2/harvests', $this->operation('harvest', [...$payload, 'items' => []]))->assertUnprocessable();
        $this->postJson('/api/v2/harvests', $this->operation('harvest', [...$payload, 'occurred_at' => '2026-09-30T08:00:00+07:00']))->assertUnprocessable();
        $foreign = $this->context($this->account());
        $grade = $this->createMaster('grades', ['greenhouse_id' => $foreign['gh']['id'], 'name' => 'Grade asing sintetis']);
        $this->asUser($ctx['manager']);
        $p = $payload;
        $p['items'][0]['grade_id'] = $grade['id'];
        $this->postJson('/api/v2/harvests', $this->operation('harvest', $p))->assertUnprocessable();
        $this->assertDatabaseCount('harvests', 0);
    }

    public function test_own_correction_snapshots_grade_history_conflict_cancel_and_closed_replay(): void
    {
        $ctx = $this->prepared();
        $payload = $this->payload($ctx);
        $op = $this->operation('harvest', $payload);
        $created = $this->asUser($ctx['worker'])->postJson('/api/v2/harvests', $op)->assertCreated()->json();
        $other = $this->account('worker');
        $this->asUser($ctx['manager'])->postJson('/api/v2/assignments', $this->operation('greenhouse_assignments', ['greenhouse_id' => $ctx['gh']['id'], 'user_id' => $other->public_id, 'effective_from' => now()->subDay()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]))->assertCreated();
        $edit = $this->operation('harvest', $this->correction($payload), 'update', $op['entity_id'], 1);
        $this->asUser($other)->patchJson('/api/v2/harvests/'.$op['entity_id'], $edit)->assertForbidden();
        $this->asUser($ctx['manager'])->patchJson('/api/v2/grades/'.$ctx['grades'][0]['id'], $this->operation('grades', ['name' => 'Grade berganti nama', 'active' => false, 'reason' => 'Uji histori'], 'update', $ctx['grades'][0]['id'], 1))->assertOk();
        $edited = $this->asUser($ctx['worker'])->patchJson('/api/v2/harvests/'.$op['entity_id'], $edit)->assertOk()->assertJsonPath('version', 2)->json('data');
        $this->assertSame('A sintetis', collect($edited['items'])->firstWhere('id', $payload['items'][0]['id'])['grade_name']);
        $this->patchJson('/api/v2/harvests/'.$op['entity_id'], $this->operation('harvest', $edit['payload'], 'update', $op['entity_id'], 1))->assertConflict()->assertJsonPath('current_version', 2);
        $retime = [...$edit['payload'], 'occurred_at' => '2026-10-30T08:12:34+07:00'];
        $this->patchJson('/api/v2/harvests/'.$op['entity_id'], $this->operation('harvest', $retime, 'update', $op['entity_id'], 2))->assertUnprocessable();
        $retime['reference_policy'] = 'refresh';
        $this->patchJson('/api/v2/harvests/'.$op['entity_id'], $this->operation('harvest', $retime, 'update', $op['entity_id'], 2))->assertOk()->assertJsonPath('data.reference_snapshot.age.hss', 30);
        $cancel = $this->operation('harvest', ['reason' => 'Panen salah catat sintetis'], 'cancel', $op['entity_id'], 3);
        $this->postJson('/api/v2/harvests/'.$op['entity_id'].'/cancel', $cancel)->assertForbidden();
        $receipt = $this->asUser($ctx['manager'])->postJson('/api/v2/harvests/'.$op['entity_id'].'/cancel', $cancel)->assertOk()->assertJsonPath('data.record_state', 'cancelled')->assertJsonCount(2, 'data.items')->json();
        $this->postJson('/api/v2/harvests/'.$op['entity_id'].'/cancel', $cancel)->assertOk()->assertExactJson($receipt);
        $this->assertDatabaseCount('harvest_reference_snapshots', 4);
        $this->getJson('/api/v2/harvests?record_state=cancelled')->assertJsonCount(1, 'data');
        foreach ($this->getJson('/api/v2/harvests/'.$op['entity_id'].'/availability')->json('data') as $available) {
            $this->assertSame('0.000000', $available['available_weight_kg']);
        }
        $this->postJson('/api/v2/seasons/'.$ctx['season']['id'].'/close', $this->operation('seasons', ['reason' => 'Tutup sintetis'], 'close', $ctx['season']['id'], 1))->assertOk();
        $this->asUser($ctx['worker'])->postJson('/api/v2/harvests', $op)->assertCreated()->assertExactJson($created);
        $this->postJson('/api/v2/harvests', $this->operation('harvest', $payload))->assertConflict()->assertJsonPath('code', 'season_closed');
    }

    public function test_future_used_balance_cannot_be_undercut_or_cancelled_and_removed_unused_items_remain_historical(): void
    {
        $ctx = $this->prepared();
        $payload = $this->payload($ctx);
        $op = $this->operation('harvest', $payload);
        $this->postJson('/api/v2/harvests', $op)->assertCreated();
        // Deliberately synthetic usage: proves the FIN08 guard, not an implemented sale.
        DB::table('harvest_items')->where('id', $payload['items'][0]['id'])->update(['sold_weight_kg' => '5', 'balance_version' => 2]);
        $tooSmall = $this->correction($payload);
        $tooSmall['items'][0]['weight_kg'] = '4.999999';
        $this->patchJson('/api/v2/harvests/'.$op['entity_id'], $this->operation('harvest', $tooSmall, 'update', $op['entity_id'], 1))->assertConflict()->assertJsonPath('code', 'harvest_in_use');
        $this->postJson('/api/v2/harvests/'.$op['entity_id'].'/cancel', $this->operation('harvest', ['reason' => 'Uji saldo'], 'cancel', $op['entity_id'], 1))->assertConflict()->assertJsonPath('code', 'harvest_in_use');
        $reduced = $this->correction($payload);
        $reduced['items'] = [$reduced['items'][0]];
        $this->patchJson('/api/v2/harvests/'.$op['entity_id'], $this->operation('harvest', $reduced, 'update', $op['entity_id'], 1))->assertOk()->assertJsonCount(1, 'data.items');
        $this->assertDatabaseHas('harvest_items', ['id' => $payload['items'][1]['id'], 'active' => false]);
        $this->assertDatabaseCount('harvest_items', 2);
        $available = collect($this->getJson('/api/v2/harvests/'.$op['entity_id'].'/availability')->json('data'))->firstWhere('id', $payload['items'][0]['id']);
        $this->assertSame('10.250000', $available['available_weight_kg']);
    }

    public function test_fault_after_receipt_sql_rolls_back_header_items_snapshots_feed_audit_and_receipt(): void
    {
        $ctx = $this->prepared();
        $op = $this->operation('harvest', $this->payload($ctx));
        $before = DB::table('change_feed')->count();
        $injected = false;
        DB::listen(function (QueryExecuted $event) use (&$injected) {
            if (! $injected && str_starts_with($event->sql, 'insert into `sync_operations`')) {
                $injected = true;
                throw new RuntimeException('Synthetic harvest post-receipt fault');
            }
        });
        $this->postJson('/api/v2/harvests', $op)->assertStatus(500)->assertJsonPath('code', 'server_error');
        $this->assertTrue($injected);
        foreach (['harvests', 'harvest_items', 'harvest_reference_snapshots'] as $table) {
            $this->assertDatabaseCount($table, 0);
        }
        $this->assertSame($before, DB::table('change_feed')->count());
        $this->assertSame(0, DB::table('sync_operations')->where('operation_id', $op['operation_id'])->count());
        $this->assertSame(0, DB::table('audit_logs')->where('operation_id', $op['operation_id'])->count());
        $this->postJson('/api/v2/harvests', $op)->assertCreated();
    }
}
