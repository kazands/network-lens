#!/usr/bin/env python3
"""Merge pinned public model facts; preserve conflicts instead of guessing a winner."""
import argparse
import gzip
import hashlib
import importlib.util
import json
import re
from collections import defaultdict,Counter
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('android_models',ROOT/'scripts/generate-android-models.py')
android=importlib.util.module_from_spec(spec); spec.loader.exec_module(android)
SNAPSHOT=ROOT/'android/catalog-original/nist-hardware.json.gz'
NIST=ROOT/'android/nist-hardware-provenance.json'
DEST=ROOT/'android/app/src/main/res/raw/product_models.gz'
PROVENANCE=ROOT/'android/product-catalog-provenance.json'
GENERIC=re.compile(r'unknown|none|null|n/?a|generic(?: device)?|device|tv|smart tv|tablet|smartphone|phone|router|printer|camera|server|switch|default|all|[0-9]{1,3}',re.I)
COMPONENT=re.compile(r'\b(?:cpu|processor|processors|chipset|gpu|ethernet controller|wireless adapter|network adapter|network interface card|microcontroller|modem chip)\b',re.I)
BRANDS={'dlink':'D-Link','tp-link':'TP-Link','hp':'HP','hpe':'HPE','qnap':'QNAP','lg':'LG','asus':'ASUS','ibm':'IBM','nvidia':'NVIDIA','western_digital':'Western Digital'}

def parts(value):
    fields=[];out='';escaped=False
    for c in value:
        if escaped: out+=c;escaped=False
        elif c=='\\': escaped=True
        elif c==':': fields.append(out);out=''
        else: out+=c
    if escaped: return []
    return fields+[out]

def normalize_title(value):
    return ' '.join(value.split()).strip(' \t')

def key(value):
    return android.key(value)

