<?php

namespace App\Http\Controllers\V2;

use App\Domain\Cultivation\MasterService;
use App\Http\Controllers\Controller;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

final class MasterController extends Controller
{
    private function table(Request $request): string
    {
        $table = $request->route('resource');
        $table = ['planting-groups' => 'planting_groups', 'expense-categories' => 'expense_categories', 'assignments' => 'greenhouse_assignments'][$table] ?? $table;
        abort_unless(in_array($table, MasterService::TABLES, true), 404);

        return $table;
    }

    public function index(Request $request, MasterService $service)
    {
        $table = $this->table($request);
        $input = $request->validate(['cursor' => 'nullable|uuid', 'limit' => 'sometimes|integer|min:1|max:100',
            'greenhouse_id' => 'nullable|uuid', 'season_id' => 'nullable|uuid']);
        $q = $service->scope($request->user(), $table);
        if (isset($input['greenhouse_id'])) {
            $q->whereIn($table === 'planting_groups' ? 'season_id' : ($table === 'greenhouses' ? 'id' : 'greenhouse_id'),
                $table === 'planting_groups' ? DB::table('seasons')->where('greenhouse_id', $input['greenhouse_id'])->select('id') : [$input['greenhouse_id']]);
        }
        if ($table === 'planting_groups' && isset($input['season_id'])) {
            $q->where('season_id', $input['season_id']);
        }
        $limit = (int) ($input['limit'] ?? 50);
        $rows = $q->when($input['cursor'] ?? null, fn ($q, $cursor) => $q->where('id', '>', $cursor))->orderBy('id')->limit($limit + 1)->get();

        return ['data' => $rows->take($limit)->map(fn ($row) => $service->publicRow($table, $row))->values(),
            'next_cursor' => $rows->count() > $limit ? $rows[$limit - 1]->id : null];
    }

    public function show(Request $request, string $resource, string $id, MasterService $service)
    {
        $table = $this->table($request);

        return $service->publicRow($table, $service->scope($request->user(), $table)->where('id', $id)->first() ?? abort(404));
    }

    public function store(Request $request, MasterService $service)
    {
        return $this->mutate($request, $service, 'create');
    }

    public function audit(Request $request, string $resource, string $id, MasterService $service)
    {
        $table = $this->table($request);
        $service->scope($request->user(), $table)->where('id', $id)->first() ?? abort(404);
        $input = $request->validate(['cursor' => 'nullable|integer|min:0', 'limit' => 'sometimes|integer|min:1|max:100']);
        $limit = (int) ($input['limit'] ?? 50);
        $rows = DB::table('audit_logs')->where('entity_type', $table)->where('entity_id', $id)
            ->where('id', '>', $input['cursor'] ?? 0)->orderBy('id')->limit($limit + 1)->get();

        return ['data' => $rows->take($limit)->map(function ($row) {
            $user = User::findOrFail($row->actor_id);

            return ['id' => $row->id, 'actor_id' => $user->public_id, 'actor_name' => $user->name, 'action' => $row->action,
                'version' => $row->version, 'reason' => $row->reason, 'changes' => json_decode($row->changes, true, flags: JSON_THROW_ON_ERROR),
                'server_at' => CarbonImmutable::parse($row->server_at, 'UTC')->toISOString()];
        })->values(), 'next_cursor' => $rows->count() > $limit ? (string) $rows[$limit - 1]->id : null];
    }

    public function update(Request $request, string $resource, string $id, MasterService $service)
    {
        return $this->mutate($request, $service, 'update', $id);
    }

    public function transition(Request $request, string $resource, string $id, string $action, MasterService $service)
    {
        abort_unless(in_array($action, ['close', 'reopen'], true), 404);

        return $this->mutate($request, $service, $action, $id);
    }

    private function mutate(Request $request, MasterService $service, string $action, ?string $id = null)
    {
        $table = $this->table($request);
        $rules = ['name' => 'required|string|max:150'];
        if (in_array($action, ['close', 'reopen'], true)) {
            $rules = [];
        } elseif ($table === 'greenhouse_assignments') {
            $rules = ['effective_from' => 'required|date_format:Y-m-d\TH:i:sP', 'effective_until' => 'present|nullable|date_format:Y-m-d\TH:i:sP'];
            if ($action === 'create') {
                $rules['user_id'] = ['required', OperationInput::UUID];
            }
        } elseif ($table === 'planting_groups') {
            $rules += ['variety_id' => ['required', OperationInput::UUID], 'population' => 'required|integer|min:0|max:2147483647',
                'sowing_date' => 'required|date_format:Y-m-d', 'transplant_date' => 'present|nullable|date_format:Y-m-d'];
            if ($action === 'create') {
                $rules['season_id'] = ['required', OperationInput::UUID];
            }
        } elseif ($table === 'seasons') {
            $rules += ['start_date' => 'required|date_format:Y-m-d', 'end_date' => 'present|nullable|date_format:Y-m-d'];
        } elseif (in_array($table, ['units', 'materials'], true)) {
            $rules['dimension'] = 'required|in:mass,volume,count';
        }
        if ($action === 'create' && ! in_array($table, ['greenhouses', 'planting_groups'], true)) {
            $rules['greenhouse_id'] = ['required', OperationInput::UUID];
        }
        if ($action === 'update' && $table !== 'seasons') {
            $rules['active'] = 'required|boolean';
        }
        if ($action !== 'create') {
            $rules['reason'] = 'required|string|min:1|max:1000';
        }
        $op = OperationInput::read($request, $table, $action, $rules, $id);
        if (isset($op['payload']['active'])) {
            abort_unless(is_bool($op['payload']['active']), 422, 'active harus boolean.');
        }
        $result = $service->mutate($request->user(), $table, $op);

        return response()->json($result['body'], $result['status']);
    }
}
