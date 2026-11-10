<?php

namespace App\Http\Controllers\V2;

use App\Domain\Activities\ActivityReviewService;
use App\Domain\Sync\ScopedRead;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Validation\ValidationException;

final class ActivityReviewController
{
    public function index(Request $request, ActivityReviewService $reviews, ScopedRead $reads)
    {
        $input = $request->validate([
            'greenhouse_id' => ['nullable', OperationInput::UUID], 'worker_id' => ['nullable', OperationInput::UUID],
            'activity_type' => 'nullable|in:measurement,nutrition,spray,general,stage',
            'status' => 'nullable|in:pending,decided,valid,invalid,legacy',
            'from' => 'nullable|date_format:Y-m-d',
            'until' => 'nullable|date_format:Y-m-d'.($request->filled('from') ? '|after_or_equal:from' : ''),
            ...$this->pagination(),
        ]);

        return $reads->run($request, fn (User $actor) => $reviews->inbox($actor, $input));
    }

    public function show(Request $request, string $id, ActivityReviewService $reviews, ScopedRead $reads)
    {
        $input = $request->validate(['activity_version' => 'sometimes|integer|min:1|max:2147483647', ...$this->pagination()]);

        return $reads->run($request, fn (User $actor) => $reviews->detail($actor, $id, $input));
    }

    public function recorders(Request $request, ActivityReviewService $reviews, ScopedRead $reads)
    {
        $input = $request->validate(['greenhouse_id' => ['nullable', OperationInput::UUID], ...$this->pagination()]);

        return $reads->run($request, fn (User $actor) => $reviews->recorders($actor, $input));
    }

    public function store(Request $request, ActivityReviewService $reviews)
    {
        $operation = OperationInput::read($request, 'activity_review', 'create', [
            'activity_id' => ['required', OperationInput::UUID],
            'activity_version' => 'required|integer:strict|min:1|max:2147483647',
            'decision' => 'required|in:valid,invalid',
            'reason' => 'required_if:operation.payload.decision,invalid|nullable|string|max:1000',
        ]);
        if ($operation['payload']['decision'] === 'invalid' && trim($operation['payload']['reason'] ?? '') === '') {
            throw ValidationException::withMessages(['reason' => 'Alasan tidak valid wajib diisi.']);
        }
        $result = $reviews->mutate($request->user(), $operation);

        return response()->json($result['body'], $result['status']);
    }

    private function pagination(): array
    {
        return ['limit' => 'sometimes|integer|min:1|max:50', 'cursor' => 'nullable|string|max:512'];
    }
}
