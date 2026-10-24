<?php

namespace App\Http\Controllers;

use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Validation\ValidationException;

class AuthController extends Controller
{
    public function login(Request $request)
    {
        $input = $request->validate(['email' => 'required|email|max:254', 'password' => 'required|string|max:256']);
        $key = 'login:'.hash('sha256', strtolower($input['email']).'|'.$request->ip());
        abort_if(RateLimiter::tooManyAttempts($key, 5), 429, 'Terlalu banyak percobaan login.');
        $user = User::where('email', strtolower($input['email']))->first();
        if (! $user || ! Hash::check($input['password'], $user->password) || ! in_array($user->role, ['owner', 'leader'], true) || ! $user->tank_id) {
            RateLimiter::hit($key, 60);
            throw ValidationException::withMessages(['email' => 'Email atau password tidak sesuai.']);
        }
        RateLimiter::clear($key);

        $expires = now()->addDay();

        return ['token' => $user->createToken('android', ['owner'], $expires)->plainTextToken, 'expiresAt' => $expires->toISOString(), 'user' => $user->only('id', 'tank_id', 'name', 'email', 'role')];
    }

    public function logout(Request $request)
    {
        $request->user()->currentAccessToken()->delete();

        return response()->noContent();
    }
}
