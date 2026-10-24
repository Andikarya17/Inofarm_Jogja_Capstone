"""Verified dependency archive download; use upstream URL and published SHA256."""
import argparse, concurrent.futures, hashlib, math, pathlib, subprocess
parser=argparse.ArgumentParser()
parser.add_argument('url'); parser.add_argument('size',type=int); parser.add_argument('sha256'); parser.add_argument('output')
args=parser.parse_args(); output=pathlib.Path(args.output).resolve(); output.parent.mkdir(parents=True,exist_ok=True)
count=32; chunk=math.ceil(args.size/count)
def download(index):
    start,end=index*chunk,min(args.size-1,(index+1)*chunk-1)
    part=output.with_name(output.name+f'.part-{index:02d}')
    if not part.exists() or part.stat().st_size!=end-start+1:
        subprocess.run(['curl.exe','-sSfL','--retry','3','--range',f'{start}-{end}',args.url,'-o',str(part)],check=True)
    if part.stat().st_size!=end-start+1: raise RuntimeError(f'Invalid HTTP range {index}')
    return part
with concurrent.futures.ThreadPoolExecutor(max_workers=count) as pool: parts=list(pool.map(download,range(count)))
with output.open('wb') as stream:
    for part in parts: stream.write(part.read_bytes())
if hashlib.sha256(output.read_bytes()).hexdigest()!=args.sha256: raise RuntimeError('SHA256 mismatch')
print('Verified dependency archive:',output.name)
