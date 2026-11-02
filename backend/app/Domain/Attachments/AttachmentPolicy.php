<?php

namespace App\Domain\Attachments;

use App\Domain\Contracts\CanonicalJson;
use App\Domain\Contracts\DomainFailure;
use Illuminate\Validation\ValidationException;

final class AttachmentPolicy
{
    // Transport/storage compatibility ceilings, not approved production upload policy.
    public const MAX_SUPPORTED_BYTES = 33554432;
    public const SUPPORTED_MIMES = ['image/jpeg', 'image/png', 'application/pdf'];

    public function profile(): array
    {
        $raw = config('finance.attachments');
        $bytes = $this->integer($raw['max_bytes'] ?? null, self::MAX_SUPPORTED_BYTES);
        $files = $this->integer($raw['max_files'] ?? null, 2147483647);
        $input = $raw['allowed_mime_types'] ?? null;
        $mimes = is_array($input) ? $input : (is_string($input) ? array_map(trim(...), explode(',', $input)) : []);
        $validMimes = $mimes !== [] && count(array_diff($mimes, self::SUPPORTED_MIMES)) === 0;
        $mimes = $validMimes ? array_values(array_unique($mimes)) : [];
        sort($mimes);
        $configured = $bytes !== null && $files !== null && $validMimes;
        $values = ['configured' => $configured, 'max_bytes' => $bytes, 'max_files' => $files, 'allowed_mime_types' => $mimes];

        return [...$values, 'policy_hash' => CanonicalJson::hash($values), 'supported_mime_types' => self::SUPPORTED_MIMES,
            'max_supported_bytes' => self::MAX_SUPPORTED_BYTES,
            'reasons' => $configured ? [] : ['Konfigurasi ukuran, jumlah, dan jenis lampiran produksi belum ditetapkan secara lengkap dan sah.']];
    }

    public function validateNew(array $file): array
    {
        $profile = $this->profile();
        if (! $profile['configured']) {
            throw new DomainFailure('attachment_configuration_missing', $profile['reasons'][0]);
        }
        $errors = [];
        if ($file['bytes'] > $profile['max_bytes']) {
            $errors['file'][] = 'Ukuran berkas melebihi batas server yang dikonfigurasi.';
        }
        if (! in_array($file['mime'], $profile['allowed_mime_types'], true)) {
            $errors['file'][] = 'Jenis berkas tidak diizinkan oleh konfigurasi server.';
        }
        if ($errors !== []) {
            throw ValidationException::withMessages($errors);
        }

        return $profile;
    }

    private function integer(mixed $value, int $max): ?int
    {
        if ((! is_int($value) && ! is_string($value)) || ! preg_match('/^[1-9][0-9]{0,9}$/D', (string) $value)) {
            return null;
        }
        $parsed = (int) $value;

        return $parsed <= $max ? $parsed : null;
    }
}
