<?php

namespace App\Domain\Attachments;

use App\Domain\Activities\ActivityService;
use App\Domain\Cultivation\GroupContext;
use App\Domain\Finance\ExpenseService;
use App\Domain\Harvest\HarvestService;
use App\Domain\Identity\GreenhouseAccess;
use App\Models\User;
use Illuminate\Support\Facades\DB;

final class AttachmentOwner
{
    public function get(User $actor, string $kind, string $id, bool $write = false): array
    {
        if ($kind === 'expense') {
            $service = app(ExpenseService::class);
            $row = $service->scope($actor)->where('id', $id)->first() ?? abort(404);
            if ($write) {
                $service->writableGreenhouse($actor, $row->greenhouse_id);
                foreach (DB::table('seasons')->whereIn('id', DB::table('cost_allocations')->where('expense_id', $id)->where('record_state', 'active')->select('season_id'))->get() as $season) {
                    $service->openSeason($season);
                }
            }
            $gh = $row->greenhouse_id;
        } else {
            abort_unless(in_array($kind, ['activity', 'harvest'], true), 422, 'Jenis catatan lampiran tidak didukung.');
            $service = $kind === 'activity' ? app(ActivityService::class) : app(HarvestService::class);
            $row = $service->scope($actor)->where('id', $id)->first() ?? abort(404);
            $ctx = app(GroupContext::class)->get($actor, $row->planting_group_id, $write);
            $gh = $ctx['greenhouse']->id;
        }
        if ($write) {
            abort_unless($row->record_state === 'active', 422, 'Catatan pemilik telah dibatalkan.');
        }

        return ['row' => $row, 'greenhouse_id' => $gh, 'audience' => $kind === 'expense' ? 'manager' : 'all'];
    }

    public function canUpload(User $actor, array $context): void
    {
        abort_unless($actor->role === 'manager' || $context['row']->recorded_by === $actor->id, 403, 'Lampiran pekerja hanya untuk catatan sendiri.');
    }

    public function metadataScope(User $actor)
    {
        return DB::table('record_attachments')->whereIn('greenhouse_id', app(GreenhouseAccess::class)->ids($actor))
            ->when($actor->role !== 'manager', fn ($q) => $q->whereIn('owner_type', ['activity', 'harvest']));
    }
}
