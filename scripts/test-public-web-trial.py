#!/usr/bin/env python3
"""Synthetic and loopback-only checks. Never reads household fixtures or contacts LAN devices."""
import importlib.util
import io
import json
from pathlib import Path
import random
import socket
import shutil
import ssl
import subprocess
import sys
import threading
import tempfile
import time
import unittest
from unittest.mock import patch
from contextlib import redirect_stdout

spec = importlib.util.spec_from_file_location("public_web", Path(__file__).with_name("trial-public-web.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class FixtureSocket:
    def __init__(self, wire):
        self.wire = wire

    def makefile(self, mode):
        return io.BytesIO(self.wire)


def der_field(tag, value):
    size = len(value)
    length = bytes([size]) if size < 128 else bytes([128 + (size.bit_length()+7)//8]) + size.to_bytes((size.bit_length()+7)//8, "big")
    return bytes([tag]) + length + value


def name_fixture(names=None, extensions=None, version=True):
    """Structural selector fixture only; actual X.509/OpenSSL interop is checked separately."""
    body = (der_field(0xa0, der_field(2, b"\x02")) if version else b"") + der_field(2, b"\x01") + der_field(0x30, b"") * 5
    if extensions is None and names is not None:
        extension = der_field(6, b"\x55\x1d\x11") + der_field(4, der_field(0x30, b"".join(der_field(tag, value) for tag, value in names)))
        extensions = [der_field(0x30, extension)]
    if extensions is not None:
        body += der_field(0xa3, der_field(0x30, b"".join(extensions)))
    return der_field(0x30, der_field(0x30, body) + der_field(0x30, b"") + der_field(3, b"\x00"))


class PublicWebTests(unittest.TestCase):
    def inspect(self, body=b"", headers=b"Content-Type: text/html\r\n", status=b"200 OK"):
        wire = b"HTTP/1.1 " + status + b"\r\n" + headers + b"Content-Length: " + str(len(body)).encode() + b"\r\n\r\n" + body
        response = module.http.client.HTTPResponse(FixtureSocket(wire))
        response.begin()
        try:
            return module.inspect_response(response)
        finally:
            response.close()

    def selection(self):
        return {"interface": "wlan0", "targets": [{"address": "192.168.50.20", "port": 80, "currentResponseConfirmed": True}]}

    def test_ignored_repeated_cookies_and_split_utf8(self):
        result = self.inspect(b"<title>Fixture</title>", b"Content-Type: text/html\r\nServer: FixtureServer\r\nSet-Cookie: a=PRIVATE1\r\nSet-Cookie: b=PRIVATE2\r\n")
        self.assertEqual(result["title"], "Fixture")
        self.assertEqual(result["server"], "FixtureServer")
        self.assertNotIn("PRIVATE", str(result))
        prefix = b"<title>Fixture</title>"
        body = prefix + b" " * (8191-len(prefix)) + "é".encode() + b"x" * 50
        self.assertEqual(self.inspect(body)["title"], "Fixture")
        with self.assertRaises(UnicodeDecodeError): self.inspect(prefix + b"\xff" + b"x" * 9000)

    def test_public_title_is_only_service_evidence(self):
        result = self.inspect(b"<title>Example appliance &amp; settings</title>", b"Content-Type: text/html; charset=UTF-8\r\nServer: nginx\r\n")
        self.assertEqual(result, {"outcome": "public_labels", "title": "Example appliance & settings", "server": "nginx"})
        self.assertTrue(set(result).isdisjoint({"model", "manufacturer", "category", "mac", "name"}))

    def test_generic_server_is_not_a_product(self):
        self.assertEqual(self.inspect(headers=b"Server: nginx\r\n"),
                         {"outcome": "public_labels", "title": "", "server": "nginx"})

    def test_scripts_comments_duplicate_and_unclosed_titles(self):
        for body in (b"<!-- <title>Hidden</title> -->", b"<script>var x='<title>Fake</title>';</script>",
                     b"<title>A</title><title>B</title>", b"<title>Unclosed", b"<title>" + b"X" * 513 + b"</title>"):
            with self.subTest(body=body[:40]):
                self.assertEqual(self.inspect(body)["title"], "")

    def test_untrusted_text_is_bounded_and_controls_removed(self):
        result = self.inspect(("<title>Model\x1b\u202e " + "X" * 180 + "</title>").encode())
        self.assertLessEqual(len(result["title"]), 160)
        self.assertNotIn("\x1b", result["title"])
        self.assertNotIn("\u202e", result["title"])

    def test_unsupported_body_is_not_interpreted(self):
        self.assertEqual(self.inspect(b"<title>Fake</title>", b"Content-Type: application/json\r\n")["title"], "")
        self.assertEqual(self.inspect(b"<title>Fake</title>", b"Content-Type: text/html; charset=shift_jis\r\n")["title"], "")
        self.assertEqual(self.inspect(headers=b"Content-Encoding: gzip\r\n")["outcome"], "unsupported_encoding")
        with self.assertRaises(ValueError):
            self.inspect(b"\xff", b"Content-Type: text/html\r\n")

    def test_redirect_auth_and_errors_are_terminal(self):
        for status, expected in ((b"302 Found", "redirect_not_followed"), (b"401 Unauthorized", "access_required"),
                                 (b"403 Forbidden", "access_required"), (b"404 Not Found", "other_http_status")):
            self.assertEqual(self.inspect(b"<title>Not evidence</title>", b"Location: http://192.0.2.1/\r\n", status), {"outcome": expected})

    def test_duplicate_headers_size_and_ambiguous_framing(self):
        for headers, body in ((b"Server: a\r\nServer: b\r\n", b""), (b"Transfer-Encoding: chunked\r\n", b"")):
            with self.assertRaises(ValueError):
                self.inspect(body, headers)

    def test_headers_survive_auth_and_prefix_is_bounded(self):
        result = self.inspect(b"private login body", b'Server: appliance\r\nWWW-Authenticate: Digest realm="Private room", nonce="SECRET"\r\nSet-Cookie: token=SECRET\r\n', b"401 Unauthorized")
        self.assertEqual(result, {"outcome": "access_required", "server": "appliance", "realm": "Private room"})
        self.assertNotIn("SECRET", str(result))
        result = self.inspect(b"<title>Prefix</title>" + b"x" * 20000)
        self.assertEqual(result["title"], "Prefix")
        self.assertTrue(result["prefixLimited"])
        result = self.inspect(headers=b'WWW-Authenticate: Basic realm="a"\r\nWWW-Authenticate: Digest realm="b", nonce="secret"\r\n', status=b"401 Unauthorized")
        self.assertNotIn("realm", result)

    def test_explicit_reader_selection(self):
        for protocol, port in (("ssh", 22), ("tls", 8009), ("tls", 443)):
            data = self.selection()
            data["targets"][0].update(protocol=protocol, port=port)
            self.assertEqual(module.validate(data), data)
        for protocol, port in (("ssh", 80), ("tls", 80), ("http", 22), ("unknown", 80)):
            data = self.selection()
            data["targets"][0].update(protocol=protocol, port=port)
            with self.assertRaises(ValueError): module.validate(data)

    def test_ssh_receives_only_software_token(self):
        class Transport:
            def __init__(self, wire): self.wire = io.BytesIO(wire)
            def settimeout(self, value): pass
            def recv(self, count): return self.wire.read(count)
        result = module.read_ssh(Transport(b"Welcome\r\nSSH-2.0-OpenSSH_9.9 Owner private comment\r\n"), time.monotonic()+2)
        self.assertEqual(result["sshSoftware"], "OpenSSH_9.9")
        self.assertNotIn("Owner", str(result))
        for wire in (b"SSH-1.5-old\r\n", b"SSH-2.0-x\n", b"x" * 254):
            with self.assertRaises(ValueError): module.read_ssh(Transport(wire), time.monotonic()+2)

    def test_only_explicit_current_private_endpoints(self):
        self.assertEqual(module.validate(self.selection())["interface"], "wlan0")
        for address in ("example.com", "127.0.0.1", "192.0.2.1", "::1", "224.0.0.1", "8.8.8.8"):
            data = self.selection()
            data["targets"][0]["address"] = address
            with self.assertRaises(ValueError): module.validate(data)
        for field, value in (("port", 22), ("port", True), ("currentResponseConfirmed", False)):
            data = self.selection()
            data["targets"][0][field] = value
            with self.assertRaises(ValueError): module.validate(data)
        data = self.selection()
        data["targets"] *= 2
        with self.assertRaises(ValueError): module.validate(data)

    def test_direct_scope_excludes_self_and_broadcast(self):
        snapshot = (2, (("192.168.50.2", 24),))
        self.assertEqual(module.source_for(snapshot, "192.168.50.20"), "192.168.50.2")
        for address in ("192.168.50.2", "192.168.50.0", "192.168.50.255", "192.168.51.20"):
            with self.assertRaises(ValueError): module.source_for(snapshot, address)

    def test_wire_budget_and_deadline(self):
        class FakeTransport:
            def settimeout(self, value): pass
            def recv_into(self, buffer, count): return count
        reader = module.BoundedReader(FakeTransport(), time.monotonic() + 5)
        buffer = bytearray(98304)
        self.assertEqual(reader.readinto(buffer), 98304)
        with self.assertRaises(ValueError): reader.readinto(buffer)
        reader = module.BoundedReader(FakeTransport(), time.monotonic() - 1)
        with self.assertRaises(TimeoutError): reader.readinto(buffer)

    def test_network_loss_stops_trial(self):
        snapshot = (2, (("192.168.50.2", 24),))
        with patch.object(module, "attachment", return_value=snapshot):
            self.assertTrue(module.same_attachment("wlan0", snapshot))
        with patch.object(module, "attachment", return_value=(3, snapshot[1])):
            self.assertFalse(module.same_attachment("wlan0", snapshot))
        with patch.object(module, "attachment", side_effect=OSError("interface disappeared")):
            self.assertFalse(module.same_attachment("wlan0", snapshot))

    def run_main(self, stable, result=None, selection=None):
        from unittest.mock import Mock
        snapshot = (2, (("192.168.50.2", 24),))
        stdin = Mock(buffer=io.BytesIO(json.dumps(selection or self.selection()).encode()))
        if result is None:
            result = {"outcome": "public_labels", "title": "Synthetic private room", "server": "SyntheticServer"}
        # Exercise a fresh checkout on every run, independently of local build outputs.
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            output = root / "artifacts" / "aggregate.json"
            self.assertFalse(output.parent.exists())
            terminal = io.StringIO()
            with patch.object(module, "ROOT", root), \
                 patch.object(module.sys, "argv", ["trial", "--output", str(output)]), \
                 patch.object(module.sys, "stdin", stdin), \
                 patch.object(module, "attachment", return_value=snapshot), \
                 patch.object(module, "same_attachment", side_effect=stable), \
                 patch.object(module, "read_page", return_value=result) as read, \
                 redirect_stdout(terminal):
                module.main()
            report = output.read_text()
            self.assertNotIn("192.168.", report + terminal.getvalue())
            self.assertNotIn("Synthetic private room", report + terminal.getvalue())
            self.assertNotIn("SyntheticServer", report + terminal.getvalue())
            return json.loads(report), read.call_count

    def test_main_saves_aggregates_only(self):
        report, requests = self.run_main([True, True])
        self.assertEqual(requests, 1)
        self.assertEqual(report["outcomes"]["page_title_available"], 1)
        self.assertNotIn("model", report)

    def test_network_change_discards_response(self):
        report, requests = self.run_main([True, False])
        self.assertEqual(requests, 1)
        self.assertEqual(report["outcomes"], {"trial_stopped": 1})

    def test_network_change_before_request_sends_nothing(self):
        report, requests = self.run_main([False])
        self.assertEqual(requests, 0)
        self.assertEqual(report["attemptedEndpoints"], 0)

    def test_one_root_get_no_redirects_or_credentials(self):
        # Real sockets are exclusively loopback; no fixture can target a physical device.
        requests = []
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            server.settimeout(3)
            port = server.getsockname()[1]
            def serve():
                with server.accept()[0] as peer:
                    peer.settimeout(2)
                    request = b""
                    while b"\r\n\r\n" not in request and len(request) < 2048:
                        chunk = peer.recv(2048)
                        if not chunk: break
                        request += chunk
                    requests.append(request)
                    peer.sendall(b"HTTP/1.1 302 Found\r\nLocation: http://192.0.2.1/\r\nContent-Length: 0\r\n\r\n")
            thread = threading.Thread(target=serve, daemon=True)
            thread.start()
            with patch.dict(module.PORTS, {port: False}):
                result = module.read_page("lo", "127.0.0.1", {"address": "127.0.0.1", "port": port}, time.monotonic() + 2)
            thread.join(3)
            self.assertFalse(thread.is_alive())
        self.assertEqual(result, {"outcome": "redirect_not_followed"})
        self.assertEqual(len(requests), 1)
        self.assertTrue(requests[0].startswith(b"GET / HTTP/1.1\r\n"))
        self.assertNotIn(b"Authorization:", requests[0])
        self.assertNotIn(b"Cookie:", requests[0])

    def test_certificate_fields_are_selected_and_bounded(self):
        from unittest.mock import Mock
        decoded = b"subject=CN=fixture.invalid,O=Synthetic fixture\n"
        certificate = name_fixture([(0x82, b"room.invalid"), (0x87, bytes([192, 0, 2, 34])),
                                    (0x81, b"private@example.invalid"), (0x86, b"https://private.invalid/DNS:private-token")])
        with patch.object(module.subprocess, "run", return_value=Mock(stdout=decoded)) as command:
            result = module.certificate_labels(certificate, time.monotonic()+2)
        self.assertEqual(result["certificateCommonName"], "fixture.invalid")
        self.assertEqual(result["certificateNames"], ["room.invalid", "192.0.2.34"])
        self.assertNotIn("private", str(result))
        self.assertEqual(command.call_args.kwargs["input"], certificate)
        self.assertNotIn("-text", command.call_args.args[0])
        self.assertNotIn("-ext", command.call_args.args[0])
        with self.assertRaises(ValueError): module.certificate_labels(b"x" * 32769, time.monotonic()+2)

    def test_alternative_names_use_types_not_embedded_markers(self):
        names = [(0x86, b"https://fixture.invalid/DNS:private-token"),
                 (0x86, b"https://fixture.invalid/a, DNS:forged.invalid, IP Address:192.0.2.99"),
                 (0x81, b"DNS:email-marker@fixture.invalid"),
                 (0xa4, der_field(0x30, b"DNS:directory-marker")),
                 (0x82, b"room.invalid"), (0x87, bytes([192, 0, 2, 34])),
                 (0x87, module.ipaddress.IPv6Address("2001:db8::34").packed)]
        self.assertEqual(module.certificate_alternative_names(name_fixture(names)),
                         (["room.invalid", "192.0.2.34", "2001:db8::34"], ""))
        self.assertEqual(module.certificate_alternative_names(name_fixture(names[:4])), ([], ""))
        self.assertEqual(module.certificate_alternative_names(name_fixture(version=False)), ([], ""))
        critical = der_field(0x30, der_field(6, b"\x55\x1d\x11") + der_field(1, b"\xff") +
                             der_field(4, der_field(0x30, der_field(0x82, b"fixture.invalid"))))
        self.assertEqual(module.certificate_alternative_names(name_fixture(extensions=[critical])), (["fixture.invalid"], ""))

    def test_alternative_name_limits_and_malformed_der(self):
        names, notice = module.certificate_alternative_names(name_fixture([(0x82, f"fixture-{n}.invalid".encode()) for n in range(9)]))
        self.assertEqual(names, [f"fixture-{n}.invalid" for n in range(8)])
        self.assertTrue(notice)
        names, notice = module.certificate_alternative_names(name_fixture([
            (0x82, b"x" * 160), (0x82, b"x" * 161), (0x82, b"bad\x00name"), (0x82, b"\xff"), (0x87, b"\x01\x02\x03")]))
        self.assertEqual(names, ["x" * 160])
        self.assertTrue(notice)
        certificate = name_fixture([(0x82, b"fixture.invalid")])
        for end in range(len(certificate)):
            with self.assertRaises(ValueError): module.certificate_alternative_names(certificate[:end])
        extension = der_field(0x30, der_field(6, b"\x55\x1d\x11") + der_field(4, der_field(0x30, der_field(0x82, b"fixture.invalid"))))
        for invalid in (certificate + b"\x00", b"\x30\x80\x00\x00", b"\x30\x81\x01\x00",
                        name_fixture(extensions=[extension, extension]),
                        name_fixture([(0x82, b"fixture.invalid")] * 129),
                        name_fixture([(0xa2, b"constructed.invalid")])):
            with self.assertRaises(ValueError): module.certificate_alternative_names(invalid)
        from unittest.mock import Mock
        with patch.object(module.subprocess, "run", return_value=Mock(stdout=b"subject=OU=Synthetic fixture\n")):
            result = module.certificate_labels(certificate[:-1], time.monotonic()+2)
        self.assertNotIn("certificateNames", result)
        self.assertIn("capture limits", result["certificateAlternativeNamesStatus"])
        rng = random.Random(34)
        for _ in range(1000):
            data = rng.randbytes(rng.randrange(512))
            try:
                names, _ = module.certificate_alternative_names(data)
                self.assertLessEqual(len(names), 8)
            except ValueError:
                pass

    def test_certificate_availability_counts_retained_labels_only(self):
        selection = self.selection()
        selection["targets"][0].update(port=443, protocol="tls")
        for fields, expected in (({}, 0), ({"certificateSubjectStatus": "Selected attributes omitted"}, 0),
                                 ({"certificateAlternativeNamesStatus": "Alternative names could not be decoded", "certificateNames": []}, 0),
                                 ({"certificateCommonName": "fixture.invalid"}, 1),
                                 ({"certificateOrganization": "Synthetic fixture"}, 1),
                                 ({"certificateNames": ["room.invalid", "192.0.2.34"]}, 1)):
            result = {"outcome": "unverified_certificate_labels", **fields}
            report, requests = self.run_main([True, True], result, selection)
            self.assertEqual(requests, 1)
            self.assertEqual(report["outcomes"]["unverified_certificate_labels"], 1)
            self.assertEqual(report["outcomes"]["certificate_labels_available"], expected)
            self.assertNotIn("fixture.invalid", json.dumps(report))

    @unittest.skipUnless(shutil.which("openssl"), "OpenSSL is required for the optional real-certificate interoperability check")
    def test_real_certificate_san_selection_and_empty_label_count(self):
        # Generate short-lived fixture keys in OpenSSL's memory and discard their
        # output to /dev/null. Only public DER enters this process; no key/cert file.
        base = "[req]\ndistinguished_name=dn\nx509_extensions=ext\nprompt=no\n[dn]\nOU=Synthetic fixture\n[ext]\nsubjectAltName=@names\n[names]\n"
        uri = "URI.1=https://fixture.invalid/DNS:private-token\nURI.2=https://fixture.invalid/a, DNS:forged.invalid\nemail.1=DNS:mail-marker@fixture.invalid\n"
        selection = self.selection()
        selection["targets"][0].update(port=443, protocol="tls")
        for extra, expected in (("", []), ("DNS.1=room.invalid\nIP.1=192.0.2.34\nIP.2=2001:db8::34\n", ["room.invalid", "192.0.2.34", "2001:db8::34"])):
            certificate = subprocess.run(["openssl", "req", "-x509", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:prime256v1",
                                          "-nodes", "-keyout", "/dev/null", "-outform", "DER", "-days", "1", "-config", "/dev/stdin"],
                                         input=(base+uri+extra).encode(), stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=5, check=True).stdout
            result = module.certificate_labels(certificate, time.monotonic()+2)
            self.assertEqual(result.get("certificateNames", []), expected)
            self.assertNotIn("private-token", str(result))
            self.assertNotIn("forged.invalid", str(result))
            self.assertNotIn("certificateCommonName", result)
            self.assertNotIn("certificateOrganization", result)
            report, _ = self.run_main([True, True], result, selection)
            self.assertEqual(report["outcomes"]["certificate_labels_available"], int(bool(expected)))

    def test_certificate_subject_escapes_and_multivalued_names(self):
        for subject, expected in (
            (r"CN=Fixture\, console+O=Fixture\+ Lab", {"CN": "Fixture, console", "O": "Fixture+ Lab"}),
            (r"CN=Caf\C3\A9,O=Fixture\=Lab", {"CN": "Café", "O": "Fixture=Lab"}),
            (r"OU=private\,CN\=Fake,CN=fixture.invalid", {"CN": "fixture.invalid"}),
            (r"CN=Fixture\\console,O=Fixture\20Lab", {"CN": "Fixture\\console", "O": "Fixture Lab"}),
        ):
            with self.subTest(subject=subject):
                self.assertEqual(module.selected_subject_attributes(subject), (expected, ""))

    def test_certificate_subject_omissions_and_invalid_syntax(self):
        for subject in ("CN=" + "\\\\" * 161, "O=" + "x" * 161, r"CN=#040161", r"CN=\FF", "CN=", "CN=\ud800",
                        "CN=fixture\\", r"CN=fixture\q", "CN=fixture,", "CN=fixture+", "CN=fixture,O", 'CN="fixture"', "CN=fixture;O=bad"):
            with self.subTest(subject=subject[:40]):
                values, notice = module.selected_subject_attributes(subject)
                self.assertEqual(values, {})
                self.assertTrue(notice)
        for subject in ("CN=one,CN=two,O=Fixture", "CN=one+CN=one,O=Fixture", "CN=one,CN=two,CN=three,O=Fixture"):
            self.assertEqual(module.selected_subject_attributes(subject),
                             ({"O": "Fixture"}, "Overlong, unsupported or duplicate subject attributes omitted"))

    def test_certificate_subject_exact_bounds(self):
        self.assertEqual(module.selected_subject_attributes("CN=" + "\\\\" * 160), ({"CN": "\\" * 160}, ""))
        self.assertEqual(module.selected_subject_attributes("CN=" + "😀" * 80), ({"CN": "😀" * 80}, ""))
        self.assertTrue(module.selected_subject_attributes("CN=" + "😀" * 81)[1])
        prefix = "CN=fixture.invalid,OU="
        subject = prefix + "x" * (2048-len(prefix))
        self.assertEqual(module.selected_subject_attributes(subject), ({"CN": "fixture.invalid"}, ""))
        self.assertEqual(module.selected_subject_attributes(subject + "x"), ({}, "Subject name exceeded capture limit"))

    def test_certificate_label_integration_has_a_process_deadline(self):
        # Run only the patched selector; a future regression must fail rather than hang this suite.
        worker = r'''
import importlib.util, pathlib, sys, time
from types import SimpleNamespace
spec = importlib.util.spec_from_file_location("public_web", pathlib.Path(sys.argv[1]))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
escaped = "\\\\" * 161
decoded = ("subject=CN=" + escaped + ",O=" + escaped + "\n").encode()
module.subprocess.run = lambda *args, **kwargs: SimpleNamespace(stdout=decoded)
result = module.certificate_labels(b"synthetic-DER", time.monotonic()+2)
assert "certificateCommonName" not in result and "certificateOrganization" not in result
assert "omitted" in result["certificateSubjectStatus"]
print("PASS")
'''
        result = subprocess.run([sys.executable, "-c", worker, str(Path(module.__file__).resolve())],
                                capture_output=True, timeout=5, check=True)
        self.assertEqual(result.stdout.strip(), b"PASS")

    def test_tls_failure_does_not_send_plaintext(self):
        from unittest.mock import Mock
        raw = Mock()
        context = Mock()
        context.wrap_socket.side_effect = ssl.SSLCertVerificationError("synthetic untrusted certificate")
        with patch.object(module.socket, "socket", return_value=raw), patch.object(module.ssl, "create_default_context", return_value=context):
            with self.assertRaises(ssl.SSLError):
                module.read_page("wlan0", "192.168.50.2", {"address": "192.168.50.20", "port": 443}, time.monotonic() + 2)
        raw.sendall.assert_not_called()
        raw.close.assert_called_once()
        raw.connect.assert_called_once()


if __name__ == "__main__":
    unittest.main()
