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

At most 16 distinct endpoints are accepted. Ports 80/8080 use HTTP and 443/8443
use HTTPS with normal certificate and IP verification. Each endpoint receives
one `GET /`, with a two-second network deadline and a 32-second overall request
budget. Response bodies are limited to 64 KiB and total HTTP wire reads to
96 KiB. No DNS, redirects, cookies, credentials, scripts, linked resources,
additional endpoints or TLS fallback are used. Interface binding must succeed;
there is no unbound fallback. Local requests may appear in device access logs.

The report contains aggregate outcomes only. `public_labels` means a page title
or server header was available; it does **not** mean an identity was found.
Use `--review` only for an authorized in-memory review of those untrusted labels.
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
claimed. Run `python3 scripts/test-public-web-trial.py` for isolated checks; the
same tests are included in `scripts/test.sh`.
