<?php

namespace App\Console\Commands;

use App\Models\User;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Validator;
use Illuminate\Support\Str;

class ProvisionFinanceUser extends Command
{
    protected $signature = 'inofarm:finance-user {email} {--name=} {--role=}';

    protected $description = 'Buat akun operasional eksplisit tanpa mengubah akun/roster legacy atau membuat assignment.';

    public function handle(): int
    {
        $input = ['email' => strtolower($this->argument('email')), 'name' => $this->option('name') ?: $this->ask('Nama'),
            'role' => $this->option('role'), 'password' => $this->secret('Password (minimal 12 karakter)')];
        $validator = Validator::make($input, ['email' => 'required|email|max:254|unique:users', 'name' => 'required|string|max:100',
            'role' => 'required|in:worker,manager', 'password' => 'required|string|min:12|max:256']);
        if ($validator->fails()) {
            $this->error($validator->errors()->first());

            return self::FAILURE;
        }
        User::create([...$input, 'public_id' => (string) Str::uuid()]);
        $this->info('Akun tersimpan. Penugasan greenhouse harus ditetapkan eksplisit.');

        return self::SUCCESS;
    }
}
