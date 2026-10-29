#!/usr/bin/env python3
"""Install pinned official build tools into the workspace cache, checking SHA-256."""
from pathlib import Path
import hashlib,json,urllib.request,zipfile
root=Path(__file__).resolve().parent.parent
lock=json.loads((root/'scripts/kotlin-toolchain.json').read_text())
cache=root/'.tools/kotlin-0300';cache.mkdir(parents=True,exist_ok=True)
def checked(path,url,sha):
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest()!=sha:
        temporary=path.with_suffix(path.suffix+'.download')
        with urllib.request.urlopen(url,timeout=90) as response,temporary.open('wb') as out:
            while chunk:=response.read(1024*1024):out.write(chunk)
        if hashlib.sha256(temporary.read_bytes()).hexdigest()!=sha:
            temporary.unlink();raise SystemExit('Build-tool checksum mismatch: '+path.name)
        temporary.replace(path)
    return path
archive=checked(cache/'kotlin-compiler.zip',lock['compilerUrl'],lock['compilerSha256'])
checked(cache/'r8.jar',lock['r8Url'],lock['r8Sha256'])
stdlib=cache/'kotlinc/lib/kotlin-stdlib.jar'
with zipfile.ZipFile(archive) as z:
    for member in z.infolist():
        target=(cache/member.filename).resolve()
        if not target.is_relative_to(cache.resolve()):raise SystemExit('Invalid compiler archive path')
    # Verify all executable/compiler inputs, rather than trusting a cached stdlib
    # as a proxy for the other launchers and libraries in the distribution.
    inputs=[m for m in z.infolist() if not m.is_dir() and m.filename.startswith(('kotlinc/bin/','kotlinc/lib/'))]
    intact=all((cache/m.filename).is_file() and hashlib.sha256((cache/m.filename).read_bytes()).digest()==hashlib.sha256(z.read(m)).digest() for m in inputs)
    if not intact:z.extractall(cache)
for path in (cache/'kotlinc/bin').iterdir():path.chmod(path.stat().st_mode|0o111)
assert hashlib.sha256(stdlib.read_bytes()).hexdigest()==lock['stdlibSha256']
print('Verified Kotlin '+lock['kotlinVersion']+' and D8/R8 '+lock['r8Version'])
