# Third-party code and assets

## Supplemental Apple mobile model facts

AppleDB device facts are MIT-licensed, Copyright (c) 2022 emiyl. The complete
notice is retained in `appledb-original/LICENSE` and packaged in
`app/src/main/res/raw/appledb_license.txt`. The upstream README, including its
credits to apple-knowledge, apple-data and The Apple Wiki, is retained alongside
the original `device/main.json` snapshot. Source: https://github.com/littlebyteorg/appledb .

`apple-model-provenance.json` pins the source Git blob and SHA-256, records every
selected/excluded mobile identifier and the generation policy. The supplemental
table contains 128 exact released iPad/iPhone codes absent from the existing
Recog table. Internal/unreleased products, board IDs and unreviewed device types
are excluded. Existing conservative Recog interpretations are retained. No
upstream executable code, API client, artwork or runtime network lookup is used.
The original full source is retained for reproduction; only selected mapping
facts and the license are bundled in the application.

## Public product catalogue facts

The manufacturer-qualified catalogue combines a filtered NIST CPE hardware
snapshot with the Google Play device facts published by
`androidtrackers/certified-android-devices` at commit
`dc47abd4e6d16ebc7e4428b933b0a388a2ddf9f0`, under MIT, Copyright (c) 2019
yshalsager. The complete MIT notice is retained in `catalog-original/LICENSE`
and packaged in `app/src/main/res/raw/android_catalog_license.txt`. The original
mirror JSON and a UTF-8 conversion of Google's public CSV are retained for
cross-checking; 156 mismatching mirror rows are rejected. No upstream sync code
is incorporated. Source: https://github.com/androidtrackers/certified-android-devices .

NIST permits voluntary use of the Official CPE Dictionary and states that it is
not subject to copyright in the United States. NIST attribution and limitations
are packaged in `app/src/main/res/raw/nist_catalog_notice.txt`. The compact
hardware input snapshot retains CPE names, IDs, English titles and deprecation
flags. No vulnerability data is incorporated and a catalogue match implies no
vulnerability finding or NIST endorsement. Source: https://nvd.nist.gov/products/cpe .

`android-catalog-provenance.json`, `nist-hardware-provenance.json` and
`product-catalog-provenance.json` record exact hashes, primary validation,
exclusions and merge rules. The build verifies the generated compressed table
offline. The table neither supplies presence nor turns an IEEE interface vendor
into a reported manufacturer. It adds no cloud service, paid licence or account.

## LineageOS Wiki hardware-category facts

The separate Android category table derives from LineageOS Wiki contributors at
commit `09e2176e2693dca067e9ce803ea1a5eb909806c3`. Wiki content is CC BY-SA 3.0,
as declared in the retained `lineage-original/footer.html`. This is separate from
the wiki theme's MIT licence. Attribution, adaptation notice and the complete
CC BY-SA 3.0 licence are packaged in `app/src/main/res/raw/lineage_categories_notice.txt`.
The source projection and category table adaptations remain CC BY-SA 3.0.

Source: https://github.com/LineageOS/lineage_wiki/tree/09e2176e2693dca067e9ce803ea1a5eb909806c3/_data/devices .
The retained projection contains only vendor, name, explicit models and physical
type, plus original path/hash. No installation instructions or device artwork are
used. The generator cross-checks exact manufacturer/model pairs against the
pinned Google-supported naming data and rejects ambiguity. It supplies neither
presence nor reported manufacturer. See `android-hardware-provenance.json` and
[Android recognition](../docs/ANDROID-RECOGNITION.md).

## Rapid7 Recog model mappings

The exact offline model-code table derives from Rapid7 Recog at commit
`f1191e24332c51d6a3d98457f64fba8dec340784`, under BSD-2-Clause, Copyright
(c) 2014-2015, Rapid7. The complete copyright, conditions and disclaimer are
retained in `recog-original/COPYING` and packaged in
`app/src/main/res/raw/recog_license.txt`. The original XML is retained in
`recog-original/mdns_device-info_txt.xml`.

Source: https://github.com/rapid7/recog/tree/f1191e24332c51d6a3d98457f64fba8dec340784 .
`recog-provenance.json` records upstream blob identities, SHA-256 digests and the
selection policy. `scripts/generate-recog-models.py` reproducibly selects exact
canonical model examples from anchored hardware rules. It excludes broad
patterns, board identifiers, OS rules and two ambiguous iMac descriptions;
low-certainty Apple TV rules supply only the family. The app runs generated
exact lookups, without the upstream Ruby/Java runtime or a runtime regex engine.
Review any data refresh explicitly and retain all upstream notices.

