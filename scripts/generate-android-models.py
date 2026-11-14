#!/usr/bin/env python3
"""Build a manufacturer-qualified offline index from pinned, cross-checked facts."""
import argparse
import csv
import hashlib
import io
import json
import re
import unicodedata
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / 'android/catalog-original/by_model.json'
PRIMARY = ROOT / 'android/catalog-original/google-supported-devices.csv'
LICENSE = ROOT / 'android/catalog-original/LICENSE'
DEST = ROOT / 'android/catalog-generated/android-models.tsv'
META = ROOT / 'android/android-catalog-provenance.json'
REVISION = 'dc47abd4e6d16ebc7e4428b933b0a388a2ddf9f0'
SOURCE_SHA = '66847b2827b84b5a8d92c4ee8900157d925b65cc6a70e15845bc9a0b6be6a450'
PRIMARY_SHA = '50ce70017856456ec890b5e9f02e080d19754fcacf9168a346d0c0fe640bc0de'
# Pins, not a mutable refresh operation. Review source/primary/license changes together.
PRIMARY_UTF8_PIN = '779c6f66185cedceb4c9aa9e61e1730a5aefa423fb43028f122a8dc0a8ba47cc'
LICENSE_BLOB = 'ce80712fceaa7a01d6a36a1943f206c36d555a74'
GENERIC = re.compile(r'unknown|none|null|n/?a|android|generic(?: device)?|device|tv|smart tv|tablet|smartphone|phone|default|all|[0-9]{1,3}', re.I)

def sha(data):
    return hashlib.sha256(data).hexdigest()

def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

def clean(value):
    return isinstance(value, str) and 0 < len(value.encode('utf-16-be')) <= 320 and value == value.strip(' \t') and not any(unicodedata.category(c).startswith('C') or c in '\t\r\n\u2028\u2029' for c in value)

def key(value):
    return value.strip(' \t').lower()

def title(brand, name):
    if key(name) == key(brand):
        return brand
    if key(name).startswith(key(brand) + ' '):
        return brand + name[len(brand):]
    return brand + ' ' + name

def generate():
    data, primary, license_data = SOURCE.read_bytes(), PRIMARY.read_bytes(), LICENSE.read_bytes()
    assert sha(data) == SOURCE_SHA, 'Pinned Android catalogue changed'
    assert sha(primary) == PRIMARY_UTF8_PIN, 'Pinned primary validation snapshot changed'
    assert blob(license_data) == LICENSE_BLOB, 'Upstream MIT notice changed'
    assert (ROOT / 'android/app/src/main/res/raw/android_catalog_license.txt').read_bytes() == license_data
    records = list(csv.DictReader(io.StringIO(primary.decode('utf-8'))))
    official = {(r['Retail Branding'].strip(), r['Marketing Name'].strip(), r['Device'].strip(), r['Model'].strip()) for r in records}
    official_names = defaultdict(set)
    for brand, name, _, model in official:
        official_names[(key(brand), key(model))].add(key(title(brand, name)))
    choices = defaultdict(set)
    stats = defaultdict(int)
    for model, rows in json.loads(data).items():
        for r in rows:
            stats['source_rows'] += 1
            brand, name, device = r['brand'], r['name'], r['device']
            if (brand, name, device, model) not in official:
                stats['primary_mismatch_rows'] += 1
                continue
            if not all(clean(v) for v in (brand, name, model)) or GENERIC.fullmatch(model) or GENERIC.fullmatch(name):
                stats['unusable_rows'] += 1
                continue
            display = title(brand, name)
            if not clean(display):
                stats['unusable_rows'] += 1
                continue
            choices[(key(brand), key(model))].add((brand, display))
    selected = []
    for (brand_key, model_key), candidates in choices.items():
        if len({key(t) for _, t in candidates}) != 1 or len(official_names[(brand_key, model_key)]) != 1:
            stats['ambiguous_pairs'] += 1
            continue
        brand, display = sorted(candidates)[0]
        selected.append((brand_key, model_key, brand, display))
    selected.sort(key=lambda r: (r[0] + '\0' + r[1]).encode('utf-16-be'))
    assert 30000 < len(selected) < 100000, 'Review a material coverage change'
    output = '# Network Lens Android model catalogue v1\n' + ''.join('\t'.join(r) + '\n' for r in selected)
    encoded = output.encode('utf-8')
    stats.update(qualified_model_pairs=len(selected), brands=len({r[0] for r in selected}), retail_titles=len({(r[0],key(r[3])) for r in selected}))
    metadata = dict(repository='androidtrackers/certified-android-devices', revision=REVISION,
        source_url=f'https://github.com/androidtrackers/certified-android-devices/blob/{REVISION}/by_model.json',
        source_sha256=sha(data), source_git_blob=blob(data), license='MIT', license_git_blob=blob(license_data),
        primary_url='https://storage.googleapis.com/play_public/supported_devices.csv',
        primary_documentation='https://support.google.com/googleplay/answer/1727131', accessed='2026-11-14',
        primary_original_sha256=PRIMARY_SHA, primary_utf8_sha256=sha(primary),
        selection='Exact reported manufacturer + Model only; no Device codenames, names, MAC vendors or substring matching. Cross-check all source tuples against primary CSV; reject generic/oversized fields and multiple retail names for a brand/model pair. No form factor is inferred from this list.',
        counts=dict(stats), generated_sha256=sha(encoded), generated_bytes=len(encoded))
    return encoded, (json.dumps(metadata,indent=2,ensure_ascii=False)+'\n').encode(), stats

def main():
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('--check',action='store_true'); args=parser.parse_args()
    encoded, metadata, stats = generate()
    if args.check:
        assert DEST.read_bytes() == encoded and META.read_bytes() == metadata, 'Regenerate and review Android catalogue outputs'
    else:
        DEST.parent.mkdir(parents=True, exist_ok=True)
        DEST.write_bytes(encoded); META.write_bytes(metadata)
    print(f"Verified {stats['qualified_model_pairs']:,} manufacturer-qualified Android model pairs / {stats['retail_titles']:,} retail titles across {stats['brands']:,} brands.")
if __name__ == '__main__':
    main()
