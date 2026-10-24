<?php

namespace App\Console\Commands;

use App\Models\Device;
use App\Models\Tank;
use Illuminate\Console\Command;
use Illuminate\Support\Str;

class ProvisionDevice extends Command
{
    protected $signature = 'inofarm:device {--name=ESP32 Inofarm} {--output=}';

    protected $description = 'Provision sole local producer; write secret to private file, never stdout.';

    public function handle(): int
    {
        $tank = Tank::firstOrCreate(['name' => 'Tandon Inofarm Jogja']);
        if (Device::where('tank_id', $tank->id)->exists()) {
            $this->error('Satu perangkat sudah diprovision. Rotasi/revocation terpisah.');

            return 1;
        }
        $path = $this->option('output');
        if (! $path || file_exists($path) || ! is_dir(dirname($path))) {
            $this->error('Tentukan --output private file baru pada folder yang sudah ada.');

            return 1;
        }
        $token = bin2hex(random_bytes(32));
        $id = (string) Str::uuid();
        if (file_put_contents($path, json_encode(['deviceId' => $id, 'token' => $token], JSON_PRETTY_PRINT), LOCK_EX) === false) {
            return 1;
        }
        Device::create(['id' => $id, 'tank_id' => $tank->id, 'name' => $this->option('name'), 'token_hash' => hash('sha256', $token)]);
        $this->info('Perangkat diprovision. Credential ditulis ke private file, tidak ditampilkan.');

        return 0;
    }
}
