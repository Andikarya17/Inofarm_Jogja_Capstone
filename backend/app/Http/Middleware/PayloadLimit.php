<?php

namespace App\Http\Middleware;

use App\Domain\Attachments\AttachmentPolicy;
use Closure;
use Illuminate\Http\Request;

class PayloadLimit
{
    public function handle(Request $request, Closure $next)
    {
        if ($request->is('api/v2/attachments') && $request->isMethod('POST') && (str_starts_with(strtolower($request->header('Content-Type', '')), 'multipart/form-data') || $request->hasFile('file'))) {
            abort_if((int) $request->header('Content-Length', 0) > AttachmentPolicy::MAX_SUPPORTED_BYTES + 65536, 413, 'Unggahan melebihi batas teknis request.');

            return $next($request);
        }
        // Customer import carries up to 1.000 short rows of an Excel sheet; every other JSON request stays at 16 KiB.
        if ($request->is('api/v2/customers/import') && $request->isMethod('POST')) {
            abort_if(strlen($request->getContent()) > 131072, 413, 'Payload impor melebihi 128 KiB. Kirim baris dalam beberapa bagian.');

            return $next($request);
        }
        abort_if(strlen($request->getContent()) > 16384, 413, 'Payload melebihi 16 KiB.');

        return $next($request);
    }
}
