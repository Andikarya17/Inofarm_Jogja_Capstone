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
        $input = $request->validate(['cursor' => 'nullable|string|max:1024', 'limit' => 'sometimes|integer|min:1|max:100',
            'activity_reviews' => 'sometimes|in:1', 'measurement_review_status' => 'sometimes|in:1']);

        return DB::transaction(function () use ($request, $input, $access, $cursor) {
            $epoch = DB::table('sync_state')->where('id', 1)->lockForUpdate()->value('epoch');
            $actor = User::findOrFail($request->user()->id);
            abort_unless($actor->active && in_array($actor->role, ['worker', 'manager'], true), 403, 'Hak akses akun telah berubah.');
            $token = $request->user()->currentAccessToken();
            if ($token instanceof PersonalAccessToken) {
                abort_unless(DB::table('personal_access_tokens')->where('id', $token->id)
                    ->where(fn ($q) => $q->whereNull('expires_at')->orWhere('expires_at', '>', now()))->exists(), 401, 'Sesi telah dicabut atau berakhir.');
            }
            $scope = $access->query($actor)->orderBy('id')->limit(1001)->get(['id', 'active', 'version']);
            abort_if($scope->count() > 1000, 422, 'Scope terlalu besar untuk protokol ini; hubungi pengelola.');
            $ids = $scope->pluck('id')->all();
            $reviewSupport = isset($input['activity_reviews']);
            $measurementStatus = $reviewSupport && isset($input['measurement_review_status']) && $actor->role === 'worker';
            $scopeKey = hash('sha256', $actor->role.'|'.implode(',', $ids)
                .($reviewSupport ? '|activity_reviews' : '').($measurementStatus ? '|measurement_review_status' : ''));
            $position = isset($input['cursor']) ? $cursor->decode($input['cursor'], $actor, $epoch) : [];
            if (($position['scope'] ?? null) !== $scopeKey) {
                // Previously invisible master records can predate the client's cursor.
                $position = ['after' => 0, 'upper' => null];
            }
            $upper = $position['upper'] ?? (int) (DB::table('change_feed')->max('sequence') ?? 0);
            $limit = (int) ($input['limit'] ?? 50);
            $q = DB::table('change_feed')->whereIn('greenhouse_id', $ids)->where('sequence', '>', $position['after'])->where('sequence', '<=', $upper);
            if (! $reviewSupport) {
                $q->where('entity_type', '!=', 'activity_review');
            }
            $reviewOwners = collect();
            if ($actor->role !== 'manager' && ! $reviewSupport) {
                $q->where('audience', 'all');
            } elseif ($actor->role !== 'manager') {
                $ownReviews = DB::table('activity_reviews')->join('activities', 'activities.id', '=', 'activity_reviews.activity_id')
                    ->where(fn ($reviews) => $reviews->where('activities.recorded_by', $actor->id)
                        ->when($measurementStatus, fn ($measurements) => $measurements->orWhereNotNull('activities.manual_ppm')))
                    ->select('activity_reviews.id');
                $q->where(fn ($query) => $query->where('audience', 'all')->orWhere(fn ($review) => $review
                    ->where('audience', 'activity_recorder')->where('entity_type', 'activity_review')->whereIn('entity_id', $ownReviews)));
            }
            $rows = $q->orderBy('sequence')->limit($limit + 1)->get();
            $more = $rows->count() > $limit;
            $after = $more ? $rows[$limit - 1]->sequence : $upper;
            if ($measurementStatus) {
                $reviewOwners = DB::table('activity_reviews')->join('activities', 'activities.id', '=', 'activity_reviews.activity_id')
                    ->whereIn('activity_reviews.id', $rows->where('entity_type', 'activity_review')->pluck('entity_id'))
                    ->pluck('activities.recorded_by', 'activity_reviews.id');
            }

            return ['schema_version' => '2', 'sync_epoch' => $epoch, 'scope' => $scope, 'has_more' => $more,
                'data' => $rows->take($limit)->map(function ($row) use ($actor, $measurementStatus, $reviewOwners) {
                    $body = json_decode($row->body, true, flags: JSON_THROW_ON_ERROR);
                    if ($measurementStatus && $row->entity_type === 'activity_review' && (int) $reviewOwners->get($row->entity_id) !== $actor->id) {
                        $body = array_intersect_key($body, array_flip(['id', 'version', 'activity_id', 'activity_version', 'decision']));
                    }

                    return ['sequence' => $row->sequence, 'entity_type' => $row->entity_type, 'entity_id' => $row->entity_id,
                        'greenhouse_id' => $row->greenhouse_id, 'version' => $row->version, 'tombstone' => (bool) $row->tombstone, 'data' => $body];
                })->values(),
                'next_cursor' => $cursor->encode($actor, $epoch, $after, $more ? $upper : null, $scopeKey)];
        });
    }
}
