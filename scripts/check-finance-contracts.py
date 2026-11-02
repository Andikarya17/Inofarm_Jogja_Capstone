"""Read-only v2 JSON Schema/fixture/reference check; requires jsonschema in authoring runtime."""
import hashlib
import base64
import json
from pathlib import Path

from jsonschema import Draft202012Validator
from contract_formats import wire_formats

root = Path(__file__).resolve().parents[1] / 'contracts' / 'v2'
schema = json.loads((root / 'activity-operation.schema.json').read_text(encoding='utf-8'))
Draft202012Validator.check_schema(schema)
fixture = json.loads((root / 'fixtures/activity-operation.json').read_text(encoding='utf-8'))
validator = Draft202012Validator(schema, format_checker=wire_formats)
validator.validate(fixture['operation'])
measurement = json.loads((root / 'fixtures/measurement-operation.json').read_text(encoding='utf-8'))
validator.validate(measurement['operation'])
assert hashlib.sha256(json.dumps(measurement['operation'], sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()).hexdigest() == measurement['canonical_sha256']
for key in ('manual_ec', 'manual_ppm'):
    for invalid in (None, '', 'NaN', '-1', '1,2', '1e2', 1.25, '0.0000001'):
        sample = json.loads(json.dumps(measurement['operation']))
        sample['payload'][key] = invalid
        assert not validator.is_valid(sample), (key, invalid)
    sample = json.loads(json.dumps(measurement['operation']))
    del sample['payload'][key]
    assert not validator.is_valid(sample), key
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
harvest_validator = Draft202012Validator(harvest_schema, format_checker=wire_formats)
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
    financial_validator = Draft202012Validator(financial_schema, format_checker=wire_formats)
    financial_fixture = json.loads((root / f'fixtures/{kind}-operation.json').read_text(encoding='utf-8'))
    financial_validator.validate(financial_fixture['operation'])
    body = json.dumps(financial_fixture['operation'], sort_keys=True, separators=(',', ':'), ensure_ascii=False)
    assert hashlib.sha256(body.encode()).hexdigest() == financial_fixture['canonical_sha256']
    for invalid in (None, 0, 1.25, True, '', '-0', '-1', '1e2', '1,00', '1.001', '1000000000000000000'):
        sample = json.loads(json.dumps(financial_fixture['operation']))
        sample['payload']['amount'] = invalid
        assert not financial_validator.is_valid(sample), (kind, invalid)
attachment_schema = json.loads((root / 'attachment-operation.schema.json').read_text(encoding='utf-8'))
Draft202012Validator.check_schema(attachment_schema)
attachment_validator = Draft202012Validator(attachment_schema, format_checker=wire_formats)
attachment_fixture = json.loads((root / 'fixtures/attachment-operation.json').read_text(encoding='utf-8'))
attachment_validator.validate(attachment_fixture['operation'])
binary = base64.b64decode(attachment_fixture['fixture_file_base64'], validate=True)
assert len(binary) == attachment_fixture['operation']['payload']['bytes']
assert hashlib.sha256(binary).hexdigest() == attachment_fixture['operation']['payload']['sha256']
encoded = json.dumps(attachment_fixture['operation'], sort_keys=True, separators=(',', ':'), ensure_ascii=False)
assert hashlib.sha256(encoded.encode()).hexdigest() == attachment_fixture['canonical_sha256']
for field, invalid in [('bytes', 1.5), ('bytes', True), ('bytes', 0), ('bytes', 33554433), ('owner_version', '1'), ('owner_type', 'activity'), ('original_name', '../bad.png')]:
    sample = json.loads(json.dumps(attachment_fixture['operation']))
    sample['payload'][field] = invalid
    assert not attachment_validator.is_valid(sample), (field, invalid)
print('PASS: activity/harvest/expense/allocation/attachment schemas, binary and operation goldens, typed rejection and local OpenAPI refs')
sale_schema = json.loads((root / 'sale-operation.schema.json').read_text(encoding='utf-8'))
Draft202012Validator.check_schema(sale_schema)
sale_validator = Draft202012Validator(sale_schema, format_checker=wire_formats)
sale = json.loads((root / 'fixtures/sale-operation.json').read_text(encoding='utf-8'))
sale_validator.validate(sale['operation'])
encoded = json.dumps(sale['operation'], sort_keys=True, separators=(',', ':'), ensure_ascii=False)
assert hashlib.sha256(encoded.encode()).hexdigest() == sale['canonical_sha256']
for key, value in [('quantity_kg', '0'), ('quantity_kg', '0.000000'), ('quantity_kg', 1.5), ('price_per_kg', '1e2'), ('price_per_kg', '0.0000001')]:
    invalid = json.loads(json.dumps(sale['operation']))
    invalid['payload']['items'][0][key] = value
    assert not sale_validator.is_valid(invalid), (key, value)
print('PASS: paid-sale schema and independent multiline HALF_UP operation golden')

# Response timestamps are normalized to UTC by services; operation input rules stay strict.
for resource in ['ExpensePublic', 'SalePublic']:
    timestamp = Draft202012Validator(api['components']['schemas'][resource]['properties']['paid_at'], format_checker=wire_formats)
    for value in ['2026-10-30T01:00:00.000000Z', '2026-10-30T01:00:00Z', '2026-10-30T08:00:00+07:00']:
        timestamp.validate(value)
    for value in ['2026-10-30T01:00:00', '2026-13-30T01:00:00Z', 'not-a-date', 123]:
        assert not timestamp.is_valid(value), (resource, value)
print('PASS: public payment UTC/fractional/offset timestamps and malformed rejections; request rules unchanged')

# FIN12 additive request + actual server public snapshot fixture.
report_schema=json.loads((root/'season-report-operation.schema.json').read_text(encoding='utf-8'))
Draft202012Validator.check_schema(report_schema)
report_fixture=json.loads((root/'fixtures/season-report-operation.json').read_text(encoding='utf-8'))
Draft202012Validator(report_schema,format_checker=wire_formats).validate(report_fixture['operation'])
assert hashlib.sha256(json.dumps(report_fixture['operation'],sort_keys=True,separators=(',', ':'),ensure_ascii=False).encode()).hexdigest()==report_fixture['canonical_sha256']
public=json.loads((root/'fixtures/season-report-public.json').read_text(encoding='utf-8'))
for name,body in [('SeasonReportPreview',public['preview']),('SeasonReportReceipt',public['receipt'])]:
    Draft202012Validator({'$ref':'#/components/schemas/'+name,'components':api['components']},format_checker=wire_formats).validate(body)
assert hashlib.sha256(json.dumps(public['full'],sort_keys=True,separators=(',', ':'),ensure_ascii=False).encode()).hexdigest()==public['receipt']['data']['snapshot_hash']
print('FIN12 request and actual report snapshot contracts: PASS')

# FIN13 separate typed DTOs preserve the narrower published FIN12 schemas.
for kind in ['investment-scenario','investment-report']:
    schema=json.loads((root/f'{kind}-operation.schema.json').read_text(encoding='utf-8'))
    Draft202012Validator.check_schema(schema)
    fixture=json.loads((root/f'fixtures/{kind}-operation.json').read_text(encoding='utf-8'))
    validator=Draft202012Validator(schema,format_checker=wire_formats)
    validator.validate(fixture['operation'])
    assert hashlib.sha256(json.dumps(fixture['operation'],sort_keys=True,separators=(',', ':'),ensure_ascii=False).encode()).hexdigest()==fixture['canonical_sha256']
    for key,value in [('horizon_years','2'),('horizon_years',True),('horizon_years',101),('initial_outlay',1.5),('initial_outlay','-1'),('discount_rate','-1.00000000'),('discount_rate','1e-2') ] if kind=='investment-scenario' else [('expected_scenario_version','1'),('expected_scenario_version',True),('expected_sources_hash','bad')]:
        invalid=json.loads(json.dumps(fixture['operation']));invalid['payload'][key]=value
        assert not validator.is_valid(invalid),(kind,key,value)
public=json.loads((root/'fixtures/investment-report-public.json').read_text(encoding='utf-8'))
for name,body in [('InvestmentReportPreview',public['preview']),('InvestmentReportReceipt',public['receipt']),('InvestmentScenarioPublic',public['scenario'])]+[('InvestmentReportSource',s) for s in public['full']['sources']]:
    Draft202012Validator({'$ref':'#/components/schemas/'+name,'components':api['components']},format_checker=wire_formats).validate(body)
assert hashlib.sha256(json.dumps(public['full'],sort_keys=True,separators=(',', ':'),ensure_ascii=False).encode()).hexdigest()==public['receipt']['data']['snapshot_hash']
assert hashlib.sha256(json.dumps({k:public['full'][k] for k in ['scope','sources']},sort_keys=True,separators=(',', ':'),ensure_ascii=False).encode()).hexdigest()==public['receipt']['data']['sources_hash']
print('FIN13 request goldens, actual scenario/report/source DTOs, independent frozen hashes: PASS')

# FIN15 immutable historical envelope and explicit missing-receipt outcome.
recovery=json.loads((root/'fixtures/recovery-review.json').read_text(encoding='utf-8'))
for name,body in [('RecoveryReviewRequest',recovery['request']),('RecoveryReceiptReview',recovery['missing_response'])]:
    Draft202012Validator({'$ref':'#/components/schemas/'+name,'components':api['components']},format_checker=wire_formats).validate(body)
assert hashlib.sha256(json.dumps(recovery['request']['operation'],sort_keys=True,separators=(',', ':'),ensure_ascii=False).encode()).hexdigest()==recovery['operation_sha256']==recovery['missing_response']['request_hash']
review_validator=Draft202012Validator({'$ref':'#/components/schemas/RecoveryReviewRequest','components':api['components']},format_checker=wire_formats)
for path,value in [('sync_epoch','bad'),('actor_id','client-cannot-select-actor')]:
    sample=json.loads(json.dumps(recovery['request']));sample[path]=value
    assert not review_validator.is_valid(sample)
print('FIN15 historical envelope hash, typed review/missing-receipt schema and forged actor rejection: PASS')

status_validator=Draft202012Validator({'$ref':'#/components/schemas/SyncStatusPublic','components':api['components']},format_checker=wire_formats)
status={'schema_version':'2','sync_epoch':'11111111-1111-4111-8111-111111111111','user':{'id':'22222222-2222-4222-8222-222222222222','name':'Akun sintetis','role':'worker','version':1}}
status_validator.validate(status)
for key,value in [('role','owner'),('id','invalid'),('version',0),('password','forbidden')]:
    bad=json.loads(json.dumps(status));bad['user'][key]=value
    assert not status_validator.is_valid(bad)
print('FIN16 sync profile: public identity, role, version and credential exclusion PASS')
