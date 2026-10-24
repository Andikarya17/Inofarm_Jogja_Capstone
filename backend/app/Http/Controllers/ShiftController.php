<?php

namespace App\Http\Controllers;

use App\Models\ShiftVersion;
use App\Models\Tank;
use App\Models\Worker;
use App\Models\WorkerVersion;
use App\Services\ShiftResolver;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

class ShiftController extends Controller
{
    public function index(Request $request)
    {
        return ShiftVersion::where('tank_id', $request->user()->tank_id)->whereNull('effective_until')->orderBy('id')->paginate(100);
    }

    public function store(Request $request, ShiftResolver $resolver, ?string $key = null)
    {
        $input = $request->validate(['workerId' => 'required|integer', 'baseVersion' => 'present|nullable|integer', 'days' => 'required|array|min:1|max:7', 'days.*' => 'integer|between:1,7|distinct', 'startTime' => 'required|date_format:H:i', 'endTime' => 'required|date_format:H:i|different:startTime', 'startDate' => 'required|date_format:Y-m-d', 'endDate' => 'present|nullable|date_format:Y-m-d|after_or_equal:startDate']);

        return DB::transaction(function () use ($request, $resolver, $key, $input) {
            $tankId = $request->user()->tank_id;
            Tank::whereKey($tankId)->lockForUpdate()->firstOrFail();
            Worker::where('tank_id', $tankId)->findOrFail($input['workerId']);
            $worker = WorkerVersion::where('worker_id', $input['workerId'])->orderByDesc('effective_from')->orderByDesc('id')->first();
            abort_unless($worker?->active, 422, 'Pekerja tidak aktif.');
            $old = $key ? ShiftVersion::where('tank_id', $tankId)->where('shift_key', $key)->whereNull('effective_until')->firstOrFail() : null;
            abort_unless($old?->id === $input['baseVersion'], 409, 'Versi shift berubah.');
            $body = array_intersect_key($input, array_flip(['days', 'startTime', 'endTime', 'startDate', 'endDate']));
            $now = now();
            $candidate = new ShiftVersion(['shift_key' => $key ?? (string) Str::uuid(), 'tank_id' => $tankId, 'worker_id' => $input['workerId'], 'body' => $body, 'effective_from' => $now, 'created_by' => $request->user()->id]);
            foreach (ShiftVersion::where('tank_id', $tankId)->whereNull('effective_until')->get() as $other) {
                if ($other->id !== $old?->id && $resolver->overlaps($candidate, $other)) {
                    throw ValidationException::withMessages(['shift' => 'Jadwal tumpang tindih, termasuk lintas tengah malam.']);
                }
            }
            if ($old) {
                $old->update(['effective_until' => $now]);
            }
            $candidate->save();

            return $candidate;
        });
    }

    public function destroy(Request $request, string $key)
    {
        $input = $request->validate(['baseVersion' => 'required|integer']);

        return DB::transaction(function () use ($request, $key, $input) {
            Tank::whereKey($request->user()->tank_id)->lockForUpdate()->firstOrFail();
            $shift = ShiftVersion::where('tank_id', $request->user()->tank_id)->where('shift_key', $key)->whereNull('effective_until')->firstOrFail();
            abort_unless($shift->id === $input['baseVersion'], 409, 'Versi shift berubah.');
            $shift->update(['effective_until' => now()]);

            return response()->noContent();
        });
    }
}
