<?php

namespace App\Domain\Recovery;

use App\Domain\Contracts\CanonicalJson;
use App\Domain\Finance\SavedReportStorage;
use App\Domain\Identity\EntityIdentity;
use App\Support\TestDatabaseGuard;
use Illuminate\Database\ConfigurationUrlParser;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;

/** Deliberately cannot restore production or a populated database. */
final class IsolatedRestore
{
    public function __construct(private RecoveryPackage $packages, private RecoveryInventory $inventory) {}

    public function assertTarget(string $nonce): array
    {
        TestDatabaseGuard::assertSafe(config()->all()); // Before the first SQL.
        $path = config('recovery.isolated_marker');
        $root = $this->packages->canonical(base_path('../.runtime/recovery-drills'));
        if (! Str::isUuid($nonce) || ! is_string($path) || $this->packages->canonical($path) !== $root.'/'.$nonce.'/owner.json' || ! is_file($path)) {
            throw new \RuntimeException('Owned isolated restore marker required.');
        }
        $marker = json_decode(file_get_contents($path), true, flags: JSON_THROW_ON_ERROR);
        $connection = (new ConfigurationUrlParser)->parseConfiguration(config('database.connections.mysql'));
        if ($marker['nonce'] !== $nonce || $connection['host'] !== '127.0.0.1' || (int) $connection['port'] !== $marker['port']) {
            throw new \RuntimeException('Restore target connection does not match ownership.');
        }
        $server = DB::selectOne('SELECT @@server_uuid AS id, @@datadir AS directory');
        $expectedDirectory = $root.'/'.$nonce;
        if ($server->id !== $marker['server_uuid'] || rtrim($this->packages->canonical($server->directory), '/') !== $expectedDirectory.'/mysql-data' || $this->packages->canonical(Storage::disk('finance_private')->path('')) !== $expectedDirectory.'/files') {
            throw new \RuntimeException('Restore target datadir/server/files ownership mismatch.');
        }

        return $marker;
    }

    public function apply(string $id, string $hash, string $nonce, string $operator, string $reason): array
    {
        $this->assertTarget($nonce);
        $manifest = $this->packages->validate($id, $hash);
        if (DB::selectOne('SELECT @@server_uuid AS id')->id === $manifest['source_server_uuid']) {
            throw new \RuntimeException('Refusing restore on backup source server.');
        }
        foreach ($this->inventory->tables() as $name => $meta) {
            $count = DB::table($name)->count();
            if ($name === 'migrations') {
                continue;
            }
            if ($count !== ($name === 'sync_state' ? 1 : 0)) {
                throw new \RuntimeException('Restore requires an unused trusted-migration target; populated target preserved.');
            }
        }
        if (Storage::disk('finance_private')->allFiles() !== []) {
            throw new \RuntimeException('Restore private file target is not empty.');
        }
        $started = microtime(true);
        // Only the two bootstrap tables are cleared, on the verified fresh isolated target.
        DB::statement('SET FOREIGN_KEY_CHECKS=0');
        try {
            DB::transaction(function () use ($manifest, $id) {
                DB::table('sync_state')->delete();
                DB::table('migrations')->delete();
                foreach ($manifest['tables'] as $name => $meta) {
                    $input = fopen($this->packages->tablePath($id, $name), 'rb');
                    $count = 0;
                    if ($input === false) {
                        throw new \RuntimeException('Restore input unavailable.');
                    }
                    try {
                        while (($line = fgets($input)) !== false) {
                            $row = json_decode($line, true, flags: JSON_THROW_ON_ERROR);
                            $keys = array_keys($row);
                            sort($keys);
                            $columns = $meta['columns'];
                            sort($columns);
                            if ($keys !== $columns) {
                                throw new \RuntimeException('Restore row has unexpected columns.');
                            }
                            $decoded = [];
                            foreach ($row as $column => $value) {
                                $decoded[$column] = $value === null ? null : base64_decode($value, true);
                                if ($decoded[$column] === false) {
                                    throw new \RuntimeException('Restore row is not valid lossless data.');
                                }
                            }
                            DB::table($name)->insert($decoded);
                            $count++;
                        }
                    } finally {
                        fclose($input);
                    }
                    if ($count !== $meta['rows']) {
                        throw new \RuntimeException('Restore row count mismatch.');
                    }
                }
            });
        } finally {
            DB::statement('SET FOREIGN_KEY_CHECKS=1');
        }
        foreach ($manifest['tables'] as $name => $meta) {
            if ($meta['next_id'] !== null) {
                if (! preg_match('/^[1-9][0-9]{0,19}$/D', $meta['next_id'])) {
                    throw new \RuntimeException('Invalid next identity.');
                }
                DB::statement('ALTER TABLE `'.$this->inventory->identifier($name).'` AUTO_INCREMENT='.$meta['next_id']);
            }
        }
        foreach ($manifest['files'] as $sha => $meta) {
            $disk = Storage::disk('finance_private');
            $relative = 'blobs/'.substr($sha, 0, 2).'/'.$sha;
            $disk->makeDirectory(dirname($relative));
            if (! copy($this->packages->blobPath($id, $sha), $disk->path($relative))) {
                throw new \RuntimeException('Restore blob copy failed.');
            }
            $this->packages->checkBytes($disk->path($relative), $meta);
        }
        $verification = $this->verifyPoint($manifest);
        $verification['elapsed_seconds'] = number_format(microtime(true) - $started, 3, '.', '');
        $verification['operator_reference'] = $operator;
        // Only after exact point-in-time verification may this target expose a new sync identity.
        $newEpoch = (string) Str::uuid();
        $restoreId = (string) Str::uuid();
        DB::transaction(function () use ($id, $hash, $manifest, $newEpoch, $restoreId, $verification, $operator, $reason) {
            DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
            $tokens = DB::table('personal_access_tokens')->count();
            $sessions = DB::table('sessions')->count();
            DB::table('personal_access_tokens')->delete();
            DB::table('sessions')->delete();
            DB::table('password_reset_tokens')->delete();
            $interrupted = DB::table('backup_runs')->where('state', 'requested')->update(['state' => 'interrupted_by_restore', 'updated_at' => now()]);
            DB::table('sync_state')->where('id', 1)->update(['epoch' => $newEpoch]);
            app(EntityIdentity::class)->claim('restore_run', $restoreId);
            DB::table('restore_runs')->insert(['id' => $restoreId, 'backup_id' => $id, 'operator_reference' => $operator, 'reason' => $reason, 'state' => 'verified_isolated', 'manifest_hash' => $hash, 'old_epoch' => $manifest['source_epoch'], 'new_epoch' => $newEpoch,
                'verification' => json_encode($verification + ['revoked_tokens' => $tokens, 'revoked_sessions' => $sessions, 'interrupted_backup_jobs' => $interrupted], JSON_THROW_ON_ERROR), 'created_at' => now(), 'updated_at' => now()]);
        });

        return ['restore_id' => $restoreId, 'state' => 'verified_isolated', 'manifest_hash' => $hash, 'old_epoch' => $manifest['source_epoch'], 'new_epoch' => $newEpoch, 'verification' => $verification];
    }

