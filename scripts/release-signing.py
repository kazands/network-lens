#!/usr/bin/env python3
"""Explicit release-key creation, recovery checks and pinned APK signing.

No build calls init. Private material and recovery receipts stay under .tools.
Only certificate fingerprints and fixed role/alias names belong in source control.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import stat
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parent.parent
PRIVATE = ROOT / '.tools/release-signing'
IDENTITY = ROOT / 'android/release-signing.json'
RECEIPT = PRIVATE / 'backup-verification.json'
ROLES = {
    'app': ('network-lens-app-signing.p12', 'network-lens-app-signing-password', 'network-lens-app'),
    'upload': ('network-lens-upload.p12', 'network-lens-upload-password', 'network-lens-upload'),
}


class SigningError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise SigningError(message)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def command(arguments, input_bytes=None):
    try:
        result = subprocess.run([str(a) for a in arguments], input=input_bytes, capture_output=True, timeout=90)
    except (OSError, subprocess.TimeoutExpired):
        raise SigningError('Signing tool unavailable or timed out; no credentials printed.') from None
    # Tool output can contain credential-related diagnostics. Never echo it on failure.
    require(result.returncode == 0, 'Signing tool rejected the operation; check the key, alias and password file.')
    return result.stdout


def keytool():
    home = os.environ.get('JAVA_HOME')
    path = Path(home) / 'bin/keytool' if home else shutil.which('keytool')
    require(path is not None, 'Set JAVA_HOME to a full JDK.')
    return str(path)


def android_tool(name):
    sdk = Path(os.environ.get('ANDROID_SDK_ROOT', str(ROOT / '.tools/sdk')))
    override = os.environ.get('NETWORK_LENS_BUILD_TOOLS')
    candidates = [Path(override)] if override else [sdk / 'android-16', sdk / 'build-tools/36.0.0']
    path = next((directory / name for directory in candidates if (directory / name).is_file()), None)
    require(path is not None, 'Android build tools are missing; see docs/BUILDING.md.')
    return path


def private_path(path, directory=False):
    require(path.exists() and not path.is_symlink(), 'Private signing file or directory is missing or is a symlink.')
    info = path.stat()
    require(info.st_uid == os.getuid(), 'Private signing material must be owned by the current user.')
    require(stat.S_IMODE(info.st_mode) == (0o700 if directory else 0o600),
            'Private directories must have mode 0700 and files mode 0600.')
    require(path.is_dir() if directory else path.is_file(), 'Unexpected signing file type.')


def json_read(path):
    def unique(pairs):
        value = {}
        for name, data in pairs:
            require(name not in value, 'Duplicate signing metadata field.')
            value[name] = data
        return value
    try:
        return json.loads(path.read_text(), object_pairs_hook=unique)
    except (OSError, ValueError, UnicodeError):
        raise SigningError('Signing metadata is missing or invalid; restore the existing identity.') from None


def json_write(path, value, mode=0o600, exclusive=False):
    data = (json.dumps(value, indent=2) + '\n').encode()
    if exclusive:
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, mode)
        with os.fdopen(fd, 'wb') as output:
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
        return
    fd, temporary = tempfile.mkstemp(prefix='.signing-', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as output:
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
        os.chmod(temporary, mode)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def certificate(store, password, alias):
    private_path(store)
    private_path(password)
    args = [keytool(), '-J-Duser.language=en', '-J-Duser.country=US',
            '-keystore', store, '-storepass:file', password, '-alias', alias]
    listing = command([*args, '-list', '-v']).decode('utf-8', errors='replace')
    require('PrivateKeyEntry' in listing, 'The expected alias must contain a private signing key.')
    der = command([*args, '-exportcert'])
    require(bool(der), 'The signing certificate could not be exported.')
    # Listing/exporting a certificate does not prove the private entry is usable.
    # Generate a signed PKCS#10 request, then have the JDK validate its signature.
    request = command([*args, '-certreq', '-keypass:file', password, '-sigalg', 'SHA256withRSA'])
    command([keytool(), '-printcertreq'], input_bytes=request)
    return hashlib.sha256(der).hexdigest()


def identity():
    require(IDENTITY.is_file() and not IDENTITY.is_symlink(), 'Pinned release identity is missing or is a symlink; restore it.')
    value = json_read(IDENTITY)
    require(isinstance(value, dict) and set(value) == {'schemaVersion', 'applicationId', 'testCertificateSha256', 'roles'},
            'Unexpected release identity fields.')
    require(type(value['schemaVersion']) is int and value['schemaVersion'] == 1 and value['applicationId'] == 'io.github.kazands.networklens', 'Unexpected release identity or application ID.')
    require(isinstance(value['roles'], dict) and set(value['roles']) == set(ROLES), 'Both distinct signing roles are required.')
    fingerprints = []
    for role, (store, password, alias) in ROLES.items():
        record = value['roles'][role]
        require(isinstance(record, dict) and set(record) == {'alias', 'certificateSha256', 'keystoreFile', 'passwordFile'},
                'Unexpected signing-role fields.')
        require((record['keystoreFile'], record['passwordFile'], record['alias']) == (store, password, alias),
                'Signing role filenames and aliases must match the approved layout.')
        fingerprint = record['certificateSha256']
        require(isinstance(fingerprint, str) and re.fullmatch('[0-9a-f]{64}', fingerprint), 'Invalid signing certificate fingerprint.')
        fingerprints.append(fingerprint)
    test = value['testCertificateSha256']
    require(test is None or isinstance(test, str) and re.fullmatch('[0-9a-f]{64}', test), 'Invalid test certificate fingerprint.')
    require(len(set(fingerprints)) == 2 and test not in fingerprints, 'App, upload and existing test signing identities must be different.')
    return value


def inspect(roles):
    value = identity()
    private_path(ROOT / '.tools', directory=True)
    private_path(PRIVATE, directory=True)
    for role in roles:
        store, password, alias = ROLES[role]
        actual = certificate(PRIVATE / store, PRIVATE / password, alias)
        require(actual == value['roles'][role]['certificateSha256'], 'Signing certificate does not match the pinned role.')
    return value


def check(roles):
    value = inspect(roles)
    private_path(RECEIPT)
    receipt = json_read(RECEIPT)
    require(isinstance(receipt, dict) and receipt.get('schemaVersion') == 1 and receipt.get('status') == 'VERIFIED',
            'A verified backup receipt is required before release signing.')
    require(isinstance(receipt.get('roles'), dict), 'Invalid backup receipt.')
    for role in roles:
        record = receipt['roles'].get(role, {})
        require(record.get('certificateSha256') == value['roles'][role]['certificateSha256'] and
                record.get('keystoreSha256') == digest(PRIVATE / ROLES[role][0]) and
                record.get('downloadedFilesMatch') is True and record.get('restoredKeyOpens') is True,
                'The backup receipt is missing, stale or belongs to another signing identity.')
    return value


def initialize():
    # Refuse partial recovery and fresh-clone reinitialization. Never replace an identity.
    require(not IDENTITY.exists() and not IDENTITY.is_symlink() and not PRIVATE.exists() and not PRIVATE.is_symlink(),
            'Release signing already exists or is partially initialized. Restore it; do not generate replacement keys.')
    tools = ROOT / '.tools'
    require(not tools.is_symlink(), 'The private tools directory cannot be a symlink.')
    tools.mkdir(mode=0o700, exist_ok=True)
    tools.chmod(0o700)
    test_store = tools / 'network-lens-development.jks'
    test_password = tools / 'network-lens-signing-password'
    require(test_store.exists() == test_password.exists(), 'Existing test signing material is incomplete; restore it first.')
    test = certificate(test_store, test_password, 'network-lens') if test_store.exists() else None
    PRIVATE.mkdir(mode=0o700)
    value = {'schemaVersion': 1, 'applicationId': 'io.github.kazands.networklens', 'testCertificateSha256': test, 'roles': {}}
    for role, (store, password, alias) in ROLES.items():
        fd = os.open(PRIVATE / password, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'w') as output:
            output.write(secrets.token_urlsafe(48) + '\n')
            output.flush()
            os.fsync(output.fileno())
        command([keytool(), '-genkeypair', '-noprompt', '-storetype', 'PKCS12',
                 '-keystore', PRIVATE / store, '-storepass:file', PRIVATE / password,
                 '-keypass:file', PRIVATE / password, '-alias', alias,
                 '-dname', 'CN=Network Lens ' + ('App Signing' if role == 'app' else 'Play Upload'),
                 '-keyalg', 'RSA', '-keysize', '4096', '-sigalg', 'SHA256withRSA', '-validity', '36500'])
        (PRIVATE / store).chmod(0o600)
        actual = certificate(PRIVATE / store, PRIVATE / password, alias)
        require(actual != test and all(actual != r['certificateSha256'] for r in value['roles'].values()), 'Signing identities must be distinct.')
        value['roles'][role] = {'alias': alias, 'certificateSha256': actual, 'keystoreFile': store, 'passwordFile': password}
        pem = command([keytool(), '-exportcert', '-rfc', '-keystore', PRIVATE / store,
                       '-storepass:file', PRIVATE / password, '-alias', alias])
        fd = os.open(PRIVATE / (role + '-certificate.pem'), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'wb') as output:
            output.write(pem)
    IDENTITY.parent.mkdir(exist_ok=True)
    json_write(IDENTITY, value, mode=0o644, exclusive=True)
    return value


def verify_backup(directory):
    value = inspect(ROLES)
    private_path(directory, directory=True)
    require(directory.resolve() != PRIVATE.resolve(), 'Use separately downloaded recovery copies, not the originals.')
    result = {'schemaVersion': 1, 'status': 'VERIFIED', 'roles': {}}
    for role, (store, password, alias) in ROLES.items():
        for name in (store, password):
            restored = directory / name
            private_path(restored)
            require(not restored.samefile(PRIVATE / name), 'Recovery copies must be independent of the originals.')
            require(restored.read_bytes() == (PRIVATE / name).read_bytes(), 'A recovery copy does not match its original.')
        actual = certificate(directory / store, directory / password, alias)
        require(actual == value['roles'][role]['certificateSha256'], 'Restored signing certificate differs from the pinned role.')
        result['roles'][role] = {'certificateSha256': actual, 'keystoreSha256': digest(PRIVATE / store),
                                 'downloadedFilesMatch': True, 'restoredKeyOpens': True}
    json_write(RECEIPT, result)
    return result


def apk_certificate(path):
    text = command([android_tool('apksigner'), 'verify', '--verbose', '--print-certs', path]).decode('utf-8', errors='replace')
    fingerprints = re.findall(r'^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-f]{64})$', text, re.M)
    require(len(fingerprints) == 1, 'Expected exactly one verified APK signing certificate.')
    return fingerprints[0]


def sign_apk(source, destination):
    value = check(['app'])
    require(source.is_file() and not source.is_symlink(), 'Unsigned APK input is missing or is a symlink.')
    allowed = ROOT / 'artifacts/release'
    require(not (ROOT / 'artifacts').is_symlink() and not allowed.is_symlink(), 'Release output directories cannot be symlinks.')
    require(destination.parent.resolve() == allowed.resolve() and destination.suffix == '.apk' and not destination.is_symlink(),
            'Release APK output must be directly under artifacts/release and must not be a symlink.')
    require(source.resolve() != destination.resolve(), 'APK input and output must be different files.')
    badging = command([android_tool('aapt'), 'dump', 'badging', source]).decode('utf-8', errors='replace')
    require(re.search(r"^package: name='io\.github\.kazands\.networklens' ", badging, re.M) is not None, 'Only the Network Lens application can use this release signer.')
    allowed.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix='.release-', suffix='.apk', dir=allowed)
    os.close(fd)
    try:
        store, password, alias = ROLES['app']
        command([android_tool('apksigner'), 'sign', '--ks', PRIVATE / store, '--ks-key-alias', alias,
                 '--ks-pass', 'file:' + str(PRIVATE / password), '--min-sdk-version', '26',
                 '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true',
                 '--v4-signing-enabled', 'false', '--out', temporary, source])
        require(apk_certificate(Path(temporary)) == value['roles']['app']['certificateSha256'], 'The signed APK has the wrong certificate.')
        os.replace(temporary, destination)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    return {'status': 'SIGNED', 'apk': destination.name, 'sha256': digest(destination),
            'certificateSha256': value['roles']['app']['certificateSha256']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('init', help='One-time explicit creation; never replaces an existing identity.')
    for name in ('inspect', 'check'):
        p = commands.add_parser(name)
        p.add_argument('--role', choices=['app', 'upload', 'all'], default='all')
    p = commands.add_parser('verify-backup')
    p.add_argument('--restored-directory', type=Path, required=True)
    p = commands.add_parser('sign-apk')
    p.add_argument('--input', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.command == 'init':
            result = initialize()
        elif args.command in ('inspect', 'check'):
            roles = list(ROLES) if args.role == 'all' else [args.role]
            value = inspect(roles) if args.command == 'inspect' else check(roles)
            result = {'status': 'INSPECTED' if args.command == 'inspect' else 'READY',
                      'roles': {r: value['roles'][r] for r in roles}}
        elif args.command == 'verify-backup':
            result = verify_backup(args.restored_directory)
        else:
            result = sign_apk(args.input, args.output)
        print(json.dumps(result, indent=2))
    except SigningError as error:
        print('FAIL: ' + str(error), file=sys.stderr)
        return 1
    except (OSError, ValueError, TypeError, KeyError):
        # Fixed diagnostics avoid accidentally serializing passwords, tool stderr or file contents.
        print('FAIL: Release signing validation failed. Check pinned identity, private file permissions, aliases and verified backups. Existing keys were not replaced.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
