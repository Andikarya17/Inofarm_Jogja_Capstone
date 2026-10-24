<?php

namespace App\Http\Middleware;

use App\Models\Device;
use Closure;
use Illuminate\Http\Request;

class DeviceAuth
{
    public function handle(Request $request, Closure $next)
    {
        $token = $request->bearerToken();
        $device = $token ? Device::where('token_hash', hash('sha256', $token))->where('enabled', true)->first() : null;
        abort_unless($device, 401, 'Otorisasi perangkat diperlukan.');
        $request->attributes->set('device', $device);
        $device->update(['last_contact_at' => now()]);

        return $next($request);
    }
}
