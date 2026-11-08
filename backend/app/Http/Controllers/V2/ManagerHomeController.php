<?php

namespace App\Http\Controllers\V2;

use App\Domain\Cultivation\ManagerHome;
use App\Domain\Sync\ScopedRead;
use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\Request;

final class ManagerHomeController extends Controller
{
    public function __invoke(Request $request, ScopedRead $read, ManagerHome $home): array
    {
        $input = $request->validate(['greenhouse_id' => ['nullable', OperationInput::UUID]]);

        return $read->run($request, fn (User $actor) => $home->snapshot($actor, $input['greenhouse_id'] ?? null));
    }
}
