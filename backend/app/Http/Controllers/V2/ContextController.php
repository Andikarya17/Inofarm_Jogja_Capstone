<?php

namespace App\Http\Controllers\V2;

use App\Domain\Identity\GreenhouseAccess;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

final class ContextController extends Controller
{
    public function greenhouses(Request $request, GreenhouseAccess $access)
    {
        $input = $request->validate(['cursor' => 'nullable|uuid', 'limit' => 'sometimes|integer|min:1|max:100']);
        $limit = (int) ($input['limit'] ?? 50);
        $rows = $access->query($request->user())->when($input['cursor'] ?? null, fn ($q, $cursor) => $q->where('id', '>', $cursor))
            ->orderBy('id')->limit($limit + 1)->get(['id', 'name', 'active', 'version']);

        return ['data' => $rows->take($limit)->values(), 'next_cursor' => $rows->count() > $limit ? $rows[$limit - 1]->id : null];
    }

    public function greenhouse(Request $request, string $id, GreenhouseAccess $access)
    {
        $row = $access->require($request->user(), $id);

        return ['id' => $row->id, 'name' => $row->name, 'active' => (bool) $row->active, 'version' => $row->version];
    }
}
