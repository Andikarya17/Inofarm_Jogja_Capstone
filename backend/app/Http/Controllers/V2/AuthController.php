<?php

namespace App\Http\Controllers\V2;

use App\Domain\Identity\GreenhouseAccess;
use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Validation\ValidationException;

final class AuthController extends Controller
{
    public function login(Request $request)
    {
        $input = $request->validate(['email' => 'required|email|max:254', 'password' => 'required|string|max:256']);
        $key = 'v2-login:'.hash('sha256', strtolower($input['email']).'|'.$request->ip());
        abort_if(RateLimiter::tooManyAttempts($key, 5), 429, 'Terlalu banyak percobaan login. Coba kembali sebentar lagi.', ['Retry-After' => (string) RateLimiter::availableIn($key)]);
        $user = User::where('email', strtolower($input['email']))->first();
        if (! $user || ! Hash::check($input['password'], $user->password) || ! $user->active || ! $user->public_id || ! in_array($user->role, ['worker', 'manager'], true)) {
            RateLimiter::hit($key, 60);
            throw ValidationException::withMessages(['email' => 'Email atau password tidak sesuai.']);
        }
        RateLimiter::clear($key);
        $expires = now()->addSeconds(config('finance.session_seconds'));

        return ['token' => $user->createToken('android-v2', ['finance-v2'], $expires)->plainTextToken,
            'expires_at' => $expires->toISOString(), 'user' => $this->identity($user)];
    }

    public function me(Request $request, GreenhouseAccess $access)
    {
        $assignments = $access->query($request->user())->orderBy('id')->limit(101)->get(['id', 'name', 'version']);

        return ['user' => $this->identity($request->user()),
            'expires_at' => $request->user()->currentAccessToken()?->expires_at?->toISOString(),
            'greenhouses' => $assignments->take(100)->values(), 'has_more' => $assignments->count() > 100];
    }

    public function logout(Request $request)
    {
        $request->user()->currentAccessToken()?->delete();

        return response()->noContent();
    }

    private function identity(User $user): array
    {
        return ['id' => $user->public_id, 'name' => $user->name, 'email' => $user->email, 'role' => $user->role, 'version' => $user->version];
    }
}
