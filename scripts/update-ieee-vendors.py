#!/usr/bin/env python3
"""Build an offline prefix/organization index from IEEE's four public MAC registries.

Run explicitly when updating a release. The Android app never downloads this data.
"""
from pathlib import Path
import argparse,csv,datetime,hashlib,io,json,re,struct,subprocess,unicodedata

root=Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--cache',default='.tools/ieee-vendors')
parser.add_argument('--refresh',action='store_true')
args=parser.parse_args();cache=root/args.cache;cache.mkdir(parents=True,exist_ok=True)
registries=[('MA-L',6,'oui/oui.csv'),('MA-M',7,'oui28/mam.csv'),('IAB',9,'iab/iab.csv'),('MA-S',9,'oui36/oui36.csv')]
entries={};sources=[];date=datetime.datetime.now(datetime.timezone.utc)
for registry,width,path in registries:
    url='https://standards-oui.ieee.org/'+path;local=cache/path.rsplit('/',1)[1]
    if args.refresh or not local.exists():
        temporary=local.with_suffix('.download')
        subprocess.run(['curl','--fail','--location','--proto','=https','--proto-redir','=https','--max-time','30','--max-filesize',str(12*1024*1024),'--output',str(temporary),url],check=True)
        if temporary.stat().st_size>12*1024*1024:raise ValueError('IEEE download exceeds limit')
        temporary.replace(local)
    data=local.read_bytes();rows=list(csv.DictReader(io.StringIO(data.decode('utf-8-sig'))))
    if not 1000<len(rows)<80000:raise ValueError('Unexpected registry size: '+registry)
    for row in rows:
        prefix=row['Assignment'].strip().upper();name=' '.join(''.join(c for c in row['Organization Name'] if unicodedata.category(c) not in ('Cc','Cf')).split())
        if row['Registry']!=registry or not re.fullmatch('[0-9A-F]{'+str(width)+'}',prefix):raise ValueError('Invalid IEEE record')
        if not name or len(name)>160 or any(ord(x)<32 or ord(x)==127 for x in name):raise ValueError('Invalid organization name')
        # MA-S is the current registry if a historical IAB entry shares its exact key.
        entries[(int(prefix,16)<<4)|width]=name
    sources.append({'downloadedUTC':datetime.datetime.fromtimestamp(local.stat().st_mtime,datetime.timezone.utc).isoformat(),'registry':registry,'url':url,'sha256':hashlib.sha256(data).hexdigest(),'rows':len(rows)})
names=sorted(set(entries.values()));ids={name:n for n,name in enumerate(names)}
snapshot=min(datetime.datetime.fromisoformat(x['downloadedUTC']) for x in sources)
asset=bytearray(struct.pack('>Iqii',0x444c5631,int(snapshot.timestamp()*1000),len(names),len(entries)))
for name in names:
    data=name.encode('utf-8');assert len(data)<=640;asset+=struct.pack('>H',len(data))+data
for key,name in sorted(entries.items()):asset+=struct.pack('>qi',key,ids[name])
output=root/'android/app/src/main/res/raw/ieee_mac_vendors.bin';output.write_bytes(asset)
record={'source':'IEEE Registration Authority public listings','compiledUTC':date.isoformat(),'retrievedUTC':snapshot.isoformat(),'registries':sources,'organizations':len(names),'prefixes':len(entries),'format':'DLV1; sorted prefix keys; 24/28/36-bit longest-prefix lookup','asset':str(output.relative_to(root)),'assetBytes':len(asset),'assetSha256':hashlib.sha256(asset).hexdigest(),'fieldsKept':['Assignment','Organization Name'],'privateOrLocalMacLookup':False}
(root/'android/ieee-vendor-provenance.json').write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps({'prefixes':len(entries),'organizations':len(names),'bytes':len(asset),'assetSha256':record['assetSha256']}))
