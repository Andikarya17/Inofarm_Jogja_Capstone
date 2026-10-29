<?php

namespace App\Http\Controllers\V2;

use App\Domain\Cultivation\MasterService;
use App\Domain\Harvest\HarvestBalance;
use App\Domain\Harvest\HarvestService;
use App\Http\Controllers\Controller;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

final class HarvestController extends Controller
{
    public function index(Request $request, HarvestService $service, MasterService $masters)
    {
        $input = $request->validate(['greenhouse_id' => 'nullable|uuid', 'season_id' => 'nullable|uuid', 'planting_group_id' => 'nullable|uuid',
            'record_state' => 'nullable|in:active,cancelled', 'from' => 'nullable|date_format:Y-m-d',
            'until' => 'nullable|date_format:Y-m-d'.($request->filled('from') ? '|after_or_equal:from' : ''),
            'limit' => 'sometimes|integer|min:1|max:100', 'cursor' => 'nullable|string|max:512']);

        return DB::transaction(function () use ($request, $service, $masters, $input) {
            $q = $service->scope($request->user());
            foreach (['planting_group_id', 'record_state'] as $key) {
                if (isset($input[$key])) {
                    $q->where($key, $input[$key]);
                }
            }
            if (isset($input['season_id']) || isset($input['greenhouse_id'])) {
                $groups = $masters->scope($request->user(), 'planting_groups')->select('id');
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

            return ['data' => collect($page->items())->map(fn ($row) => $service->publicRow($row)), 'next_cursor' => $page->nextCursor()?->encode()];
        });
    }

    public function show(Request $request, string $id, HarvestService $service)
    {
        return DB::transaction(fn () => $service->publicRow($service->scope($request->user())->where('id', $id)->first() ?? abort(404)));
    }

    public function availability(Request $request, string $id, HarvestService $service, HarvestBalance $balance)
    {
        return DB::transaction(function () use ($request, $id, $service, $balance) {
            $harvest = $service->scope($request->user())->where('id', $id)->first() ?? abort(404);

            return ['data' => DB::table('harvest_items')->where('harvest_id', $id)->orderBy('id')->get()->map(fn ($item) => $balance->publicRow($item, $harvest))->values()];
        });
    }

    public function store(Request $request, HarvestService $service)
    {
        return $this->perform($request, $service, 'create');
    }

    public function update(Request $request, string $id, HarvestService $service)
    {
        return $this->perform($request, $service, 'update', $id);
    }

    public function cancel(Request $request, string $id, HarvestService $service)
    {
        return $this->perform($request, $service, 'cancel', $id);
    }

    public function audit(Request $request, string $id, HarvestService $service)
    {
        $service->scope($request->user())->where('id', $id)->first() ?? abort(404);
        $input = $request->validate(['cursor' => 'nullable|integer|min:0', 'limit' => 'sometimes|integer|min:1|max:100']);
        $limit = (int) ($input['limit'] ?? 50);
        $rows = DB::table('audit_logs')->where('entity_type', 'harvest')->where('entity_id', $id)->where('id', '>', $input['cursor'] ?? 0)->orderBy('id')->limit($limit + 1)->get();

        return ['data' => $rows->take($limit)->map(function ($row) {
            $actor = User::findOrFail($row->actor_id);

            return ['id' => $row->id, 'actor_id' => $actor->public_id, 'actor_name' => $actor->name, 'action' => $row->action, 'version' => $row->version,
                'reason' => $row->reason, 'changes' => json_decode($row->changes, true, flags: JSON_THROW_ON_ERROR), 'server_at' => CarbonImmutable::parse($row->server_at, 'UTC')->toISOString()];
        })->values(), 'next_cursor' => $rows->count() > $limit ? (string) $rows[$limit - 1]->id : null];
    }

    private function perform(Request $request, HarvestService $service, string $action, ?string $id = null)
    {
        $rules = $action === 'cancel' ? [] : ['planting_group_id' => ['required', OperationInput::UUID], 'occurred_at' => 'required|date_format:Y-m-d\TH:i:sP',
            'items' => 'required|array|min:1|max:30', 'items.*' => 'required|array:id,grade_id,fruit_count,weight_kg',
            'items.*.id' => ['required', OperationInput::UUID, 'distinct'], 'items.*.grade_id' => ['required', OperationInput::UUID, 'distinct'],
            'items.*.fruit_count' => 'required|integer|min:0|max:2147483647', 'items.*.weight_kg' => 'required|string|max:30', 'notes' => 'present|string|max:2000'];
        if ($action !== 'create') {
            $rules['reason'] = 'required|string|min:1|max:1000';
        }
        if ($action === 'update') {
            $rules['reference_policy'] = 'required|in:preserve,refresh';
        }
        $result = $service->mutate($request->user(), OperationInput::read($request, 'harvest', $action, $rules, $id));

        return response()->json($result['body'], $result['status']);
    }
}
