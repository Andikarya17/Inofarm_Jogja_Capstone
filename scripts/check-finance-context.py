"""Read-only context links/coverage and exact historical-byte verification (stdlib only)."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path
from urllib.parse import unquote

parser = argparse.ArgumentParser()
parser.add_argument('--staged', action='store_true', help='Also verify the exact Git index blobs.')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
files = list((root / 'docs/context').glob('*.md'))
files += list((root / 'docs/migration/finance-20261029').glob('*.md'))
files += [root / name for name in ['README.md', 'AGENTS.md', 'backend/AGENTS.md']]
files += [root / 'docs/implementation' / name for name in [
    'ANDROID_PERFORMANCE.md', 'API.md', 'DATABASE_ACCESS.md', 'DECISIONS.md',
    'DEMO_DATA.md', 'UI_REDESIGN.md', 'VERIFICATION.md',
]]
errors = []
links = 0
for path in files:
    source = path.read_text(encoding='utf-8-sig')
    if sum(line.startswith('```') for line in source.splitlines()) % 2:
        errors.append(str(path.relative_to(root)) + ': unclosed fence')
    source = re.sub(r'```.*?```', '', source, flags=re.S)
    for target in re.findall(r'\[[^\]]*\]\(([^)]+)\)', source):
        target = unquote(target.strip('<>').split('#')[0])
        if not target or re.match(r'^[A-Za-z][A-Za-z0-9+.-]*:', target):
            continue
        links += 1
        if not (path.parent / target).exists():
            errors.append(str(path.relative_to(root)) + ': ' + target)

archive = root / 'docs/archive/pre-finance-20261029'
manifest = json.loads((archive / 'manifest.json').read_text(encoding='utf-8-sig'))
for entry in manifest:
    path = (archive / entry['path']).resolve()
    assert path.is_relative_to(archive.resolve()), 'Archive path escapes its directory'
    assert hashlib.sha256(path.read_bytes()).hexdigest() == entry['sha256'], entry['path']
    if args.staged:
        blob = subprocess.run(['git', 'show', ':' + path.relative_to(root).as_posix()], cwd=root,
                              capture_output=True, check=True).stdout
        assert hashlib.sha256(blob).hexdigest() == entry['sha256'], 'Index bytes differ: ' + entry['path']

prd = (root / 'docs/context/PRD.md').read_text(encoding='utf-8-sig')
plan = (root / 'docs/migration/finance-20261029/TEST_PLAN.md').read_text(encoding='utf-8-sig')
for prefix, count, source in [('KF', 14, prd), ('KNF', 8, prd), ('U', 21, plan)]:
    for index in range(1, count + 1):
        assert f'{prefix}-{index:02d}' in source, (prefix, index)
assert not errors, errors
print(json.dumps({'active_documents': len(files), 'links_checked': links, 'archive_hashes': len(manifest),
                  'staged_archive_verified': args.staged, 'KF': 14, 'KNF': 8, 'U': 21, 'errors': errors}, indent=2))
