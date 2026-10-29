<?php

namespace App\Http\Controllers\V2;

use App\Domain\Attachments\AttachmentOwner;
use App\Domain\Attachments\AttachmentPolicy;
use App\Domain\Attachments\AttachmentService;
use App\Domain\Attachments\PrivateEvidenceStore;
use App\Domain\Attachments\UploadedEvidence;
use App\Domain\Sync\AcceptedSnapshot;
use App\Domain\Sync\ScopedRead;
use App\Http\Controllers\Controller;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;
use Symfony\Component\HttpFoundation\HeaderUtils;
use Symfony\Component\HttpKernel\Exception\HttpExceptionInterface;

final class AttachmentController extends Controller
{
    private const TYPES = 'activity_attachment,harvest_attachment,expense_attachment';

    private function parentInput(Request $request): array
    {
        return $request->validate(['owner_type' => 'required|in:activity,harvest,expense', 'owner_id' => ['required', OperationInput::UUID]]);
    }

    public function policy(Request $request)
    {
        $input = $this->parentInput($request);

        return app(ScopedRead::class)->run($request, function (User $actor) use ($input) {
            $owners = app(AttachmentOwner::class);
            $context = $owners->get($actor, $input['owner_type'], $input['owner_id']);
            $profile = app(AttachmentPolicy::class)->profile();
            $allowed = true;
            try {$owners->canUpload($actor, $context);$owners->get($actor, $input['owner_type'], $input['owner_id'], true);}
            catch (HttpExceptionInterface $error) {$allowed = false;$profile['reasons'][] = $error->getMessage();}

            return ['data' => [...$profile, 'upload_allowed' => $profile['configured'] && $allowed, 'owner_version' => $context['row']->version]];
        });
    }

    public function index(Request $request)
    {
        $input = [...$this->parentInput($request), ...$request->validate(['record_state' => 'nullable|in:active,cancelled', 'cursor' => 'nullable|uuid', 'limit' => 'sometimes|integer|min:1|max:100'])];

        return app(ScopedRead::class)->run($request, function (User $actor) use ($input) {
            $owners = app(AttachmentOwner::class);
            $owners->get($actor, $input['owner_type'], $input['owner_id']);
            $limit = (int) ($input['limit'] ?? 50);
            $rows = $owners->metadataScope($actor)->where('owner_type', $input['owner_type'])->where('owner_id', $input['owner_id'])
                ->when($input['record_state'] ?? null, fn ($q, $state) => $q->where('record_state', $state))
                ->when($input['cursor'] ?? null, fn ($q, $cursor) => $q->where('id', '>', $cursor))->orderBy('id')->limit($limit + 1)->get();

            return ['data' => $rows->take($limit)->map(fn ($row) => app(AcceptedSnapshot::class)->read($row->owner_type.'_attachment', $row))->values(),
                'next_cursor' => $rows->count() > $limit ? $rows[$limit - 1]->id : null];
        });
    }

    public function show(Request $request, string $id)
    {
        return app(ScopedRead::class)->run($request, function (User $actor) use ($id) {
            $row = app(AttachmentService::class)->authorizedRow($actor, $id);

            return app(AcceptedSnapshot::class)->read($row->owner_type.'_attachment', $row);
        });
    }

    public function content(Request $request, string $id)
    {
        return app(ScopedRead::class)->run($request, function (User $actor) use ($id) {
            $row = app(AttachmentService::class)->authorizedRow($actor, $id);
            $body = app(PrivateEvidenceStore::class)->read($row);
            $extension = ['image/jpeg' => 'jpg', 'image/png' => 'png', 'application/pdf' => 'pdf'][$row->mime] ?? 'bin';

            return response($body, 200, ['Content-Type' => $row->mime, 'Content-Length' => (string) strlen($body),
                'Content-Disposition' => HeaderUtils::makeDisposition('attachment', $row->original_name, $row->id.'.'.$extension),
                'Cache-Control' => 'private, no-store', 'X-Content-Type-Options' => 'nosniff', 'Content-Security-Policy' => "default-src 'none'"]);
        });
    }

