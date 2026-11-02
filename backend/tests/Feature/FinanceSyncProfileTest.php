<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\DB;
use Tests\FinanceTestCase;

class FinanceSyncProfileTest extends FinanceTestCase
{
    public function test_sync_status_returns_current_public_identity_without_credentials(): void
    {
        $actor = $this->account();
        $this->asUser($actor);
        $this->getJson('/api/v2/sync/status')->assertOk()->assertExactJson([
            'schema_version' => '2', 'sync_epoch' => DB::table('sync_state')->where('id', 1)->value('epoch'),
            'user' => ['id' => $actor->public_id, 'name' => $actor->name, 'role' => 'manager', 'version' => 1],
        ]);
        // Simulate an administrative change between authenticated requests.
        $actor->update(['name' => 'Nama terbaru', 'role' => 'worker', 'version' => 2]);
        $this->getJson('/api/v2/sync/status')->assertOk()->assertJsonPath('user.name', 'Nama terbaru')->assertJsonPath('user.role', 'worker')->assertJsonPath('user.version', 2);
        $actor->tokens()->delete();
        $this->getJson('/api/v2/sync/status')->assertUnauthorized();
    }

    public function test_disabled_actor_cannot_fetch_profile_or_epoch(): void
    {
        $actor = $this->account();
        $this->asUser($actor);
        $actor->update(['active' => false]);
        $this->getJson('/api/v2/sync/status')->assertForbidden()->assertJsonMissingPath('sync_epoch')->assertJsonMissingPath('user');
    }
}
