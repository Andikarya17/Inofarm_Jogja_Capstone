<?php

return [
    'session_seconds' => (int) env('FINANCE_SESSION_SECONDS', 86400),
    // Q07: production limits/types remain unset until configured explicitly. No fixture defaults.
    'attachments' => [
        'max_bytes' => env('FINANCE_ATTACHMENT_MAX_BYTES'),
        'max_files' => env('FINANCE_ATTACHMENT_MAX_FILES'),
        'allowed_mime_types' => env('FINANCE_ATTACHMENT_MIME_TYPES'),
    ],
];
