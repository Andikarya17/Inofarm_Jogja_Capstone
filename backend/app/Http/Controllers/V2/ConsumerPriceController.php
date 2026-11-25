<?php

namespace App\Http\Controllers\V2;

use App\Domain\Finance\ConsumerPriceService;
use App\Domain\Sync\ScopedRead;
use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\Request;

final class ConsumerPriceController extends Controller
{
    public function index(Request $request)
    {
        $input = $request->validate(['all' => 'sometimes|boolean']);

        return app(ScopedRead::class)->run($request, fn (User $actor) => app(ConsumerPriceService::class)->list($actor, (bool) ($input['all'] ?? false)));
    }

    public function store(Request $request)
    {
        return $this->write($request, 'create');
    }

    public function update(Request $request, string $id)
    {
        return $this->write($request, 'update', $id);
    }

    private function write(Request $request, string $action, ?string $id = null)
    {
        $op = OperationInput::read($request, 'consumer_price', $action, [
            'variety_name' => ['required', 'string', 'max:150', 'regex:/\S/u'],
            'quality' => 'required|in:saleable,defective',
            'price_per_kg' => ['required', 'string', 'regex:/^\d{1,14}(\.\d{1,6})?$/'],
            'record_state' => 'sometimes|in:active,inactive',
        ], $id);
        $result = app(ConsumerPriceService::class)->mutate($request->user(), $op);

        return response()->json($result['body'], $result['status']);
    }
}
