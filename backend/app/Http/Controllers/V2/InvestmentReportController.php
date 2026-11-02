<?php

namespace App\Http\Controllers\V2;

use App\Domain\Finance\InvestmentReportService;
use App\Domain\Sync\ScopedRead;
use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Validator;

final class InvestmentReportController extends Controller
{
    private function rules(bool $create): array
    {
        $rules = ['scenario_id' => ['required', OperationInput::UUID], 'review_note' => ['required', 'string', 'max:2000', 'regex:/\\S/u']];
        if ($create) {
            $rules['expected_scenario_version'] = 'required|integer|min:1|max:2147483647';
            $rules['expected_sources_hash'] = ['required', 'regex:/^[0-9a-f]{64}$/D'];
            $rules['expected_formula_version'] = 'required|string|max:100';
        }

        return $rules;
    }

    public function preview(Request $request)
    {
        $rules = $this->rules(false);
        $input = Validator::make(['input' => $request->all()], ['input' => 'required|array:'.implode(',', array_keys($rules)), ...collect($rules)->mapWithKeys(fn ($v, $k) => ['input.'.$k => $v])->all()])->validate()['input'];

        return app(ScopedRead::class)->run($request, fn (User $actor) => app(InvestmentReportService::class)->preview($actor, $input));
    }

    public function store(Request $request)
    {
        $op = OperationInput::read($request, 'investment_report', 'create', $this->rules(true));
        abort_unless(is_int($op['payload']['expected_scenario_version']), 422, 'Versi skenario harus integer JSON.');
        $result = app(InvestmentReportService::class)->create($request->user(), $op);

        return response()->json($result['body'], $result['status']);
    }

    public function index(Request $request)
    {
        $input = $request->validate(['greenhouse_id' => 'nullable|uuid', 'scenario_id' => 'nullable|uuid', 'limit' => 'sometimes|integer|min:1|max:100', 'cursor' => 'nullable|string|max:512']);

        return app(ScopedRead::class)->run($request, function (User $actor) use ($input) {
            $service = app(InvestmentReportService::class);
            $q = $service->scope($actor);
            foreach (['greenhouse_id', 'scenario_id'] as $key) {
                if (isset($input[$key])) {
                    $q->where($key, $input[$key]);
                }
            }$page = $q->orderByDesc('created_at')->orderByDesc('id')->cursorPaginate($input['limit'] ?? 50);

            return ['data' => collect($page->items())->map($service->publicRow(...)), 'next_cursor' => $page->nextCursor()?->encode()];
        });
    }

    public function show(Request $request, string $id)
    {
        return app(ScopedRead::class)->run($request, function (User $actor) use ($id) {
            $service = app(InvestmentReportService::class);

            return $service->publicRow($service->scope($actor)->where('id', $id)->first() ?? abort(404));
        });
    }

    public function sources(Request $request, string $id)
    {
        $input = $request->validate(['cursor' => 'sometimes|integer|min:0|max:101', 'limit' => 'sometimes|integer|min:1|max:100']);

        return app(ScopedRead::class)->run($request, function (User $actor) use ($id, $input) {
            $service = app(InvestmentReportService::class);
            $row = $service->scope($actor)->where('id', $id)->first() ?? abort(404);
            $full = $service->full($row);
            $offset = (int) ($input['cursor'] ?? 0);
            $limit = (int) ($input['limit'] ?? 25);
            $count = count($full['sources']);

            return ['data' => array_slice($full['sources'], $offset, $limit), 'source_count' => $count, 'sources_hash' => $row->sources_hash, 'snapshot_hash' => $row->snapshot_hash, 'next_cursor' => $offset + $limit < $count ? (string) ($offset + $limit) : null];
        });
    }
}