    public function store(Request $request)
    {
        if (array_diff(array_keys($request->all()), ['operation', 'file']) !== []) {
            throw ValidationException::withMessages(['operation' => 'Gunakan hanya operation JSON dan satu file.']);
        }
        $json = $request->input('operation');
        if (! is_string($json) || strlen($json) > 16384) {
            throw ValidationException::withMessages(['operation' => 'Metadata operasi harus JSON maksimal16KiB.']);
        }
        try {$body = json_decode($json, true, flags: JSON_THROW_ON_ERROR);} catch (\JsonException) {throw ValidationException::withMessages(['operation' => 'Metadata operasi bukan JSON yang sah.']);}
        if (! is_array($body)) {throw ValidationException::withMessages(['operation' => 'Metadata operasi harus berupa object.']);}
        $operationRequest = Request::create($request->path(), 'POST', $body);
        $op = OperationInput::read($operationRequest, self::TYPES, 'create', [
            'owner_type' => 'required|in:activity,harvest,expense', 'owner_id' => ['required', OperationInput::UUID], 'owner_version' => 'required|integer|min:1|max:2147483647',
            'original_name' => ['required', 'string', 'max:200', 'not_regex:/[\x00-\x1f\x7f\/\\\\]/u'],
            'mime' => 'required|string|max:80', 'bytes' => 'required|integer|min:1|max:33554432', 'sha256' => ['required', 'regex:/^[0-9a-f]{64}$/D'],
        ]);
        if (! is_int($op['payload']['owner_version']) || ! is_int($op['payload']['bytes'])) {
            throw ValidationException::withMessages(['operation' => 'Versi pemilik dan jumlah byte harus integer JSON.']);
        }
        $file = $request->file('file');
        if (! $file instanceof UploadedFile) {throw ValidationException::withMessages(['file' => 'Pilih satu berkas yang telah disalin utuh.']);}
        $inspected = app(ScopedRead::class)->run($request, function (User $actor) use ($op, $file) {
            $owners = app(AttachmentOwner::class);
            $owners->canUpload($actor, $owners->get($actor, $op['payload']['owner_type'], $op['payload']['owner_id']));

            return app(UploadedEvidence::class)->inspect($file, $op['payload']);
        });
        $result = app(AttachmentService::class)->mutate($request->user(), $op, $inspected);

        return response()->json($result['body'], $result['status']);
    }

    public function cancel(Request $request, string $id)
    {
        $op = OperationInput::read($request, self::TYPES, 'cancel', ['reason' => ['required', 'string', 'max:1000', 'regex:/\S/u']], $id);
        $result = app(AttachmentService::class)->mutate($request->user(), $op);

        return response()->json($result['body'], $result['status']);
    }

    public function audit(Request $request, string $id)
    {
        $input = $request->validate(['cursor' => 'nullable|integer|min:0', 'limit' => 'sometimes|integer|min:1|max:100']);

        return app(ScopedRead::class)->run($request, function (User $actor) use ($id, $input) {
            $row = app(AttachmentService::class)->authorizedRow($actor, $id);
            $limit = (int) ($input['limit'] ?? 50);
            $rows = DB::table('audit_logs')->where('entity_type', $row->owner_type.'_attachment')->where('entity_id', $id)->where('id', '>', $input['cursor'] ?? 0)->orderBy('id')->limit($limit + 1)->get();

            return ['data' => $rows->take($limit)->map(function ($entry) {
                $actor = User::findOrFail($entry->actor_id);

                return ['id' => $entry->id, 'actor_id' => $actor->public_id, 'actor_name' => $actor->name, 'action' => $entry->action, 'version' => $entry->version,
                    'reason' => $entry->reason, 'changes' => json_decode($entry->changes, true, flags: JSON_THROW_ON_ERROR), 'server_at' => CarbonImmutable::parse($entry->server_at, 'UTC')->toISOString()];
            })->values(), 'next_cursor' => $rows->count() > $limit ? (string) $rows[$limit - 1]->id : null];
        });
    }
}
