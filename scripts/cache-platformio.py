"""Seed PlatformIO's normal verified download cache from an upstream archive."""
import argparse, hashlib, os, pathlib, shutil
parser=argparse.ArgumentParser(); parser.add_argument('url'); parser.add_argument('sha256'); parser.add_argument('archive'); args=parser.parse_args()
root=pathlib.Path(__file__).resolve().parents[1]
os.environ['PLATFORMIO_CORE_DIR']=str(root/'.tools/platformio')
from platformio.registry.mirror import RegistryFileMirrorIterator
from platformio.package.manager.tool import ToolPackageManager
archive=pathlib.Path(args.archive)
if hashlib.sha256(archive.read_bytes()).hexdigest()!=args.sha256: raise RuntimeError('Archive checksum mismatch')
manager=ToolPackageManager()
mirror,checksum=next(iter(RegistryFileMirrorIterator(args.url)))
if checksum!=args.sha256: raise RuntimeError('Registry checksum changed')
destination=manager.compute_download_path(mirror,checksum)
shutil.copyfile(archive,destination); manager.set_download_utime(destination)
print('PlatformIO verified cache populated:', archive.name)
