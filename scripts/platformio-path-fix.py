"""Fix PlatformIO 6.1.18 Windows package hook quoting for this workspace path.

Only changes the local ignored virtualenv; no global package is modified.
"""
import pathlib
root = pathlib.Path(__file__).resolve().parents[1]
target = root / '.tools/pio-venv/Lib/site-packages/platformio/package/manager/base.py'
source = target.read_text(encoding='utf-8')
old = '" ".join(cmd) if shell else cmd'
new = 'subprocess.list2cmdline(cmd) if shell else cmd'
if old in source:
    target.write_text(source.replace(old, new), encoding='utf-8')
elif new not in source:
    raise RuntimeError('Unsupported PlatformIO package hook implementation')
print('Local PlatformIO Windows package hook quoting ready')
