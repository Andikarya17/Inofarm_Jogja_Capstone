<?php

namespace App\Http\Controllers\V2;

use App\Domain\Cultivation\ReferenceService;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

final class ReferenceController extends Controller
{
    public function index(Request $request, string $resource, ReferenceService $service)
    {
        $input = $request->validate(['planting_group_id' => 'nullable|uuid', 'cursor' => 'nullable|uuid', 'limit' => 'sometimes|integer|min:1|max:100']);
        $limit = (int) ($input['limit'] ?? 50);
        $rows = $service->scope($request->user(), $resource)
            ->when($input['planting_group_id'] ?? null, fn ($q, $id) => $q->where('planting_group_id', $id))
            ->when($input['cursor'] ?? null, fn ($q, $id) => $q->where('id', '>', $id))->orderBy('id')->limit($limit + 1)->get();

        return ['data' => $rows->take($limit)->map(fn ($row) => $service->publicRow($row))->values(),
            'next_cursor' => $rows->count() > $limit ? $rows[$limit - 1]->id : null];
    }

    public function show(Request $request, string $resource, string $id, ReferenceService $service)
    {
        return $service->publicRow($service->scope($request->user(), $resource)->where('id', $id)->first() ?? abort(404));
    }

    public function versions(Request $request, string $resource, string $id, ReferenceService $service)
    {
        $service->scope($request->user(), $resource)->where('id', $id)->first() ?? abort(404);
        $input = $request->validate(['cursor' => 'nullable|integer|min:0', 'limit' => 'sometimes|integer|min:1|max:100']);
        $limit = (int) ($input['limit'] ?? 50);
        $rows = DB::table('cultivation_reference_versions')->where('entity_type', $resource)->where('entity_id', $id)
            ->where('version', '>', $input['cursor'] ?? 0)->orderBy('version')->limit($limit + 1)->get();

        return ['data' => $rows->take($limit)->map(fn ($row) => json_decode($row->body, true, flags: JSON_THROW_ON_ERROR))->values(),
            'next_cursor' => $rows->count() > $limit ? (string) $rows[$limit - 1]->version : null];
    }

    public function store(Request $request, string $resource, ReferenceService $service)
    {
        return $this->mutate($request, $resource, $service, 'create');
    }

    public function update(Request $request, string $resource, string $id, ReferenceService $service)
    {
        return $this->mutate($request, $resource, $service, 'update', $id);
    }

    private function mutate(Request $request, string $table, ReferenceService $service, string $action, ?string $id = null)
    {
        $rules = ['activity_type' => 'required|in:nutrition,spray,general,stage'];
        if ($table === 'schedules') {
            $rules += ['planned_at' => 'required|date_format:Y-m-d\TH:i:sP', 'notes' => 'present|string|max:2000'];
        } else {
            $rules += ['parameter' => 'required|in:manual_ppm,manual_ph', 'unit' => 'required|in:ppm,pH',
                'effective_from' => 'required|date_format:Y-m-d\TH:i:sP', 'effective_until' => 'present|nullable|date_format:Y-m-d\TH:i:sP'];
            foreach (['target_value', 'minimum', 'maximum', 'tolerance'] as $key) {
                $rules[$key] = 'present|nullable|string|max:30';
            }
        }
        if ($action === 'create') {
            $rules['planting_group_id'] = ['required', OperationInput::UUID];
        } else {
            $rules += ['active' => 'required|boolean', 'reason' => 'required|string|max:1000'];
        }
        $op = OperationInput::read($request, $table, $action, $rules, $id);
        abort_if(isset($op['payload']['active']) && ! is_bool($op['payload']['active']), 422, 'active harus boolean.');
        $result = $service->mutate($request->user(), $table, $op);

        return response()->json($result['body'], $result['status']);
    }
}
