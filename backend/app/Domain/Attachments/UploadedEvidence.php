<?php

namespace App\Domain\Attachments;

use Illuminate\Http\UploadedFile;
use Illuminate\Validation\ValidationException;

final class UploadedEvidence
{
    /** Validate the binary identity on every submission, including accepted replay, independent of mutable policy. */
    public function inspect(UploadedFile $file, array $declared): array
    {
        $errors = [];
        if (! $file->isValid() || ! is_file($file->getPathname())) {
            throw ValidationException::withMessages(['file' => 'Berkas belum diterima utuh. Pilih atau coba kirim kembali.']);
        }
        $path = $file->getPathname();
        $bytes = filesize($path);
        abort_if($bytes > AttachmentPolicy::MAX_SUPPORTED_BYTES, 413, 'Berkas melebihi batas teknis yang didukung.');
        $mime = (new \finfo(FILEINFO_MIME_TYPE))->file($path);
        $sha = hash_file('sha256', $path);
        if (! is_int($declared['bytes']) || $declared['bytes'] !== $bytes || $bytes < 1) {
            $errors['bytes'][] = 'Jumlah byte harus integer JSON dan sesuai berkas yang dikirim.';
        }
        if (! hash_equals($declared['sha256'], $sha)) {
            $errors['sha256'][] = 'Checksum berkas tidak cocok dengan pengajuan.';
        }
        if ($declared['mime'] !== $mime) {
            $errors['mime'][] = 'Jenis berkas harus cocok dengan isi, bukan hanya ekstensi atau header unggahan.';
        }
        $extension = strtolower(pathinfo($declared['original_name'], PATHINFO_EXTENSION));
        $extensions = ['image/png' => ['png'], 'image/jpeg' => ['jpg', 'jpeg'], 'application/pdf' => ['pdf']];
        if (! in_array($extension, $extensions[$mime] ?? [], true)) {
            $errors['original_name'][] = 'Ekstensi nama berkas harus sesuai isi PNG, JPEG, atau PDF.';
        }
        $stream = fopen($path, 'rb');
        try {$prefix = fread($stream, 8);} finally {fclose($stream);}
        $signatureMatches = match ($mime) {
            'image/png' => $prefix === "\x89PNG\r\n\x1a\n",
            'image/jpeg' => str_starts_with($prefix, "\xff\xd8\xff"),
            'application/pdf' => str_starts_with($prefix, '%PDF-'),
            default => false,
        };
        if (! $signatureMatches) {
            $errors['file'][] = 'Isi berkas tidak cocok dengan format JPEG, PNG, atau PDF yang didukung aplikasi.';
        }
        if ($errors !== []) {
            throw ValidationException::withMessages($errors);
        }

        return ['path' => $path, 'bytes' => $bytes, 'mime' => $mime, 'sha256' => $sha];
    }
}
