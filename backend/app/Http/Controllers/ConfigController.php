<?php

namespace App\Http\Controllers;

use App\Models\ConfigVersion;
use App\Models\PhaseVersion;
use App\Services\ConfigurationStore;
use App\Services\Input;
use Illuminate\Http\Request;

class ConfigController extends Controller
{
    public function show(Request $request, ConfigurationStore $store)
    {
        $tankId = $request->user()?->tank_id ?? $request->attributes->get('device')->tank_id;

        return ['config' => $store->latest(ConfigVersion::class, $tankId), 'activePhase' => $store->latest(PhaseVersion::class, $tankId), 'timezone' => 'Asia/Jakarta'];
    }

    public function save(Request $request, ConfigurationStore $store)
    {
        $input = $request->validate(['baseVersion' => 'present|nullable|integer|min:1', 'config' => 'required|array']);
        $config = Input::config($input['config']);

        return ['config' => $store->save(ConfigVersion::class, $request->user()->tank_id, $request->user()->id, $input['baseVersion'], $config)];
    }

    public function phase(Request $request, ConfigurationStore $store)
    {
        $input = $request->validate(['baseVersion' => 'present|nullable|integer|min:1', 'phase' => 'required|in:vegetatif,generatif']);

        return ['activePhase' => $store->save(PhaseVersion::class, $request->user()->tank_id, $request->user()->id, $input['baseVersion'], ['phase' => $input['phase']])];
    }

    public function applied(Request $request, ConfigurationStore $store)
    {
        $input = $request->validate(['version' => 'required|integer|min:1']);
        $device = $request->attributes->get('device');
        abort_unless(ConfigVersion::where('tank_id', $device->tank_id)->whereKey($input['version'])->exists(), 422, 'Versi tidak dikenal.');
        $device->update(['applied_config_version' => $input['version']]);

        return ['appliedVersion' => $input['version']];
    }
}
