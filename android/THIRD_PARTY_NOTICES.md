# Third-party code and assets

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
