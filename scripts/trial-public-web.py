#!/usr/bin/env python3
"""Opt-in Linux public-page trial. Explicit current peers only; aggregate output, no identity guesses."""
import argparse
from collections import Counter
import codecs
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
MAX_BODY = 8192
TLS_PORTS = {443, 8443, 8009}
SSH_PORTS = {22}
REALM = re.compile(r'(?i)(?:^|[, \t])realm[ \t]*=[ \t]*"((?:[^"\\\r\n]|\\[ -~]){0,256})"')


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
    headers = {}
    challenges = []
    for key, value in response.getheaders():
        key = key.lower()
        if key == "www-authenticate":
            if len(challenges) >= 4 or len(value) > 4096:
                raise ValueError("Authentication challenge limit")
            challenges.append(value)
        elif key in {"server", "content-type", "content-encoding", "content-length", "transfer-encoding"}:
            if key in headers:
                raise ValueError("Repeated selected or framing header")
            headers[key] = value
    fields = {"server": label(headers.get("server", ""))}
    realms = set(m[1] for value in challenges for m in REALM.finditer(value))
    if len(realms) == 1:
        fields["realm"] = label(next(iter(realms)))
    fields = {k: v for k, v in fields.items() if v}
    if response.status in (401, 403):
        return {"outcome": "access_required", **fields}
    if 300 <= response.status < 400:
        return {"outcome": "redirect_not_followed", **fields}
    if response.status != 200:
        return {"outcome": "other_http_status", **fields}
    if headers.get("content-encoding", "identity").lower() != "identity":
        return {"outcome": "unsupported_encoding"}
    length = headers.get("content-length")
    transfer = headers.get("transfer-encoding")
    if transfer and (length is not None or transfer.lower() != "chunked"):
        raise ValueError("Unsupported or ambiguous framing")
    if length is not None and not re.fullmatch(r"[0-9]{1,10}", length):
        raise ValueError("Body size limit")
    body = response.read(MAX_BODY)
    limited = len(body) == MAX_BODY
    if length is not None and int(length) <= MAX_BODY and len(body) != int(length):
        raise ValueError("Body truncation")
    title = ""
    content_type = headers.get("content-type", "").lower()
    if content_type.split(";")[0].strip() == "text/html":
        # Restrict the trial to explicit UTF-8/ASCII; do not invent text from unknown encodings.
        charset = re.search(r"charset\s*=\s*\"?([^;\s\"]+)", content_type)
        if charset is None or charset[1] in ("utf-8", "us-ascii"):
            parser = PageTitle()
            parser.feed(codecs.getincrementaldecoder("utf-8")("strict").decode(body, final=not limited))
            parser.close()
            title = parser.title()
    server = label(headers.get("server", ""))
    return {"outcome": "public_labels" if title or server or fields.get("realm") else "no_public_labels", "title": title, "server": server,
            **({"realm": fields["realm"]} if "realm" in fields else {}), **({"prefixLimited": True} if limited else {})}


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
        if not isinstance(target, dict) or set(target) not in ({"address", "port", "currentResponseConfirmed"}, {"address", "port", "currentResponseConfirmed", "protocol"}):
            raise ValueError("Use address, port, currentResponseConfirmed and optional protocol only")
        if target["currentResponseConfirmed"] is not True:
            raise ValueError("Select endpoints with a response in the current baseline")
        if not isinstance(target["address"], str):
            raise ValueError("Use a literal private IPv4 address")
        address = ipaddress.IPv4Address(target["address"])
        if not any(address in network for network in PRIVATE):
            raise ValueError("Only private LAN IPv4 endpoints are supported")
        protocol = target.get("protocol", "http")
        allowed = {"http": PORTS, "tls": TLS_PORTS, "ssh": SSH_PORTS}
        if not isinstance(protocol, str) or protocol not in allowed or type(target["port"]) is not int or target["port"] not in allowed[protocol]:
            raise ValueError("Select a supported public HTTP, TLS-certificate or SSH endpoint")
        pair = (str(address), target["port"], protocol)
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


