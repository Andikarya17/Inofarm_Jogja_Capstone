"""Read-only v2 JSON Schema/fixture/reference check; requires jsonschema in authoring runtime."""
import hashlib
import json
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker

root = Path(__file__).resolve().parents[1] / 'contracts' / 'v2'
schema = json.loads((root / 'activity-operation.schema.json').read_text(encoding='utf-8'))
Draft202012Validator.check_schema(schema)
fixture = json.loads((root / 'fixtures/activity-operation.json').read_text(encoding='utf-8'))
validator = Draft202012Validator(schema, format_checker=FormatChecker())
validator.validate(fixture['operation'])
canonical = json.dumps(fixture['operation'], sort_keys=True, separators=(',', ':'), ensure_ascii=False)
assert hashlib.sha256(canonical.encode()).hexdigest() == fixture['canonical_sha256']
api = json.loads((root / 'openapi.json').read_text(encoding='utf-8'))
assert api['openapi'] == '3.1.1'

def inspect(value):
    if isinstance(value, dict):
        if '$ref' in value:
            ref = value['$ref']
            if ref.startswith('#/'):
                target = api
                for key in ref[2:].split('/'):
                    target = target[key]
            else:
                assert (root / ref).is_file(), ref
        for child in value.values():
            inspect(child)
    elif isinstance(value, list):
        for child in value:
            inspect(child)

inspect(api)
unicode_fixture = json.loads((root / 'fixtures/canonical-unicode.json').read_text(encoding='utf-8'))
unicode_canonical = json.dumps(unicode_fixture['value'], sort_keys=True, separators=(',', ':'), ensure_ascii=False)
assert hashlib.sha256(unicode_canonical.encode()).hexdigest() == unicode_fixture['canonical_sha256']
harvest_schema = json.loads((root / 'harvest-operation.schema.json').read_text(encoding='utf-8'))
Draft202012Validator.check_schema(harvest_schema)
harvest_validator = Draft202012Validator(harvest_schema, format_checker=FormatChecker())
harvest_fixture = json.loads((root / 'fixtures/harvest-operation.json').read_text(encoding='utf-8'))
harvest_validator.validate(harvest_fixture['operation'])
harvest_canonical = json.dumps(harvest_fixture['operation'], sort_keys=True, separators=(',', ':'), ensure_ascii=False)
assert hashlib.sha256(harvest_canonical.encode()).hexdigest() == harvest_fixture['canonical_sha256']
for invalid in ('2', 1.5, True, -1, 2147483648):
    sample = json.loads(json.dumps(harvest_fixture['operation']))
    sample['payload']['items'][0]['fruit_count'] = invalid
    assert not harvest_validator.is_valid(sample)
for invalid in (1.5, 'NaN', '1,5', '1e2'):
    sample = json.loads(json.dumps(fixture['operation']))
    sample['payload']['manual_ph'] = invalid
    assert not validator.is_valid(sample)
for kind in ('expense', 'cost-allocation'):
    financial_schema = json.loads((root / f'{kind}-operation.schema.json').read_text(encoding='utf-8'))
    Draft202012Validator.check_schema(financial_schema)
    financial_validator = Draft202012Validator(financial_schema, format_checker=FormatChecker())
    financial_fixture = json.loads((root / f'fixtures/{kind}-operation.json').read_text(encoding='utf-8'))
    financial_validator.validate(financial_fixture['operation'])
    body = json.dumps(financial_fixture['operation'], sort_keys=True, separators=(',', ':'), ensure_ascii=False)
    assert hashlib.sha256(body.encode()).hexdigest() == financial_fixture['canonical_sha256']
    for invalid in (None, 0, 1.25, True, '', '-0', '-1', '1e2', '1,00', '1.001', '1000000000000000000'):
        sample = json.loads(json.dumps(financial_fixture['operation']))
        sample['payload']['amount'] = invalid
        assert not financial_validator.is_valid(sample), (kind, invalid)
print('PASS: activity/harvest/expense/allocation schemas, fixtures, decimal/integer rejection, SHA256 and local OpenAPI refs')
