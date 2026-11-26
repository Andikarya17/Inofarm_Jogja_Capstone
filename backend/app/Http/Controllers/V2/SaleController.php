<?php

namespace App\Http\Controllers\V2;

use App\Domain\Finance\SaleService;
use App\Domain\Sync\AcceptedSnapshot;
use App\Domain\Sync\ScopedRead;
use App\Http\Controllers\Controller;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

final class SaleController extends Controller
{
    public function index(Request $request)
    {
        $input = $request->validate(['greenhouse_id' => 'nullable|uuid', 'season_id' => 'nullable|uuid', 'record_state' => 'nullable|in:active,cancelled',
            'from' => 'nullable|date_format:Y-m-d', 'until' => 'nullable|date_format:Y-m-d'.($request->filled('from') ? '|after_or_equal:from' : ''), 'limit' => 'sometimes|integer|min:1|max:100', 'cursor' => 'nullable|string|max:512']);
        return app(ScopedRead::class)->run($request, function (User $actor) use ($input) {
            $q = app(SaleService::class)->scope($actor);
            foreach (['greenhouse_id', 'season_id', 'record_state'] as $key) {if (isset($input[$key])) {$q->where($key, $input[$key]);}}
            if (isset($input['from'])) {$q->where('paid_at', '>=', CarbonImmutable::parse($input['from'], 'Asia/Jakarta')->utc()->format('Y-m-d H:i:s'));}
            if (isset($input['until'])) {$q->where('paid_at', '<', CarbonImmutable::parse($input['until'], 'Asia/Jakarta')->addDay()->utc()->format('Y-m-d H:i:s'));}
            $page = $q->orderByDesc('paid_at')->orderByDesc('id')->cursorPaginate((int) ($input['limit'] ?? 50));
            return ['data' => collect($page->items())->map(fn ($row) => app(AcceptedSnapshot::class)->read('sale', $row)), 'next_cursor' => $page->nextCursor()?->encode()];
        });
    }
    public function show(Request $request, string $id)
    {
        return app(ScopedRead::class)->run($request, fn (User $actor) => app(AcceptedSnapshot::class)->read('sale', app(SaleService::class)->scope($actor)->where('id', $id)->first() ?? abort(404)));
    }
    public function store(Request $request) {return $this->perform($request, 'create');}
    public function update(Request $request, string $id) {return $this->perform($request, 'update', $id);}
    public function cancel(Request $request, string $id) {return $this->perform($request, 'cancel', $id);}
    private function perform(Request $request, string $action, ?string $id = null)
    {
        $rules = $action === 'cancel' ? [] : ['season_id' => ['required', OperationInput::UUID], 'paid_at' => 'required|date_format:Y-m-d\TH:i:sP',
            'payment_status' => 'required|in:paid', 'customer_name' => 'present|string|max:200',
            'payment_method' => 'sometimes|nullable|in:cash,qris,transfer', 'payment_reference' => 'sometimes|nullable|string|max:100', 'customer_type' => 'sometimes|nullable|in:consumer,retail,partner', 'notes' => 'present|string|max:2000',
            'customer' => 'sometimes|nullable|array:id,type,name,whatsapp', 'customer.id' => ['required_with:customer', OperationInput::UUID],
            'customer.type' => 'required_with:customer|in:consumer,retail,partner', 'customer.name' => ['required_with:customer', 'string', 'max:200', 'regex:/\S/u'],
            'customer.whatsapp' => 'present_with:customer|nullable|string|max:40', 'items' => 'required_without:operation.payload.lines|prohibits:operation.payload.lines|array|min:1|max:30',
            'items.*' => 'required|array:id,harvest_item_id,quantity_kg,price_per_kg,buyer_grade', 'items.*.buyer_grade' => 'sometimes|nullable|in:A,B,C', 'items.*.id' => ['required', OperationInput::UUID, 'distinct'],
            'items.*.harvest_item_id' => ['required', OperationInput::UUID], 'items.*.quantity_kg' => 'required|string|max:30', 'items.*.price_per_kg' => 'required|string|max:30',
            // Sale lines (25 Nov 2026): variety group + grade; the server takes the kg from the oldest harvests first.
            'lines' => 'required_without:operation.payload.items|array|min:1|max:30', 'lines.*' => 'required|array:id,planting_group_id,grade_id,quantity_kg,fruit_count,price_per_kg,buyer_grade',
            'lines.*.id' => ['required', OperationInput::UUID, 'distinct'], 'lines.*.planting_group_id' => ['required', OperationInput::UUID], 'lines.*.grade_id' => ['required', OperationInput::UUID],
            'lines.*.quantity_kg' => 'required|string|max:30', 'lines.*.price_per_kg' => 'required|string|max:30', 'lines.*.fruit_count' => 'sometimes|nullable|integer|min:1|max:4294967295',
            'lines.*.buyer_grade' => 'sometimes|nullable|in:A,B,C'];
        if ($action !== 'create') {$rules['reason'] = OperationInput::reasonRule($request, ['required', 'string', 'max:1000', 'regex:/\S/u']);}
        $result = app(SaleService::class)->mutate($request->user(), OperationInput::read($request, 'sale', $action, $rules, $id));
        return response()->json($result['body'], $result['status']);
    }
    public function audit(Request $request, string $id)
    {
        $input = $request->validate(['cursor' => 'nullable|integer|min:0', 'limit' => 'sometimes|integer|min:1|max:100']);
        return app(ScopedRead::class)->run($request, function (User $actor) use ($id, $input) {
            app(SaleService::class)->scope($actor)->where('id', $id)->first() ?? abort(404);$limit = (int) ($input['limit'] ?? 50);
            $rows = DB::table('audit_logs')->where('entity_type', 'sale')->where('entity_id', $id)->where('id', '>', $input['cursor'] ?? 0)->orderBy('id')->limit($limit + 1)->get();
            return ['data' => $rows->take($limit)->map(function ($row) {
                $actor = User::findOrFail($row->actor_id);
                return ['id' => $row->id, 'actor_id' => $actor->public_id, 'actor_name' => $actor->name, 'action' => $row->action, 'version' => $row->version,
                    'reason' => $row->reason, 'changes' => json_decode($row->changes, true, flags: JSON_THROW_ON_ERROR), 'server_at' => CarbonImmutable::parse($row->server_at, 'UTC')->toISOString()];
            })->values(), 'next_cursor' => $rows->count() > $limit ? (string) $rows[$limit - 1]->id : null];
        });
    }
}
