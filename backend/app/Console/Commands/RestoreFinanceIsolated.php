<?php

namespace App\Console\Commands;

use App\Domain\Recovery\IsolatedRestore;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Validator;

final class RestoreFinanceIsolated extends Command
{
    protected $signature = 'inofarm:restore-isolated {backup} {--manifest-hash=} {--nonce=} {--operator=} {--reason=}';

    protected $description = 'Restore only a fresh owned inofarm_test instance, reconcile, revoke sessions and rotate epoch.';

    public function handle(IsolatedRestore $restore): int
    {
        $input = ['backup' => $this->argument('backup'), 'hash' => $this->option('manifest-hash'), 'nonce' => $this->option('nonce'), 'operator' => $this->option('operator'), 'reason' => $this->option('reason')];
        $v = Validator::make($input, ['backup' => 'required|uuid', 'hash' => 'required|regex:/^[0-9a-f]{64}$/', 'nonce' => 'required|uuid', 'operator' => 'required|string|min:1|max:150', 'reason' => 'required|string|min:1|max:1000']);
        if ($v->fails()) {
            $this->error('Complete explicit isolated restore identity, hash, operator and reason required.');

            return self::FAILURE;
        }
        try {
            $result = $restore->apply($input['backup'], $input['hash'], $input['nonce'], $input['operator'], $input['reason']);
        } catch (\Throwable $e) {
            report($e);
            $this->error('Restore NOT VERIFIED; target remains isolated. '.$e->getMessage());

            return self::FAILURE;
        }
        $this->line(json_encode($result, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES));

        return self::SUCCESS;
    }
}