def remaining(deadline):
    value = deadline - time.monotonic()
    if value <= 0:
        raise TimeoutError("Deadline")
    return value


def read_ssh(transport, deadline):
    # Receive greeting only. No client banner, authentication or SSH commands.
    for _ in range(8):
        line = bytearray()
        while True:
            transport.settimeout(remaining(deadline))
            value = transport.recv(1)
            if not value:
                raise ValueError("SSH greeting ended")
            if value == b"\r":
                transport.settimeout(remaining(deadline))
                if transport.recv(1) != b"\n":
                    raise ValueError("Invalid SSH line")
                break
            if len(line) >= 253 or value[0] not in range(32, 127):
                raise ValueError("SSH greeting limit")
            line.extend(value)
        if line.startswith(b"SSH-"):
            match = re.fullmatch(rb"SSH-(?:2\.0|1\.99)-([!-~]{1,160})(?: [ -~]{0,80})?", line)
            if not match:
                raise ValueError("Invalid SSH software string")
            return {"outcome": "ssh_software_label", "sshSoftware": label(match[1].decode("ascii"))}
    raise ValueError("SSH greeting line limit")


def selected_subject_attributes(subject):
    """Single forward scan of canonical RFC2253 output; no regular expressions."""
    try:
        if len(subject) > 2048 or len(subject.encode("utf-16-le")) > 4096:
            return {}, "Subject name exceeded capture limit"
    except UnicodeEncodeError:
        return {}, "Subject name could not be decoded"
    special = ' ,+"\\<>;=#'
    def escape_end(text, at):
        if at+1 >= len(text):
            return -1
        if at+2 < len(text) and all(c in "0123456789abcdefABCDEF" for c in text[at+1:at+3]):
            return at+3
        return at+2 if text[at+1] in special else -1
    def decode(raw):
        if not raw or raw.startswith("#"):
            return None
        data, at = bytearray(), 0
        while at < len(raw):
            if raw[at] == "\\":
                end = escape_end(raw, at)
                if end < 0:
                    return None
                data.extend(bytes([int(raw[at+1:at+3], 16)]) if end == at+3 else raw[at+1].encode("utf-8"))
                at = end
            else:
                start = at
                while at < len(raw) and raw[at] != "\\":
                    at += 1
                data.extend(raw[start:at].encode("utf-8"))
            if len(data) > 640:
                return None
        try:
            value = data.decode("utf-8", errors="strict")
        except UnicodeDecodeError:
            return None
        if not 1 <= len(value.encode("utf-16-le"))//2 <= 160:
            return None
        return label(value) or None
    values, seen, omitted, at = {}, set(), False, 0
    while at < len(subject):
        start = at
        while at < len(subject) and subject[at] != "=":
            if subject[at] in ',+\\"':
                return {}, "Subject name could not be decoded"
            at += 1
        if at == len(subject) or at == start:
            return {}, "Subject name could not be decoded"
        key = subject[start:at].strip().upper()
        at += 1
        start = at
        while at < len(subject) and subject[at] not in ",+":
            if subject[at] == "\\":
                at = escape_end(subject, at)
                if at < 0:
                    return {}, "Subject name could not be decoded"
            else:
                if subject[at] in '";':
                    return {}, "Subject name could not be decoded"
                at += 1
        if key in {"CN", "O"}:
            if key in seen:
                values.pop(key, None)
                omitted = True
            else:
                seen.add(key)
                value = decode(subject[start:at])
                if value is None:
                    omitted = True
                else:
                    values[key] = value
        if at < len(subject):
            at += 1
            if at == len(subject):
                return {}, "Subject name could not be decoded"
    return values, "Overlong, unsupported or duplicate subject attributes omitted" if omitted else ""


