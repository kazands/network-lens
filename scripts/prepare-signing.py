#!/usr/bin/env python3
"""Keep the local development signing key private; never emit its password."""
import argparse
import os
from pathlib import Path
import secrets
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--keytool', default='keytool')
args = parser.parse_args()
directory = Path(__file__).resolve().parent.parent / '.tools'
directory.mkdir(mode=0o700, exist_ok=True)
directory.chmod(0o700)
key = directory / 'network-lens-development.jks'
password = directory / 'network-lens-signing-password'
pending = directory / 'network-lens-signing-password.pending'

def keytool(*arguments, secret):
    env = dict(os.environ, NETWORK_LENS_SIGNING_PASSWORD=secret)
    return subprocess.run([args.keytool, *arguments], env=env,
                          stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)

if key.exists():
    key.chmod(0o600)
if password.exists():
    password.chmod(0o600)
    if not key.is_file():
        raise SystemExit('Signing password exists but its key is missing. Restore the matching key.')
else:
    if not pending.exists():
        with os.fdopen(os.open(pending, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'w') as output:
            output.write(secrets.token_urlsafe(48) + '\n')
            output.flush()
            os.fsync(output.fileno())
    pending.chmod(0o600)
    secret = pending.read_text().strip()
    if key.exists():
        # Recover if a previous run changed the key password before finalizing the password file.
        validated = keytool('-list', '-keystore', str(key), '-storepass:env',
                            'NETWORK_LENS_SIGNING_PASSWORD', secret=secret)
        if validated.returncode:
            result = keytool('-storepasswd', '-keystore', str(key), '-storepass', 'android',
                             '-new:env', 'NETWORK_LENS_SIGNING_PASSWORD', secret=secret)
            if result.returncode:
                raise SystemExit('Could not protect the existing development key. No replacement key was generated.')
    else:
        result = keytool('-genkeypair', '-keystore', str(key), '-storepass:env',
                         'NETWORK_LENS_SIGNING_PASSWORD', '-keypass:env', 'NETWORK_LENS_SIGNING_PASSWORD',
                         '-alias', 'network-lens', '-dname', 'CN=Network Lens Development', '-keyalg', 'RSA',
                         '-keysize', '2048', '-validity', '10000', secret=secret)
        if result.returncode:
            raise SystemExit('Could not create the private development signing key.')
    key.chmod(0o600)
    os.replace(pending, password)
