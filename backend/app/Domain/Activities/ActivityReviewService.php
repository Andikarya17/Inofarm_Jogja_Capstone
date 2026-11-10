<?php

namespace App\Domain\Activities;

use App\Domain\Contracts\DomainFailure;
use App\Domain\Cultivation\GroupContext;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\AcceptedSnapshot;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Database\Query\Builder;
use Illuminate\Support\Facades\DB;

final class ActivityReviewService
{
    public function __construct(
        private ActivityService $activities,
        private GreenhouseAccess $access,
        private GroupContext $context,
        private AcceptedSnapshot $snapshots,
        private MutationLedger $ledger,
    ) {}

    public function inbox(User $actor, array $filter): array
    {
        $this->access->manager($actor);
        $status = $filter['status'] ?? 'pending';
        $query = $this->scope($actor, $filter['greenhouse_id'] ?? null);
        if ($status === 'legacy') {
            $query->whereNull('recorded_by_role');
        } else {
            $query->where('recorded_by_role', 'worker');
        }
        if (isset($filter['worker_id'])) {
            $query->whereIn('recorded_by', User::where('public_id', $filter['worker_id'])->select('id'));
        }
        if (isset($filter['activity_type'])) {
            $query->where('activity_type', $filter['activity_type']);
        }
        foreach (['from' => '>=', 'until' => '<'] as $key => $operator) {
            if (isset($filter[$key])) {
                $date = CarbonImmutable::parse($filter[$key], 'Asia/Jakarta');
                $query->where('occurred_at', $operator, ($key === 'until' ? $date->addDay() : $date)->utc()->toDateTimeString());
            }
        }
        if ($status === 'pending') {
            $query->where('record_state', 'active')->whereNotExists(self::current());
        } elseif ($status === 'decided') {
            $query->whereExists(DB::table('activity_reviews')->selectRaw('1')->whereColumn('activity_reviews.activity_id', 'activities.id'));
        } elseif ($status !== 'legacy') {
            $query->whereExists(self::current($status));
        }
        $page = $query->orderByDesc('occurred_at')->orderByDesc('id')->cursorPaginate((int) ($filter['limit'] ?? 25));
        $snapshots = $this->snapshots->readMany('activity', $page->items());

        return ['data' => array_map(fn ($row) => $this->entry($actor, $row, $snapshots[$row->id]), $page->items()),
            'next_cursor' => $page->nextCursor()?->encode()];
    }

    public function recorders(User $actor, array $filter): array
    {
        $this->access->manager($actor);
        $recorders = $this->scope($actor, $filter['greenhouse_id'] ?? null)->where('recorded_by_role', 'worker')->select('recorded_by');
        $page = DB::table('users')->whereIn('id', $recorders)->select(['public_id as id', 'name'])
            ->orderBy('name')->orderBy('id')->cursorPaginate((int) ($filter['limit'] ?? 50));

        return ['data' => $page->items(), 'next_cursor' => $page->nextCursor()?->encode()];
    }

    public function detail(User $actor, string $id, array $filter): array
    {
        $row = $this->find($actor, $id);
        abort_unless($actor->role === 'manager' || $row->recorded_by === $actor->id, 404);
        $version = (int) ($filter['activity_version'] ?? $row->version);
        abort_if($version > $row->version, 404);
        $snapshot = $this->snapshots->read('activity', (object) ['id' => $id, 'version' => $version]);
        $history = DB::table('activity_reviews')->where('activity_id', $id)->orderByDesc('activity_version')
            ->cursorPaginate((int) ($filter['limit'] ?? 25));

        return $this->entry($actor, $row, $snapshot) + ['current_version' => $row->version,
            'history' => array_map($this->decision(...), $history->items()), 'next_cursor' => $history->nextCursor()?->encode()];
    }

