<?php

namespace App\Domain\Attachments;

use App\Domain\Contracts\DomainFailure;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;

/** Immutable content-addressed blobs on a dedicated local disk with no public/signed serving route. */
final class PrivateEvidenceStore
{
    private function disk()
    {
        $disk = Storage::disk('finance_private');
        $root = $disk->path('');
        if (! str_starts_with(str_replace('\\', '/', $root), '/') && ! preg_match('/^[A-Za-z]:[\\\\\/]/', $root)) {
            throw new DomainFailure('private_storage_misconfigured', 'Penyimpanan berkas privat belum dikonfigurasi dengan sah.');
        }
        $canonical = function (string $path): string {
            $value = str_replace('\\', '/', realpath($path) ?: $path);
            $parts = [];
            foreach (explode('/', $value) as $part) {
                if ($part === '' || $part === '.') {continue;}
                if ($part === '..') {array_pop($parts);} else {$parts[] = $part;}
            }
            $result = (str_starts_with($value, '/') ? '/' : '').implode('/', $parts);

            return PHP_OS_FAMILY === 'Windows' ? strtolower($result) : $result;
        };
        $resolved = $canonical($root);
        foreach ([public_path(), storage_path('app/public')] as $public) {
            $public = $canonical($public);
            if ($resolved === $public || str_starts_with($resolved, $public.'/')) {
                throw new DomainFailure('private_storage_misconfigured', 'Berkas harus disimpan di luar direktori yang dipublikasikan server.');
            }
        }

        return $disk;
    }

    private function relativePath(string $sha): string
    {
        if (! preg_match('/^[0-9a-f]{64}$/D', $sha)) {
            throw new \LogicException('Private evidence requires a canonical SHA256.');
        }

        return 'blobs/'.substr($sha, 0, 2).'/'.$sha;
    }

    public function put(array $file): void
    {
        $disk = $this->disk();
        $relative = $this->relativePath($file['sha256']);
        $path = $disk->path($relative);
        if (is_file($path)) {
            $this->verify($path, $file);

            return;
        }
        $stage = 'staging/'.Str::uuid();
        $stagePath = $disk->path($stage);
        $disk->makeDirectory('staging');
        $disk->makeDirectory(dirname($relative));
        try {
            $input = fopen($file['path'], 'rb');
            try {$disk->put($stage, $input);} finally {fclose($input);}
            $this->verify($stagePath, $file);
            $handle = fopen($stagePath, 'r+b');
            try {if (! fsync($handle)) {throw new \RuntimeException('Unable to sync evidence staging file.');}} finally {fclose($handle);}
            if (! rename($stagePath, $path)) {
                throw new \RuntimeException('Unable to finalize private evidence.');
            }
        } finally {
            // Only this newly generated staging UUID, never arbitrary/user-supplied paths.
            if (is_file($stagePath)) {unlink($stagePath);}
        }
    }

    public function read(object $attachment): string
    {
        $path = $this->disk()->path($this->relativePath($attachment->sha256));
        $this->verify($path, ['bytes' => $attachment->bytes, 'sha256' => $attachment->sha256]);
        $body = file_get_contents($path);
        if ($body === false || strlen($body) !== $attachment->bytes || ! hash_equals($attachment->sha256, hash('sha256', $body))) {
            throw new DomainFailure('attachment_integrity_failed', 'Isi lampiran belum dapat diverifikasi.', 500);
        }

        return $body;
    }

    private function verify(string $path, array $expected): void
    {
        if (! is_file($path) || filesize($path) !== $expected['bytes'] || ! hash_equals($expected['sha256'], hash_file('sha256', $path))) {
            throw new DomainFailure('attachment_integrity_failed', 'Berkas privat tidak lengkap atau checksum berubah. Perlu rekonsiliasi.', 500);
        }
    }
}
