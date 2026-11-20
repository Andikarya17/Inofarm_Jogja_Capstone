<?php

namespace App\Http\Controllers\V2;

use App\Domain\Finance\SeasonCostSheetService;
use App\Domain\Sync\ScopedRead;
use App\Http\Controllers\Controller;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

final class SeasonCostSheetController extends Controller
{
    public function index(Request $request)
    {
        $input = $request->validate(['sequence' => 'nullable|integer|min:1|max:100000']);

        return app(ScopedRead::class)->run($request, function (User $actor) use ($input) {
            $sheets = app(SeasonCostSheetService::class)->all($actor);
            if (isset($input['sequence'])) {
                $sheets = array_values(array_filter($sheets, fn ($sheet) => $sheet['sequence'] === (int) $input['sequence']));
            }

            return ['data' => $sheets];
        });
    }

    public function show(Request $request, string $id)
    {
        return app(ScopedRead::class)->run($request, function (User $actor) use ($id) {
            app(SeasonCostSheetService::class)->all($actor);
            $sheet = DB::table('season_cost_sheets')->where('id', $id)->first() ?? abort(404);

            return ['data' => app(SeasonCostSheetService::class)->publicSheet($sheet)];
        });
    }

    public function update(Request $request, string $id)
    {
        $op = OperationInput::read($request, 'season_cost_sheet', 'update', [
            'lines' => 'required|array|min:7|max:40',
            'lines.*' => 'required|array:id,name,cost_class,amount,is_required',
            'lines.*.id' => ['required', OperationInput::UUID, 'distinct'],
            'lines.*.name' => ['required', 'string', 'max:150', 'regex:/\S/u'],
            'lines.*.cost_class' => 'required|in:FC,VC',
            'lines.*.amount' => ['present', 'nullable', 'string', 'regex:/^\d{1,18}(\.\d{1,2})?$/'],
            'lines.*.is_required' => 'required|boolean',
        ], $id);
        $result = app(SeasonCostSheetService::class)->mutate($request->user(), $op);

        return response()->json($result['body'], $result['status']);
    }

    public function audit(Request $request, string $id)
    {
        $input = $request->validate(['cursor' => 'nullable|integer|min:0', 'limit' => 'sometimes|integer|min:1|max:100']);

        return app(ScopedRead::class)->run($request, function (User $actor) use ($id, $input) {
            app(SeasonCostSheetService::class)->all($actor);
            DB::table('season_cost_sheets')->where('id', $id)->exists() || abort(404);
            $limit = (int) ($input['limit'] ?? 50);
            $rows = DB::table('audit_logs')->where('entity_type', 'season_cost_sheet')->where('entity_id', $id)->where('id', '>', $input['cursor'] ?? 0)->orderBy('id')->limit($limit + 1)->get();

            return ['data' => $rows->take($limit)->map(function ($row) {
                $actor = User::findOrFail($row->actor_id);

                return ['id' => $row->id, 'actor_id' => $actor->public_id, 'actor_name' => $actor->name, 'action' => $row->action, 'version' => $row->version,
                    'reason' => $row->reason, 'changes' => json_decode($row->changes, true, flags: JSON_THROW_ON_ERROR), 'server_at' => CarbonImmutable::parse($row->server_at, 'UTC')->toISOString()];
            })->values(), 'next_cursor' => $rows->count() > $limit ? (string) $rows[$limit - 1]->id : null];
        });
    }
}