    public function verifyPoint(array $manifest): array
    {
        $rows = 0;
        $schema = $this->inventory->tables();
        foreach ($manifest['tables'] as $name => $meta) {
            if ($schema[$name]['schema_hash'] !== $meta['schema_hash'] || $schema[$name]['next_id'] !== $meta['next_id']) {
                throw new \RuntimeException('Restored schema/next identity mismatch.');
            }
            $actual = $this->inventory->rows($name, $meta);
            foreach (['rows', 'bytes', 'sha256'] as $field) {
                if ($actual[$field] !== $meta[$field]) {
                    throw new \RuntimeException('Restored table content mismatch.');
                }
            }
            $rows += $actual['rows'];
        }
        if (CanonicalJson::hash($this->inventory->totals()) !== CanonicalJson::hash($manifest['totals'])) {
            throw new \RuntimeException('Restored financial totals mismatch.');
        }
        $relations = $this->inventory->assertRelations();
        if ($relations !== $manifest['foreign_keys_verified']) {
            throw new \RuntimeException('Restored relationship inventory mismatch.');
        }
        $snapshots = 0;
        foreach (DB::table('report_snapshots')->get() as $snapshot) {
            app(SavedReportStorage::class)->full($snapshot);
            $snapshots++;
        }
        foreach (DB::table('record_attachments')->get() as $attachment) {
            $sha = $attachment->sha256;
            $this->packages->checkBytes(Storage::disk('finance_private')->path('blobs/'.substr($sha, 0, 2).'/'.$sha), ['bytes' => $attachment->bytes, 'sha256' => $sha]);
        }
        if (DB::table('sync_state')->where('id', 1)->value('epoch') !== $manifest['source_epoch']) {
            throw new \RuntimeException('Restored source epoch mismatch.');
        }

        return ['tables' => count($manifest['tables']), 'rows' => $rows, 'files' => count($manifest['files']), 'foreign_keys' => $relations, 'snapshots' => $snapshots, 'totals' => $manifest['totals'], 'point_in_time_match' => true, 'next_identities_verified' => true];
    }
}