    public function mutate(User $session, array $op): array
    {
        $payload = $op['payload'];

        return $this->ledger->run($session, $op, function (User $actor) use ($payload) {
            $this->access->manager($actor);
            $row = $this->find($actor, $payload['activity_id']);
            abort_unless($row->recorded_by_role === 'worker', 422, 'Verifikasi hanya tersedia untuk catatan pekerja dengan peran asal tercatat.');
        }, function (User $actor) use ($op, $payload) {
            $row = $this->find($actor, $payload['activity_id']);
            $ctx = $this->context->get($actor, $row->planting_group_id, true);
            if ($row->version !== $payload['activity_version']) {
                throw new DomainFailure('activity_revision_changed', 'Catatan sudah berubah. Buka revisi terbaru sebelum memeriksa.');
            }
            abort_unless($row->record_state === 'active', 422, 'Catatan ini sudah dibatalkan.');
            if ($this->review($row->id, $row->version) !== null) {
                throw new DomainFailure('activity_already_reviewed', 'Revisi ini sudah diputuskan. Buka detail untuk melihat hasil.');
            }
            $decision = ['id' => $op['entity_id'], 'activity_id' => $row->id, 'activity_version' => $row->version,
                'reviewer_id' => $actor->id, 'reviewer_name' => $actor->name, 'decision' => $payload['decision'],
                'reason' => $payload['reason'] ?? null, 'reviewed_at' => CarbonImmutable::now('UTC')->toDateTimeString()];
            DB::table('activity_reviews')->insert($decision);

            return ['before' => null, 'after' => $this->decision((object) $decision) + ['greenhouse_id' => $ctx['greenhouse']->id],
                'greenhouse_id' => $ctx['greenhouse']->id, 'audience' => 'activity_recorder'];
        });
    }

    public static function current(?string $decision = null): Builder
    {
        return DB::table('activity_reviews')->selectRaw('1')
            ->whereColumn('activity_reviews.activity_id', 'activities.id')
            ->whereColumn('activity_reviews.activity_version', 'activities.version')
            ->when($decision !== null, fn ($query) => $query->where('activity_reviews.decision', $decision));
    }

    private function find(User $actor, string $id): object
    {
        return $this->activities->scope($actor)->where('id', $id)->first() ?? abort(404);
    }

    private function scope(User $actor, ?string $greenhouse): Builder
    {
        $query = $this->activities->scope($actor);
        if ($greenhouse !== null) {
            $this->access->require($actor, $greenhouse);
            $query->whereIn('planting_group_id', DB::table('planting_groups')->join('seasons', 'seasons.id', '=', 'planting_groups.season_id')
                ->where('seasons.greenhouse_id', $greenhouse)->select('planting_groups.id'));
        }

        return $query;
    }

    private function review(string $id, int $version): ?object
    {
        return DB::table('activity_reviews')->where('activity_id', $id)->where('activity_version', $version)->first();
    }

    private function entry(User $actor, object $row, array $snapshot): array
    {
        $ctx = $this->context->get($actor, $row->planting_group_id);
        $review = $this->review($row->id, $snapshot['version']);
        $status = $review->decision ?? match ($row->recorded_by_role) {
            'worker' => 'pending',
            'manager' => 'manager',
            default => 'legacy',
        };

        return ['activity' => $snapshot, 'status' => $status, 'decision' => $review ? $this->decision($review) : null,
            'context' => ['greenhouse_id' => $ctx['greenhouse']->id, 'greenhouse' => $ctx['greenhouse']->name,
                'season' => $ctx['season']->name, 'group' => $ctx['group']->name,
                'variety' => DB::table('varieties')->where('id', $ctx['group']->variety_id)->value('name')]];
    }

    private function decision(object $row): array
    {
        return [...(array) $row, 'version' => 1, 'reviewer_id' => User::findOrFail($row->reviewer_id)->public_id,
            'reviewed_at' => CarbonImmutable::parse($row->reviewed_at, 'UTC')->toISOString()];
    }
}
