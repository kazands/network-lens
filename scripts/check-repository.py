#!/usr/bin/env python3
"""Check staged repository paths and blobs without printing sensitive contents."""
import re
import subprocess
from pathlib import PurePosixPath

ROOT_FILES = {'.gitignore', 'README.md', 'LICENSE', 'NOTICE', 'CONTRIBUTING.md', 'SECURITY.md', 'AGENTS.md'}
ROOT_DIRS = {'android', 'scripts', 'tests', 'store-listing', 'docs', '.github'}
PRIVATE_PARTS = {'.tools', '.codex', '.codex-attachments', 'evidence', 'artifacts', 'downloads', '__pycache__', '.gradle', '.kotlin', 'build'}
PRIVATE_SUFFIXES = {'.jks', '.keystore', '.pem', '.key', '.p12', '.pfx', '.apk', '.aab', '.idsig', '.log'}
BINARY_ASSETS = {'android/app/src/main/res/raw/ieee_mac_vendors.bin', 'android/branding/network-lens-approved-mockup.png', 'store-listing/en-US/icon.png'}
SECRETS = [re.compile(rb'-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----'),
           re.compile(rb'\bgh[pousr]_[A-Za-z0-9_]{20,}'),
           re.compile(rb'\bgithub_pat_[A-Za-z0-9_]{20,}'),
           re.compile(rb'\bAKIA[A-Z0-9]{16}\b')]

def problems(path, mode, data):
    p = PurePosixPath(path)
    reasons = []
    if not ((len(p.parts) == 1 and path in ROOT_FILES) or (len(p.parts) > 1 and p.parts[0] in ROOT_DIRS)):
        reasons.append('outside reviewed project areas')
    if mode not in {'100644', '100755'}:
        reasons.append('symlink or unsupported Git entry')
    if any(part in PRIVATE_PARTS for part in p.parts) or p.suffix.lower() in PRIVATE_SUFFIXES or p.name == 'local.properties' or (p.name.startswith('.env') and p.name != '.env.example'):
        reasons.append('private or generated file')
    if path == 'scripts/package-source.py':
        reasons.append('local historical evidence packager')
    if any(pattern.search(data) for pattern in SECRETS):
        reasons.append('possible credential or private key')
    if len(data) > 5 * 1024 * 1024:
        reasons.append('unexpectedly large source file')
    if path not in BINARY_ASSETS:
        try:
            data.decode('utf-8')
        except UnicodeDecodeError:
            reasons.append('unapproved binary asset')
        if b'\x00' in data:
            reasons.append('unexpected binary content')
    return reasons

def main():
    raw = subprocess.check_output(['git', 'ls-files', '--stage', '-z'])
    errors = []
    count = 0
    for entry in raw.split(b'\0'):
        if not entry:
            continue
        metadata, name = entry.split(b'\t', 1)
        mode, oid, stage = metadata.decode('ascii').split()
        path = name.decode('utf-8')
        if stage != '0':
            errors.append((path, ['unmerged entry']))
            continue
        data = subprocess.check_output(['git', 'cat-file', 'blob', oid])
        reasons = problems(path, mode, data)
        if reasons:
            errors.append((path, reasons))
        count += 1
    if errors:
        for path, reasons in errors:
            print('FAIL:', path, '; '.join(reasons))
        raise SystemExit(1)
    print('PASS:', count, 'staged files; no prohibited paths or common credentials detected')

if __name__ == '__main__':
    main()
