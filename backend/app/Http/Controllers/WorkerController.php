<?php

namespace App\Http\Controllers;

use App\Models\ShiftVersion;
use App\Models\Tank;
use App\Models\Worker;
use App\Models\WorkerVersion;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class WorkerController extends Controller
{
    public function index(Request $request)
    {
        return Worker::where('tank_id', $request->user()->tank_id)->orderBy('id')->paginate(100)->through(function ($worker) {
            $version = WorkerVersion::where('worker_id', $worker->id)->orderByDesc('effective_from')->orderByDesc('id')->first();

            return ['id' => $worker->id, 'version' => $version->id, 'name' => $version->name, 'active' => $version->active];
        });
    }

    public function store(Request $request)
    {
        $input = $request->validate(['name' => 'required|string|max:100|not_regex:/[\x00-\x1f]/']);

        return DB::transaction(function () use ($input, $request) {
            Tank::whereKey($request->user()->tank_id)->lockForUpdate()->firstOrFail();
            $worker = Worker::create(['tank_id' => $request->user()->tank_id]);
            $version = WorkerVersion::create(['worker_id' => $worker->id, 'name' => $input['name'], 'active' => true, 'effective_from' => now()]);

            return response()->json(['id' => $worker->id, 'version' => $version->id, 'name' => $version->name, 'active' => true], 201);
        });
    }

    public function update(Request $request, int $id)
    {
        $input = $request->validate(['name' => 'required|string|max:100|not_regex:/[\x00-\x1f]/', 'active' => 'required|boolean', 'baseVersion' => 'required|integer']);

        return DB::transaction(function () use ($request, $id, $input) {
            Tank::whereKey($request->user()->tank_id)->lockForUpdate()->firstOrFail();
            $worker = Worker::where('tank_id', $request->user()->tank_id)->findOrFail($id);
            $latest = WorkerVersion::where('worker_id', $id)->orderByDesc('effective_from')->orderByDesc('id')->firstOrFail();
            abort_unless($latest->id === $input['baseVersion'], 409, 'Versi pekerja berubah.');
            $version = WorkerVersion::create(['worker_id' => $id, 'name' => $input['name'], 'active' => $input['active'], 'effective_from' => now()]);
            if (! $input['active']) {
                ShiftVersion::where('tank_id', $worker->tank_id)->where('worker_id', $id)->whereNull('effective_until')->update(['effective_until' => now()->format('Y-m-d H:i:s.u')]);
            }

            return ['id' => $id, 'version' => $version->id, 'name' => $version->name, 'active' => $version->active];
        });
    }

    public function destroy(Request $request, int $id)
    {
        Worker::where('tank_id', $request->user()->tank_id)->findOrFail($id);
        $latest = WorkerVersion::where('worker_id', $id)->orderByDesc('effective_from')->orderByDesc('id')->firstOrFail();
        $request->merge(['name' => $latest->name, 'active' => false]);

        return $this->update($request, $id);
    }
}
