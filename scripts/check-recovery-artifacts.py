"""Independent stdlib verification of a private FIN15 drill; never reads credentials/APP_KEY."""
import argparse
import base64
import hashlib
import json
import re
from decimal import Decimal, getcontext
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('drill', help='Owned .runtime/recovery-drills/<uuid> directory')
args = parser.parse_args()
getcontext().prec = 100
drill = Path(args.drill).resolve()
proof = json.loads((drill / 'evidence/restore.json').read_text(encoding='utf-8-sig'))
assert proof['state'] == 'verified_isolated'
assert proof['old_epoch'] != proof['new_epoch']
assert proof['verification']['point_in_time_match'] is True
assert proof['verification']['next_identities_verified'] is True
packages = list((drill / 'packages').iterdir())
assert len(packages) == 1
package = packages[0].resolve()
assert package.is_relative_to(drill)
raw = (package / 'manifest.json').read_bytes()
manifest = json.loads(raw)
assert manifest['version'] == 'finance-recovery-v1'
assert manifest['id'] == package.name
assert hashlib.sha256(raw).hexdigest() == proof['manifest_hash']
assert manifest['source_epoch'] == proof['old_epoch']
owner = json.loads((drill / 'owner.json').read_text(encoding='utf-8-sig'))
assert owner['server_uuid'] != manifest['source_server_uuid']

def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode('utf-8')

tables = {}
total_rows = 0
for name, meta in manifest['tables'].items():
    assert re.fullmatch(r'[a-zA-Z0-9_]+', name)
    path = (package / 'tables' / (name + '.jsonl')).resolve()
    assert path.is_relative_to(package)
    payload = path.read_bytes()
    assert len(payload) == meta['bytes']
    assert hashlib.sha256(payload).hexdigest() == meta['sha256']
    assert hashlib.sha256(meta['ddl'].encode('utf-8')).hexdigest() == meta['schema_hash']
    rows = []
    for line in payload.splitlines():
        row = json.loads(line)
        assert set(row) == set(meta['columns'])
        rows.append({key: None if value is None else base64.b64decode(value, validate=True)
                     for key, value in row.items()})
    assert len(rows) == meta['rows']
    total_rows += len(rows)
    tables[name] = rows
assert total_rows == proof['verification']['rows']
assert len(tables) == proof['verification']['tables']
assert manifest['foreign_keys_verified'] == proof['verification']['foreign_keys']
for label, expected in manifest['totals'].items():
    table, column = label.split('.')
    values = [Decimal(row[column].decode('ascii')) for row in tables[table] if row[column] is not None]
    if not values:
        assert expected is None
    else:
        assert sum(values, Decimal(0)) == Decimal(expected), label
assert proof['verification']['totals'] == manifest['totals']

for sha, meta in manifest['files'].items():
    assert re.fullmatch('[0-9a-f]{64}', sha)
    data = (package / 'blobs' / sha).read_bytes()
    assert len(data) == meta['bytes'] and hashlib.sha256(data).hexdigest() == sha
for row in tables['record_attachments']:
    sha = row['sha256'].decode('ascii')
    assert manifest['files'][sha]['bytes'] == int(row['bytes'])
snapshots = 0
for row in tables['report_snapshots']:
    doc = json.loads(row['content'])
    assert hashlib.sha256(canonical(doc)).hexdigest() == row['snapshot_hash'].decode('ascii')
    assert hashlib.sha256(canonical({k: doc[k] for k in ['scope', 'sources']})).hexdigest() == doc['sources_hash']
    expected = '4132231.40' if doc['kind'] == 'investment_report' else '113.33'
    metric = 'NPV' if doc['kind'] == 'investment_report' else 'TC'
    assert doc['metrics'][metric]['value'] == expected
    snapshots += 1
assert snapshots == proof['verification']['snapshots'] == 2
device = drill / 'evidence/device-proof.json'
if device.exists():
    result = json.loads(device.read_text(encoding='utf-8-sig'))
    assert result['state'] == 'PASS' and result['new_epoch'] == proof['new_epoch']
    assert result['raw_preserved'] is True and result['missing_held'] is True
    before = json.loads((drill/'evidence/private-device-before.json').read_text(encoding='utf-8-sig'))
    assert before['fixture_id'] == result['fixture_id']
    assert hashlib.sha256(canonical(before['accepted_operation'])).hexdigest() == result['accepted_hash']
    assert hashlib.sha256(canonical(before['missing_operation'])).hexdigest() == result['missing_hash']
    accepted = before['accepted_operation']; missing = before['missing_operation']
    assert accepted['sync_epoch'] == missing['sync_epoch'] == manifest['source_epoch']
    receipts = {r['operation_id'].decode('ascii'): r for r in tables['sync_operations']}
    assert receipts[accepted['operation_id']]['request_hash'].decode('ascii') == result['accepted_hash']
    assert missing['operation_id'] not in receipts
    assert missing['entity_id'].encode() not in [row['id'] for row in tables['activities']]
print(f'PASS independent package: {len(tables)} tables, {total_rows} rows, '
      f'{len(manifest["files"])} blobs, {snapshots} snapshots, '
      f'{len(manifest["totals"])} exact decimal totals; distinct target/epoch.')
