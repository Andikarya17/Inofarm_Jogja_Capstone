<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\DB;
use Tests\FinanceTestCase;

/**
 * The Android "Tambah greenhouse" wizard sends these requests one after another as a manager who has no
 * assignment and gives no change note. Payload shapes mirror AdminForm.payload() on the device.
 */
final class ManagerGreenhouseSetupSequenceTest extends FinanceTestCase
{
    public function test_a_new_manager_can_set_up_a_greenhouse_end_to_end_without_notes_or_assignment(): void
    {
        $manager = $this->account();
        $this->assertSame(0, DB::table('greenhouse_assignments')->where('user_id', $manager->id)->count());
        $this->asUser($manager);

        $gh = $this->createMaster('greenhouses', ['name' => 'GH dari wizard']);
        $season = $this->createMaster('seasons', ['greenhouse_id' => $gh['id'], 'name' => 'Musim wizard', 'start_date' => '2026-11-10', 'end_date' => null]);
        $variety = $this->createMaster('varieties', ['greenhouse_id' => $gh['id'], 'name' => 'Varietas wizard']);
        $group = $this->createMaster('planting_groups', ['season_id' => $season['id'], 'variety_id' => $variety['id'], 'name' => 'Kelompok wizard',
            'population' => 120, 'sowing_date' => '2026-11-10', 'transplant_date' => null, 'harvest_target' => null]);

        $designate = $this->operation('greenhouses', ['planting_group_id' => $group['id']], 'designate_period', $gh['id'], (int) DB::table('greenhouses')->where('id', $gh['id'])->value('version'));
        $this->postJson('/api/v2/greenhouses/'.$gh['id'].'/running-period', $designate)->assertOk();
        $this->postJson('/api/v2/greenhouses/'.$gh['id'].'/running-period', $designate)->assertOk();

        $worker = $this->account('worker');
        $this->createMaster('greenhouse_assignments', ['greenhouse_id' => $gh['id'], 'user_id' => $worker->public_id,
            'effective_from' => now()->subMinute()->format('Y-m-d\TH:i:sP'), 'effective_until' => null]);
        $account = $this->operation('users', ['greenhouse_id' => $gh['id'], 'name' => 'Pekerja baru', 'role' => 'worker',
            'email' => 'pekerja-wizard@example.invalid', 'password' => 'dua-belas-huruf']);
        $this->postJson('/api/v2/users', $account)->assertCreated();
        $this->postJson('/api/v2/users', $account)->assertCreated();

        $home = $this->getJson('/api/v2/manager-home?greenhouse_id='.$gh['id'])->assertOk();
        $home->assertJsonPath('greenhouses.0.period.group_id', $group['id']);
        $this->assertSame(1, DB::table('users')->where('email', 'pekerja-wizard@example.invalid')->count());
        $this->assertSame(0, DB::table('audit_logs')->whereNotNull('reason')->count());
    }

    public function test_a_retried_step_reuses_its_operation_and_never_creates_a_second_record(): void
    {
        $this->asUser($this->account());
        $op = $this->operation('greenhouses', ['name' => 'GH ulang']);
        $first = $this->postJson('/api/v2/greenhouses', $op)->assertCreated()->json();
        $this->postJson('/api/v2/greenhouses', $op)->assertCreated()->assertExactJson($first);
        $this->assertSame(1, DB::table('greenhouses')->where('name', 'GH ulang')->count());
    }

    public function test_a_worker_cannot_start_the_setup(): void
    {
        $this->asUser($this->account('worker'));
        $this->postJson('/api/v2/greenhouses', $this->operation('greenhouses', ['name' => 'Tidak boleh']))->assertForbidden();
    }
}
