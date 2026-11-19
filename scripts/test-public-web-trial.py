#!/usr/bin/env python3
"""Synthetic and loopback-only checks. Never reads household fixtures or contacts LAN devices."""
import importlib.util
import io
import json
from pathlib import Path
import socket
import ssl
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
        for headers, body in ((b"Server: a\r\nServer: b\r\n", b""), (b"Transfer-Encoding: chunked\r\n", b""),
                              (b"", b"X" * 65537)):
            with self.assertRaises(ValueError):
                self.inspect(body, headers)

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

    def run_main(self, stable):
        from unittest.mock import Mock
        snapshot = (2, (("192.168.50.2", 24),))
        stdin = Mock(buffer=io.BytesIO(json.dumps(self.selection()).encode()))
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
                 patch.object(module, "read_page", return_value={"outcome": "public_labels", "title": "Synthetic private room", "server": "SyntheticServer"}) as read, \
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
