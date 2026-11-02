<?php

namespace App\Http\Controllers\V2;

use App\Domain\Activities\ActivityService;
use App\Http\Controllers\Controller;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

final class ActivityController extends Controller
{
    public function index(Request $request, ActivityService $service)
    {
        $input = $request->validate(['planting_group_id' => 'nullable|uuid', 'activity_type' => 'nullable|in:nutrition,spray,general,stage',
            'from' => 'nullable|date_format:Y-m-d', 'until' => 'nullable|date_format:Y-m-d', 'limit' => 'sometimes|integer|min:1|max:100', 'cursor' => 'nullable|string|max:512']);
        $q = $service->scope($request->user());
        foreach (['planting_group_id', 'activity_type'] as $key) {
            if (isset($input[$key])) {
                $q->where($key, $input[$key]);
            }
        }
        if (isset($input['from'])) {
            $q->where('occurred_at', '>=', CarbonImmutable::parse($input['from'], 'Asia/Jakarta')->utc()->format('Y-m-d H:i:s'));
        }
        if (isset($input['until'])) {
            $q->where('occurred_at', '<', CarbonImmutable::parse($input['until'], 'Asia/Jakarta')->addDay()->utc()->format('Y-m-d H:i:s'));
        }
        $page = $q->orderByDesc('occurred_at')->orderByDesc('id')->cursorPaginate((int) ($input['limit'] ?? 50));

        return ['data' => collect($page->items())->map(fn ($row) => $service->publicRow($row)), 'next_cursor' => $page->nextCursor()?->encode()];
    }

    public function show(Request $request, string $id, ActivityService $service)
    {
        return $service->publicRow($service->scope($request->user())->where('id', $id)->first() ?? abort(404));
    }

    public function store(Request $request, ActivityService $service)
    {
        return $this->perform($request, $service, 'create');
    }

    public function update(Request $request, string $id, ActivityService $service)
    {
        return $this->perform($request, $service, 'update', $id);
    }

    public function cancel(Request $request, string $id, ActivityService $service)
    {
        return $this->perform($request, $service, 'cancel', $id);
    }

    public function sync(Request $request, ActivityService $service)
    {
        $action = $request->input('action');
        abort_unless(in_array($action, ['create', 'update', 'cancel'], true), 422, 'Aksi operasi tidak didukung.');

        return $this->perform($request, $service, $action);
    }

    public function audit(Request $request, string $id, ActivityService $service)
    {
        $service->scope($request->user())->where('id', $id)->first() ?? abort(404);
        $input = $request->validate(['cursor' => 'nullable|integer|min:0', 'limit' => 'sometimes|integer|min:1|max:100']);
        $limit = (int) ($input['limit'] ?? 50);
        $rows = DB::table('audit_logs')->where('entity_type', 'activity')->where('entity_id', $id)->where('id', '>', $input['cursor'] ?? 0)->orderBy('id')->limit($limit + 1)->get();

        return ['data' => $rows->take($limit)->map(function ($row) {
            return ['id' => $row->id, 'actor_id' => User::findOrFail($row->actor_id)->public_id, 'action' => $row->action, 'version' => $row->version,
                'reason' => $row->reason, 'changes' => json_decode($row->changes, true, flags: JSON_THROW_ON_ERROR), 'server_at' => CarbonImmutable::parse($row->server_at, 'UTC')->toISOString()];
        })->values(), 'next_cursor' => $rows->count() > $limit ? (string) $rows[$limit - 1]->id : null];
    }

    private function perform(Request $request, ActivityService $service, string $action, ?string $id = null)
    {
        $rules = $action === 'cancel' ? [] : [
            'planting_group_id' => ['required', OperationInput::UUID], 'schedule_id' => ['sometimes', 'nullable', OperationInput::UUID],
            'activity_type' => 'required|in:nutrition,spray,general,stage', 'occurred_at' => 'required|date_format:Y-m-d\TH:i:sP',
            'manual_ppm' => 'sometimes|nullable|string|max:30', 'manual_ph' => 'sometimes|nullable|string|max:30',
            'work_state' => 'required|in:completed,skipped', 'skipped_reason' => 'sometimes|nullable|string|max:1000', 'actual_stage' => 'sometimes|nullable|string|max:100',
            'materials' => 'present|array|max:30', 'materials.*' => 'required|array:material_id,unit_id,quantity',
            'materials.*.material_id' => ['required', OperationInput::UUID], 'materials.*.unit_id' => ['required', OperationInput::UUID], 'materials.*.quantity' => 'required|string|max:30',
            'notes' => 'present|string|max:2000'];
        if ($action !== 'create') {
            $rules['reason'] = 'required|string|min:1|max:1000';
        }
        if ($action === 'update') {
            $rules['reference_policy'] = 'required|in:preserve,refresh';
        }
        $op = OperationInput::read($request, 'activity', $action, $rules, $id);
        $result = $service->mutate($request->user(), $op);

        return response()->json($result['body'], $result['status']);
    }
}
