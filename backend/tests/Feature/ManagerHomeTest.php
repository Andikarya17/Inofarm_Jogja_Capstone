<?php

namespace Tests\Feature;

use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\FinanceTestCase;

final class ManagerHomeTest extends FinanceTestCase
{
    private const HOME = '/api/v2/manager-home';

    private function designate(array $ctx, ?string $group, int $version): array
    {
        return $this->operation('greenhouses', ['planting_group_id' => $group, 'reason' => 'Review budidaya sintetis'],
            'designate_period', $ctx['gh']['id'], $version);
    }

    public function test_scope_and_data_driven_counts(): void
    {
        $manager = $this->account();
        $other = $this->context($this->account());
        $this->asUser($manager)->getJson(self::HOME)->assertOk()->assertJsonCount(0, 'greenhouses');
        $this->getJson(self::HOME.'?greenhouse_id='.$other['gh']['id'])->assertNotFound();
        foreach (range(1, 5) as $number) {
            $this->createMaster('greenhouses', ['name' => "Greenhouse $number"]);
            $this->getJson(self::HOME)->assertOk()->assertJsonCount($number, 'greenhouses')
                ->assertJsonPath('greenhouses.'.($number - 1).'.period.state', 'no_active_period');
        }
        $this->asUser($this->account('worker'))->getJson(self::HOME)->assertForbidden();
        DB::table('greenhouse_assignments')->where('user_id', $manager->id)->update(['active' => false]);
        $this->asUser($manager)->getJson(self::HOME)->assertOk()->assertJsonCount(0, 'greenhouses');
    }

    public function test_designation_is_explicit_versioned_and_separate_from_reopen(): void
    {
        $this->travelTo(CarbonImmutable::parse('2026-11-08T08:00:00+07:00'));
        $ctx = $this->context($this->account());
        $gh = $ctx['gh']['id'];
        $group = $ctx['group']['id'];
        $path = "/api/v2/greenhouses/$gh/running-period";
        $home = self::HOME."?greenhouse_id=$gh";
        $this->getJson($home)->assertOk()->assertJsonPath('greenhouses.0.period.state', 'needs_review')
            ->assertJsonPath('attention.0.type', 'period_review');
        $this->postJson($path, $this->designate($ctx, (string) Str::uuid(), 1))->assertUnprocessable();
        $op = $this->designate($ctx, $group, 1);
        $receipt = $this->postJson($path, $op)->assertOk()->assertJsonPath('version', 2)->json();
        $this->postJson($path, $op)->assertExactJson($receipt);
        $this->postJson($path, $this->designate($ctx, null, 1))->assertConflict();
        $this->getJson($home)->assertJsonPath('greenhouses.0.period.group_id', $group)
            ->assertJsonPath('greenhouses.0.period.age.hss', 39)->assertJsonPath('attention.0.type', 'measurement_missing_today');
        $season = $ctx['season']['id'];
        $close = $this->operation('seasons', ['reason' => 'Musim selesai'], 'close', $season, 1);
        $this->postJson("/api/v2/seasons/$season/close", $close)->assertUnprocessable();
        $this->postJson($path, $this->designate($ctx, null, 2))->assertOk();
        $this->postJson("/api/v2/seasons/$season/close", $close)->assertOk();
        $this->postJson("/api/v2/seasons/$season/reopen", $this->operation('seasons', ['reason' => 'Koreksi keuangan'], 'reopen', $season, 2))->assertOk();
        $this->getJson($home)->assertJsonPath('greenhouses.0.period.state', 'no_active_period')->assertJsonCount(0, 'attention');
        $this->getJson("/api/v2/greenhouses/$gh/audit")->assertOk()->assertJsonPath('data.2.action', 'designate_period');
        $this->postJson('/api/v2/activities', $this->operation('activity', [
            'planting_group_id' => $group, 'activity_type' => 'general', 'occurred_at' => '2026-11-08T08:00:00+07:00',
            'work_state' => 'completed', 'materials' => [], 'notes' => 'Draf periode sebelumnya', 'running_period_version' => 2,
        ]))->assertUnprocessable()->assertJsonPath('code', 'running_period_changed');
    }

