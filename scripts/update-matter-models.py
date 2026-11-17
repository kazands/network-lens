#!/usr/bin/env python3
"""Refresh an allowlisted projection of public DCL facts, or verify it entirely offline."""
import argparse,hashlib,json,urllib.request,urllib.parse
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
SNAP=ROOT/'android/matter-original/models.json'
DEST=ROOT/'android/app/src/main/res/raw/matter_models.tsv'
PROV=ROOT/'android/matter-model-provenance.json'
BASE='https://on.dcl.csa-iot.org'
def clean(v):
 return isinstance(v,str) and 0<len(v)<=160 and v==v.strip() and all(ord(c)>=32 and ord(c)!=127 for c in v)
def refresh():
 height=None; receipts=[]; snapshot=datetime.now(timezone.utc).strftime('%Y%m%d')
 def pages(path,field):
  nonlocal height
  result=[];key='';seen=set()
  for _ in range(100):
   query=urllib.parse.urlencode({'pagination.limit':200,**({'pagination.key':key} if key else {})})
   req=urllib.request.Request(BASE+path+'?'+query,headers={'x-cosmos-block-height':height} if height else {})
   with urllib.request.urlopen(req,timeout=30) as response:
    raw=response.read(4*1024*1024+1);assert len(raw)<=4*1024*1024
    current=response.headers.get('x-cosmos-block-height')
    if current and current.isdecimal():
     if height is None:height=current
     assert height==current,'Inconsistent DCL block height'
   d=json.loads(raw);assert isinstance(d[field],list)
   result.extend(d[field]);assert len(result)<=20000
   receipts.append({'path':path,'page':len(seen)+1,'sha256':hashlib.sha256(raw).hexdigest()})
   key=d.get('pagination',{}).get('next_key') or ''
   if not key:return result
   assert key not in seen;seen.add(key)
  raise ValueError('Pagination bound reached')
 vendors={v['vendorID']:v['vendorName'] for v in pages('/dcl/vendorinfo/vendors','vendorInfo') if clean(v.get('vendorName'))}
 models=pages('/dcl/model/models','model');rows=[];excluded=0
 for m in models:
  vid,pid=m.get('vid'),m.get('pid');name=m.get('productName');part=m.get('partNumber','');dt=m.get('deviceTypeId',0)
  if not (type(vid)==int and type(pid)==int and 0<vid<0xfff0 and 0<=pid<=65535 and vid in vendors and clean(name) and type(dt)==int and 0<=dt<=0xffffffff):excluded+=1;continue
  rows.append({'vid':vid,'pid':pid,'vendor':vendors[vid],'name':name,'part':part if clean(part) and part!='-' else '', 'type':dt})
 rows.sort(key=lambda r:(r['vid'],r['pid']));assert len({(r['vid'],r['pid']) for r in rows})==len(rows)
 SNAP.parent.mkdir(exist_ok=True)
 SNAP.write_text(json.dumps({'source':BASE,'height':height,'snapshot':snapshot,'retrieved':datetime.now(timezone.utc).isoformat(),'rows':rows,'excluded':excluded,'receipts':receipts},ensure_ascii=False,indent=2)+'\n')
def generate():
 raw=SNAP.read_bytes();d=json.loads(raw);lines=['DCL1\t'+d['snapshot']]
 for r in d['rows']:
  lines.append('\t'.join(str(r[k]) for k in ['vid','pid','vendor','name','part','type']))
 data='\n'.join(lines)+'\n'
 p={'source':d['source'],'blockHeight':d['height'],'snapshot':d['snapshot'],'retrieved':d['retrieved'],'models':len(d['rows']),'excluded':d['excluded'],'snapshotSha256':hashlib.sha256(raw).hexdigest(),'generatedSha256':hashlib.sha256(data.encode()).hexdigest(),'policy':'Public vendor/product facts only; no software downloads, certification claims, credentials or runtime network lookups. Test vendor IDs excluded.'}
 return data,json.dumps(p,indent=2)+'\n'
if __name__=='__main__':
 p=argparse.ArgumentParser();g=p.add_mutually_exclusive_group();g.add_argument('--refresh',action='store_true');g.add_argument('--check',action='store_true');a=p.parse_args()
 if a.refresh:refresh()
 data,prov=generate()
 for path,value in [(DEST,data),(PROV,prov)]:
  if a.check:assert path.read_text()==value,'Stale Matter output'
  else:path.write_text(value)
 print('PASS: offline DCL models:',json.loads(prov)['models'])
