<?php

namespace App\Http\Controllers\V2;

use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\ChangeCursor;
use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Laravel\Sanctum\PersonalAccessToken;

final class ChangeFeedController extends Controller
{
    public function __invoke(Request $request, GreenhouseAccess $access, ChangeCursor $cursor)
    {
        $input = $request->validate(['cursor' => 'nullable|string|max:1024', 'limit' => 'sometimes|integer|min:1|max:100']);

        return DB::transaction(function () use ($request, $input, $access, $cursor) {
            $epoch = DB::table('sync_state')->where('id', 1)->lockForUpdate()->value('epoch');
            $actor = User::findOrFail($request->user()->id);
            abort_unless($actor->active && in_array($actor->role, ['worker', 'manager'], true), 403, 'Hak akses akun telah berubah.');
            $token = $request->user()->currentAccessToken();
            if ($token instanceof PersonalAccessToken) {
                abort_unless(DB::table('personal_access_tokens')->where('id', $token->id)
                    ->where(fn ($q) => $q->whereNull('expires_at')->orWhere('expires_at', '>', now()))->exists(), 401, 'Sesi telah dicabut atau berakhir.');
            }
            $position = isset($input['cursor']) ? $cursor->decode($input['cursor'], $actor, $epoch) : ['after' => 0, 'upper' => null];
            $upper = $position['upper'] ?? (int) (DB::table('change_feed')->max('sequence') ?? 0);
            $limit = (int) ($input['limit'] ?? 50);
            $q = DB::table('change_feed')->whereIn('greenhouse_id', $access->ids($actor))->where('sequence', '>', $position['after'])->where('sequence', '<=', $upper);
            if ($actor->role !== 'manager') {
                $q->where('audience', 'all');
            }
            $rows = $q->orderBy('sequence')->limit($limit + 1)->get();
            $more = $rows->count() > $limit;
            $after = $more ? $rows[$limit - 1]->sequence : $upper;
            $scope = $access->query($actor)->orderBy('id')->limit(1001)->get(['id', 'active', 'version']);
            abort_if($scope->count() > 1000, 422, 'Scope terlalu besar untuk protokol ini; hubungi pengelola.');

            return ['schema_version' => '2', 'sync_epoch' => $epoch, 'scope' => $scope, 'has_more' => $more,
                'data' => $rows->take($limit)->map(fn ($row) => ['sequence' => $row->sequence, 'entity_type' => $row->entity_type, 'entity_id' => $row->entity_id,
                    'greenhouse_id' => $row->greenhouse_id, 'version' => $row->version, 'tombstone' => (bool) $row->tombstone,
                    'data' => json_decode($row->body, true, flags: JSON_THROW_ON_ERROR)])->values(),
                'next_cursor' => $cursor->encode($actor, $epoch, $after, $more ? $upper : null)];
        });
    }
}
