#!/usr/bin/env python3
"""Fail closed if the final APK's requested permissions exceed the default policy."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
POLICY = {
    'android.permission.INTERNET': 'Discover responding devices on the user-selected Wi-Fi network.',
    'android.permission.ACCESS_WIFI_STATE': 'Read the connected Wi-Fi name on supported older Android versions.',
    'android.permission.ACCESS_COARSE_LOCATION': 'Optional foreground Wi-Fi name access; requested together with precise access as required by Android.',
    'android.permission.ACCESS_FINE_LOCATION': 'User-approved optional foreground SSID access; no coordinates or background location requested.',
    'android.permission.ACCESS_NETWORK_STATE': 'Select the connected Wi-Fi and read its IP routes.',
    'android.permission.CHANGE_WIFI_MULTICAST_STATE': 'Receive advertised mDNS services on supported older Android versions.',
    'android.permission.ACCESS_LOCAL_NETWORK': 'User-approved local-network discovery on Android 17+.' ,
}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', nargs='?', default=str(ROOT / 'artifacts/Network-Lens-0.3.10.apk'))
    parser.add_argument('--aapt')
    parser.add_argument('--output')
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_SDK_ROOT', str(ROOT / '.tools/sdk')))
    candidates = [sdk / 'android-16/aapt', sdk / 'build-tools/36.0.0/aapt']
    aapt = Path(args.aapt) if args.aapt else next((p for p in candidates if p.is_file()), None)
    if aapt is None:
        parser.error('Android build-tools aapt is required')
    apk = Path(args.apk).resolve()
    output = subprocess.check_output([str(aapt), 'dump', 'permissions', str(apk)], text=True)
    if not re.search(r'^package:\s+io\.github\.kazands\.networklens\s*$', output, re.M):
        raise SystemExit('FAIL: expected the io.github.kazands.networklens application package')
    permissions = set(re.findall(r"^uses-permission(?:-sdk-\d+)?: name='([^']+)'", output, re.M))
    if not permissions:
        raise SystemExit('FAIL: permission inventory is empty or could not be parsed')
    unexpected = permissions - POLICY.keys()
    if unexpected:
        raise SystemExit('FAIL: unapproved APK permissions: ' + ', '.join(sorted(unexpected)))
    missing = POLICY.keys() - permissions
    if missing:
        raise SystemExit('FAIL: missing required APK permissions: ' + ', '.join(sorted(missing)))
    report = {
        'status': 'PASS', 'apk': apk.name,
        'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
        'scope': 'Requested permissions in the final packaged APK; not a claim of zero network access.',
        'permissions': {p: POLICY[p] for p in sorted(permissions)},
        'runtime_permission': 'ACCESS_LOCAL_NETWORK on Android 17+ for scans; optional foreground FINE/COARSE_LOCATION only after choosing Show Wi-Fi name.',
        'target_sdk': 37,
        'platform_limit': 'Targets SDK 37. Local-network permission is checked before scanning on Android 17+; older Android versions have no standard runtime prompt for this grant.',
    }
    serialized = json.dumps(report, indent=2) + '\n'
    if args.output:
        Path(args.output).write_text(serialized)
    print(serialized, end='')

if __name__ == '__main__':
    main()