def certificate_alternative_names(der):
    """Bounded RFC 5280 SAN selector, after OpenSSL accepts the certificate.

    Read actual GeneralName tags; OpenSSL's display text is ambiguous when a
    URI/email value contains commas or strings such as 'DNS:'. No trust decision
    or generic recursive ASN.1 decoding is performed here.
    """
    if not 1 <= len(der) <= 32768:
        raise ValueError("Certificate size limit")

    def children(data, maximum):
        result, at = [], 0
        while at < len(data):
            if len(result) >= maximum or at+2 > len(data):
                raise ValueError("Certificate structure limit")
            tag, size = data[at], data[at+1]
            at += 2
            if tag & 31 == 31:
                raise ValueError("Unsupported certificate tag")
            if size & 128:
                count = size & 127
                if count not in (1, 2) or at+count > len(data) or data[at] == 0:
                    raise ValueError("Invalid certificate length")
                size = int.from_bytes(data[at:at+count], "big")
                at += count
                if size < 128:
                    raise ValueError("Noncanonical certificate length")
            if at+size > len(data):
                raise ValueError("Truncated certificate field")
            result.append((tag, data[at:at+size]))
            at += size
        return result

    def sequence(data, maximum):
        outer = children(data, 1)
        if len(outer) != 1 or outer[0][0] != 0x30:
            raise ValueError("Certificate sequence required")
        return children(outer[0][1], maximum)

    certificate = sequence(memoryview(der), 3)
    if [tag for tag, _ in certificate] != [0x30, 0x30, 0x03]:
        raise ValueError("Invalid certificate structure")
    body = children(certificate[0][1], 10)
    start = int(bool(body) and body[0][0] == 0xa0)
    if [tag for tag, _ in body[start:start+6]] != [0x02, 0x30, 0x30, 0x30, 0x30, 0x30]:
        raise ValueError("Invalid certificate body")
    optional = body[start+6:]
    tags = [tag for tag, _ in optional]
    if tags != sorted(set(tags)) or any(tag not in (0x81, 0x82, 0xa3) for tag in tags):
        raise ValueError("Invalid certificate extensions")
    extensions = next((value for tag, value in optional if tag == 0xa3), None)
    if extensions is None:
        return [], ""
    san = None
    for tag, value in sequence(extensions, 64):
        if tag != 0x30:
            raise ValueError("Invalid certificate extension")
        parts = children(value, 3)
        if len(parts) == 3 and parts[1][0] == 0x01 and bytes(parts[1][1]) in (b"\x00", b"\xff"):
            parts = [parts[0], parts[2]]
        if [kind for kind, _ in parts] != [0x06, 0x04]:
            raise ValueError("Invalid certificate extension fields")
        if bytes(parts[0][1]) == b"\x55\x1d\x11":  # id-ce-subjectAltName, 2.5.29.17
            if san is not None:
                raise ValueError("Duplicate alternative-name extension")
            san = parts[1][1]
    if san is None:
        return [], ""
    names, omitted = [], False
    for tag, value in sequence(san, 128):
        if tag not in (0xa0, 0x81, 0x82, 0xa3, 0xa4, 0xa5, 0x86, 0x87, 0x88):
            raise ValueError("Invalid alternative-name type")
        if tag not in (0x82, 0x87):  # dNSName [2] or iPAddress [7], never a URI/email.
            continue
        if len(names) >= 8:
            omitted = True
            continue
        if tag == 0x82:
            if not 1 <= len(value) <= 160 or any(c < 33 or c > 126 for c in value):
                omitted = True
                continue
            names.append(bytes(value).decode("ascii"))
        elif len(value) in (4, 16):
            names.append(str(ipaddress.ip_address(bytes(value))))
        else:
            omitted = True
    return names, "Invalid or overlong alternative names omitted; at most eight retained" if omitted else ""


