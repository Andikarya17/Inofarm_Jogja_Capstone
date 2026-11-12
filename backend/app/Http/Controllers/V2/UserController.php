<?php

namespace App\Http\Controllers\V2;

use App\Domain\Identity\GreenhouseAccess;
use App\Domain\Identity\EntityIdentity;
use App\Domain\Sync\MutationLedger;
use App\Domain\Sync\ScopedRead;
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

    public function index(Request $request, GreenhouseAccess $access, ScopedRead $read)
    {
        return $read->run($request, function (User $actor) use ($request, $access) {
            $access->manager($actor);
            $input = $request->validate(['greenhouse_id' => 'required|uuid', 'cursor' => 'nullable|uuid', 'limit' => 'sometimes|integer|min:1|max:100', 'for_assignment' => 'sometimes|boolean', 'all_accounts' => 'sometimes|boolean']);
            $access->require($actor, $input['greenhouse_id']);
            $limit = (int) ($input['limit'] ?? 50);
            $assigned = DB::table('greenhouse_assignments')->where('greenhouse_id', $input['greenhouse_id'])->select('user_id');
            $query = match (true) {
                (bool) ($input['for_assignment'] ?? false) => $access->manageableUsers($actor)->where('active', true)
                    ->where('role', 'worker')->whereNotIn('id', $assigned),
                (bool) ($input['all_accounts'] ?? false) => $access->manageableUsers($actor),
                default => User::whereIn('id', $assigned),
            };
            $rows = $query
                ->when($input['cursor'] ?? null, fn ($q, $cursor) => $q->where('public_id', '>', $cursor))
                ->whereNotNull('public_id')->orderBy('public_id')->limit($limit + 1)->get();

            return ['data' => $rows->take($limit)->map(fn ($user) => $this->publicUser($user, $input['greenhouse_id']))->values(),
                'next_cursor' => $rows->count() > $limit ? $rows[$limit - 1]->public_id : null];
        });
    }

    public function store(Request $request, GreenhouseAccess $access, MutationLedger $ledger)
    {
        return $this->mutate($request, $access, $ledger, 'create');
    }

    public function assignmentCandidate(Request $request, GreenhouseAccess $access, ScopedRead $read)
    {
        return $read->run($request, function (User $actor) use ($request, $access) {
            $access->manager($actor);
            $input = $request->validate(['greenhouse_id' => 'required|uuid', 'email' => 'required|email|max:254']);
            $access->require($actor, $input['greenhouse_id']);
            $user = User::where('email', strtolower($input['email']))->whereNotNull('public_id')
                ->where('active', true)->where('role', 'worker')
                ->whereNotIn('id', DB::table('greenhouse_assignments')->select('user_id'))->first();
            abort_unless($user, 404, 'Akun pekerja aktif tanpa penugasan tidak ditemukan untuk email tersebut.');

            return ['data' => $this->publicUser($user, $input['greenhouse_id'])];
        });
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
            $rules += ['active' => 'required|boolean', 'reason' => OperationInput::reasonRule($request, 'required|string|max:1000'), 'email' => 'sometimes|required|email|max:254', 'password' => 'sometimes|string|min:12|max:256'];
        }
        $op = OperationInput::read($request, 'users', $action, $rules, $id);
        $gh = $op['payload']['greenhouse_id'];
        $result = $ledger->run($request->user(), $op, function (User $actor) use ($access, $action, $op, $gh) {
            $access->manager($actor);
            $access->require($actor, $gh);
            if ($action !== 'create') {
                abort_unless($access->manageableUsers($actor)->where('public_id', $op['entity_id'])->exists(), 404, 'Akun tidak ditemukan.');
            }
        }, function (User $actor) use ($ledger, $op, $action, $gh) {
            $p = $op['payload'];
            $email = isset($p['email']) ? strtolower($p['email']) : null;
            $before = null;
            if ($action === 'create') {
                abort_if(User::where('public_id', $op['entity_id'])->orWhere('email', $email)->exists(), 422, 'Email atau identitas akun telah digunakan.');
                $user = User::create(['public_id' => $op['entity_id'], 'name' => $p['name'], 'email' => $email, 'role' => $p['role'], 'password' => $p['password']]);
                $assignmentId = (string) Str::uuid();
                app(EntityIdentity::class)->claim('greenhouse_assignments', $assignmentId);
                DB::table('greenhouse_assignments')->insert(['id' => $assignmentId, 'greenhouse_id' => $gh, 'user_id' => $user->id,
                    'created_by' => $actor->id, 'effective_from' => now(), 'created_at' => now(), 'updated_at' => now()]);
            } else {
                $user = User::where('public_id', $op['entity_id'])->firstOrFail();
                $before = $this->publicUser($user, $gh);
                $ledger->version((object) ['version' => $user->version], $op['base_version'], $before);
                abort_if($email !== null && User::where('email', $email)->whereKeyNot($user->id)->exists(), 422, 'Email telah digunakan akun lain.');
                abort_unless(is_bool($p['active']), 422, 'active harus boolean.');
                abort_if($user->id === $actor->id && (! $p['active'] || $p['role'] !== $user->role), 422, 'Anda tidak bisa menonaktifkan atau mengubah peran akun sendiri.');
                abort_if($user->role === 'manager' && ($p['role'] !== 'manager' || ! $p['active'])
                    && ! User::where('role', 'manager')->where('active', true)->whereKeyNot($user->id)->exists(), 422, 'Harus ada minimal satu pengelola aktif.');
                $user->update(['name' => $p['name'], 'role' => $p['role'], 'active' => $p['active'], 'version' => $user->version + 1,
                    ...($email !== null ? ['email' => $email] : []),
                    ...(isset($p['password']) ? ['password' => $p['password']] : [])]);
                // Identity/role/password edits invalidate sessions; drafts stay on the device.
                $user->tokens()->delete();
            }

            return ['before' => $before, 'after' => $this->publicUser($user, $gh), 'greenhouse_id' => $gh, 'audience' => 'manager'];
        });

        return response()->json($result['body'], $result['status']);
    }
}
