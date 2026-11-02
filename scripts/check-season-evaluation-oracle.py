"""Independent Fraction oracle and actual HTTP snapshot/schema/hash verification. No SQL."""
import argparse
import hashlib
import json
import random
import subprocess
from fractions import Fraction
from pathlib import Path

from jsonschema import Draft202012Validator
from contract_formats import wire_formats

parser = argparse.ArgumentParser()
parser.add_argument('--php', required=True)
parser.add_argument('--public', default='tmp/fin12-public.json')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]


def fixed(integer, scale):
    sign = '-' if integer < 0 else ''
    digits = str(abs(integer)).zfill(scale + 1)
    return sign + (digits if not scale else digits[:-scale] + '.' + digits[-scale:])


def half_up(value, scale):
    whole, remainder = divmod(abs(value.numerator) * 10**scale, value.denominator)
    whole += 2 * remainder >= value.denominator
    return fixed(-whole if value < 0 else whole, scale)


def expected(case):
    fc, vc, tr, q = [None if case[k] is None else Fraction(case[k]) for k in ['FC', 'VC', 'TR', 'Q']]
    tc = None if fc is None or vc is None else fc + vc
    profit = None if tr is None or tc is None else tr - tc
    return {
        'FC': (fc, 2), 'VC': (vc, 2), 'TC': (tc, 2), 'TR': (tr, 2), 'Q': (q, 6), 'L': (profit, 2),
        'RC': (None if tr is None or tc is None or tc <= 0 else tr/tc, 8),
        'BC': (None if profit is None or tc is None or tc <= 0 else profit/tc, 8),
        'P': (None if tr is None or q is None or q <= 0 else tr/q, 6),
        'BEP_Q': (None if tc is None or tr is None or q is None or tr <= 0 or q <= 0 else tc*q/tr, 6),
        'BEP_PRICE': (None if tc is None or q is None or q <= 0 else tc/q, 6),
    }


def verify(case, actual):
    for name, (value, scale) in expected(case).items():
        metric = actual[name]
        assert metric['value'] == (None if value is None else half_up(value, scale)), (name, case, metric)
        assert metric['display_scale'] == scale
        assert metric['rounding'] == 'HALF_UP'
        if value is None:
            assert metric['status'] == 'unavailable' and metric['exact_fraction'] is None and metric['reasons']
        else:
            exact = metric['exact_fraction']
            assert Fraction(int(exact['numerator']), int(exact['denominator'])) == value
            groups = ['costs'] if name in ['FC', 'VC', 'TC'] else ['sales'] if name in ['TR', 'Q', 'P'] else ['costs', 'sales']
            status = 'available' if all(case['coverage'][g] == 'complete' for g in groups) else 'provisional'
            assert metric['status'] == status
            if status == 'provisional':
                assert metric['reasons']


rng = random.Random(20261030)
cases = []
for values in [('5000000', '3000000', '12000000', '400'), ('0', '0', '0', '0'), ('0.01', '0', '0.01', '3'), ('1', '0', '0', '1'),
               (None, '10', None, None), ('999999999999999999.99', '999999999999999999.99', '0.01', '0.000001')]:
    cases.append(dict(zip(['FC', 'VC', 'TR', 'Q'], values), coverage={'costs': 'complete', 'sales': 'complete'}))
for _ in range(1018):
    case = {k: None if rng.randrange(13) == 0 else fixed(0 if rng.randrange(8) == 0 else rng.randrange(10**24), scale)
            for k, scale in [('FC', 2), ('VC', 2), ('TR', 2), ('Q', 6)]}
    case['coverage'] = {k: rng.choice(['unknown', 'partial', 'complete']) for k in ['costs', 'sales']}
    cases.append(case)
result = subprocess.run([args.php, str(root/'scripts/season-evaluation-oracle.php')], input=json.dumps(cases).encode(), stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
actual = json.loads(result.stdout)
assert len(actual) == len(cases)
for case, metrics in zip(cases, actual):
    verify(case, metrics)

api = json.loads((root/'contracts/v2/openapi.json').read_text(encoding='utf-8'))


def schema(name):
    return Draft202012Validator({'$ref': '#/components/schemas/'+name, 'components': api['components']}, format_checker=wire_formats)


for metrics in actual:
    for metric in metrics.values():
        schema('SeasonReportIndicator').validate(metric)
request_schema = json.loads((root/'contracts/v2/season-report-operation.schema.json').read_text(encoding='utf-8'))
golden = json.loads((root/'contracts/v2/fixtures/season-report-operation.json').read_text(encoding='utf-8'))
validator = Draft202012Validator(request_schema, format_checker=wire_formats)
validator.validate(golden['operation'])


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest()


assert digest(golden['operation']) == golden['canonical_sha256']
for value in [False, '0', -1, 0.5]:
    bad = json.loads(json.dumps(golden['operation']))
    bad['payload']['known_pending_count'] = value
    assert not validator.is_valid(bad)
sample = root/args.public
if sample.exists():
    data = json.loads(sample.read_text(encoding='utf-8'))
    schema('SeasonReportReceipt').validate(data['receipt'])
    full = data['full']
    for source in full['sources']:
        schema('ReportSource').validate(source)
    assert digest({'scope': full['scope'], 'sources': full['sources']}) == full['sources_hash']
    assert digest(full) == data['receipt']['data']['snapshot_hash']
    assert data['receipt']['data'] == {k: v for k, v in full.items() if k != 'sources'} | {'snapshot_hash': digest(full)}
    sums = {key: Fraction(0) for key in ['FC', 'VC', 'TR', 'Q']}
    for source in full['sources']:
        if source['included']:
            for key, amount in source['contribution'].items():
                sums[key] += Fraction(amount)
    known = {k: half_up(v, 6 if k == 'Q' else 2) for k, v in sums.items()}
    verify({**known, 'coverage': full['coverage']}, full['metrics'])
    print('PASS actual HTTP receipt/source schemas, Python snapshot/source hashes and source-derived totals.')
else:
    raise AssertionError('Actual HTTP snapshot fixture missing; run FinanceSeasonReportTest first.')
print(f'PASS {len(cases)} independent Fraction cases, exact fractions, per-indicator null/status/rounding and operation golden.')
