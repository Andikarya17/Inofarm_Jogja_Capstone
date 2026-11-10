"""Independent Fraction/Decimal oracle. No Laravel bootstrap, SQL or financial defaults."""
import argparse,json,random,subprocess
from fractions import Fraction as F
from decimal import Decimal,localcontext
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--php',required=True);args=p.parse_args();root=Path(__file__).resolve().parents[1]
def money(c): return ('-' if c<0 else '')+str(abs(c)//100)+'.'+str(abs(c)%100).zfill(2)
def rounded(v,scale):
    q,r=divmod(abs(v.numerator)*10**scale,v.denominator);q+=2*r>=v.denominator
    d=str(q).zfill(scale+1);return ('-' if v<0 and q else '')+(d[:-scale]+'.'+d[-scale:] if scale else d)
cases=[dict(initial='100000000.00',flows=['60000000.00','60000000.00'],rate='0.10',convention='interpolation',sources_complete=True)]
# The study's displayed present values reconcile once against I0. They are not annual raw cash flows.
study_pv = sum(F(v) for v in ('483459.70', '12549164.83', '7013850.31', '14902175.31', '16186296.70', '12390235.58', '2038171.36', '14707300.63'))
assert study_pv == F('80270654.42')
assert study_pv - F('300000000') == F('-219729345.58')
for initial,flows,rate,convention in [('0.00',['0.00'],'0','end_of_year'),('100.00',['0.00','0.00'],'0','interpolation'),('100.00',['110.00','-120.00'],'0.1','interpolation'),('100.00',['110.00',None],None,'interpolation'),('100.00',[None,'110.00'],'0','end_of_year'),('100.00',['100.00'],None,'interpolation'),('100.00',['100.00'],'-1','interpolation'),('0.01',['999999999999999999.99'],'0','interpolation'),('999999999999999999.99',['0.01'],'0','interpolation')]:
    cases.append(dict(initial=initial,flows=flows,rate=rate,convention=convention,sources_complete=True))
for initial,flows,rate in [('100.00',['1.00']*100,'0'),('999999999999999999.99',['0.00']*99+['0.01'],'-0.99999999'),('0.01',['999999999999999999.99']*100,'999999999999.99999999')]:
    cases.append(dict(initial=initial,flows=flows,rate=rate,convention='interpolation',sources_complete=True))
rng=random.Random(20261102)
for i in range(246):
    n=rng.randrange(1,13);flows=[None if rng.randrange(31)==0 else money(rng.randrange(0 if i%2 else -10**11,10**12)) for _ in range(n)]
    cases.append(dict(initial=None if rng.randrange(29)==0 else money(rng.randrange(0,10**12)),flows=flows,rate=rng.choice([None,'0','0.10','-0.10','1.00000000','-0.99999999']),convention=rng.choice([None,'interpolation','end_of_year']),sources_complete=bool(rng.randrange(2))))
proc=subprocess.run([args.php,str(root/'scripts/investment-evaluation-oracle.php')],input=json.dumps(cases).encode(),stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
actual=json.loads(proc.stdout);assert len(actual)==len(cases)
def assert_metric(metric,value,status=None,scale=8):
    if value is None:
        assert metric['value'] is None and metric['exact_fraction'] is None and metric['reasons']
        if status: assert metric['status']==status,(metric,status)
    else:
        assert metric['value']==rounded(value,scale),(metric['value'],rounded(value,scale))
        exact=metric['exact_fraction'];assert F(int(exact['numerator']),int(exact['denominator']))==value
        assert metric['status'] in ['available','provisional']
    assert metric['rounding']=='HALF_UP' and metric['display_scale']==scale
for index,(case,out) in enumerate(zip(cases,actual)):
    initial=None if case['initial'] is None else F(case['initial']);flows=[None if v is None else F(v) for v in case['flows']]
    rate=None if case['rate'] is None else F(case['rate']);valid_rate=rate is not None and 1+rate>0
    npv=None if initial is None or not valid_rate or None in flows else -initial+sum(cf/(1+rate)**t for t,cf in enumerate(flows,1))
    assert_metric(out['NPV'],npv,'unavailable' if npv is None else None,2)
    for name,discounted in [('PP',False),('DPP',True)]:
        val=None;status='unavailable';recovery=None;unknown_later=False;negative_later=False
        if initial is not None and initial>0 and case['convention'] is not None and (not discounted or valid_rate):
            total=F(0);known=True
            for year,cf in enumerate(flows,1):
                if recovery is not None:
                    unknown_later |= cf is None;negative_later |= cf is not None and cf<0;continue
                if cf is None: known=False;break
                amount=cf/(1+rate)**year if discounted else cf
                if total+amount>=initial:
                    recovery=year;val=F(year) if case['convention']=='end_of_year' else year-1+(initial-total)/amount
                total+=amount
            if recovery is None and known:status='not_recovered'
        assert_metric(out[name],val,status if val is None else None)
        if val is not None:
            assert out[name]['calculation_detail']['recovery_year']==recovery
            assert out[name]['calculation_detail']['negative_after_recovery']==negative_later
            if unknown_later: assert out[name]['status']=='provisional'
    irr_valid=initial is not None and initial>0 and None not in flows and all(cf>=0 for cf in flows) and any(cf>0 for cf in flows)
    irr=out['IRR']
    if not irr_valid:
        assert_metric(irr,None,'unavailable');continue
    assert irr['status'] in ['available','provisional'] and irr['exact_fraction'] is None
    meta=irr['calculation_detail'];lo=F(meta['lower_rate_fraction']);hi=F(meta['upper_rate_fraction'])
    assert -1<lo<=hi and hi-lo<=F('0.00000000000001') and meta['iterations']<=256
    def residual(r): return -initial+sum(cf/(1+r)**t for t,cf in enumerate(flows,1))
    # Endpoints are checked by exact Fraction arithmetic, not the PHP sign evaluator.
    assert residual(lo)>=0,(index,'lower bound',case,meta)
    assert residual(hi)<=0,(index,'upper bound',case,meta)
    approx=F(irr['value'])/100
    assert lo-F('0.00000000005')<=approx<=hi+F('0.00000000005')
    if len(flows)<=2:
        with localcontext() as ctx:
            ctx.prec=110
            dec=lambda f: Decimal(f.numerator)/Decimal(f.denominator)
            if len(flows)==1: root_rate=dec(flows[0]/initial)-1
            else:
                a,b,c=dec(initial),dec(flows[0]),dec(flows[1]);root_rate=(b+(b*b+4*a*c).sqrt())/(2*a)-1
            assert abs(Decimal(irr['value'])-100*root_rate)<=Decimal('0.0000000051')
first=actual[0]
assert {k:first[k]['value'] for k in first}=={'NPV':'4132231.40','IRR':'13.06623863','PP':'1.66666667','DPP':'1.91666667'}
print(f'PASS {len(cases)} independent investment Fraction/Decimal cases: exact NPV/PP/DPP, missing vs zero, signed/unknown flows, strict IRR root bounds and analytic one/two-year roots.')
