<?php

namespace App\Domain\Recovery;

use App\Domain\Contracts\CanonicalJson;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;

final class RecoveryPackage
{
    public const VERSION = 'finance-recovery-v1';

    public function __construct(private RecoveryInventory $inventory) {}

    public function directory(string $id): string
    {
        if (! preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/D', $id)) {
            throw new \RuntimeException('Invalid recovery identity.');
        }
        $root = config('recovery.root');
        if (! is_string($root) || (! str_starts_with($root, '/') && ! preg_match('/^[A-Za-z]:[\\\\\/]/', $root))) {
            throw new \RuntimeException('Recovery root must be absolute and private.');
        }
        if (! is_dir($root) && ! mkdir($root, 0700, true)) {
            throw new \RuntimeException('Recovery root unavailable.');
        }
        $root = $this->canonical($root);
        $served = [public_path(), storage_path('app/public')];
        foreach (config('filesystems.disks', []) as $disk) {
            if ((($disk['serve'] ?? false) || ($disk['visibility'] ?? null) === 'public') && isset($disk['root'])) {
                $served[] = $disk['root'];
            }
        }
        foreach ($served as $public) {
            $public = $this->canonical($public);
            if ($root === $public || str_starts_with($root, $public.'/')) {
                throw new \RuntimeException('Recovery root cannot be public.');
            }
        }
        $path = $root.'/'.$id;
        if (file_exists($path) && ($this->canonical($path) !== $path || is_link($path))) {
            throw new \RuntimeException('Recovery path escapes private root.');
        }

        return $path;
    }

    public function canonical(string $path): string
    {
        $p = str_replace('\\', '/', realpath($path) ?: $path);

        return PHP_OS_FAMILY === 'Windows' ? strtolower($p) : $p;
    }

    private function file(string $directory, string $relative): string
    {
        if (! preg_match('~^(tables/[a-zA-Z0-9_]+\.jsonl|blobs/[0-9a-f]{64}|manifest\.json|manifest\.hmac)$~D', $relative)) {
            throw new \RuntimeException('Unsafe package path.');
        }
        $path = $directory.'/'.$relative;
        if (file_exists($path) && (! str_starts_with($this->canonical($path), $this->canonical($directory).'/') || is_link($path))) {
            throw new \RuntimeException('Package file escapes root.');
        }

        return $path;
    }

    public function create(string $id): array
    {
        // Caller owns sync_state FOR UPDATE in a repeatable-read transaction.
        if (DB::transactionLevel() < 1 || strtoupper(DB::selectOne('SELECT @@transaction_isolation AS level')->level) !== 'REPEATABLE-READ') {
            throw new \RuntimeException('Consistent recovery transaction unavailable.');
        }
        $directory = $this->directory($id);
        if (file_exists($directory)) {
            throw new \RuntimeException('Recovery package already exists; refusing overwrite.');
        }
        mkdir($directory, 0700);
        mkdir($directory.'/tables', 0700);
        mkdir($directory.'/blobs', 0700);
        $tables = $this->inventory->tables();
        foreach ($tables as $name => &$meta) {
            $out = fopen($this->file($directory, 'tables/'.$name.'.jsonl'), 'xb');
            if ($out === false) {
                throw new \RuntimeException('Backup table unavailable.');
            }
            try {
                $meta += $this->inventory->rows($name, $meta, function ($line) use ($out) {
                    if (fwrite($out, $line) !== strlen($line)) {
                        throw new \RuntimeException('Backup write incomplete.');
                    }
                });
            } finally {
                fclose($out);
            }
        }
        unset($meta);
        $files = [];
        foreach (DB::table('record_attachments')->select('sha256', 'bytes')->distinct()->get() as $blob) {
            $sha = $blob->sha256;
            if (! preg_match('/^[0-9a-f]{64}$/D', $sha)) {
                throw new \RuntimeException('Invalid attachment checksum.');
            }
            $disk = Storage::disk('finance_private');
            $source = $disk->path('blobs/'.substr($sha, 0, 2).'/'.$sha);
            if (! str_starts_with($this->canonical($source), $this->canonical($disk->path('')).'/')) {
                throw new \RuntimeException('Attachment path escaped storage.');
            }
            $this->checkBytes($source, ['bytes' => (int) $blob->bytes, 'sha256' => $sha]);
            $target = $this->file($directory, 'blobs/'.$sha);
            if (! file_exists($target) && ! copy($source, $target)) {
                throw new \RuntimeException('Attachment backup failed.');
            }
            $files[$sha] = ['bytes' => (int) $blob->bytes, 'sha256' => $sha];
            $this->checkBytes($target, $files[$sha]);
        }
        ksort($files);
        $manifest = ['version' => self::VERSION, 'id' => $id, 'created_at' => now()->toISOString(), 'source_server_uuid' => DB::selectOne('SELECT @@server_uuid AS id')->id,
            'source_epoch' => DB::table('sync_state')->where('id', 1)->value('epoch'), 'tables' => $tables, 'files' => $files, 'totals' => $this->inventory->totals(), 'foreign_keys_verified' => $this->inventory->assertRelations()];
        $json = CanonicalJson::encode($manifest);
        $this->writeNew($this->file($directory, 'manifest.json'), $json);
        $this->writeNew($this->file($directory, 'manifest.hmac'), hash_hmac('sha256', $json, $this->key()));

        return ['hash' => hash('sha256', $json), 'manifest' => $manifest];
    }

