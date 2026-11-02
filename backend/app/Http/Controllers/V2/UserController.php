<?php

namespace App\Http\Controllers\V2;

use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Identity\EntityIdentity;
use App\Domain\Sync\MutationLedger;
use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

final class UserController extends Controller
{
    private function publicUser(User $user, string $gh): array
    {
        return ['id' => $user->public_id, 'greenhouse_id' => $gh, 'name' => $user->name,
            'email' => $user->email, 'role' => $user->role, 'active' => $user->active, 'version' => $user->version];
    }

    public function index(Request $request, GreenhouseAccess $access)
    {
        $access->manager($request->user());
        $input = $request->validate(['greenhouse_id' => 'required|uuid', 'cursor' => 'nullable|uuid', 'limit' => 'sometimes|integer|min:1|max:100']);
        $access->require($request->user(), $input['greenhouse_id']);
        $limit = (int) ($input['limit'] ?? 50);
        $rows = User::whereIn('id', DB::table('greenhouse_assignments')->where('greenhouse_id', $input['greenhouse_id'])->select('user_id'))
            ->when($input['cursor'] ?? null, fn ($q, $cursor) => $q->where('public_id', '>', $cursor))
            ->whereNotNull('public_id')->orderBy('public_id')->limit($limit + 1)->get();

        return ['data' => $rows->take($limit)->map(fn ($user) => $this->publicUser($user, $input['greenhouse_id']))->values(),
            'next_cursor' => $rows->count() > $limit ? $rows[$limit - 1]->public_id : null];
    }

    public function store(Request $request, GreenhouseAccess $access, MutationLedger $ledger)
    {
        return $this->mutate($request, $access, $ledger, 'create');
    }

    public function update(Request $request, string $id, GreenhouseAccess $access, MutationLedger $ledger)
    {
        return $this->mutate($request, $access, $ledger, 'update', $id);
    }

    private function mutate(Request $request, GreenhouseAccess $access, MutationLedger $ledger, string $action, ?string $id = null)
    {
        $rules = ['greenhouse_id' => ['required', OperationInput::UUID], 'name' => 'required|string|max:100', 'role' => 'required|in:worker,manager'];
        if ($action === 'create') {
            $rules += ['email' => 'required|email|max:254', 'password' => 'required|string|min:12|max:256'];
        } else {
            $rules += ['active' => 'required|boolean', 'reason' => 'required|string|max:1000', 'password' => 'sometimes|string|min:12|max:256'];
        }
        $op = OperationInput::read($request, 'users', $action, $rules, $id);
        $gh = $op['payload']['greenhouse_id'];
        $result = $ledger->run($request->user(), $op, function (User $actor) use ($access, $action, $op, $gh) {
            $access->manager($actor);
            $access->require($actor, $gh);
            if ($action !== 'create') {
                $target = User::where('public_id', $op['entity_id'])->first() ?? abort(404);
                abort_unless(DB::table('greenhouse_assignments')->where('greenhouse_id', $gh)->where('user_id', $target->id)->exists(), 404);
                abort_if(DB::table('greenhouse_assignments')->where('user_id', $target->id)->whereNotIn('greenhouse_id', $access->ids($actor))->exists(), 403, 'Akun juga terhubung greenhouse di luar kewenangan Anda.');
            }
        }, function (User $actor) use ($ledger, $op, $action, $gh) {
            $p = $op['payload'];
            $before = null;
            if ($action === 'create') {
                abort_if(User::where('public_id', $op['entity_id'])->orWhere('email', strtolower($p['email']))->exists(), 422, 'Email atau identitas akun telah digunakan.');
                $user = User::create(['public_id' => $op['entity_id'], 'name' => $p['name'], 'email' => strtolower($p['email']), 'role' => $p['role'], 'password' => $p['password']]);
                $assignmentId = (string) Str::uuid();
                app(EntityIdentity::class)->claim('greenhouse_assignments', $assignmentId);
                DB::table('greenhouse_assignments')->insert(['id' => $assignmentId, 'greenhouse_id' => $gh, 'user_id' => $user->id,
                    'created_by' => $actor->id, 'effective_from' => now(), 'created_at' => now(), 'updated_at' => now()]);
            } else {
                $user = User::where('public_id', $op['entity_id'])->firstOrFail();
                $before = $this->publicUser($user, $gh);
                $ledger->version((object) ['version' => $user->version], $op['base_version'], $before);
                abort_unless(is_bool($p['active']), 422, 'active harus boolean.');
                abort_if($user->id === $actor->id && (! $p['active'] || $p['role'] !== $user->role), 422, 'Tidak dapat menonaktifkan atau mengubah peran akun sendiri.');
                $user->update(['name' => $p['name'], 'role' => $p['role'], 'active' => $p['active'], 'version' => $user->version + 1,
                    ...(isset($p['password']) ? ['password' => $p['password']] : [])]);
                // Identity/role/password edits invalidate sessions; drafts stay on the device.
                $user->tokens()->delete();
            }

            return ['before' => $before, 'after' => $this->publicUser($user, $gh), 'greenhouse_id' => $gh, 'audience' => 'manager'];
        });

        return response()->json($result['body'], $result['status']);
    }
}
