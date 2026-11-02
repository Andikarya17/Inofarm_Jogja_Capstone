<?php

return [
    // No inferred production operator, schedule, retention, RPO or RTO.
    'operators' => array_values(array_filter(explode(',', env('FINANCE_RECOVERY_OPERATORS', '')))),
    'root' => env('FINANCE_RECOVERY_ROOT', storage_path('app/recovery-private-v2')),
    'isolated_marker' => env('FINANCE_RESTORE_ISOLATION_MARKER'),
    'schedule' => null, 'retention' => null, 'rpo' => null, 'rto' => null,
];
