<?php

namespace App\Http\Controllers\V2;

use App\Domain\Finance\CostAllocationService;
use App\Domain\Finance\ExpenseBalance;
use App\Domain\Finance\ExpenseService;
use App\Domain\Sync\AcceptedSnapshot;
use App\Domain\Sync\ScopedRead;
use App\Http\Controllers\Controller;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

final class ExpenseController extends Controller
{
    private function service(string $financeResource): ExpenseService|CostAllocationService
    {
        return $financeResource === 'expenses' ? app(ExpenseService::class) : app(CostAllocationService::class);
    }

    private function kind(string $financeResource): string
    {
        return $financeResource === 'expenses' ? 'expense' : 'cost_allocation';
    }

    public function index(Request $request, string $financeResource)
    {
        $input = $request->validate(['greenhouse_id' => 'nullable|uuid', 'season_id' => 'nullable|uuid', 'expense_id' => 'nullable|uuid',
            'record_state' => 'nullable|in:active,cancelled', 'from' => 'nullable|date_format:Y-m-d',
            'until' => 'nullable|date_format:Y-m-d'.($request->filled('from') ? '|after_or_equal:from' : ''),
            'limit' => 'sometimes|integer|min:1|max:100', 'cursor' => 'nullable|string|max:512']);

        return app(ScopedRead::class)->run($request, function (User $actor) use ($financeResource, $input) {
            $q = $this->service($financeResource)->scope($actor);
            $expenses = app(ExpenseService::class)->scope($actor);
            if (isset($input['greenhouse_id'])) {
                $expenses->where('greenhouse_id', $input['greenhouse_id']);
            }
            if ($financeResource === 'expenses') {
                if (isset($input['expense_id'])) {
                    $q->where('id', $input['expense_id']);
                }
                if (isset($input['greenhouse_id'])) {
                    $q->where('greenhouse_id', $input['greenhouse_id']);
                }
                if (isset($input['season_id'])) {
                    $q->whereIn('id', DB::table('cost_allocations')->where('season_id', $input['season_id'])->where('record_state', 'active')->select('expense_id'));
                }
            } else {
                $q->whereIn('expense_id', $expenses->select('id'));
                foreach (['season_id', 'expense_id'] as $key) {
                    if (isset($input[$key])) {
                        $q->where($key, $input[$key]);
                    }
                }
            }
            if (isset($input['record_state'])) {
                $q->where('record_state', $input['record_state']);
            }
            $date = $financeResource === 'expenses' ? 'paid_at' : 'created_at';
            if (isset($input['from'])) {
                $q->where($date, '>=', CarbonImmutable::parse($input['from'], 'Asia/Jakarta')->utc()->format('Y-m-d H:i:s'));
            }
            if (isset($input['until'])) {
                $q->where($date, '<', CarbonImmutable::parse($input['until'], 'Asia/Jakarta')->addDay()->utc()->format('Y-m-d H:i:s'));
            }
            $page = $q->orderByDesc($date)->orderByDesc('id')->cursorPaginate((int) ($input['limit'] ?? 50));

            return ['data' => collect($page->items())->map(fn ($row) => app(AcceptedSnapshot::class)->read($this->kind($financeResource), $row)),
                'next_cursor' => $page->nextCursor()?->encode()];
        });
    }

    public function show(Request $request, string $financeResource, string $id)
    {
        return app(ScopedRead::class)->run($request, fn (User $actor) => app(AcceptedSnapshot::class)->read($this->kind($financeResource), $this->service($financeResource)->scope($actor)->where('id', $id)->first() ?? abort(404)));
    }

    public function availability(Request $request, string $id)
    {
        return app(ScopedRead::class)->run($request, fn (User $actor) => ['data' => app(ExpenseBalance::class)->publicRow(app(ExpenseService::class)->scope($actor)->where('id', $id)->first() ?? abort(404))]);
    }

    public function store(Request $request, string $financeResource)
    {
        return $this->perform($request, $financeResource, 'create');
    }

    public function update(Request $request, string $financeResource, string $id)
    {
        return $this->perform($request, $financeResource, 'update', $id);
    }

    public function cancel(Request $request, string $financeResource, string $id)
    {
        return $this->perform($request, $financeResource, 'cancel', $id);
    }

    public function audit(Request $request, string $financeResource, string $id)
    {
        $input = $request->validate(['cursor' => 'nullable|integer|min:0', 'limit' => 'sometimes|integer|min:1|max:100']);

        return app(ScopedRead::class)->run($request, function (User $actor) use ($financeResource, $id, $input) {
            $this->service($financeResource)->scope($actor)->where('id', $id)->first() ?? abort(404);
            $limit = (int) ($input['limit'] ?? 50);
            $rows = DB::table('audit_logs')->where('entity_type', $this->kind($financeResource))->where('entity_id', $id)
                ->where('id', '>', $input['cursor'] ?? 0)->orderBy('id')->limit($limit + 1)->get();

            return ['data' => $rows->take($limit)->map(function ($row) {
                $actor = User::findOrFail($row->actor_id);

                return ['id' => $row->id, 'actor_id' => $actor->public_id, 'actor_name' => $actor->name, 'action' => $row->action,
                    'version' => $row->version, 'reason' => $row->reason, 'changes' => json_decode($row->changes, true, flags: JSON_THROW_ON_ERROR),
                    'server_at' => CarbonImmutable::parse($row->server_at, 'UTC')->toISOString()];
            })->values(), 'next_cursor' => $rows->count() > $limit ? (string) $rows[$limit - 1]->id : null];
        });
    }

    private function perform(Request $request, string $financeResource, string $action, ?string $id = null)
    {
        $rules = $action === 'cancel' ? [] : ($financeResource === 'expenses' ? [
            'greenhouse_id' => ['required', OperationInput::UUID], 'category_id' => ['required', OperationInput::UUID],
            'paid_at' => 'required|date_format:Y-m-d\TH:i:sP', 'amount' => 'required|string|max:30',
            'payment_kind' => 'required|in:operating,asset_acquisition', 'notes' => 'present|string|max:2000',
        ] : ['expense_id' => ['required', OperationInput::UUID], 'season_id' => ['required', OperationInput::UUID],
            'amount' => 'required|string|max:30', 'cost_class' => 'required|in:FC,VC', 'allocation_basis' => ['required', 'string', 'max:2000', 'regex:/\S/u']]);
        if ($action !== 'create') {
            $rules['reason'] = ['required', 'string', 'max:1000', 'regex:/\S/u'];
        }
        $result = $this->service($financeResource)->mutate($request->user(), OperationInput::read($request, $this->kind($financeResource), $action, $rules, $id));

        return response()->json($result['body'], $result['status']);
    }
}
