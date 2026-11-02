"""Independent typed CSV/PDF artifact verification; authoring runtime needs pypdf."""
import argparse
import csv
import hashlib
import json
import re
import unicodedata
from pathlib import Path

from pypdf import PdfReader

parser = argparse.ArgumentParser()
parser.add_argument('prefix', help='Synthetic export prefix, e.g. tmp/fin14-investment')
args = parser.parse_args()
prefix = Path(args.prefix)
document = json.loads(Path(str(prefix) + '-document.json').read_text(encoding='utf-8'))

def leaves(value, path=''):
    if not isinstance(value, (dict, list)) or not value:
        yield path, value
        return
    items = value.items() if isinstance(value, dict) else enumerate(value)
    for key, child in items:
        yield from leaves(child, path + '/' + str(key).replace('~', '~0').replace('/', '~1'))

expected = dict(leaves(document))
with Path(str(prefix) + '.csv').open(encoding='utf-8-sig', newline='') as stream:
    rows = list(csv.DictReader(stream))
assert len(rows) == len(expected)
assert len({row['field'] for row in rows}) == len(rows)
for row in rows:
    value = json.loads(row['json_value'])
    original = expected[row['field']]
    assert type(value) is type(original) and value == original, row['field']
    raw = row['value']
    if row['text_escaped'] == 'true':
        assert raw.startswith("'")
        raw = raw[1:]
    else:
        leading = raw.lstrip()
        while leading and unicodedata.category(leading[0])[0] in ['C', 'Z']:
            leading = leading[1:].lstrip()
        assert not leading.startswith(('=', '+', '-', '@')), row['field']

full = {k: v for k, v in document.items() if k not in ['snapshot_hash', 'export_schema_version']}
canonical = json.dumps(full, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()
assert hashlib.sha256(canonical).hexdigest() == document['snapshot_hash']
assert hashlib.sha256(json.dumps({'scope': full['scope'], 'sources': full['sources']}, ensure_ascii=False,
                                 sort_keys=True, separators=(',', ':')).encode()).hexdigest() == full['sources_hash']
reader = PdfReader(str(prefix) + '.pdf')
text = '\n'.join(page.extract_text() or '' for page in reader.pages)
compact = re.sub(r'\s+', '', text.replace('\u200b', ''))
for field in ['id', 'snapshot_hash', 'sources_hash', 'formula_version']:
    assert document[field] in compact, field
for key, metric in full['metrics'].items():
    assert key in text
    if metric['value'] is not None:
        assert metric['value'] in compact, (key, metric['value'])
for source in full['sources']:
    assert source.get('id', source.get('scenario_id')) in compact
assert len(reader.pages) >= 1
print(f'PASS: {len(rows)} typed CSV leaves, canonical snapshot/source hashes, '
      f'{len(full["metrics"])} PDF metrics, {len(full["sources"])} sources, {len(reader.pages)} pages')
