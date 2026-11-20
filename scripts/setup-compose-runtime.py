#!/usr/bin/env python3
"""Prepare the locked AndroidX runtime for the direct Kotlin/AAPT2/D8 build."""
import argparse, hashlib, io, json, os, shutil, subprocess, urllib.request, xml.etree.ElementTree as ET, zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / '.tools/compose-build'
PROVENANCE = ROOT / 'android/compose-runtime-provenance.json'
GRADLE_HASH = '20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78'
def digest(data): return hashlib.sha256(data).hexdigest()
def prepare(refresh=False):
    CACHE.mkdir(parents=True, exist_ok=True)
    archive = CACHE / 'gradle-8.13-bin.zip'
    if not archive.exists():
        with urllib.request.urlopen('https://services.gradle.org/distributions/gradle-8.13-bin.zip', timeout=60) as response:
            archive.write_bytes(response.read(160 * 1024 * 1024))
    if digest(archive.read_bytes()) != GRADLE_HASH: raise SystemExit('Gradle distribution checksum mismatch')
    gradle = CACHE / 'gradle-8.13/bin/gradle'
    if not gradle.exists():
        with zipfile.ZipFile(archive) as z: z.extractall(CACHE)
    gradle.chmod(0o755)
    artifacts = CACHE / 'artifacts'
    expected = json.loads(PROVENANCE.read_text()) if PROVENANCE.exists() else None
    if refresh or expected is None or any(not (artifacts / x['file']).exists() for x in expected['artifacts']):
        env = dict(os.environ, GRADLE_USER_HOME=str(CACHE / 'home'))
        subprocess.run([str(gradle), '-p', str(ROOT / 'scripts/compose-dependencies'), '--no-daemon', '--console=plain', '--max-workers=2', 'resolveRuntime'], env=env, check=True)
    rows = sorted(set(tuple(line.split('\t')) for line in (artifacts / 'resolved.tsv').read_text().splitlines()))
    inventory = [dict(group=g, name=n, version=v, file=f, sha256=digest((artifacts/f).read_bytes())) for g,n,v,f in rows]
    if expected is not None and not refresh and inventory != expected['artifacts']: raise SystemExit('Compose runtime differs from reviewed artifact hashes')
    output = CACHE / 'runtime'
    if output.exists(): shutil.rmtree(output)
    output.mkdir(); (output/'jars').mkdir(); (output/'native').mkdir()
    jars, resources, namespaces, classes, native, notices = [], [], set(), set(), [], []
    for item in inventory:
        path = artifacts / item['file']; stem = path.stem
        with zipfile.ZipFile(path) as z:
            if any(name.startswith('libs/') and name.endswith('.jar') for name in z.namelist()):
                raise SystemExit('Embedded dependency jars require an explicit runtime inventory review')
            for name in z.namelist():
                if ('LICENSE' in name.upper() or 'NOTICE' in name.upper()) and not name.endswith('/'):
                    notices.append(item['group'] + ':' + item['name'] + ':' + item['version'] + '\n' + z.read(name).decode('utf-8'))
            if path.suffix == '.aar':
                manifest = ET.fromstring(z.read('AndroidManifest.xml'))
                package = manifest.get('package')
                if package: namespaces.add(package)
                sdk = manifest.find('uses-sdk')
                if sdk is not None and int(sdk.get('{http://schemas.android.com/apk/res/android}minSdkVersion','1')) > 26:
                    raise SystemExit('Dependency requires an Android version above the app minimum')
                jar_data = z.read('classes.jar')
                res = output / stem / 'res'
                for name in z.namelist():
                    if name.startswith('res/') and not name.endswith('/'):
                        target = output / stem / name
                        if not target.resolve().is_relative_to(output): raise SystemExit('Unsafe dependency archive path')
                        target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(z.read(name))
                    if name.startswith('jni/') and name.endswith('.so'):
                        target = output / 'native/lib' / name[4:]
                        if not target.resolve().is_relative_to(output): raise SystemExit('Unsafe native library archive path')
                        target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(z.read(name))
                        native.append(dict(path='lib/'+name[4:],sha256=digest(z.read(name))))
                if res.exists(): resources.append(str(res))
            else: jar_data = path.read_bytes()
        target = output / 'jars' / (stem+'.jar'); target.write_bytes(jar_data); jars.append(str(target))
        with zipfile.ZipFile(io.BytesIO(jar_data)) as z:
            classes.update('L'+name[:-6]+';' for name in z.namelist() if name.endswith('.class') and not name.startswith('META-INF/'))
    companions = ['Landroidx/core/app/NotificationCompat-IA;', 'Landroidx/core/content/IntentSanitizer-IA;',
        'Landroidx/core/view/ScrollFeedbackProviderCompat-IA;', 'Landroidx/core/view/inputmethod/TextAttributeCompat-IA;',
        'Landroidx/emoji2/text/FlatTrieBuilder-IA;']
    if not all(x.removesuffix('-IA;')+';' in classes for x in companions): raise SystemExit('Unexpected D8 interface companion parent')
    notice = 'AndroidX / Jetpack Compose and supporting runtime libraries\n\n' + '\n\n'.join(sorted(set(notices)))
    receipt = dict(dexCompanions=companions,licenseResource='res/raw/compose_runtime_notices.txt',licenseSha256=digest(notice.encode()),artifacts=inventory, resourceNamespaces=sorted(namespaces), classes=sorted(classes), nativeLibraries=sorted(native,key=lambda x:x['path']),
        gradleVersion='8.13',gradleSha256=GRADLE_HASH,composeBom='2026.11.01',activityVersion='1.13.0',
        sources=['https://developer.android.com/develop/ui/compose/bom','https://dl.google.com/dl/android/maven2/','https://repo.maven.apache.org/maven2/'])
    if expected is None or refresh: PROVENANCE.write_text(json.dumps(receipt,indent=2)+'\n')
    elif receipt != expected: raise SystemExit('Compose runtime extraction differs from the reviewed inventory')
    (output/'classpath.txt').write_text(':'.join(jars))
    (output/'resources.txt').write_text('\n'.join(resources)+'\n')
    (output/'packages.txt').write_text(':'.join(sorted(namespaces)))
    (output/'jars.txt').write_text('\n'.join(jars)+'\n')
    notice = 'AndroidX / Jetpack Compose and supporting runtime libraries\n\n' + '\n\n'.join(sorted(set(notices)))
    license_path = ROOT/'android/app/src/main/res/raw/compose_runtime_notices.txt'
    if refresh or not license_path.exists(): license_path.write_text(notice)
    elif license_path.read_text() != notice: raise SystemExit('Bundled Compose notices differ from the dependency artifacts')
    print(f'Verified {len(inventory)} Compose runtime artifacts; no library manifest components are merged')
if __name__ == '__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--refresh',action='store_true');args=parser.parse_args();prepare(args.refresh)
