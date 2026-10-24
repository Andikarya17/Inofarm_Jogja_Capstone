<?php

namespace App\Http\Controllers;

use App\Models\ConfigVersion;
use App\Models\Device;
use App\Models\Event;
use App\Models\PhaseVersion;
use App\Services\ConfigurationStore;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;

class ReadController extends Controller
{
    public function dashboard(Request $request, ConfigurationStore $store)
    {
        $tankId = $request->user()->tank_id;
        $config = $store->latest(ConfigVersion::class, $tankId);
        $device = Device::where('tank_id', $tankId)->first();
        $state = 'unknown';
        $stale = $config?->body['deviceStaleSeconds'] ?? null;
        if (! $device?->last_contact_at) {
            $state = 'never_seen';
        } elseif ($stale !== null) {
            $state = $device->last_contact_at->addSeconds($stale)->isPast() ? 'disconnected' : 'recent_contact';
        }
        $latest = fn ($kind) => Event::where('tank_id', $tankId)->where('kind', $kind)->where('clock_trusted', true)->whereNotNull('source_at')->orderByDesc('source_at')->orderByDesc('id')->first();
        $receipt = fn ($kind) => Event::where('tank_id', $tankId)->where('kind', $kind)->orderByDesc('id')->first();

        return ['device' => $device, 'connectionState' => $state, 'telemetry' => $latest('telemetry'), 'latestTelemetryReceipt' => $receipt('telemetry'), 'latestRecord' => $latest('record'), 'latestRecordReceipt' => $receipt('record'), 'activePhase' => $store->latest(PhaseVersion::class, $tankId), 'configVersion' => $config?->id, 'serverTime' => now()->toISOString()];
    }

    private function query(Request $request, string $kind)
    {
        $input = $request->validate(['from' => 'nullable|date_format:Y-m-d', 'to' => 'nullable|date_format:Y-m-d|after_or_equal:from', 'status' => 'nullable|in:valid,issues,incomplete', 'limit' => 'nullable|integer|between:1,100', 'page' => 'nullable|integer|min:1']);
        $query = Event::where('tank_id', $request->user()->tank_id)->where('kind', $kind);
        if ($input['from'] ?? null) {
            $query->where('source_at', '>=', CarbonImmutable::parse($input['from'], 'Asia/Jakarta')->utc());
        }
        if ($input['to'] ?? null) {
            $query->where('source_at', '<', CarbonImmutable::parse($input['to'], 'Asia/Jakarta')->addDay()->utc());
        }
        if ($input['status'] ?? null) {
            $query->where('validation_result->status', $input['status']);
        }

        // Immutable receipt sequence keeps cursor pagination stable even for invalid/null clocks.
        return $query->orderByDesc('id');
    }

    public function history(Request $request)
    {
        return $this->query($request, 'record')->cursorPaginate((int) $request->input('limit', 25));
    }

    public function telemetry(Request $request)
    {
        return $this->query($request, 'telemetry')->cursorPaginate((int) $request->input('limit', 25));
    }

    public function export(Request $request)
    {
        $rows = $this->query($request, 'record')->limit(1001)->get();
        abort_if($rows->count() > 1000, 422, 'Ekspor maksimum 1000 catatan; persempit filter.');

        return response()->streamDownload(function () use ($rows) {
            $file = fopen('php://output', 'w');
            fputcsv($file, ['Konfirmasi perangkat (RFC3339)', 'Clock dipercaya server', 'Pengukuran handheld (RFC3339)', 'Waktu penerimaan (UTC)', 'Waktu evaluasi (UTC)', 'Fase pekerja', 'Fase owner saat catatan', 'PPM manual', 'Status', 'Pekerja terjadwal', 'Versi konfigurasi', 'Versi fase', 'Versi aturan', 'Seluruh pemeriksaan'], ',', '"', '');
            foreach ($rows as $row) {
                $safe = fn ($s) => preg_match('/^[\s]*[=+\-@]/u', (string) $s) ? "'".$s : $s;
                fputcsv($file, array_map($safe, [$row->body['confirmedAt'], $row->clock_trusted ? 'true' : 'false', $row->body['measuredAt'], $row->received_at->toISOString(), $row->validation_result['evaluatedAt'], $row->phase, $row->validation_result['ownerPhaseSnapshot']['phase'] ?? '', $row->manual_ppm, $row->validation_result['status'], $row->assignment['workerNameSnapshot'] ?? '', $row->config_version_id, $row->phase_version_id, $row->validation_result['ruleVersion'], json_encode($row->validation_result['checks'], JSON_UNESCAPED_UNICODE)]), ',', '"', '');
            }
            fclose($file);
        }, 'inofarm-history.csv', ['Content-Type' => 'text/csv; charset=UTF-8']);
    }
}
