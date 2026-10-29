<?php

namespace App\Http\Controllers\V2;

use App\Http\Controllers\Controller;
use Illuminate\Support\Facades\DB;

final class SyncStatusController extends Controller
{
    public function __invoke()
    {
        return ['schema_version' => '2', 'sync_epoch' => DB::table('sync_state')->where('id', 1)->value('epoch')];
    }
}
