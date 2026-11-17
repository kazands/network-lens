#!/usr/bin/env python3
"""Project pinned Home Assistant discovery facts; never execute upstream Python."""
import argparse,ast,hashlib,json,fnmatch
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
SRC=ROOT/'android/ha-original'
OUT=ROOT/'android/app/src/main/java/io/github/kazands/networklens/HaMatchers.kt'
PINS={'zeroconf.py':'77c20770ebc01604354b3f1d14749b5319b8f40b','ssdp.py':'ddcb214885d367bc11b621789acc0f3708844141','LICENSE':'261eeb9e9f8b2b4b0d119366dda99c6fd7d35c64'}
def quote(s):return json.dumps(s,ensure_ascii=False).replace('$','\\$')
def generate():
 data={};hashes={}
 for file,pin in PINS.items():
  b=(SRC/file).read_bytes();blob=hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
  # Upstream files may omit a final newline; retain exact bytes when refreshing.
  assert blob==pin,(file,blob)
  hashes[file]={'gitBlob':blob,'sha256':hashlib.sha256(b).hexdigest()}
  if file.endswith('.py'):
   for n in ast.parse(b.decode()).body:
    if isinstance(n,ast.Assign):data[n.targets[0].id]=ast.literal_eval(n.value)
 rules=[]
 def add(kind,domain,conditions):
  assert 0<len(conditions)<=8
  rules.append((kind,domain,conditions))
 for typ,entries in sorted(data['ZEROCONF'].items()):
  for row in entries:
   assert set(row)<= {'domain','name','properties'}
   c={'type':typ}
   if 'name' in row:c['name']=row['name']
   c.update({'txt:'+k.lower():v for k,v in row.get('properties',{}).items()})
   add('mdns',row['domain'],c)
 for model,row in sorted(data['HOMEKIT'].items()):add('homekit',row['domain'],{'model':model})
 for domain,entries in sorted(data['SSDP'].items()):
  for row in entries:add('ssdp',domain,{k.lower():v for k,v in row.items()})
 lines=['// Generated from Home Assistant Core (Apache-2.0); do not edit.',
 '// Adaptation: normalized case-insensitive conditions; no DHCP, pairing or control code.',
 '// Retained sources: android/ha-original/; full license: res/raw/ha_matchers_license.txt.',
 'package io.github.kazands.networklens','','internal object HaMatchers {',
 '    val rules = listOf(']
 for kind,domain,c in rules:
  cond=', '.join('DiscoveryMatchers.Condition('+quote(k)+', '+quote(fnmatch.translate(v.lower()).replace('\\Z','\\z'))+')' for k,v in sorted(c.items()))
  lines.append('        DiscoveryMatchers.Rule('+quote(kind)+', '+quote(domain)+', listOf('+cond+')),')
 lines+=['    )','    val types = listOf('+', '.join(quote(x.lower().removesuffix('local.')) for x in sorted(data['ZEROCONF']))+')','}','']
 generated='\n'.join(lines)
 provenance={'source':'https://github.com/home-assistant/core/tree/dev/homeassistant/generated','files':hashes,'policy':'All conditions preserved. Family candidates only; reviewed local policy determines physical categories. HomeKit always_discover is HA routing policy, not an identity condition. No DHCP matchers. No runtime downloads.','counts':{'mdnsTypes':len(data['ZEROCONF']),'rules':len(rules),'ssdpDomains':len(data['SSDP'])},'generatedSha256':hashlib.sha256(generated.encode()).hexdigest()}
 return {OUT:generated,ROOT/'android/ha-matcher-provenance.json':json.dumps(provenance,indent=2)+'\n',ROOT/'android/app/src/main/res/raw/ha_matchers_license.txt':(SRC/'LICENSE').read_text()}
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
 for dest,value in generate().items():
  if a.check:assert dest.read_text()==value,'Stale generated file: '+str(dest)
  else:dest.write_text(value)
 print('PASS: pinned Home Assistant discovery matcher projection')
