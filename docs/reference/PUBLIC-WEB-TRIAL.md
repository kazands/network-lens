# Public-page identification trial

This developer diagnostic evaluates whether public web pages contain useful
clues for unresolved devices. It does not change Network Lens, guess products,
or claim improved recognition. Start with a current
[recognition baseline](RECOGNITION-BASELINE.md), including its exact APK hash.

## Select a small sample

Separate missing responses from missing interpretation. Select unresolved
endpoints that responded in the current scan and have an observed web service.
Include already identified controls and generic software servers. Do not reuse
old addresses as device identities. Confirm product expectations with the owner
or label; otherwise leave them unknown.

Use an explicitly selected Linux interface attached to the same network. The
tool requires direct-subnet private IPv4 targets and binds every socket to that
interface and its source address. It checks the interface index and IPv4
configuration before and after each endpoint, discarding results after a detected
change. This is not Android selected-Wi-Fi validation and cannot prove an
unchanged physical network if an interface reconnects with identical settings.

## Run

```sh
python3 scripts/trial-public-web.py --output artifacts/public-web-trial/report.json
```

Paste the selection into standard input, then end input with Ctrl+D. The example
below is synthetic: replace the interface and endpoint with the explicitly
authorized, currently confirmed target. Do not save household input to a file.

```json
{
  "interface": "wlan0",
  "targets": [
    {"address": "192.168.50.20", "port": 80, "currentResponseConfirmed": true}
  ]
}
```

At most 16 distinct endpoint/protocol selections are accepted. The optional `protocol` is `http` (default), `tls` (certificate observation on 443/8443/8009) or `ssh` (greeting only on 22). The certificate observer uses a separate in-memory handshake, sends no application request and makes no trust claim; it does not weaken the HTTP reader. SSH sends no banner, credentials or commands. Certificate decoding uses local OpenSSL through standard input/output, with no certificate/key file. Both observers cap network reads and deadlines.

Certificate subject selection uses a bounded forward scan of canonical RFC2253
output, not a backtracking expression. The whole subject is limited to 2,048
UTF-16 code units and each decoded CN/organisation label to 160. Unsupported,
duplicate or overlong attributes produce an omission notice; malformed subjects
produce no subject labels. The app reader follows the same selection rules.

Alternative names are selected by their encoded DNS/IP types in the certificate,
following [RFC 5280](https://www.rfc-editor.org/rfc/rfc5280.html#section-4.2.1.6),
rather than searching OpenSSL's display text. URI/email values cannot supply DNS
names even when they contain `DNS:`, `IP Address:` or commas. This selector reads
at most 64 extensions and 128 alternative-name entries from the existing 32 KiB
certificate bound, retaining at most eight names of at most 160 characters each.
Invalid structure or omitted selected names produce a fixed diagnostic notice.
It does not validate certificate trust; normal HTTPS verification is unchanged.

For HTTP, Ports 80/8080 use HTTP and 443/8443
use HTTPS with normal certificate and IP verification. Each endpoint receives
one `GET /`, with a two-second network deadline and a 32-second overall request
budget. Response bodies are limited to an 8 KiB prefix and total HTTP wire reads to
96 KiB. No DNS, redirects, cookies, credentials, scripts, linked resources,
additional endpoints or TLS fallback are used. Interface binding must succeed;
there is no unbound fallback. Local requests may appear in device access logs.

The report contains aggregate outcomes only. `public_labels` means a page title
or server/realm label was available; it does **not** mean an identity was found.
Authentication-required responses can retain a selected realm without reading the login body. Certificate and SSH label counts remain separate from public-page counts. Use `--review` only for an authorized in-memory review of those untrusted labels.
`certificate_labels_available` counts certificates with at least one retained CN,
organisation or DNS/IP name. A decoded certificate with no selected names still
has its observation outcome counted, but contributes zero to label availability.
Do not redirect, save or commit that terminal output: titles can contain personal
names. Raw bodies, cookies and addresses are not written to the report. Review
labels are bounded and terminal control characters are removed.

## Decide what to implement

For each candidate, check whether the page actually identifies hardware, a
software service, a generic login screen or a hosted/proxied service. Require
specific corroborated evidence and independent positive/negative fixtures before
adding a product rule. Generic server names such as nginx never establish a
manufacturer, model or device category.

Count newly corroborated family/model evidence separately from available page
titles. Record access-required, redirect, TLS and transport failures without
weakening their safeguards. Keep the original baseline unchanged; a host trial
is not an improved Android result. Re-measure on the phone only after any reader
is integrated and tested against a separately identified APK.

Current validation is synthetic and loopback-only. No physical accuracy gain is
claimed. Run `python3 scripts/test-public-web-trial.py` for isolated checks; OpenSSL
is needed for the real-certificate interoperability check, which is explicitly
reported as skipped when unavailable. The same tests are included in
`scripts/test.sh`.
