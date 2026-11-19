#!/usr/bin/env python3
"""Opt-in Linux public-page trial. Explicit current peers only; aggregate output, no identity guesses."""
import argparse
from collections import Counter
from html.parser import HTMLParser
import http.client
import io
import ipaddress
import json
from pathlib import Path
import re
import socket
import ssl
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parent.parent
PRIVATE = tuple(ipaddress.ip_network(n) for n in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"))
PORTS = {80: False, 8080: False, 443: True, 8443: True}
MAX_BODY = 65536


def label(value):
    # Terminal evidence is untrusted. Do not emit controls, bidi overrides or unbounded text.
    return " ".join("".join(c for c in value if c.isprintable()).split())[:160]


class PageTitle(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.count = 0
        self.inside = False
        self.parts = []
        self.length = 0
        self.closed = False

    def handle_starttag(self, tag, attrs):
        if tag == "title":
            self.count += 1
            self.inside = True

    def handle_endtag(self, tag):
        if tag == "title" and self.inside:
            self.closed = True
            self.inside = False

    def handle_data(self, data):
        if self.inside:
            self.length += len(data)
            if self.length <= 512:
                self.parts.append(data)

    def title(self):
        return label("".join(self.parts)) if self.count == 1 and self.closed and self.length <= 512 else ""


def inspect_response(response):
    """Only public service labels, never manufacturer/model/category or follow-up URLs."""
    if response.status in (401, 403):
        return {"outcome": "access_required"}
    if 300 <= response.status < 400:
        return {"outcome": "redirect_not_followed"}
    if response.status != 200:
        return {"outcome": "other_http_status"}
    headers = {}
    for key, value in response.getheaders():
        key = key.lower()
        if key in headers:
            raise ValueError("Repeated header")
        headers[key] = value
    if headers.get("content-encoding", "identity").lower() != "identity":
        return {"outcome": "unsupported_encoding"}
    length = headers.get("content-length")
    transfer = headers.get("transfer-encoding")
    if transfer and (length is not None or transfer.lower() != "chunked"):
        raise ValueError("Unsupported or ambiguous framing")
    if length is not None and (not re.fullmatch(r"[0-9]{1,6}", length) or int(length) > MAX_BODY):
        raise ValueError("Body size limit")
    body = response.read(MAX_BODY + 1)
    if len(body) > MAX_BODY or length is not None and len(body) != int(length):
        raise ValueError("Body size or truncation")
    title = ""
    content_type = headers.get("content-type", "").lower()
    if content_type.split(";")[0].strip() == "text/html":
        # Restrict the trial to explicit UTF-8/ASCII; do not invent text from unknown encodings.
        charset = re.search(r"charset\s*=\s*\"?([^;\s\"]+)", content_type)
        if charset is None or charset[1] in ("utf-8", "us-ascii"):
            parser = PageTitle()
            parser.feed(body.decode("utf-8", errors="strict"))
            parser.close()
            title = parser.title()
    server = label(headers.get("server", ""))
    return {"outcome": "public_labels" if title or server else "no_public_labels", "title": title, "server": server}


class BoundedReader(io.RawIOBase):
    def __init__(self, transport, deadline):
        self.transport = transport
        self.deadline = deadline
        self.remaining = 98304  # Includes headers, chunk framing and body.

    def readable(self):
        return True

    def readinto(self, buffer):
        seconds = self.deadline - time.monotonic()
        if seconds <= 0:
            raise TimeoutError("Deadline")
        if self.remaining <= 0:
            raise ValueError("Wire size limit")
        self.transport.settimeout(seconds)
        count = self.transport.recv_into(buffer, min(len(buffer), self.remaining))
        self.remaining -= count
        return count


class ResponseSocket:
    def __init__(self, transport, deadline):
        self.transport, self.deadline = transport, deadline

    def makefile(self, mode):
        return io.BufferedReader(BoundedReader(self.transport, self.deadline), 1024)


def validate(data):
    if not isinstance(data, dict) or set(data) != {"interface", "targets"}:
        raise ValueError("Provide interface and explicit targets only")
    if not isinstance(data["interface"], str) or not re.fullmatch(r"[A-Za-z0-9_.-]{1,15}", data["interface"]):
        raise ValueError("Invalid interface")
    if not isinstance(data["targets"], list) or not 1 <= len(data["targets"]) <= 16:
        raise ValueError("Select 1 to 16 current endpoints")
    seen = set()
    for target in data["targets"]:
        if not isinstance(target, dict) or set(target) != {"address", "port", "currentResponseConfirmed"}:
            raise ValueError("Use address, port and currentResponseConfirmed only")
        if target["currentResponseConfirmed"] is not True:
            raise ValueError("Select endpoints with a response in the current baseline")
        if not isinstance(target["address"], str):
            raise ValueError("Use a literal private IPv4 address")
        address = ipaddress.IPv4Address(target["address"])
        if not any(address in network for network in PRIVATE):
            raise ValueError("Only private LAN IPv4 endpoints are supported")
        if type(target["port"]) is not int or target["port"] not in PORTS:
            raise ValueError("Only explicitly selected HTTP 80/8080 or HTTPS 443/8443")
        pair = (str(address), target["port"])
        if pair in seen:
            raise ValueError("Repeated endpoint")
        seen.add(pair)
    return data


def attachment(interface):
    raw = subprocess.run(["ip", "-j", "-4", "address", "show", "dev", interface],
                         check=True, capture_output=True, timeout=3).stdout
    rows = json.loads(raw)
    if len(rows) != 1 or "UP" not in rows[0].get("flags", []):
        raise ValueError("Selected interface is unavailable")
    return (rows[0]["ifindex"], tuple(sorted((a["local"], a["prefixlen"])
            for a in rows[0].get("addr_info", []) if a["family"] == "inet")))


def source_for(snapshot, target):
    peer = ipaddress.IPv4Address(target)
    for local, prefix in snapshot[1]:
        network = ipaddress.IPv4Network(f"{local}/{prefix}", strict=False)
        if peer in network and peer not in (network.network_address, network.broadcast_address) and str(peer) != local:
            return local
    raise ValueError("Endpoint must be directly connected to the selected interface")


def same_attachment(interface, snapshot):
    try:
        return attachment(interface) == snapshot
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError):
        return False


def read_page(interface, source, target, deadline):
    # No DNS, proxy environment, authentication, cookies, redirects or secondary requests.
    raw = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    transport = raw
    try:
        raw.setsockopt(socket.SOL_SOCKET, socket.SO_BINDTODEVICE, interface.encode("ascii") + b"\0")
        raw.bind((source, 0))
        raw.settimeout(max(0.001, deadline - time.monotonic()))
        raw.connect((target["address"], target["port"]))
        if PORTS[target["port"]]:
            raw.settimeout(max(0.001, deadline - time.monotonic()))
            transport = ssl.create_default_context().wrap_socket(raw, server_hostname=target["address"])
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError("Deadline")
        transport.settimeout(remaining)
        authority = f'{target["address"]}:{target["port"]}'
        transport.sendall((f"GET / HTTP/1.1\r\nHost: {authority}\r\nAccept: text/html\r\n"
                           "Accept-Encoding: identity\r\nConnection: close\r\n\r\n").encode("ascii"))
        response = http.client.HTTPResponse(ResponseSocket(transport, deadline))
        try:
            response.begin()
            return inspect_response(response)
        finally:
            response.close()
    finally:
        transport.close()
        if transport is not raw:
            raw.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--review", action="store_true", help="Display untrusted page labels in the terminal only; do not redirect or save")
    args = parser.parse_args()
    output = args.output.resolve()
    if not any(output.is_relative_to(ROOT / folder) for folder in ("artifacts", ".tools", "evidence")):
        parser.error("Write aggregate reports only to ignored evidence directories")
    raw = sys.stdin.buffer.read(65537)
    if len(raw) > 65536:
        parser.error("Input size limit")
    try:
        data = validate(json.loads(raw))
        snapshot = attachment(data["interface"])
        sources = [source_for(snapshot, t["address"]) for t in data["targets"]]
    except (OSError, ValueError, TypeError, KeyError, subprocess.SubprocessError):
        parser.error("Invalid selection or unavailable interface; no requests sent")
    counts = Counter()
    started = time.monotonic()
    attempted = 0
    for index, (target, source) in enumerate(zip(data["targets"], sources), 1):
        if time.monotonic() - started >= 32 or not same_attachment(data["interface"], snapshot):
            counts["trial_stopped"] += 1
            break
        attempted += 1
        try:
            result = read_page(data["interface"], source, target, min(started + 32, time.monotonic() + 2))
        except ssl.SSLError:
            result = {"outcome": "tls_validation_or_handshake_failed"}
        except (OSError, ValueError, http.client.HTTPException):
            result = {"outcome": "unavailable_or_invalid_response"}
        if not same_attachment(data["interface"], snapshot):
            counts["trial_stopped"] += 1
            break  # Discard a response whose attachment changed during the request.
        counts[result["outcome"]] += 1
        counts["page_title_available"] += bool(result.get("title"))
        counts["server_header_available"] += bool(result.get("server"))
        if args.review:
            print(json.dumps({"endpoint": index, **result}, ensure_ascii=True))
    report = {"environment": "host-interface trial, not Android", "selectedEndpoints": len(sources),
              "attemptedEndpoints": attempted, "outcomes": dict(counts),
              "limits": "Public labels are unverified service evidence, not hardware identity or accuracy gains. No device identities or addresses are saved. TLS failures are not bypassed."}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n")
    print("Public-page trial finished; aggregate report written. No device identities assigned.")


if __name__ == "__main__":
    main()
