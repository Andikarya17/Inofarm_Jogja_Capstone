<?php

namespace App\Console\Commands;

use App\Models\Tank;
use App\Models\User;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Validator;

class ProvisionOwner extends Command
{
    protected $signature = 'inofarm:owner {email} {--name=} {--role=owner}';

    protected $description = 'Provision local owner/leader with hidden password prompt; no agronomic seed.';

    public function handle(): int
    {
        $email = strtolower($this->argument('email'));
        $name = $this->option('name') ?: $this->ask('Nama owner');
        $password = $this->secret('Password (minimal 12 karakter)');
        $validator = Validator::make(['email' => $email, 'name' => $name, 'password' => $password, 'role' => $this->option('role')], ['email' => 'required|email|unique:users', 'name' => 'required|string|max:100', 'password' => 'required|string|min:12', 'role' => 'in:owner,leader']);
        if ($validator->fails()) {
            $this->error($validator->errors()->first());

            return 1;
        }
        DB::transaction(function () use ($email, $name, $password) {
            $tank = Tank::firstOrCreate(['name' => 'Tandon Inofarm Jogja']);
            User::create(['name' => $name, 'email' => $email, 'password' => $password, 'role' => $this->option('role'), 'tank_id' => $tank->id]);
        });
        $this->info('Akun tersimpan. Konfigurasi produksi tetap kosong.');

        return 0;
    }
}
