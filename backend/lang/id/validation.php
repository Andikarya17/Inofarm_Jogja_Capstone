<?php

return [
    'required' => ':attribute wajib diisi.',
    'present' => ':attribute harus disertakan, gunakan null bila belum berlaku.',
    'prohibited' => ':attribute tidak boleh diisi untuk aksi ini.',
    'array' => ':attribute harus berupa daftar/objek dengan kolom yang diizinkan.',
    'string' => ':attribute harus berupa teks.',
    'integer' => ':attribute harus berupa bilangan bulat.',
    'numeric' => ':attribute harus berupa angka.',
    'boolean' => ':attribute harus berupa pilihan benar/salah.',
    'email' => ':attribute harus berupa alamat email yang sah.',
    'uuid' => ':attribute harus berupa identitas UUID yang sah.',
    'regex' => 'Format :attribute tidak sesuai.',
    'in' => 'Pilihan :attribute tidak dikenal.',
    'date' => ':attribute harus berupa tanggal yang sah.',
    'date_format' => 'Format :attribute harus :format.',
    'min' => ['array' => ':attribute minimal :min isian.', 'numeric' => ':attribute minimal :min.', 'string' => ':attribute minimal :min karakter.'],
    'max' => ['array' => ':attribute maksimal :max isian.', 'numeric' => ':attribute maksimal :max.', 'string' => ':attribute maksimal :max karakter.'],
    'unique' => ':attribute telah digunakan.',
    'attributes' => [
        'email' => 'Email', 'password' => 'Password', 'operation.payload.name' => 'Nama',
        'operation.payload.occurred_at' => 'Waktu kejadian', 'operation.payload.manual_ppm' => 'PPM manual', 'operation.payload.manual_ph' => 'pH manual',
        'operation.payload.materials' => 'Bahan', 'operation.payload.notes' => 'Keterangan', 'operation.payload.reason' => 'Alasan',
        'operation.payload.skipped_reason' => 'Alasan kegiatan dilewati', 'operation.payload.actual_stage' => 'Stadia aktual',
        'operation.payload.materials.*.material_id' => 'Bahan', 'operation.payload.materials.*.unit_id' => 'Satuan bahan', 'operation.payload.materials.*.quantity' => 'Jumlah bahan',
    ],
];
