<?php

namespace App\Http\Controllers\V2;

use App\Domain\Finance\SeasonReportService;
use App\Domain\Sync\ScopedRead;
use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Validator;

final class SeasonReportController extends Controller
{
    private function rules(bool $create): array
    {
        $rules = ['season_id' => ['required', OperationInput::UUID], 'coverage' => 'required|array:costs,sales',
            'coverage.costs' => 'required|in:unknown,partial,complete', 'coverage.sales' => 'required|in:unknown,partial,complete',
            'known_pending_count' => 'present|nullable|integer|min:0|max:2147483647', 'requested_state' => 'required|in:provisional,reviewed_final',
            'review_note' => ['required', 'string', 'max:2000', 'regex:/\S/u']];
        if ($create) {
            $rules['expected_sources_hash'] = ['required', 'regex:/^[0-9a-f]{64}$/D'];
            $rules['expected_formula_version'] = 'required|string|max:100';
        }

        return $rules;
    }

    public function preview(Request $request)
    {
        $rules = $this->rules(false);
        $input = Validator::make(['input' => $request->all()], ['input' => 'required|array:season_id,coverage,known_pending_count,requested_state,review_note',
            ...collect($rules)->mapWithKeys(fn ($value, $key) => ['input.'.$key => $value])->all()])->validate()['input'];
        abort_unless($input['known_pending_count'] === null || is_int($input['known_pending_count']), 422, 'Jumlah antrean harus integer JSON atau null.');

        return app(ScopedRead::class)->run($request, fn (User $actor) => app(SeasonReportService::class)->preview($actor, $input));
    }

    public function store(Request $request)
    {
        $operation = OperationInput::read($request, 'season_report', 'create', $this->rules(true));
        abort_unless($operation['payload']['known_pending_count'] === null || is_int($operation['payload']['known_pending_count']), 422, 'Jumlah antrean harus integer JSON atau null.');
        $result = app(SeasonReportService::class)->create($request->user(), $operation);

        return response()->json($result['body'], $result['status']);
    }

    public function index(Request $request)
    {
        $input = $request->validate(['greenhouse_id' => 'nullable|uuid', 'season_id' => 'nullable|uuid', 'limit' => 'sometimes|integer|min:1|max:100', 'cursor' => 'nullable|string|max:512']);

        return app(ScopedRead::class)->run($request, function (User $actor) use ($input) {
            $service = app(SeasonReportService::class);
            $query = $service->scope($actor);
            foreach (['greenhouse_id', 'season_id'] as $key) {
                if (isset($input[$key])) {
                    $query->where($key, $input[$key]);
                }
            }
            $page = $query->orderByDesc('created_at')->orderByDesc('id')->cursorPaginate($input['limit'] ?? 50);

            return ['data' => collect($page->items())->map($service->publicRow(...)), 'next_cursor' => $page->nextCursor()?->encode()];
        });
    }

    public function show(Request $request, string $id)
    {
        return app(ScopedRead::class)->run($request, function (User $actor) use ($id) {
            $service = app(SeasonReportService::class);

            return $service->publicRow($service->scope($actor)->where('id', $id)->first() ?? abort(404));
        });
    }

    public function previewSources(Request $request)
    {
        $input = $request->validate(['season_id' => ['required', OperationInput::UUID], 'sources_hash' => ['required', 'regex:/^[0-9a-f]{64}$/D'],
            'cursor' => 'sometimes|integer|min:0|max:10000', 'limit' => 'sometimes|integer|min:1|max:100']);

        return app(ScopedRead::class)->run($request, fn (User $actor) => $this->page(app(SeasonReportService::class)->sources($actor, $input['season_id'], $input['sources_hash']), $input));
    }

    public function sources(Request $request, string $id)
    {
        $input = $request->validate(['cursor' => 'sometimes|integer|min:0|max:10000', 'limit' => 'sometimes|integer|min:1|max:100']);

        return app(ScopedRead::class)->run($request, function (User $actor) use ($id, $input) {
            $service = app(SeasonReportService::class);
            $row = $service->scope($actor)->where('id', $id)->first() ?? abort(404);

            return $this->page($service->full($row), $input) + ['snapshot_hash' => $row->snapshot_hash];
        });
    }

    private function page(array $data, array $input): array
    {
        $start = (int) ($input['cursor'] ?? 0);
        $limit = (int) ($input['limit'] ?? 50);
        $count = count($data['sources']);

        return ['data' => array_slice($data['sources'], $start, $limit), 'sources_hash' => $data['sources_hash'], 'source_count' => $count,
            'next_cursor' => $start + $limit < $count ? (string) ($start + $limit) : null];
    }
}
