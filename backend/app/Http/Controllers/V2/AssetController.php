<?php

namespace App\Http\Controllers\V2;

use App\Domain\Finance\AssetService;
use App\Domain\Sync\AcceptedSnapshot;
use App\Domain\Sync\ScopedRead;
use App\Http\Controllers\Controller;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

final class AssetController extends Controller
{
    private function kind(string $assetResource): string
    {
        return $assetResource === 'assets' ? 'asset' : 'depreciation_allocation';
    }

    public function index(Request $request, string $assetResource)
    {
        $input = $request->validate(['greenhouse_id' => 'nullable|uuid', 'season_id' => 'nullable|uuid', 'asset_id' => 'nullable|uuid',
            'record_state' => 'nullable|in:active,cancelled', 'limit' => 'sometimes|integer|min:1|max:100', 'cursor' => 'nullable|string|max:512']);

        return app(ScopedRead::class)->run($request, function (User $actor) use ($assetResource, $input) {
            $kind = $this->kind($assetResource);
            $q = app(AssetService::class)->scope($actor, $kind);
            foreach (['greenhouse_id', 'record_state'] as $key) {
                if (isset($input[$key])) {
                    $q->where($key, $input[$key]);
                }
            }
            if ($kind === 'depreciation_allocation') {
                foreach (['season_id', 'asset_id'] as $key) {
                    if (isset($input[$key])) {
                        $q->where($key, $input[$key]);
                    }
                }
            }
            $page = $q->orderByDesc('created_at')->orderByDesc('id')->cursorPaginate((int) ($input['limit'] ?? 50));

            return ['data' => collect($page->items())->map(fn ($row) => app(AcceptedSnapshot::class)->read($kind, $row)), 'next_cursor' => $page->nextCursor()?->encode()];
        });
    }

    public function show(Request $request, string $assetResource, string $id)
    {
        $kind = $this->kind($assetResource);

        return app(ScopedRead::class)->run($request, fn (User $actor) => app(AcceptedSnapshot::class)->read($kind, app(AssetService::class)->scope($actor, $kind)->where('id', $id)->first() ?? abort(404)));
    }

    public function audit(Request $request, string $assetResource, string $id)
    {
        $input = $request->validate(['cursor' => 'nullable|integer|min:0', 'limit' => 'sometimes|integer|min:1|max:100']);

        return app(ScopedRead::class)->run($request, function (User $actor) use ($assetResource, $id, $input) {
            $kind = $this->kind($assetResource);
            app(AssetService::class)->scope($actor, $kind)->where('id', $id)->first() ?? abort(404);
            $limit = (int) ($input['limit'] ?? 50);
            $rows = DB::table('audit_logs')->where('entity_type', $kind)->where('entity_id', $id)->where('id', '>', $input['cursor'] ?? 0)->orderBy('id')->limit($limit + 1)->get();

            return ['data' => $rows->take($limit)->map(function ($row) {
                $actor = User::findOrFail($row->actor_id);

                return ['id' => $row->id, 'actor_id' => $actor->public_id, 'actor_name' => $actor->name, 'action' => $row->action,
                    'version' => $row->version, 'reason' => $row->reason, 'changes' => json_decode($row->changes, true, flags: JSON_THROW_ON_ERROR),
                    'server_at' => CarbonImmutable::parse($row->server_at, 'UTC')->toISOString()];
            })->values(), 'next_cursor' => $rows->count() > $limit ? (string) $rows[$limit - 1]->id : null];
        });
    }

    public function store(Request $request, string $assetResource)
    {
        return $this->perform($request, $assetResource, 'create');
    }

    public function update(Request $request, string $assetResource, string $id)
    {
        return $this->perform($request, $assetResource, 'update', $id);
    }

    public function cancel(Request $request, string $assetResource, string $id)
    {
        return $this->perform($request, $assetResource, 'cancel', $id);
    }

    private function perform(Request $request, string $resource, string $action, ?string $id = null)
    {
        $text = ['required', 'string', 'max:2000', 'regex:/\S/u'];
        $rules = $action === 'cancel' ? [] : ($resource === 'assets' ? [
            'expense_id' => ['required', OperationInput::UUID], 'name' => 'required|string|max:150', 'cost' => 'required|string|max:30',
            'residual' => 'required|string|max:30', 'life_years' => 'required|integer|min:1|max:1000',
            'in_service_date' => 'required|date_format:Y-m-d', 'usage_basis' => $text,
        ] : ['asset_id' => ['required', OperationInput::UUID], 'season_id' => ['required', OperationInput::UUID],
            'usage_reference' => ['required', OperationInput::UUID], 'usage_year' => 'required|integer|min:1|max:1000',
            'period_start' => 'required|date_format:Y-m-d', 'period_end' => 'required|date_format:Y-m-d|after:operation.payload.period_start',
            'amount' => 'required|string|max:30', 'allocation_basis' => $text]);
        if ($action !== 'create') {
            $rules['reason'] = OperationInput::reasonRule($request, $text, 2000);
        }
        $op = OperationInput::read($request, $this->kind($resource), $action, $rules, $id);
        foreach (['life_years', 'usage_year'] as $key) {
            abort_if(isset($op['payload'][$key]) && ! is_int($op['payload'][$key]), 422, 'Tahun harus integer JSON.');
        }
        $result = app(AssetService::class)->mutate($request->user(), $op);

        return response()->json($result['body'], $result['status']);
    }
}
