<?php

namespace App\Http\Controllers\V2;

use App\Domain\Harvest\HarvestReviewService;
use App\Domain\Sync\ScopedRead;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Validation\ValidationException;

final class HarvestReviewController
{
    public function index(Request $request, HarvestReviewService $reviews, ScopedRead $reads)
    {
        $input = $request->validate([
            'greenhouse_id' => ['nullable', OperationInput::UUID],
            'status' => 'nullable|in:pending,decided,valid,invalid,legacy',
            ...$this->pagination(),
        ]);

        return $reads->run($request, fn (User $actor) => $reviews->inbox($actor, $input));
    }

    public function show(Request $request, string $id, HarvestReviewService $reviews, ScopedRead $reads)
    {
        $input = $request->validate(['harvest_version' => 'sometimes|integer|min:1|max:2147483647', ...$this->pagination()]);

        return $reads->run($request, fn (User $actor) => $reviews->detail($actor, $id, $input));
    }

    public function store(Request $request, HarvestReviewService $reviews)
    {
        $operation = OperationInput::read($request, 'harvest_review', 'create', [
            'harvest_id' => ['required', OperationInput::UUID],
            'harvest_version' => 'required|integer:strict|min:1|max:2147483647',
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
