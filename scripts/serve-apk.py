#!/usr/bin/env python3
"""Temporary LAN download of one APK; no directory listing or upload endpoint."""
import argparse
import http.server
import ipaddress
from pathlib import Path
import secrets
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bind', required=True)
    parser.add_argument('--subnet', required=True)
    parser.add_argument('--port', type=int, default=8765)
    parser.add_argument('--minutes', type=int, default=60)
    parser.add_argument('--apk', type=Path, required=True)
    args = parser.parse_args()
    address = ipaddress.ip_address(args.bind)
    subnet = ipaddress.ip_network(args.subnet)
    if address not in subnet or not address.is_private or address.is_unspecified:
        parser.error('Bind to this computer\'s private LAN address inside the allowed subnet.')
    if not 1 <= args.minutes <= 60:
        parser.error('Use a lifetime of 1 to 60 minutes.')
    apk = args.apk.resolve()
    if not apk.is_file() or apk.suffix != '.apk':
        parser.error('Choose an existing APK.')
    payload = apk.read_bytes()
    path = '/' + secrets.token_urlsafe(18) + '/' + apk.name
    host = f'{address}:{args.port}'

    class Handler(http.server.BaseHTTPRequestHandler):
        def setup(self):
            super().setup()
            self.connection.settimeout(10)

        def log_message(self, *_):
            pass

        def send_apk(self, body):
            if (ipaddress.ip_address(self.client_address[0]) not in subnet
                    or self.headers.get('Host') != host or self.path != path):
                self.send_error(404)
                return
            self.send_response(200)
            self.send_header('Content-Type', 'application/vnd.android.package-archive')
            self.send_header('Content-Disposition', f'attachment; filename="{apk.name}"')
            self.send_header('Content-Length', str(len(payload)))
            self.send_header('Cache-Control', 'no-store')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.end_headers()
            if body:
                self.wfile.write(payload)
                print(f'APK delivered ({len(payload)} bytes).', flush=True)

        def do_GET(self):
            self.send_apk(True)

        def do_HEAD(self):
            self.send_apk(False)

    class Server(http.server.ThreadingHTTPServer):
        daemon_threads = True

    with Server((str(address), args.port), Handler) as server:
        server.timeout = 1
        deadline = time.monotonic() + args.minutes * 60
        print(f'Download: http://{host}{path}', flush=True)
        print(f'Expires in {args.minutes} minutes. Only this APK is available.', flush=True)
        try:
            while time.monotonic() < deadline:
                server.handle_request()
        except KeyboardInterrupt:
            pass
        print('Download server stopped.', flush=True)


if __name__ == '__main__':
    main()
