#!/usr/bin/env python3
"""Run the release integration matrix only on an explicitly selected emulator.

This changes emulator data and grants. It refuses physical-device serials.
No test APK is installed on a user's phone.
"""
import argparse, hashlib, json, re, subprocess, time, xml.etree.ElementTree as E
from pathlib import Path

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--modes', default='scan-efficiency,split-mdns,homekit,branding,session-only,session-cold,fresh-presence,mac-vendors,details-upgrade,service-retries,resolver-upgrade,engine-upgrade,icmp,full,grouping,identification,discovery-upgrade,ws-discovery,navigation,scan-details,options,wifi-name')
parser.add_argument('--output', required=True)
args = parser.parse_args()
if not re.fullmatch(r'emulator-[0-9]+', args.serial):
    parser.error('Only an isolated emulator serial is allowed')
adb = root / '.tools/sdk/platform-tools/adb'
base = [str(adb), '-s', args.serial]
output = root / args.output
output.mkdir(parents=True, exist_ok=True)
def run(arguments, timeout=60):
    r = subprocess.run(base + arguments, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=timeout)
    if r.returncode:
        raise RuntimeError(r.stdout)
    return r.stdout
assert run(['shell', 'getprop', 'ro.kernel.qemu']).strip() == '1', 'Selected target must be an emulator'
api = int(run(['shell', 'getprop', 'ro.build.version.sdk']).strip())
version = E.parse(root / 'android/app/src/main/AndroidManifest.xml').getroot().get('{http://schemas.android.com/apk/res/android}versionName')
apk = root / ('artifacts/Network-Lens-' + version + '.apk')
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
test_apk = root / '.tools/android-tests/tests.apk'
test_digest = hashlib.sha256(test_apk.read_bytes()).hexdigest()
print(run(['install', '--no-incremental', '-r', str(apk)]), flush=True)
print(run(['install', '--no-incremental', '-r', str(root / '.tools/android-tests/tests.apk')]), flush=True)
installed = run(['shell', 'pm', 'path', 'io.github.kazands.networklens']).strip()
assert installed.startswith('package:/data/app/') and installed.endswith('/base.apk'), installed
installed_digest = run(['shell', 'sha256sum', installed[len('package:'):]]).split()[0]
assert installed_digest == digest, 'Installed release differs from the APK under test'
installed_test = run(['shell', 'pm', 'path', 'io.github.kazands.networklens.test']).strip()
assert installed_test.startswith('package:/data/app/') and installed_test.endswith('/base.apk')
assert run(['shell', 'sha256sum', installed_test[len('package:'):]]).split()[0] == test_digest, 'Installed test APK differs from the test binary under review'
results = []
for mode in args.modes.split(','):
    print('START API ' + str(api) + ': ' + mode, flush=True)
    began = time.monotonic()
    try:
        assert hashlib.sha256(apk.read_bytes()).hexdigest() == digest, 'APK changed during matrix'
        run(['shell', 'am', 'force-stop', 'io.github.kazands.networklens'])
        if api >= 37 and mode not in ('permission', 'icmp-denied'):
            run(['shell', 'pm', 'grant', 'io.github.kazands.networklens', 'android.permission.ACCESS_LOCAL_NETWORK'])
        permissions = ['android.permission.ACCESS_FINE_LOCATION', 'android.permission.ACCESS_COARSE_LOCATION'] if mode == 'wifi-name' else ['android.permission.ACCESS_LOCAL_NETWORK'] if mode in ('permission', 'icmp-denied') else []
        for permission in permissions:
            run(['shell', 'pm', 'revoke', 'io.github.kazands.networklens', permission])
            if api >= 28:
                run(['shell', 'pm', 'clear-permission-flags', 'io.github.kazands.networklens', permission, 'user-set', 'user-fixed'])
        stream = run(['shell', 'am', 'instrument', '-w', '-e', 'mode', mode, 'io.github.kazands.networklens.test/io.github.kazands.networklens.test.SmokeInstrumentation'], 420)
        (output / (mode + '-api' + str(api) + '.txt')).write_text(stream)
        match = re.search(r'PASS · (\d+) Android checks', stream)
        passed = bool(match) and 'FAIL' not in stream and 'INSTRUMENTATION_FAILED' not in stream
        result = {'mode': mode, 'status': 'PASS' if passed else 'FAIL', 'checks': int(match[1]) if passed else 0, 'seconds': round(time.monotonic() - began)}
        print(stream, flush=True)
    except Exception as error:
        result = {'mode': mode, 'status': 'FAIL', 'error': str(error), 'seconds': round(time.monotonic() - began)}
        (output / (mode + '-api' + str(api) + '.txt')).write_text(str(error))
        print('FAIL: ' + mode + ': ' + str(error), flush=True)
        run(['shell', 'am', 'force-stop', 'io.github.kazands.networklens'])
    results.append(result)
    report = {'version': version, 'apkSha256': digest, 'androidAPI': api, 'target': 'isolated emulator', 'installedHashVerified': True, 'testApkSha256': test_digest, 'results': results, 'plannedModes': args.modes.split(','), 'completed': len(results) == len(args.modes.split(',')), 'status': ('PASS' if all(r['status'] == 'PASS' for r in results) else 'FAIL') if len(results) == len(args.modes.split(',')) else 'RUNNING', 'totalChecks': sum(r.get('checks', 0) for r in results)}
    (output / ('matrix-api' + str(api) + '.json')).write_text(json.dumps(report, indent=2) + '\n')
if not all(r['status'] == 'PASS' for r in results):
    raise SystemExit(1)
print('PASS: complete API ' + str(api) + ' matrix', flush=True)
