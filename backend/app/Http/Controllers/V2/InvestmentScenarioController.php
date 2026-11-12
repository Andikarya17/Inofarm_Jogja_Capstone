<?php

namespace App\Http\Controllers\V2;

use App\Domain\Finance\InvestmentScenarioService;
use App\Domain\Sync\AcceptedSnapshot;
use App\Domain\Sync\ScopedRead;
use App\Http\Controllers\Controller;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

final class InvestmentScenarioController extends Controller
{
    private function rules(bool $reason): array
    {
        $money = ['present', 'nullable', 'string', 'regex:/^-?(?:0|[1-9][0-9]{0,17})(?:\\.[0-9]{1,2})?$/D'];
        $rules = ['greenhouse_id' => ['required', OperationInput::UUID], 'name' => 'required|string|max:120', 'base_date' => 'required|date_format:Y-m-d', 'horizon_years' => 'required|integer|min:1|max:100',
            'initial_outlay' => $money, 'initial_source' => 'present|array:kind,reference,basis', 'initial_source.kind' => 'present|nullable|in:actual,projection', 'initial_source.reference' => 'present|nullable|string|max:200', 'initial_source.basis' => 'present|nullable|string|max:2000',
            'discount_rate' => ['present', 'nullable', 'string', 'regex:/^-?(?:0|[1-9][0-9]{0,11})(?:\\.[0-9]{1,8})?$/D'], 'payback_convention' => 'present|nullable|in:interpolation,end_of_year',
            'cash_flows' => 'required|array|min:1|max:100', 'cash_flows.*' => 'required|array:year,amount,source_kind,source_reference,basis', 'cash_flows.*.year' => 'required|integer|min:1|max:100', 'cash_flows.*.amount' => $money,
            'cash_flows.*.source_kind' => 'present|nullable|in:actual,projection', 'cash_flows.*.source_reference' => 'present|nullable|string|max:200', 'cash_flows.*.basis' => 'present|nullable|string|max:2000', 'notes' => 'present|nullable|string|max:2000'];
        if ($reason) {
            $rules['reason'] = OperationInput::reasonRule(request(), ['required', 'string', 'max:2000', 'regex:/\\S/u'], 2000);
        }

        return $rules;
    }

    public function store(Request $request)
    {
        return $this->write($request, 'create');
    }

    public function update(Request $request, string $id)
    {
        return $this->write($request, 'update', $id);
    }

    public function cancel(Request $request, string $id)
    {
        return $this->write($request, 'cancel', $id);
    }

    private function write(Request $request, string $action, ?string $id = null)
    {
        $rules = $action === 'cancel' ? ['reason' => OperationInput::reasonRule($request, ['required', 'string', 'max:2000', 'regex:/\\S/u'], 2000)] : $this->rules($action === 'update');
        $op = OperationInput::read($request, 'investment_scenario', $action, $rules, $id);
        if ($action !== 'cancel') {
            $raw = json_decode($request->getContent(), flags: JSON_THROW_ON_ERROR);
            abort_unless(is_array($raw->payload->cash_flows ?? null), 422, 'Arus kas harus list JSON.');
        }
        $result = app(InvestmentScenarioService::class)->mutate($request->user(), $op);

        return response()->json($result['body'], $result['status']);
    }

    public function index(Request $request)
    {
        $input = $request->validate(['greenhouse_id' => 'nullable|uuid', 'record_state' => 'nullable|in:active,cancelled', 'limit' => 'sometimes|integer|min:1|max:100', 'cursor' => 'nullable|string|max:512']);

        return app(ScopedRead::class)->run($request, function (User $actor) use ($input) {
            $q = app(InvestmentScenarioService::class)->scope($actor);
            foreach (['greenhouse_id', 'record_state'] as $key) {
                if (isset($input[$key])) {
                    $q->where($key, $input[$key]);
                }
            }
            $page = $q->orderByDesc('created_at')->orderByDesc('id')->cursorPaginate($input['limit'] ?? 50);

            return ['data' => collect($page->items())->map(fn ($r) => app(AcceptedSnapshot::class)->read('investment_scenario', $r)), 'next_cursor' => $page->nextCursor()?->encode()];
        });
    }

    public function show(Request $request, string $id)
    {
        return app(ScopedRead::class)->run($request, fn (User $actor) => app(AcceptedSnapshot::class)->read('investment_scenario', app(InvestmentScenarioService::class)->scope($actor)->where('id', $id)->first() ?? abort(404)));
    }

    public function audit(Request $request, string $id)
    {
        $input = $request->validate(['cursor' => 'nullable|integer|min:0', 'limit' => 'sometimes|integer|min:1|max:100']);

        return app(ScopedRead::class)->run($request, function (User $actor) use ($id, $input) {
            app(InvestmentScenarioService::class)->scope($actor)->where('id', $id)->first() ?? abort(404);
            $limit = (int) ($input['limit'] ?? 50);
            $rows = DB::table('audit_logs')->where('entity_type', 'investment_scenario')->where('entity_id', $id)->where('id', '>', $input['cursor'] ?? 0)->orderBy('id')->limit($limit + 1)->get();

            return ['data' => $rows->take($limit)->map(function ($r) {
                $a = User::findOrFail($r->actor_id);

                return ['id' => $r->id, 'actor_id' => $a->public_id, 'actor_name' => $a->name, 'action' => $r->action, 'version' => $r->version, 'reason' => $r->reason, 'changes' => json_decode($r->changes, true, flags: JSON_THROW_ON_ERROR), 'server_at' => CarbonImmutable::parse($r->server_at, 'UTC')->toISOString()];
            })->values(), 'next_cursor' => $rows->count() > $limit ? (string) $rows[$limit - 1]->id : null];
        });
    }
}