    private function key(): string
    {
        $key = config('app.key');
        if (! is_string($key) || strlen($key) < 32) {
            throw new \RuntimeException('Recovery signing key unavailable.');
        }

        return $key;
    }

    private function writeNew(string $path, string $bytes): void
    {
        $out = fopen($path, 'xb');
        if ($out === false) {
            throw new \RuntimeException('Recovery output unavailable.');
        }
        try {
            if (fwrite($out, $bytes) !== strlen($bytes)) {
                throw new \RuntimeException('Recovery output incomplete.');
            }
        } finally {
            fclose($out);
        }
    }

    public function checkBytes(string $path, array $expected): void
    {
        if (! is_file($path) || filesize($path) !== $expected['bytes'] || ! hash_equals($expected['sha256'], hash_file('sha256', $path))) {
            throw new \RuntimeException('Recovery file missing or checksum mismatch.');
        }
    }

    public function validate(string $id, string $expected): array
    {
        if (! preg_match('/^[0-9a-f]{64}$/D', $expected)) {
            throw new \RuntimeException('Expected manifest checksum required.');
        }
        $directory = $this->directory($id);
        $json = file_get_contents($this->file($directory, 'manifest.json'));
        $hmac = file_get_contents($this->file($directory, 'manifest.hmac'));
        if ($json === false || $hmac === false || ! hash_equals($expected, hash('sha256', $json)) || ! hash_equals($hmac, hash_hmac('sha256', $json, $this->key()))) {
            throw new \RuntimeException('Recovery manifest authentication failed.');
        }
        $manifest = json_decode($json, true, flags: JSON_THROW_ON_ERROR);
        if ($manifest['id'] !== $id || $manifest['version'] !== self::VERSION) {
            throw new \RuntimeException('Recovery version/identity mismatch.');
        }
        $current = $this->inventory->tables();
        if (array_keys($current) !== array_keys($manifest['tables'])) {
            throw new \RuntimeException('Recovery table inventory incompatible.');
        }
        $expectedFiles = ['manifest.json', 'manifest.hmac'];
        foreach ($current as $name => $meta) {
            $saved = $manifest['tables'][$name];
            foreach (['schema_hash', 'columns', 'pk'] as $key) {
                if ($saved[$key] !== $meta[$key]) {
                    throw new \RuntimeException('Recovery schema incompatible.');
                }
            }
            $relative = 'tables/'.$name.'.jsonl';
            $this->checkBytes($this->file($directory, $relative), $saved);
            $expectedFiles[] = $relative;
        }
        foreach ($manifest['files'] as $sha => $meta) {
            if ($sha !== $meta['sha256']) {
                throw new \RuntimeException('Blob identity mismatch.');
            }
            $relative = 'blobs/'.$sha;
            $this->checkBytes($this->file($directory, $relative), $meta);
            $expectedFiles[] = $relative;
        }
        $actual = [];
        foreach (new \RecursiveIteratorIterator(new \RecursiveDirectoryIterator($directory, \FilesystemIterator::SKIP_DOTS)) as $file) {
            $actual[] = str_replace('\\', '/', substr($file->getPathname(), strlen($directory) + 1));
        }
        sort($actual);
        sort($expectedFiles);
        if ($actual !== $expectedFiles) {
            throw new \RuntimeException('Recovery package has missing or unexpected files.');
        }

        return $manifest;
    }

    public function tablePath(string $id, string $name): string
    {
        return $this->file($this->directory($id), 'tables/'.$this->inventory->identifier($name).'.jsonl');
    }

    public function blobPath(string $id, string $sha): string
    {
        return $this->file($this->directory($id), 'blobs/'.$sha);
    }
}