    public function test_exact_cash_boundaries_and_recent_from_full_scope(): void
    {
        $this->travelTo(CarbonImmutable::parse('2026-11-08T08:00:00+07:00'));
        $ctx = $this->context($this->account());
        $gh = $ctx['gh']['id'];
        $group = $ctx['group']['id'];
        $this->postJson("/api/v2/greenhouses/$gh/running-period", $this->designate($ctx, $group, 1))->assertOk();
        $category = $this->createMaster('expense_categories', ['greenhouse_id' => $gh, 'name' => 'Kategori sintetis']);
        $payment = ['greenhouse_id' => $gh, 'category_id' => $category['id'], 'amount' => '1.01',
            'paid_at' => '2026-11-01T00:00:00+07:00', 'payment_kind' => 'operating', 'notes' => 'Pembayaran sintetis'];
        $allocated = $this->operation('expense', $payment);
        $this->postJson('/api/v2/expenses', $allocated)->assertCreated();
        for ($i = 1; $i < 52; $i++) {
            $this->postJson('/api/v2/expenses', $this->operation('expense', $payment))->assertCreated();
        }
        $this->postJson('/api/v2/cost-allocations', $this->operation('cost_allocation', [
            'expense_id' => $allocated['entity_id'], 'season_id' => $ctx['season']['id'], 'amount' => '0.50',
            'cost_class' => 'VC', 'allocation_basis' => 'Bagian biaya sintetis',
        ]))->assertCreated();
        $asset = $this->operation('expense', [...$payment, 'amount' => '90.10', 'payment_kind' => 'asset_acquisition']);
        $this->postJson('/api/v2/expenses', $asset)->assertCreated();
        foreach (['2026-10-31T23:59:59+07:00', '2026-12-01T00:00:00+07:00'] as $outside) {
            $this->postJson('/api/v2/expenses', $this->operation('expense', [...$payment, 'paid_at' => $outside]))->assertCreated();
        }
        $grade = $this->createMaster('grades', ['greenhouse_id' => $gh, 'name' => 'Grade sintetis']);
        $item = (string) Str::uuid();
        $harvest = $this->operation('harvest', ['planting_group_id' => $group, 'occurred_at' => '2026-11-07T12:00:00+07:00',
            'notes' => '', 'items' => [['id' => $item, 'grade_id' => $grade['id'], 'fruit_count' => 4, 'weight_kg' => '5']]]);
        $this->postJson('/api/v2/harvests', $harvest)->assertCreated();
        $sale = $this->operation('sale', ['season_id' => $ctx['season']['id'], 'paid_at' => '2026-11-07T15:00:00+07:00',
            'payment_status' => 'paid', 'customer_name' => '', 'notes' => '', 'items' => [
                ['id' => (string) Str::uuid(), 'harvest_item_id' => $item, 'quantity_kg' => '2', 'price_per_kg' => '32.10'],
            ]]);
        $this->postJson('/api/v2/sales', $sale)->assertCreated();
        $measure = $this->operation('activity', ['planting_group_id' => $group, 'activity_type' => 'measurement',
            'occurred_at' => '2026-11-08T00:00:00+07:00', 'manual_ec' => '1.5', 'manual_ppm' => '750',
            'work_state' => 'completed', 'materials' => [], 'notes' => '', 'running_period_version' => 2]);
        $this->postJson('/api/v2/activities', $measure)->assertCreated();
        $home = self::HOME."?greenhouse_id=$gh";
        $this->getJson($home)->assertOk()->assertJsonPath('cash.payments.records', 53)->assertJsonPath('cash.payments.amount', '142.62')
            ->assertJsonPath('cash.receipts.amount', '64.20')->assertJsonPath('detail.harvest.records', 1)
            ->assertJsonPath('recent.0.id', $measure['entity_id'])->assertJsonPath('recent.0.recorded_by_name', 'Akun sintetis')
            ->assertJsonPath('recent.1.id', $harvest['entity_id'])->assertJsonPath('detail.measurement.manual_ec', '1.500000')
            ->assertJsonCount(0, 'attention');
        $this->postJson('/api/v2/expenses/'.$asset['entity_id'].'/cancel',
            $this->operation('expense', ['reason' => 'Koreksi sintetis'], 'cancel', $asset['entity_id'], 1))->assertOk();
        $this->getJson($home)->assertJsonPath('cash.payments.amount', '52.52');
    }

    public function test_attention_uses_versioned_comparator_and_rolls_over_at_jakarta_midnight(): void
    {
        $this->travelTo(CarbonImmutable::parse('2026-11-08T23:59:00+07:00'));
        $ctx = $this->context($this->account());
        $gh = $ctx['gh']['id'];
        $group = $ctx['group']['id'];
        $this->postJson("/api/v2/greenhouses/$gh/running-period", $this->designate($ctx, $group, 1))->assertOk();
        $target = $this->createMaster('targets', ['planting_group_id' => $group, 'activity_type' => 'general', 'parameter' => 'manual_ppm',
            'unit' => 'ppm', 'target_value' => null, 'minimum' => '500', 'maximum' => '650', 'tolerance' => null,
            'effective_from' => '2026-11-01T00:00:00+07:00', 'effective_until' => null]);
        $measure = $this->operation('activity', ['planting_group_id' => $group, 'activity_type' => 'general',
            'occurred_at' => '2026-11-08T23:00:00+07:00', 'manual_ec' => '1.5', 'manual_ppm' => '750',
            'work_state' => 'completed', 'materials' => [], 'notes' => 'Catatan ukur sintetis']);
        $this->postJson('/api/v2/activities', $measure)->assertCreated();
        $home = self::HOME."?greenhouse_id=$gh";
        $this->getJson($home)->assertJsonPath('attention.0.type', 'measurement_outside_range')
            ->assertJsonPath('attention.0.source_record_id', $measure['entity_id'])->assertJsonPath('attention.0.target.id', $target['id'])
            ->assertJsonPath('attention.0.target.version', 1);
        $this->travelTo(CarbonImmutable::parse('2026-11-09T00:00:00+07:00'));
        $this->getJson($home)->assertJsonPath('day', '2026-11-09')->assertJsonPath('attention.0.type', 'measurement_missing_today')
            ->assertJsonPath('detail.measurement.id', $measure['entity_id']);
        DB::table('audit_logs')->where('entity_id', $measure['entity_id'])->delete();
        $this->getJson($home)->assertOk()->assertJsonPath('coverage', 'partial')
            ->assertJsonPath('detail.measurement_available', false)->assertJsonPath('recent', null)
            ->assertJsonPath('cash.payments.amount', '0.00')->assertJsonCount(0, 'attention');
    }
}