## Kotlin JVM standard library

Network Lens 0.3.0 uses the official Kotlin JVM standard library 2.3.21. Its Apache 2.0 license and applicable JVM standard-library third-party notices are packaged together in `app/src/main/res/raw/kotlin_license.txt`: GWT-derived collections (Apache 2.0), Guava-derived unsigned arithmetic (Apache 2.0), ThreeTenBP-derived time code (3-clause BSD), and Boost-derived math functions (Boost 1.0). The notices and attribution are copied from the pinned official Kotlin distribution's `license/README.md` and referenced license files. This does not incorporate the Kotlin compiler into the app.

Source: https://github.com/JetBrains/kotlin/tree/v2.3.21 . `kotlin-runtime-provenance.json` records the exact standard-library hash and class inventory. `scripts/kotlin-toolchain.json` pins the official compiler and D8 build tools; those build tools are not bundled in the APK.

## Lucide device pictograms

Device pictograms are Lucide Icons. The complete upstream ISC license and the MIT notice for Feather-derived icons are packaged in `app/src/main/res/raw/lucide_license.txt` and in the APK. No Fing assets are included.

Source: https://github.com/lucide-icons/lucide

The original SVGs are retained in `lucide-original/`. `scripts/convert-lucide-icons.py` converts their existing path, rectangle, circle and line geometry into native Android vectors, preserving the 24-unit grid, 2-unit stroke and rounded caps/joins. Network Lens applies one foreground colour per theme through ImageView. See `lucide-provenance.json` for exact source paths and Git blob identities.

## IEEE MAC registry facts

The offline MAC-vendor index is derived directly from the IEEE Registration Authority public MA-L, MA-M, MA-S and historical IAB CSV listings. Only assignment prefixes and organization names are retained; postal addresses are omitted. Source URLs, retrieval times and SHA-256 digests are in `ieee-vendor-provenance.json`. It is an identifier-registry lookup, not a device-model database or IEEE endorsement.

IEEE's historical statement that it does not assert copyright or restrict distribution of the public listing is recorded in Debian's `ieee-data` copyright record: https://sources.debian.org/src/ieee-data/20240722/debian/copyright/ . The earlier IEEE source URL now redirects to the current registry page. Current IEEE guidance describes using the public listings to identify registered owners: https://standards.ieee.org/products-programs/regauth/ and https://standards-support.ieee.org/hc/en-us/articles/4888705676564-Guidelines-for-Use-of-Extended-Unique-Identifier-EUI-Organizationally-Unique-Identifier-OUI-and-Company-ID-CID . No Debian code, Wireshark database or Nmap database is included.

Refresh explicitly with `python3 scripts/update-ieee-vendors.py --refresh`. The app does not contact IEEE or another lookup service. Registered organizations may be interface suppliers rather than device manufacturers; private, locally administered and multicast addresses do not produce a vendor guess.

## Broader discovery data and protocol references

Home Assistant Core's mDNS/SSDP/HomeKit matcher tables are retained with the
Apache-2.0 license in `ha-original/`; the application bundles the full license.
`ha-matcher-provenance.json` pins the exact inputs and generated projection.
All rule conditions are preserved; no DHCP matchers or integration control code
are incorporated.

The Matter DCL snapshot contains public vendor/product facts only. See
`matter-model-provenance.json`, `matter-original/models.json` and the bundled
`matter_registry_notice.txt`. The ledger software's Apache license is not
claimed as a blanket license over vendor-submitted records.

The Easy Smart static stream table/transform is adapted from rgl's GPL-3.0
Ansible collection. Its original source and GPL license are retained in
`easysmart-original/`; `easysmart-provenance.json` records the source and
modification date. Network Lens implements only discovery, with independent
strict request correlation and public-field parsing.

TinyTuya was consulted for public UDP discovery envelopes; its MIT notice is
packaged in `app/src/main/res/raw/tuya_protocol_notice.txt`. No device local key
or control client is incorporated. Other protocol modules are original code
based on the factual wire references in `../docs/DISCOVERY-COVERAGE.md`.

## Amazon device-type fact

One public Amazon device-type mapping is adapted from ioBroker.alexa2's MIT
table at commit `6471cf116653e50b6cbc6c4dd54c2d52bfea4190`. The display title is
normalized to Amazon Fire TV Stick 4K, without a generation claim. Exact source
blob, retained fact and modifications are in `fire-tv-provenance.json`; the full
MIT attribution and license are bundled as `res/raw/fire_tv_type_notice.txt`.
No upstream network, cloud-account or control code is included.
