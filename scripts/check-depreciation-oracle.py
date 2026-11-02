"""Independent exact Fraction oracle versus the PHP service, and schema validation.

Read-only: no DB bootstrap, SQL, production values or dependency installation.
Usage: python scripts/check-depreciation-oracle.py --php path/to/php.exe
"""
import argparse
import copy
import json
import subprocess
from fractions import Fraction
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker

parser = argparse.ArgumentParser()
parser.add_argument('--php', default='php')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
cases = []
for cost, residual, life in [('100.00', '0.00', 3), ('0.01', '0.00', 2), ('1.00', '0.00', 3),
                              ('10.00', '10.00', 4), ('999999999999999999.99', '0.00', 997), ('100.99', '12.34', 7)]:
    cents = (Fraction(cost) - Fraction(residual)) * 100
    def cumulative(k):
        value = cents * k / life
        return (2 * value.numerator + value.denominator) // (2 * value.denominator)
    total = 0
    for year in range(1, life + 1):
        budget_cents = cumulative(year) - cumulative(year - 1)
        total += budget_cents
        cases.append({'base': f'{int(cents)//100}.{int(cents)%100:02}', 'life': life, 'year': year,
                      'expected': f'{budget_cents//100}.{budget_cents%100:02}'})
    assert total == cents
php_code = r'''
require 'backend/vendor/autoload.php';
$cases=json_decode(stream_get_contents(STDIN), true, flags:JSON_THROW_ON_ERROR);
$out=[];
foreach($cases as $case) {$out[]=App\Domain\Finance\DepreciationMath::year($case['base'],$case['life'],$case['year'],'2024-02-29');}
echo json_encode($out,JSON_THROW_ON_ERROR);
'''
result = subprocess.run([args.php, '-r', php_code], input=json.dumps(cases), text=True, capture_output=True, cwd=root, check=True)
rows = json.loads(result.stdout)
assert len(rows) == len(cases)
for case, row in zip(cases, rows):
    assert row['budget'] == case['expected'], (case, row)
    assert row['formula_version'] == 'anniversary-cumulative-half-up-v1'
assert rows[1]['start'] == '2025-02-28' and rows[1]['end'] == '2026-02-28'
uuid = '11111111-1111-4111-8111-111111111111'
payloads = {'asset': {'expense_id':uuid, 'name':'Synthetic', 'cost':'100.00', 'residual':'0.00', 'life_years':3,
                       'in_service_date':'2024-02-29', 'usage_basis':'Explicit synthetic usage'},
            'depreciation_allocation': {'asset_id':uuid, 'season_id':uuid, 'usage_reference':uuid, 'usage_year':1,
                                        'period_start':'2024-02-29', 'period_end':'2025-02-28', 'amount':'33.33', 'allocation_basis':'Synthetic'}}
for kind, payload in payloads.items():
    schema = json.loads((root/'contracts/v2'/f'{kind.replace("_", "-")}-operation.schema.json').read_text(encoding='utf-8'))
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    operation = {'schema_version':'2', 'sync_epoch':uuid, 'operation_id':uuid, 'entity_id':uuid, 'entity_type':kind,
                 'action':'create', 'base_version':None, 'payload':payload}
    validator.validate(operation)
    invalid = copy.deepcopy(operation); invalid['payload']['amount' if kind != 'asset' else 'cost'] = 1.25
    assert not validator.is_valid(invalid)
    operation.update(action='update', base_version=1, payload={**payload,'reason':'Synthetic correction'})
    validator.validate(operation)
    operation.update(action='cancel', payload={'reason':'Synthetic cancellation'})
    validator.validate(operation)
print(f'PASS independent Fraction oracle: {len(cases)} PHP annual budgets, telescoping exact bases and two operation schemas/create-update-cancel.')
public = root/'tmp/fin11-public.json'
if public.is_file():
    api = json.loads((root/'contracts/v2/openapi.json').read_text(encoding='utf-8'))
    data = json.loads(public.read_text(encoding='utf-8'))
    for kind, title in [('asset','AssetPublic'),('allocation','DepreciationAllocationPublic')]:
        Draft202012Validator(api['components']['schemas'][title], format_checker=FormatChecker()).validate(data[kind])
    print('PASS actual guarded MySQL HTTP asset/allocation DTOs versus OpenAPI public schemas.')
