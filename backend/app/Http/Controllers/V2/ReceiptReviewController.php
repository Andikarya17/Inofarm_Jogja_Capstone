<?php

namespace App\Http\Controllers\V2;

use App\Domain\Contracts\CanonicalJson;
use App\Domain\Contracts\DomainFailure;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\ScopedRead;
use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;

final class ReceiptReviewController extends Controller
{
    public function __invoke(Request $request, ScopedRead $reads, GreenhouseAccess $access)
    {
        $rules = ['sync_epoch' => 'required|uuid', 'operation' => 'required|array:schema_version,sync_epoch,operation_id,entity_id,entity_type,action,base_version,payload', 'operation.schema_version' => 'required|in:2', 'operation.action' => 'required|in:create,update,cancel', 'operation.base_version' => 'present|nullable|integer|min:1|max:2147483647', 'operation.payload' => 'required|array', 'operation.operation_id' => 'required|uuid', 'operation.entity_id' => 'required|uuid', 'operation.sync_epoch' => 'required|uuid',
            'operation.entity_type' => 'required|in:activity,harvest,expense,cost_allocation,sale,asset,depreciation_allocation,season_report,investment_scenario,investment_report,activity_attachment,harvest_attachment,expense_attachment'];
        foreach (['greenhouse_id', 'planting_group_id', 'season_id', 'expense_id', 'asset_id', 'scenario_id', 'owner_id'] as $field) {
            $rules['operation.payload.'.$field] = ['sometimes', OperationInput::UUID];
        }
        $input = $request->validate($rules);

        // Preserve the complete original wire envelope for its immutable receipt hash.
        $input['operation'] = $request->input('operation');
        abort_if(array_diff(array_keys($request->all()), ['sync_epoch', 'operation']) !== [], 422);
        abort_unless($input['operation']['schema_version'] === '2' && ($input['operation']['base_version'] === null || is_int($input['operation']['base_version'])), 422);
        try {
            CanonicalJson::encode($input['operation']);
        } catch (\InvalidArgumentException) {
            throw ValidationException::withMessages(['operation' => 'Desimal pada operasi asli harus berupa string.']);
        }

        return $reads->run($request, function (User $actor) use ($input, $access) {
            $epoch = DB::table('sync_state')->where('id', 1)->value('epoch');
            $op = $input['operation'];
            if ($input['sync_epoch'] !== $epoch) {
                throw new DomainFailure('epoch_mismatch', 'Identitas server berubah lagi. Periksa ulang antrean.');
            }
            abort_if($op['sync_epoch'] === $epoch, 422, 'Operasi ini masih menggunakan identitas server saat ini.');
            if (! in_array($op['entity_type'], ['activity', 'harvest', 'activity_attachment', 'harvest_attachment'], true)) {
                $access->manager($actor);
            }
            $row = DB::table('sync_operations')->where('actor_id', $actor->id)->where('operation_id', $op['operation_id'])->first();
            $hash = CanonicalJson::hash($op);
            $result = ['sync_epoch' => $epoch, 'operation_id' => $op['operation_id'], 'entity_id' => $op['entity_id'], 'request_hash' => $hash];
            if (! $row) {
                // Missing receipt is ambiguous after recovery. Never infer it is safe to replay.
                $gh = $this->greenhouseForMissing($op);
                abort_unless(is_string($gh), 404);
                $access->require($actor, $gh);

                return $result + ['state' => 'manual_review', 'receipt' => null, 'message' => 'Tanda terima tidak ditemukan pada titik pemulihan. Bandingkan sumber/versi bersama pengelola; operasi lama tetap ditahan dan tidak dikirim ulang.'];
            }
            abort_unless($row->entity_id === $op['entity_id'] && $row->entity_type === $op['entity_type'] && hash_equals($row->request_hash, $hash), 409, 'Isi operasi berbeda dari tanda terima historis.');
            $receipt = json_decode($row->receipt, true, flags: JSON_THROW_ON_ERROR);
            $data = $receipt['data'];
            $gh = $data['greenhouse_id'] ?? $data['scope']['greenhouse']['id'] ?? null;
            if ($gh === null && isset($data['planting_group_id'])) {
                $gh = DB::table('planting_groups')->join('seasons', 'seasons.id', '=', 'planting_groups.season_id')->where('planting_groups.id', $data['planting_group_id'])->value('seasons.greenhouse_id');
            }
            abort_unless(is_string($gh), 404);
            $access->require($actor, $gh);

            return $result + ['state' => 'receipt_found', 'receipt' => $receipt, 'message' => 'Tanda terima historis cocok. Ini mengakui penerimaan lama, bukan mengirim atau mengubah transaksi.'];
        });
    }

    private function greenhouseForMissing(array $op): ?string
    {
        $tables = ['activity' => 'activities', 'harvest' => 'harvests', 'expense' => 'expenses', 'cost_allocation' => 'cost_allocations', 'sale' => 'sales', 'asset' => 'assets', 'depreciation_allocation' => 'depreciation_allocations', 'investment_scenario' => 'investment_scenarios', 'season_report' => 'report_snapshots', 'investment_report' => 'report_snapshots', 'activity_attachment' => 'record_attachments', 'harvest_attachment' => 'record_attachments', 'expense_attachment' => 'record_attachments'];
        $current = DB::table($tables[$op['entity_type']])->where('id', $op['entity_id'])->first();
        if ($current) {
            return $current->greenhouse_id ?? (isset($current->planting_group_id) ? $this->groupGreenhouse($current->planting_group_id) : null);
        }
        $p = $op['payload'];
        if (isset($p['planting_group_id'])) {
            return $this->groupGreenhouse($p['planting_group_id']);
        }
        foreach (['season_id' => 'seasons', 'expense_id' => 'expenses', 'asset_id' => 'assets', 'scenario_id' => 'investment_scenarios'] as $key => $table) {
            if (isset($p[$key])) {
                return DB::table($table)->where('id', $p[$key])->value('greenhouse_id');
            }
        }
        if (isset($p['owner_id'],$p['owner_type'])) {
            abort_unless($op['entity_type'] === $p['owner_type'].'_attachment' && in_array($p['owner_type'], ['activity', 'harvest', 'expense'], true), 422);
            $row = DB::table(['activity' => 'activities', 'harvest' => 'harvests', 'expense' => 'expenses'][$p['owner_type']])->where('id', $p['owner_id'])->first();

            return $row?->greenhouse_id ?? (isset($row->planting_group_id) ? $this->groupGreenhouse($row->planting_group_id) : null);
        }

        return $p['greenhouse_id'] ?? null;
    }

    private function groupGreenhouse(string $id): ?string
    {
        return DB::table('planting_groups')->join('seasons', 'seasons.id', '=', 'planting_groups.season_id')->where('planting_groups.id', $id)->value('seasons.greenhouse_id');
    }
}
