#!/usr/bin/env python3
"""Cross-check pinned LineageOS physical types against exact Google model facts."""
import argparse
import csv
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REVISION = '09e2176e2693dca067e9ce803ea1a5eb909806c3'
SOURCE = ROOT / 'android/lineage-original/device-types.json'
DEST = ROOT / 'android/app/src/main/res/raw/android_hardware.tsv'
META = ROOT / 'android/android-hardware-provenance.json'
TYPES = {'phone': 'PHONE', 'phone (slider)': 'PHONE', 'foldable': 'PHONE',
         'tablet': 'TABLET', 'Set top box': 'TV_PLAYER', 'TV': 'TV'}

def digest(data):
    return hashlib.sha256(data).hexdigest()

def key(text):
    return text.strip(' \t').lower()

def generate():
    raw = SOURCE.read_bytes()
    assert digest(raw) == '8964b0e2745e0dd5bfffd2e0b526aec8a9630f633b976b2f156c6688ff7e7338'
    source = json.loads(raw)
    assert source['revision'] == REVISION
    license_data = (ROOT / 'android/lineage-original/CC-BY-SA-3.0.txt').read_bytes()
    assert digest(license_data) == '3f941b3b89cf7b8370ceb83cc76d2120d471b58735d8ca60238a751a48d7f72f'
    assert (ROOT / 'android/app/src/main/res/raw/lineage_categories_notice.txt').read_bytes().endswith(license_data)
    primary = ROOT / 'android/catalog-original/google-supported-devices.csv'
    assert digest(primary.read_bytes()) == '779c6f66185cedceb4c9aa9e61e1730a5aefa423fb43028f122a8dc0a8ba47cc'
    by_name = defaultdict(set)
    by_model = defaultdict(set)
    for row in csv.DictReader(primary.open()):
        vendor, name, model = (key(row[f]) for f in ('Retail Branding', 'Marketing Name', 'Model'))
        by_name[vendor, name].add(model)
        by_model[vendor, model].add(name)
    # The naming generator has already rejected generic, ambiguous or oversized codes.
    naming = ROOT / 'android/catalog-generated/android-models.tsv'
    naming_meta = json.loads((ROOT / 'android/android-catalog-provenance.json').read_text())
    assert digest(naming.read_bytes()) == naming_meta['generated_sha256']
    eligible = {tuple(line.split('\t')[:2]) for line in naming.read_text().splitlines()[1:]}
    choices = defaultdict(set)
    origins = defaultdict(set)
    for row in source['records']:
        category = TYPES.get(row['type'])
        if category is None:
            continue
        vendor = key(row['vendor'])
        # Explicit model lists take precedence. Never promote a device codename or
        # a partial name to a runtime identifier. Name joins are exact and offline.
        models = {key(m) for m in row['models']} if row['models'] else by_name[vendor, key(row['name'])]
        for model in models:
            pair = vendor, model
            if pair not in eligible or len(by_model[pair]) != 1:
                continue
            choices[pair].add(category)
            origins[pair].add(row['path'].removesuffix('.yml'))
    records = []
    for pair, categories in sorted(choices.items()):
        if len(categories) != 1:
            continue
        reference = 'LineageOS@' + REVISION + '/' + sorted(origins[pair])[0]
        records.append((*pair, next(iter(categories)), reference))
    assert 500 < len(records) < 2000, 'Review material category coverage changes'
    data = ('# Network Lens Android hardware categories v1\n' + ''.join('\t'.join(r) + '\n' for r in records)).encode()
    meta = dict(revision=REVISION, source='https://github.com/LineageOS/lineage_wiki/tree/' + REVISION + '/_data/devices',
        attribution='LineageOS Wiki contributors', license='CC-BY-SA-3.0', accessed='2026-11-16',
        source_projection_sha256=digest(raw), generated_sha256=digest(data),
        selection='Exact manufacturer/model pairs in the existing Google-cross-checked naming table. Use explicit upstream models, otherwise join exact manufacturer + marketing name offline. Exclude conflicting categories and unsupported types. No runtime name, codename, OUI, prefix or substring inference.',
        counts=dict(qualified_pairs=len(records), brands=len({r[0] for r in records}), categories=dict(Counter(r[2] for r in records))))
    return data, (json.dumps(meta, indent=2) + '\n').encode(), meta['counts']

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    data, meta, counts = generate()
    if args.check:
        assert DEST.read_bytes() == data and META.read_bytes() == meta, 'Regenerate and review hardware categories'
    else:
        DEST.write_bytes(data); META.write_bytes(meta)
    print('Verified Android hardware categories:', counts)

if __name__ == '__main__':
    main()
