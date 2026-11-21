<?php

namespace App\Http\Controllers\V2;

use App\Domain\Finance\CustomerService;
use App\Domain\Sync\ScopedRead;
use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

final class CustomerController extends Controller
{
    public function index(Request $request)
    {
        $input = $request->validate(['type' => 'nullable|in:consumer,partner', 'q' => 'nullable|string|max:100',
            'limit' => 'sometimes|integer|min:1|max:100', 'cursor' => 'nullable|string|max:500']);

        return app(ScopedRead::class)->run($request, fn (User $actor) => app(CustomerService::class)->search($actor, $input['type'] ?? null,
            $input['q'] ?? null, (int) ($input['limit'] ?? 50), $input['cursor'] ?? null));
    }

    public function show(Request $request, string $id)
    {
        return app(ScopedRead::class)->run($request, function (User $actor) use ($id) {
            app(CustomerService::class)->search($actor, null, null, 1, null);
            $row = DB::table('customers')->where('id', $id)->first() ?? abort(404);

            return ['data' => app(CustomerService::class)->publicRow($row)];
        });
    }

    /**
     * Excel import. Up to 1.000 rows per request; this route accepts bodies up to 128 KiB (the default 16 KiB limit
     * would force ~40 requests for one sheet), and the client still sends rows in chunks.
     */
    public function import(Request $request)
    {
        $input = $request->validate(['dry_run' => 'required|boolean', 'type' => 'required|in:consumer,partner',
            'rows' => 'required|array|min:1|max:'.CustomerService::MAX_IMPORT_ROWS, 'rows.*' => 'required|array:row,name,whatsapp,id', 'rows.*.id' => ['sometimes', OperationInput::UUID],
            'rows.*.row' => 'required|integer|min:1', 'rows.*.name' => 'present|nullable|string|max:200', 'rows.*.whatsapp' => 'present|nullable|string|max:40']);

        return app(ScopedRead::class)->run($request, fn (User $actor) => app(CustomerService::class)->import($actor, $input['type'], $input['rows'], (bool) $input['dry_run']));
    }
}
