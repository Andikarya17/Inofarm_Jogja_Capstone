#pragma once
// Copy to ignored secrets.h locally. HTTPS required outside local bench.
#define INOFARM_WIFI_SSID ""
#define INOFARM_WIFI_PASSWORD ""
#define INOFARM_API_BASE "" // Include /api/v1/ and trailing slash.
#define INOFARM_DEVICE_ID ""
#define INOFARM_DEVICE_TOKEN ""
#define INOFARM_ROOT_CA "" // PEM CA for HTTPS. No setInsecure fallback.
#define INOFARM_ALLOW_HTTP_BENCH 0 // Explicit bench-only gate; private IPv4 literal required.
