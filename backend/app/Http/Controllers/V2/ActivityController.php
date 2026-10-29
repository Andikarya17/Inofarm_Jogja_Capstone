<?php

namespace App\Http\Controllers\V2;

use App\Domain\Activities\ActivityService;
use App\Domain\Cultivation\MasterService;
use App\Domain\Sync\AcceptedSnapshot;
use App\Domain\Sync\ScopedRead;
use App\Http\Controllers\Controller;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

final class ActivityController extends Controller
{
    public function index(Request $request, ActivityService $service, MasterService $masters)
    {
        $input = $request->validate(['planting_group_id' => 'nullable|uuid', 'activity_type' => 'nullable|in:nutrition,spray,general,stage',
            'greenhouse_id' => 'nullable|uuid', 'season_id' => 'nullable|uuid', 'record_state' => 'nullable|in:active,cancelled',
            'from' => 'nullable|date_format:Y-m-d', 'until' => 'nullable|date_format:Y-m-d'.($request->filled('from') ? '|after_or_equal:from' : ''), 'limit' => 'sometimes|integer|min:1|max:100', 'cursor' => 'nullable|string|max:512']);
        return app(ScopedRead::class)->run($request, function (User $actor) use ($request, $service, $masters, $input) {
            $q = $service->scope($actor);
            foreach (['planting_group_id', 'activity_type', 'record_state'] as $key) {
                if (isset($input[$key])) {
                    $q->where($key, $input[$key]);
                }
            }
            if (isset($input['season_id']) || isset($input['greenhouse_id'])) {
                $groups = $masters->scope($actor, 'planting_groups')->select('id');
                if (isset($input['season_id'])) {
                    $groups->where('season_id', $input['season_id']);
                }
                if (isset($input['greenhouse_id'])) {
                    $groups->whereIn('season_id', DB::table('seasons')->select('id')->where('greenhouse_id', $input['greenhouse_id']));
                }
                $q->whereIn('planting_group_id', $groups);
            }
            if (isset($input['from'])) {
                $q->where('occurred_at', '>=', CarbonImmutable::parse($input['from'], 'Asia/Jakarta')->utc()->format('Y-m-d H:i:s'));
            }
            if (isset($input['until'])) {
                $q->where('occurred_at', '<', CarbonImmutable::parse($input['until'], 'Asia/Jakarta')->addDay()->utc()->format('Y-m-d H:i:s'));
            }
            $page = $q->orderByDesc('occurred_at')->orderByDesc('id')->cursorPaginate((int) ($input['limit'] ?? 50));

            return ['data' => collect($page->items())->map(fn ($row) => app(AcceptedSnapshot::class)->read('activity', $row)), 'next_cursor' => $page->nextCursor()?->encode()];
        });
    }

    public function show(Request $request, string $id, ActivityService $service)
    {
        return app(ScopedRead::class)->run($request, fn (User $actor) => app(AcceptedSnapshot::class)->read('activity', $service->scope($actor)->where('id', $id)->first() ?? abort(404)));
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
        $input = $request->validate(['cursor' => 'nullable|integer|min:0', 'limit' => 'sometimes|integer|min:1|max:100']);
        return app(ScopedRead::class)->run($request, function (User $actor) use ($service, $id, $input) {
            $service->scope($actor)->where('id', $id)->first() ?? abort(404);
            $limit = (int) ($input['limit'] ?? 50);
            $rows = DB::table('audit_logs')->where('entity_type', 'activity')->where('entity_id', $id)->where('id', '>', $input['cursor'] ?? 0)->orderBy('id')->limit($limit + 1)->get();

            return ['data' => $rows->take($limit)->map(function ($row) {
                $actor = User::findOrFail($row->actor_id);

                return ['id' => $row->id, 'actor_id' => $actor->public_id, 'actor_name' => $actor->name, 'action' => $row->action, 'version' => $row->version,
                    'reason' => $row->reason, 'changes' => json_decode($row->changes, true, flags: JSON_THROW_ON_ERROR), 'server_at' => CarbonImmutable::parse($row->server_at, 'UTC')->toISOString()];
            })->values(), 'next_cursor' => $rows->count() > $limit ? (string) $rows[$limit - 1]->id : null];
        });
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
