<?php

namespace App\Http\Controllers\V2;

use App\Domain\Contracts\DomainFailure;
use App\Domain\Recovery\BackupService;
use App\Domain\Recovery\RecoveryPackage;
use App\Domain\Sync\ScopedRead;
use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

final class RecoveryController extends Controller
{
    public function __construct(private ScopedRead $reads, private BackupService $backups, private RecoveryPackage $packages) {}

    private function run(Request $request, callable $read): mixed
    {
        return $this->reads->run($request, function (User $actor) use ($read) {
            $this->backups->authorize($actor);

            return $read($actor);
        });
    }

    private function pageQuery(string $table, array $input)
    {
        $q = DB::table($table);
        if (isset($input['before'])) {
            $anchor = DB::table($table)->where('id', $input['before'])->first() ?? abort(404);
            $q->where(fn ($q) => $q->where('created_at', '<', $anchor->created_at)->orWhere(fn ($q) => $q->where('created_at', $anchor->created_at)->where('id', '<', $anchor->id)));
        }

        return $q;
    }

    public function policy(Request $request)
    {
        return $this->reads->run($request, function (User $actor) {
            abort_unless($actor->role === 'manager', 403);

            return ['data' => ['available' => in_array($actor->public_id, config('recovery.operators', []), true), 'schedule' => null, 'retention' => null, 'rpo' => null, 'rto' => null, 'production_restore_available' => false,
                'message' => 'Cadangan sistem memerlukan kewenangan operator server. Validasi paket berbeda dari restore terverifikasi; pemulihan produksi tidak tersedia melalui aplikasi.']];
        });
    }

    public function restores(Request $request)
    {
        $input = $request->validate(['before' => 'nullable|uuid', 'limit' => 'sometimes|integer|min:1|max:50']);

        return $this->run($request, function () use ($input) {
            $rows = $this->pageQuery('restore_runs', $input)->orderByDesc('created_at')->orderByDesc('id')->limit(($input['limit'] ?? 20) + 1)->get();
            $more = $rows->count() > ($input['limit'] ?? 20);
            $rows = $rows->take($input['limit'] ?? 20);

            return ['data' => $rows->map(fn ($r) => ['id' => $r->id, 'backup_id' => $r->backup_id, 'state' => $r->state, 'manifest_hash' => $r->manifest_hash, 'old_epoch' => $r->old_epoch, 'new_epoch' => $r->new_epoch, 'operator_reference' => $r->operator_reference, 'reason' => $r->reason, 'created_at' => $r->created_at, 'verification' => json_decode($r->verification, true, flags: JSON_THROW_ON_ERROR)])->values(), 'next_cursor' => $more ? $rows->last()->id : null];
        });
    }

    public function index(Request $request)
    {
        $input = $request->validate(['before' => 'nullable|uuid', 'limit' => 'sometimes|integer|min:1|max:50']);

        return $this->run($request, function () use ($input) {
            $rows = $this->pageQuery('backup_runs', $input)->orderByDesc('created_at')->orderByDesc('id')->limit(($input['limit'] ?? 20) + 1)->get();
            $more = $rows->count() > ($input['limit'] ?? 20);
            $rows = $rows->take($input['limit'] ?? 20);

            return ['data' => $rows->map($this->backups->publicRow(...))->values(), 'next_cursor' => $more ? $rows->last()->id : null];
        });
    }

    public function show(Request $request, string $id)
    {
        return $this->run($request, fn () => ['data' => $this->backups->publicRow(DB::table('backup_runs')->where('id', $id)->first() ?? abort(404))]);
    }

    public function store(Request $request)
    {
        $input = $request->validate(['request_id' => ['required', 'uuid', 'regex:/^[0-9a-f-]{36}$/D'], 'reason' => 'required|string|min:1|max:1000']);
        abort_if(trim($input['reason']) === '' || array_diff(array_keys($request->all()), ['request_id', 'reason']) !== [], 422);
        $row = $this->run($request, fn ($actor) => $this->backups->request($actor, $input));
        try {
            $this->run($request, fn ($actor) => $this->backups->capture($actor, $row->id));
        } catch (\Throwable $e) {
            DB::table('backup_runs')->where('id', $row->id)->where('state', 'requested')->update(['state' => 'failed', 'updated_at' => now()]);
            report($e);
            throw new DomainFailure('backup_failed', 'Cadangan belum berhasil. Tinjau status; paket parsial tidak boleh digunakan.', 500);
        }

        return $this->show($request, $row->id);
    }

    public function validatePackage(Request $request, string $id)
    {
        $input = $request->validate(['expected_manifest_hash' => ['required', 'string', 'regex:/^[0-9a-f]{64}$/']]);

        return $this->run($request, function () use ($id, $input) {
            $row = DB::table('backup_runs')->where('id', $id)->where('state', 'completed')->first() ?? abort(404);
            abort_unless(hash_equals($row->manifest_hash, $input['expected_manifest_hash']), 409);
            try {
                $m = $this->packages->validate($id, $row->manifest_hash);
            } catch (\Throwable $e) {
                report($e);
                throw new DomainFailure('backup_integrity_failed', 'Paket cadangan tidak lolos pemeriksaan kelengkapan, checksum atau schema.', 409);
            }

            return ['data' => ['id' => $id, 'manifest_hash' => $row->manifest_hash, 'state' => 'package_validated', 'restore_verified' => false, 'tables' => count($m['tables']), 'files' => count($m['files']), 'message' => 'Integritas paket cocok. Restore belum dibuktikan; jalankan drill terisolasi melalui operator server.']];
        });
    }
}
