"""Download official Gradle release in verified HTTP ranges; local portable tooling only."""
import concurrent.futures, hashlib, math, pathlib, subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1] / '.tools'
URL = 'https://github.com/gradle/gradle-distributions/releases/download/v9.3.1/gradle-9.3.1-bin.zip'
SIZE = 137037885

def part(index):
    chunk = math.ceil(SIZE / 24)
    start, end = index * chunk, min(SIZE - 1, (index + 1) * chunk - 1)
    dest = ROOT / f'gradle-part-{index:02d}'
    subprocess.run(['curl.exe','-sSfL','--retry','3','--range',f'{start}-{end}',URL,'-o',str(dest)],check=True)
    if dest.stat().st_size != end - start + 1:
        raise RuntimeError(f'Range {index} size mismatch: {dest.stat().st_size}')
    return dest

if __name__ == '__main__':
    # Size read from release response before running this script; full checksum verified after merge.
    with concurrent.futures.ThreadPoolExecutor(max_workers=24) as pool:
        parts = list(pool.map(part, range(24)))
    dest = ROOT / 'gradle-complete.zip'
    with dest.open('wb') as out:
        for p in parts: out.write(p.read_bytes())
    expected = subprocess.check_output(['curl.exe','-sSfL','https://services.gradle.org/distributions/gradle-9.3.1-bin.zip.sha256'],text=True).strip()
    actual = hashlib.sha256(dest.read_bytes()).hexdigest()
    if actual != expected: raise RuntimeError('Gradle SHA256 mismatch')
    print('Verified Gradle 9.3.1 distribution:', actual)
