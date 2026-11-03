"""Read-only retirement check; complements actual Gradle/API runtime tests."""
from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
main = root / 'android/app/src/main'
for retired in ('firmware', 'src'):
    assert not any(p.is_file() for p in (root / retired).rglob('*')), retired
for retired in ('package.json', 'vite.config.ts', 'scripts/package-local.ps1', 'scripts/build-firmware.ps1'):
    assert not (root / retired).exists(), retired
for path in (main / 'java').rglob('*.kt'):
    source = path.read_text(encoding='utf-8-sig')
    for marker in ('com.inofarm.jogja.data', 'OwnerViewModel', 'BuildConfig.FINANCE_APP', '/api/v1/', 'dev.chrisbanes.haze'):
        assert marker not in source, (str(path), marker)
gradle = (root / 'android/app/build.gradle.kts').read_text()
assert 'hasProperty("financeApp")' not in gradle
assert 'minSdk = 29' in gradle and 'applicationIdSuffix = ".finance"' in gradle
assert 'androidx.navigation' not in gradle and 'dev.chrisbanes.haze' not in gradle
routes = (root / 'backend/routes/api.php').read_text()
assert "require __DIR__.'/api_v2.php';" in routes and "prefix('v1')" not in routes
bootstrap = (root / 'backend/bootstrap/app.php').read_text()
assert 'DeviceAuth' not in bootstrap and 'OwnerScope' not in bootstrap
for script in ('build-android.ps1', 'run-android.ps1', 'package-finance.ps1', 'test-finance-readiness.ps1'):
    source = (root / 'scripts' / script).read_text()
    for marker in ('-PfinanceApp', 'PlatformIO', 'build-firmware.ps1', 'npm ', '/api/v1/'):
        assert marker not in source, (script, marker)
assert (root / 'backend/database/migrations/2026_10_10_000001_create_inofarm.php').is_file()
assert (root / 'docs/archive/pre-finance-20261029/manifest.json').is_file()
print(json.dumps({'active_product': 'Android Kotlin Compose + Laravel v2 + MySQL',
                  'legacy_runtime_dependencies': 0, 'historical_migrations_and_archive': 'retained'}))
