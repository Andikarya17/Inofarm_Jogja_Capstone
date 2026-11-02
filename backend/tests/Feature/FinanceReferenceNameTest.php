<?php

namespace Tests\Feature;

use Illuminate\Support\Str;
use Tests\FinanceTestCase;

class FinanceReferenceNameTest extends FinanceTestCase
{
    public function test_full_master_names_survive_operational_financial_snapshots_and_later_renames(): void
    {
        $ctx = $this->context($this->account());
        $name = str_repeat('é', 150);
        $renamed = str_repeat('中', 150);
        $grade = $this->createMaster('grades', ['greenhouse_id' => $ctx['gh']['id'], 'name' => $name]);
        $category = $this->createMaster('expense_categories', ['greenhouse_id' => $ctx['gh']['id'], 'name' => $name]);
        $season = [...$ctx['season'], 'name' => $name];
        $seasonPayload = ['name' => $name, 'start_date' => '2026-10-01', 'end_date' => null, 'reason' => 'Nama lengkap sintetis'];
        $this->patchJson('/api/v2/seasons/'.$season['id'], $this->operation('seasons', $seasonPayload, 'update', $season['id'], 1))->assertOk();
        $harvest = $this->operation('harvest', ['planting_group_id' => $ctx['group']['id'], 'occurred_at' => '2026-10-30T08:00:00+07:00', 'notes' => '',
            'items' => [['id' => (string) Str::uuid(), 'grade_id' => $grade['id'], 'fruit_count' => 1, 'weight_kg' => '1']]]);
        $this->postJson('/api/v2/harvests', $harvest)->assertCreated()->assertJsonPath('data.items.0.grade_name', $name);
        $expense = $this->operation('expense', ['greenhouse_id' => $ctx['gh']['id'], 'category_id' => $category['id'], 'paid_at' => '2026-10-30T08:00:00+07:00',
            'amount' => '1', 'payment_kind' => 'operating', 'notes' => '']);
        $this->postJson('/api/v2/expenses', $expense)->assertCreated()->assertJsonPath('data.category_name', $name);
        $allocation = $this->operation('cost_allocation', ['expense_id' => $expense['entity_id'], 'season_id' => $season['id'], 'amount' => '1', 'cost_class' => 'VC', 'allocation_basis' => 'Nominal eksplisit sintetis']);
        $this->postJson('/api/v2/cost-allocations', $allocation)->assertCreated()->assertJsonPath('data.season_name', $name);
        foreach (['grades' => $grade, 'expense-categories' => $category] as $path => $row) {
            $kind = $path === 'grades' ? 'grades' : 'expense_categories';
            $this->patchJson('/api/v2/'.$path.'/'.$row['id'], $this->operation($kind, ['name' => $renamed, 'active' => true, 'reason' => 'Ganti nama sintetis'], 'update', $row['id'], 1))->assertOk();
        }
        $this->patchJson('/api/v2/seasons/'.$season['id'], $this->operation('seasons', [...$seasonPayload, 'name' => $renamed], 'update', $season['id'], 2))->assertOk();
        $this->patchJson('/api/v2/harvests/'.$harvest['entity_id'], $this->operation('harvest', [...$harvest['payload'], 'reason' => 'Koreksi mempertahankan histori', 'reference_policy' => 'preserve'], 'update', $harvest['entity_id'], 1))
            ->assertOk()->assertJsonPath('data.items.0.grade_name', $name);
        $this->patchJson('/api/v2/expenses/'.$expense['entity_id'], $this->operation('expense', [...$expense['payload'], 'reason' => 'Koreksi mempertahankan histori'], 'update', $expense['entity_id'], 1))
            ->assertOk()->assertJsonPath('data.category_name', $name);
        $this->patchJson('/api/v2/cost-allocations/'.$allocation['entity_id'], $this->operation('cost_allocation', [...$allocation['payload'], 'reason' => 'Koreksi mempertahankan histori'], 'update', $allocation['entity_id'], 1))
            ->assertOk()->assertJsonPath('data.season_name', $name);
        foreach (['harvests' => [$harvest, 'items.0.grade_name'], 'expenses' => [$expense, 'category_name'], 'cost-allocations' => [$allocation, 'season_name']] as $path => [$op, $field]) {
            $this->getJson('/api/v2/'.$path.'/'.$op['entity_id'])->assertOk()->assertJsonPath($field, $name);
            $this->getJson('/api/v2/'.$path.'/'.$op['entity_id'].'/audit')->assertOk()->assertJsonPath('data.0.changes.after.'.$field, $name);
        }
        $this->postJson('/api/v2/grades', $this->operation('grades', ['greenhouse_id' => $ctx['gh']['id'], 'name' => str_repeat('é', 151)]))->assertUnprocessable();
    }
}
