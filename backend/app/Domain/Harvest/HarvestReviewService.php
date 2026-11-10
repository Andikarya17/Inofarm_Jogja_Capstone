<?php

namespace App\Domain\Harvest;

use App\Domain\Contracts\DomainFailure;
use App\Domain\Cultivation\GroupContext;
use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Sync\AcceptedSnapshot;
use App\Domain\Sync\MutationLedger;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;

final class HarvestReviewService
{
    public function __construct(
        private HarvestService $harvests,
        private GreenhouseAccess $access,
        private GroupContext $context,
        private AcceptedSnapshot $snapshots,
        private MutationLedger $ledger,
    ) {}

    public function inbox(User $actor, array $filter): array
    {
        $this->access->manager($actor);
        $status = $filter['status'] ?? 'pending';
        $query = $this->harvests->scope($actor);
        if (isset($filter['greenhouse_id'])) {
            $this->access->require($actor, $filter['greenhouse_id']);
            $query->whereIn('planting_group_id', DB::table('planting_groups')
                ->join('seasons', 'seasons.id', '=', 'planting_groups.season_id')
                ->where('seasons.greenhouse_id', $filter['greenhouse_id'])->select('planting_groups.id'));
        }
        $reviews = DB::table('harvest_reviews')->selectRaw('1')->whereColumn('harvest_id', 'harvests.id');
        $current = (clone $reviews)->whereColumn('harvest_version', 'harvests.version');
        if ($status === 'legacy') {
            $query->whereNull('recorded_by_role');
        } else {
            $query->where('recorded_by_role', 'worker');
            match ($status) {
                'pending' => $query->where('record_state', 'active')->whereNotExists($current),
                'decided' => $query->whereExists($reviews),
                default => $query->whereExists($current->where('decision', $status)),
            };
        }
        $page = $query->orderByDesc('occurred_at')->orderByDesc('id')->cursorPaginate((int) ($filter['limit'] ?? 25));
        $snapshots = $this->snapshots->readMany('harvest', $page->items());

        return ['data' => array_map(fn ($row) => $this->entry($actor, $row, $snapshots[$row->id]), $page->items()),
            'next_cursor' => $page->nextCursor()?->encode()];
    }

    public function detail(User $actor, string $id, array $filter): array
    {
        $row = $this->find($actor, $id);
        abort_unless($actor->role === 'manager' || $row->recorded_by === $actor->id, 404);
        $version = (int) ($filter['harvest_version'] ?? $row->version);
        abort_if($version > $row->version, 404);
        $revision = $this->snapshots->read('harvest', (object) ['id' => $id, 'version' => $version]);
        $history = DB::table('harvest_reviews')->where('harvest_id', $id)->orderByDesc('harvest_version')
            ->cursorPaginate((int) ($filter['limit'] ?? 25));

        return $this->entry($actor, $row, $revision) + ['current_version' => $row->version,
            'history' => array_map($this->decision(...), $history->items()), 'next_cursor' => $history->nextCursor()?->encode()];
    }

    public function mutate(User $session, array $op): array
    {
        $payload = $op['payload'];

        return $this->ledger->run($session, $op, function (User $actor) use ($payload) {
            $this->access->manager($actor);
            $row = $this->find($actor, $payload['harvest_id']);
            abort_unless($row->recorded_by_role === 'worker', 422, 'Verifikasi hanya tersedia untuk panen asal pekerja.');
            abort_if($row->recorded_by === $actor->id, 403, 'Panen sendiri perlu diperiksa pengelola lain.');
        }, function (User $actor) use ($op, $payload) {
            $row = $this->find($actor, $payload['harvest_id']);
            $ctx = $this->context->get($actor, $row->planting_group_id);
            if ($row->version !== $payload['harvest_version']) {
                throw new DomainFailure('harvest_revision_changed', 'Panen sudah berubah. Buka revisi terbaru sebelum memeriksa.',
                    currentVersion: $row->version);
            }
            abort_unless($row->record_state === 'active', 422, 'Panen ini sudah dibatalkan.');
            if ($this->review($row->id, $row->version) !== null) {
                throw new DomainFailure('harvest_already_reviewed', 'Revisi panen sudah diputuskan. Buka detail untuk melihat hasil.',
                    currentVersion: $row->version);
            }
            $decision = ['id' => $op['entity_id'], 'harvest_id' => $row->id, 'harvest_version' => $row->version,
                'reviewer_id' => $actor->id, 'reviewer_name' => $actor->name, 'decision' => $payload['decision'],
                'reason' => $payload['reason'] ?? null, 'reviewed_at' => CarbonImmutable::now('UTC')->toDateTimeString()];
            DB::table('harvest_reviews')->insert($decision);

            return ['before' => null, 'after' => $this->decision((object) $decision) + ['greenhouse_id' => $ctx['greenhouse']->id],
                'greenhouse_id' => $ctx['greenhouse']->id, 'audience' => 'harvest_recorder'];
        });
    }

    private function find(User $actor, string $id): object
    {
        return $this->harvests->scope($actor)->where('id', $id)->first() ?? abort(404);
    }

    private function review(string $id, int $version): ?object
    {
        return DB::table('harvest_reviews')->where('harvest_id', $id)->where('harvest_version', $version)->first();
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
        $entry = ['harvest' => $snapshot, 'status' => $status, 'decision' => $review ? $this->decision($review) : null,
            'context' => ['greenhouse_id' => $ctx['greenhouse']->id, 'greenhouse' => $ctx['greenhouse']->name,
                'season' => $ctx['season']->name, 'group' => $ctx['group']->name]];
        if ($actor->role === 'manager') {
            $entry['used_in_sales'] = DB::table('harvest_items')->where('harvest_id', $row->id)->where('sold_weight_kg', '>', 0)->exists();
            $entry['can_review'] = $status === 'pending' && $row->recorded_by !== $actor->id &&
                $row->record_state === 'active' && $snapshot['version'] === $row->version;
        }

        return $entry;
    }

    private function decision(object $row): array
    {
        return [...(array) $row, 'version' => 1, 'reviewer_id' => User::findOrFail($row->reviewer_id)->public_id,
            'reviewed_at' => CarbonImmutable::parse($row->reviewed_at, 'UTC')->toISOString()];
    }
}