def certificate_labels(der, deadline):
    if len(der) > 32768:
        raise ValueError("Certificate size limit")
    # DER and decoded subject never touch disk. Only selected CN/O/DNS/IP labels leave this function.
    result = subprocess.run(["openssl", "x509", "-inform", "DER", "-noout", "-subject", "-nameopt", "RFC2253"],
                            input=der, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=remaining(deadline), check=True)
    if len(result.stdout) > 8192:
        raise ValueError("Certificate label limit")
    text = result.stdout.decode("utf-8", errors="strict")
    fields = {"outcome": "unverified_certificate_labels"}
    subject = text.splitlines()[0].removeprefix("subject=")
    selected, notice = selected_subject_attributes(subject)
    for attr, key in (("CN", "certificateCommonName"), ("O", "certificateOrganization")):
        if attr in selected:
            fields[key] = selected[attr]
    if notice:
        fields["certificateSubjectStatus"] = notice
    try:
        names, notice = certificate_alternative_names(der)
    except ValueError:
        names, notice = [], "Alternative names could not be decoded within capture limits"
    if names:
        fields["certificateNames"] = names
    if notice:
        fields["certificateAlternativeNamesStatus"] = notice
    return fields


def read_certificate(transport, deadline):
    # Isolated observation context: no credentials, HTTP, redirects, application data or trust claim.
    # The ordinary HTTPS reader below still requires platform chain and IP validation.
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    context.check_hostname = False
    context.verify_mode = ssl.CERT_NONE
    incoming, outgoing = ssl.MemoryBIO(), ssl.MemoryBIO()
    observer = context.wrap_bio(incoming, outgoing, server_side=False)
    received = sent = 0
    for _ in range(192):
        transport.settimeout(remaining(deadline))
        try:
            observer.do_handshake()
            # Do not flush post-handshake output or send an application request.
            return certificate_labels(observer.getpeercert(binary_form=True), deadline)
        except ssl.SSLWantReadError:
            while outgoing.pending:
                data = outgoing.read(8192)
                sent += len(data)
                if sent > 65536:
                    raise ValueError("TLS output limit")
                transport.settimeout(remaining(deadline))
                transport.sendall(data)
            if received >= 65536:
                raise ValueError("TLS input limit")
            transport.settimeout(remaining(deadline))
            data = transport.recv(min(8192, 65536-received))
            if not data:
                raise ValueError("TLS observation ended")
            received += len(data)
            incoming.write(data)
    raise ValueError("TLS observation step limit")


def read_page(interface, source, target, deadline):
    # No DNS, proxy environment, authentication, cookies, redirects or secondary requests.
    raw = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    transport = raw
    try:
        raw.setsockopt(socket.SOL_SOCKET, socket.SO_BINDTODEVICE, interface.encode("ascii") + b"\0")
        raw.bind((source, 0))
        raw.settimeout(max(0.001, deadline - time.monotonic()))
        raw.connect((target["address"], target["port"]))
        protocol = target.get("protocol", "http")
        if protocol == "ssh":
            return read_ssh(raw, deadline)
        if protocol == "tls":
            return read_certificate(raw, deadline)
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
        except (OSError, ValueError, http.client.HTTPException, subprocess.SubprocessError):
            result = {"outcome": "unavailable_or_invalid_response"}
        if not same_attachment(data["interface"], snapshot):
            counts["trial_stopped"] += 1
            break  # Discard a response whose attachment changed during the request.
        counts[result["outcome"]] += 1
        counts["page_title_available"] += bool(result.get("title"))
        counts["server_header_available"] += bool(result.get("server"))
        counts["authentication_realm_available"] += bool(result.get("realm"))
        counts["certificate_labels_available"] += result["outcome"] == "unverified_certificate_labels" and any(
            result.get(field) for field in ("certificateCommonName", "certificateOrganization", "certificateNames"))
        counts["ssh_software_available"] += bool(result.get("sshSoftware"))
        if args.review:
            print(json.dumps({"endpoint": index, **result}, ensure_ascii=True))
    report = {"environment": "host-interface trial, not Android", "selectedEndpoints": len(sources),
              "attemptedEndpoints": attempted, "outcomes": dict(counts),
              "limits": "Public labels are unverified service evidence, not hardware identity or accuracy gains. No device identities or addresses are saved. HTTP TLS validation is preserved. Separate certificate observation sends no application request and establishes no trust."}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n")
    print("Public-page trial finished; aggregate report written. No device identities assigned.")


if __name__ == "__main__":
    main()
