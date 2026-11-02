<?php

namespace App\Http\Controllers\V2;

use App\Http\Controllers\Controller;
use App\Domain\Sync\ScopedRead;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

final class SyncStatusController extends Controller
{
    public function __invoke(Request $request, ScopedRead $read)
    {
        return $read->run($request, fn (User $actor) => [
            'schema_version' => '2', 'sync_epoch' => DB::table('sync_state')->where('id', 1)->value('epoch'),
            'user' => ['id' => $actor->public_id, 'name' => $actor->name, 'role' => $actor->role, 'version' => $actor->version],
        ]);
    }
}