def generate():
    android_data,android_meta,_=android.generate()
    assert (ROOT/'android/android-catalog-provenance.json').read_bytes()==android_meta
    meta=json.loads(NIST.read_text());raw=SNAPSHOT.read_bytes()
    assert hashlib.sha256(raw).hexdigest()==meta['snapshot_sha256'],'Pinned NIST hardware snapshot changed'
    rows=json.loads(gzip.decompress(raw));assert len(rows)==meta['rows']==64985
    choices=defaultdict(set); stats=Counter(); candidate_source=defaultdict(set)
    # A second source cannot erase ambiguity already present in Google's primary list.
    import csv,io
    primary_names=defaultdict(set)
    for r in csv.DictReader(io.StringIO(android.PRIMARY.read_text())):
        bk,mk=key(r['Retail Branding']),key(r['Model'])
        primary_names[(bk,mk)].add(key(android.title(r['Retail Branding'].strip(),r['Marketing Name'].strip())))
    blocked={pair for pair,names in primary_names.items() if len(names)>1}
    blocked|={(bk,bk+' '+mk) for bk,mk in list(blocked) if not mk.startswith(bk+' ')}
    for line in android_data.decode().splitlines()[1:]:
        bk,mk,brand,title=line.split('\t')
        for alias in {mk, mk if mk.startswith(bk+' ') else bk+' '+mk}:
            if android.clean(alias):
                choices[(bk,alias)].add((brand,title,'androidtrackers@'+android.REVISION));candidate_source[(bk,alias)].add('Android')
    for cpe,identifier,titles,deprecated in rows:
        stats['nist_entries']+=1
        p=parts(cpe)
        if len(p)!=13 or p[:3]!=['cpe','2.3','h'] or deprecated or len(titles)!=1:
            stats['nist_ineligible_entries']+=1;continue
        vendor,product=p[3],p[4]
        title=normalize_title(titles[0])
        if vendor in {'intel','amd','arm','qualcomm','mediatek','micron','skhynix'} and not re.search(r'\b(?:NUC|Compute Stick|development kit|server|appliance)\b',title,re.I) or COMPONENT.search(title) or re.match(r'Apple (?:A|M)\d+(?:\b| )',title):
            stats['component_entries_excluded']+=1;continue
        brand=BRANDS.get(vendor)
        if brand is None:
            plain=vendor.replace('_',' ')
            prefix=title[:len(plain)]
            brand=prefix if key(prefix)==key(plain) else plain.title()
        if not all(android.clean(v) for v in (brand,title,product)):
            stats['nist_unusable_entries']+=1;continue
        bk=key(brand)
        base=normalize_title(product.replace('_',' '))
        # CPE version/SKU prose must not add precision to the product token.
        named=re.search(r'(?<![A-Za-z0-9])'+re.escape(base)+r'(?![A-Za-z0-9])',title,re.I)
        family=brand+' '+(named.group(0) if named else base)
        if not android.clean(family):
            stats['nist_unusable_entries']+=1;continue
        aliases={product,base}
        # A full, explicitly named model may include a revision absent from the CPE product token.
        if key(title).startswith(bk+' '): aliases.add(title[len(brand)+1:])
        aliases|={brand+' '+a for a in list(aliases) if not key(a).startswith(bk+' ')}
        for model in aliases:
            model=normalize_title(model)
            if not android.clean(model) or GENERIC.fullmatch(model):continue
            mk=key(model)
            choices[(bk,mk)].add((brand,family,'NIST-CPE@2026-11-14'))
            candidate_source[(bk,mk)].add('NIST')
    selected=[]; sources=Counter();by_brand=Counter();conflicts=[]
    for (bk,mk),entries in choices.items():
        if (bk,mk) in blocked or len({key(t) for _,t,_ in entries})!=1:
            stats['ambiguous_aliases_excluded']+=1
            if len(candidate_source[(bk,mk)])>1:stats['cross_source_conflicts']+=1
            continue
        brand,title,source=sorted(entries)[0]
        # Preserve provenance of both sources when they independently agree.
        origins=sorted({s for _,_,s in entries});source=';'.join(origins)
        if not all(android.clean(v) for v in (bk,mk,brand,title,source)):continue
        selected.append((bk,mk,brand,title,source));by_brand[bk]+=1
        sources.update(candidate_source[(bk,mk)])
    selected.sort(key=lambda r:(r[0]+'\0'+r[1]).encode('utf-16-be'))
    output=('# Network Lens product model catalogue v1\n'+''.join('\t'.join(r)+'\n' for r in selected)).encode()
    assert 50000<len(selected)<300000 and len(output)<32*1024*1024
    stats.update(qualified_model_aliases=len(selected),product_titles=len({(r[0],key(r[3])) for r in selected}),brands=len(by_brand))
    packed=gzip.compress(output,mtime=0)
    # Fixed gzip OS marker keeps output stable across Python/platform revisions.
    packed=packed[:9]+bytes([255])+packed[10:]
    metadata=dict(schema=1,generated_sha256=hashlib.sha256(packed).hexdigest(),generated_bytes=len(packed),uncompressed_sha256=hashlib.sha256(output).hexdigest(),uncompressed_bytes=len(output),sources=['android/android-catalog-provenance.json','android/nist-hardware-provenance.json'],counts=dict(stats),alias_source_counts=dict(sources),largest_brand_groups=dict(by_brand.most_common(20)),selection='Exact reported manufacturer and model only. No names, MAC prefixes, Device codenames or fuzzy matching. Redundant brand prefixes are explicit generated aliases and pass the same ambiguity checks. Reject ambiguous aliases, including cross-source disagreement. NIST matches are product-family interpretations; no form factor, edition, SKU, hardware revision or vulnerability claim is inferred from a CPE name.',nist_component_filter='Exclude explicit CPU/processor/chip/controller/adapter records and component-focused vendors unless a named NUC, server, appliance or development kit. Hardware part h only, nondeprecated, one English title. CPE product spelling and underscore-to-space equivalents are exact aliases; an explicit full title without its brand is also accepted. Canonical NIST titles contain the brand and product token, using English-title case only where the same token appears exactly. Extra SKU/version prose is discarded; unmatched tokens retain their literal spelling. Version fields never supply a revision.')
    return packed,(json.dumps(metadata,indent=2,ensure_ascii=False)+'\n').encode(),stats

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');args=p.parse_args()
    data,metadata,stats=generate()
    if args.check:assert DEST.read_bytes()==data and PROVENANCE.read_bytes()==metadata,'Regenerate and review global product outputs'
    else:DEST.write_bytes(data);PROVENANCE.write_bytes(metadata)
    print(f"Verified {stats['qualified_model_aliases']:,} manufacturer-qualified aliases / {stats['product_titles']:,} product titles across {stats['brands']:,} brands.")
if __name__=='__main__':main()
