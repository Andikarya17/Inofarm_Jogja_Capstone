<?php

namespace App\Http\Controllers\V2;

use App\Domain\Finance\FieldReports;
use App\Domain\Sync\ScopedRead;
use App\Http\Controllers\Controller;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;

final class FieldReportController extends Controller
{
    public function cashFlow(Request $request, FieldReports $reports)
    {
        $input = $request->validate(['from' => 'required|date_format:Y-m', 'until' => 'required|date_format:Y-m',
            'greenhouse_id' => 'nullable|uuid', 'format' => 'sometimes|in:json,csv']);
        $months = CarbonImmutable::parse($input['from'].'-01')->diffInMonths(CarbonImmutable::parse($input['until'].'-01'), false);
        abort_unless($months >= 0 && $months < 24, 422, 'Rentang bulan harus urut dan paling lama 24 bulan.');
        $report = app(ScopedRead::class)->run($request, fn (User $actor) => $reports->cashFlow($actor, $input['from'], $input['until'], $input['greenhouse_id'] ?? null));
        if (($input['format'] ?? 'json') === 'csv') {
            return response($reports->cashFlowCsv($report), 200, ['Content-Type' => 'text/csv; charset=UTF-8',
                'Content-Disposition' => 'attachment; filename="inofarm-cash-flow-'.$input['from'].'-'.$input['until'].'.csv"']);
        }

        return ['data' => $report];
    }

    public function compare(Request $request, FieldReports $reports)
    {
        $input = $request->validate(['season_ids' => 'required|array|min:1|max:10', 'season_ids.*' => 'required|uuid|distinct']);

        return app(ScopedRead::class)->run($request, fn (User $actor) => ['data' => $reports->compareSeasons($actor, array_values($input['season_ids']))]);
    }

    public function materialUsage(Request $request, string $id, FieldReports $reports)
    {
        return app(ScopedRead::class)->run($request, fn (User $actor) => ['data' => $reports->materialUsage($actor, $id)]);
    }
}
