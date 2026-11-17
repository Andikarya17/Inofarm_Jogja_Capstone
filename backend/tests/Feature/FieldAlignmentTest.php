<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;

/** Backend changes from the Inofarm field answers of 15 Nov 2026. */
class FieldAlignmentTest extends FinanceTestCase
{
    private function expense(array $ctx, string $amount, string $paidAt = '2026-10-29T00:30:00+07:00'): array
    {
        $category = $this->createMaster('expense_categories', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'Listrik '.Str::random(4)]);
        $op = $this->operation('expense', ['greenhouse_id' => $ctx['gh']['id'], 'category_id' => $category['id'], 'amount' => $amount,
            'paid_at' => $paidAt, 'payment_kind' => 'operating', 'notes' => 'Listrik satu meteran']);
        $this->postJson('/api/v2/expenses', $op)->assertCreated();

        return $op;
    }

    private function allocation(string $expense, string $season, string $amount): array
    {
        return $this->operation('cost_allocation', ['expense_id' => $expense, 'season_id' => $season, 'amount' => $amount,
            'cost_class' => 'VC', 'allocation_basis' => 'Dibagi rata antar greenhouse']);
    }

    private function harvest(array $ctx, array $item): array
    {
        $grade = $this->createMaster('grades', ['greenhouse_id' => $ctx['gh']['id'], 'name' => 'A']);
        $op = $this->operation('harvest', ['planting_group_id' => $ctx['group']['id'], 'occurred_at' => '2026-10-30T08:00:00+07:00', 'notes' => 'Panen raya',
            'items' => [['id' => (string) Str::uuid(), 'grade_id' => $grade['id'], ...$item]]]);
        $this->postJson('/api/v2/harvests', $op)->assertCreated();

        return $op;
    }

    public function test_shared_expense_is_split_evenly_across_greenhouses_moved_and_counted_in_the_other_season(): void
    {
        $manager = $this->account();
        $a = $this->context($manager);
        $b = $this->context($manager);
        $c = $this->context($manager);
        $expense = $this->expense($a, '100.01');
        $seasons = [$a['season']['id'], $b['season']['id'], $c['season']['id']];
        $split = $this->getJson('/api/v2/expenses/'.$expense['entity_id'].'/availability?'.http_build_query(['split_season_ids' => $seasons]))
            ->assertOk()->json('data.even_split');
        $this->assertSame(['33.34', '33.34', '33.33'], array_column($split, 'amount'));
        $this->assertSame($seasons, array_column($split, 'season_id'));
        $this->getJson('/api/v2/expenses/'.$expense['entity_id'].'/availability?'.http_build_query(['split_season_ids' => [(string) Str::uuid()]]))->assertNotFound();

        $toB = $this->allocation($expense['entity_id'], $b['season']['id'], '33.34');
        $this->postJson('/api/v2/cost-allocations', $toB)->assertCreated()->assertJsonPath('data.season_id', $b['season']['id']);
        $preview = ['coverage' => ['costs' => 'partial', 'sales' => 'partial'], 'known_pending_count' => null, 'requested_state' => 'provisional', 'review_note' => 'Cek'];
        $this->postJson('/api/v2/season-reports/preview', ['season_id' => $b['season']['id'], ...$preview])->assertOk()->assertJsonPath('source_counts.VC', 1);

        // "Dipindah": a wrongly booked allocation moves to another season in one versioned update.
        $move = $this->operation('cost_allocation', [...$toB['payload'], 'season_id' => $c['season']['id']], 'update', $toB['entity_id'], 1);
        $this->patchJson('/api/v2/cost-allocations/'.$toB['entity_id'], $move)->assertOk()
            ->assertJsonPath('data.season_id', $c['season']['id'])->assertJsonPath('data.season_name', 'Musim sintetis')->assertJsonPath('data.version', 2);
        $this->postJson('/api/v2/season-reports/preview', ['season_id' => $b['season']['id'], ...$preview])->assertOk()->assertJsonPath('source_counts.VC', 0);
        $this->getJson('/api/v2/cost-allocations/'.$toB['entity_id'].'/audit')->assertJsonPath('data.1.changes.before.season_id', $b['season']['id']);

        $this->postJson('/api/v2/seasons/'.$b['season']['id'].'/close', $this->operation('seasons', [], 'close', $b['season']['id'], 1))->assertOk();
        $back = $this->operation('cost_allocation', [...$toB['payload'], 'season_id' => $b['season']['id']], 'update', $toB['entity_id'], 2);
        $this->patchJson('/api/v2/cost-allocations/'.$toB['entity_id'], $back)->assertConflict()->assertJsonPath('code', 'season_closed');
    }

    public function test_weighed_only_harvest_and_kg_target_keep_unknown_counts_null(): void
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $group = $ctx['group'];
        $this->patchJson('/api/v2/planting-groups/'.$group['id'], $this->operation('planting_groups', ['name' => $group['name'], 'variety_id' => $group['variety_id'],
            'population' => 100, 'sowing_date' => '2026-10-01', 'transplant_date' => '2026-10-10', 'active' => true,
            'harvest_target' => ['fruit_count' => null, 'weight_kg' => '10', 'basis' => 'Diskusi pengurus']], 'update', $group['id'], 1))
            ->assertOk()->assertJsonPath('data.harvest_target.fruit_count', null);
        $this->harvest($ctx, ['fruit_count' => null, 'weight_kg' => '4', 'quality' => 'saleable']);
        $this->getJson('/api/v2/planting-groups/'.$group['id'].'/harvest-progress')->assertOk()
            ->assertJsonPath('totals.saleable.fruit_count', null)->assertJsonPath('totals.saleable.weight_kg', '4.000000')
            ->assertJsonPath('achievement_percent', null)->assertJsonPath('weight_achievement_percent', '40.000000');
        $this->getJson('/api/v2/manager-home?greenhouse_id='.$ctx['gh']['id'])->assertOk()->assertJsonPath('detail.harvest.fruit_count', null);
        $missing = $this->operation('harvest', ['planting_group_id' => $group['id'], 'occurred_at' => '2026-10-30T09:00:00+07:00', 'notes' => '',
            'items' => [['id' => (string) Str::uuid(), 'grade_id' => (string) Str::uuid(), 'weight_kg' => '1']]]);
        $errors = $this->postJson('/api/v2/harvests', $missing)->assertUnprocessable()->json('field_errors');
        $this->assertStringContainsString('fruit_count', implode(' ', array_keys($errors)));
    }

    public function test_season_closes_only_when_sold_out_or_with_a_note_and_sale_keeps_payment_trace(): void
    {
        $manager = $this->account();
        $ctx = $this->context($manager);
        $harvest = $this->harvest($ctx, ['fruit_count' => null, 'weight_kg' => '5']);
        $sale = $this->operation('sale', ['season_id' => $ctx['season']['id'], 'paid_at' => '2026-10-30T09:00:00+07:00', 'payment_status' => 'paid',
            'customer_name' => 'Pengepul', 'notes' => '', 'payment_method' => 'qris', 'payment_reference' => 'QRIS-0001', 'customer_type' => 'partner',
            'items' => [['id' => (string) Str::uuid(), 'harvest_item_id' => $harvest['payload']['items'][0]['id'], 'quantity_kg' => '3', 'price_per_kg' => '20000']]]);
        $this->postJson('/api/v2/sales', $sale)->assertCreated()->assertJsonPath('data.payment_reference', 'QRIS-0001');
        $older = $sale['payload'];
        unset($older['payment_method'], $older['payment_reference'], $older['customer_type']);
        $this->patchJson('/api/v2/sales/'.$sale['entity_id'], $this->operation('sale', [...$older, 'notes' => 'Koreksi catatan'], 'update', $sale['entity_id'], 1))
            ->assertOk()->assertJsonPath('data.payment_method', 'qris')->assertJsonPath('data.customer_type', 'partner');

        $close = fn (array $payload) => $this->postJson('/api/v2/seasons/'.$ctx['season']['id'].'/close', $this->operation('seasons', $payload, 'close', $ctx['season']['id'], 1));
        $close([])->assertUnprocessable()->assertJsonPath('code', 'season_unsold_balance');
        $close(['reason' => '   '])->assertUnprocessable()->assertJsonPath('code', 'season_unsold_balance');
        $close(['reason' => 'Sisa 2 kg gagal jual'])->assertOk()->assertJsonPath('data.status', 'closed');
    }

    public function test_skip_reason_and_measurement_moment_follow_field_practice(): void
    {
        $ctx = $this->context($this->account());
        $base = ['planting_group_id' => $ctx['group']['id'], 'schedule_id' => null, 'occurred_at' => '2026-10-29T08:00:00+07:00', 'manual_ph' => null,
            'actual_stage' => null, 'materials' => [], 'notes' => ''];
        $this->postJson('/api/v2/activities', $this->operation('activity', [...$base, 'activity_type' => 'measurement', 'manual_ec' => '2.1', 'manual_ppm' => '1050',
            'work_state' => 'completed', 'skipped_reason' => null, 'observations' => ['measurement_moment' => 'after_injection', 'water_temperature_c' => '27']]))
            ->assertCreated()->assertJsonPath('data.observations.measurement_moment', 'after_injection');
        $this->postJson('/api/v2/activities', $this->operation('activity', [...$base, 'activity_type' => 'measurement', 'manual_ec' => '2', 'manual_ppm' => '1000',
            'work_state' => 'completed', 'skipped_reason' => null, 'observations' => ['measurement_moment' => 'noon']]))->assertUnprocessable();
        $this->postJson('/api/v2/activities', $this->operation('activity', [...$base, 'activity_type' => 'nutrition', 'manual_ppm' => null,
            'work_state' => 'skipped', 'skipped_reason' => null]))->assertUnprocessable()->assertJsonPath('field_errors.skipped_reason.0', 'Pemberian nutrisi yang dilewati wajib diberi alasan.');
    }

    public function test_cash_flow_comparison_material_usage_and_master_copy(): void
    {
        $manager = $this->account();
        $a = $this->context($manager);
        $b = $this->context($manager);
        $this->expense($a, '150000');
        $this->expense($b, '50000.50', '2026-11-01T03:00:00+07:00');
        $harvest = $this->harvest($a, ['fruit_count' => 4, 'weight_kg' => '6']);
        $this->postJson('/api/v2/sales', $this->operation('sale', ['season_id' => $a['season']['id'], 'paid_at' => '2026-10-30T09:00:00+07:00', 'payment_status' => 'paid',
            'customer_name' => '', 'notes' => '', 'items' => [['id' => (string) Str::uuid(), 'harvest_item_id' => $harvest['payload']['items'][0]['id'],
                'quantity_kg' => '5', 'price_per_kg' => '30000']]]))->assertCreated();

        $flow = $this->getJson('/api/v2/reports/cash-flow?from=2026-10&until=2026-11')->assertOk()->json('data');
        $this->assertSame(['2026-10', '2026-11'], array_column($flow['months'], 'month'));
        $this->assertSame(['150000.00', '50000.50'], array_column($flow['months'], 'payments'));
        $this->assertSame(['150000.00', '0.00'], array_column($flow['months'], 'receipts'));
        $this->assertSame(['receipts' => '150000.00', 'payments' => '200000.50', 'net' => '-50000.50'], $flow['totals']);
        $this->assertSame([$b['gh']['id']], array_column($flow['months'][1]['greenhouses'], 'greenhouse_id'));
        $this->getJson('/api/v2/reports/cash-flow?from=2026-10&until=2026-11&greenhouse_id='.$b['gh']['id'])->assertJsonPath('data.totals.payments', '50000.50');
        $csv = $this->get('/api/v2/reports/cash-flow?from=2026-10&until=2026-10&format=csv')->assertOk()->assertHeader('Content-Type', 'text/csv; charset=UTF-8')->getContent();
        $this->assertStringContainsString("2026-10,\"Semua greenhouse\",150000.00,150000.00,0.00\r\n", $csv);
        $this->getJson('/api/v2/reports/cash-flow?from=2026-11&until=2026-10')->assertUnprocessable();

        $compare = $this->getJson('/api/v2/season-reports/compare?'.http_build_query(['season_ids' => [$a['season']['id'], $b['season']['id']]]))->assertOk()->json('data');
        $this->assertSame(['6.000000', '0.000000'], array_column($compare, 'harvest_weight_kg'));
        $this->assertSame(['1.000000', '0.000000'], array_column($compare, 'unsold_weight_kg'));
        $this->assertSame([$a['gh']['id'], $b['gh']['id']], array_column($compare, 'greenhouse_id'));
        $this->assertArrayHasKey('RC', $compare[0]['metrics']);

        $unit = $this->createMaster('units', ['greenhouse_id' => $a['gh']['id'], 'name' => 'Paket 100 L', 'dimension' => 'volume']);
        $mix = $this->createMaster('materials', ['greenhouse_id' => $a['gh']['id'], 'name' => 'AB mix LAN', 'dimension' => 'volume']);
        foreach (['1', '0.5'] as $quantity) {
            $this->postJson('/api/v2/activities', $this->operation('activity', ['planting_group_id' => $a['group']['id'], 'schedule_id' => null, 'activity_type' => 'nutrition',
                'occurred_at' => '2026-10-29T08:00:00+07:00', 'manual_ppm' => '1000', 'manual_ph' => '6', 'actual_stage' => null, 'work_state' => 'completed',
                'skipped_reason' => null, 'materials' => [['material_id' => $mix['id'], 'unit_id' => $unit['id'], 'quantity' => $quantity]], 'notes' => '']))->assertCreated();
        }
        $this->getJson('/api/v2/seasons/'.$a['season']['id'].'/material-usage')->assertOk()->assertJsonCount(1, 'data.items')
            ->assertJsonPath('data.items.0.material_name', 'AB mix LAN')->assertJsonPath('data.items.0.quantity', '1.500000')->assertJsonPath('data.items.0.activities', 2);

        $copy = fn () => $this->postJson('/api/v2/greenhouses/'.$b['gh']['id'].'/copy-masters', ['source_greenhouse_id' => $a['gh']['id']])->assertOk();
        // Both greenhouses already have "Varietas sintetis"; units/materials/grades are new for B.
        $copy()->assertJsonPath('data.copied.units', 1)->assertJsonPath('data.copied.materials', 1)->assertJsonPath('data.copied.varieties', 0);
        $copy()->assertJsonPath('data.copied', ['varieties' => 0, 'units' => 0, 'materials' => 0, 'grades' => 0, 'expense_categories' => 0]);
        $copiedNames = array_column($this->getJson('/api/v2/materials?greenhouse_id='.$b['gh']['id'].'&limit=100')->json('data'), 'name');
        $this->assertContains('AB mix LAN', $copiedNames);
        $this->postJson('/api/v2/greenhouses/'.$b['gh']['id'].'/copy-masters', ['source_greenhouse_id' => $b['gh']['id']])->assertUnprocessable();

        $worker = $this->account('worker');
        $this->asUser($worker)->getJson('/api/v2/reports/cash-flow?from=2026-10&until=2026-10')->assertForbidden();
        // Material usage is a field report, not finance; every worker now reaches active greenhouses (17 Nov 2026).
        $this->getJson('/api/v2/seasons/'.$a['season']['id'].'/material-usage')->assertOk();
        DB::table('greenhouses')->where('id', $a['gh']['id'])->update(['active' => false]);
        $this->getJson('/api/v2/seasons/'.$a['season']['id'].'/material-usage')->assertNotFound();
    }
}
