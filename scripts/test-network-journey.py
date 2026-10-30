#!/usr/bin/env python3
"""Black-box Wi-Fi loss/recovery journey. Isolated emulators only."""
from pathlib import Path
import argparse,hashlib,json,re,subprocess,time,xml.etree.ElementTree as E
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);p.add_argument('--version',default='0.3.9');p.add_argument('--output',required=True);a=p.parse_args()
assert re.fullmatch(r'emulator-[0-9]+',a.serial),'Physical devices are forbidden'
root=Path(__file__).resolve().parent.parent;adb=root/'.tools/sdk/platform-tools/adb';out=root/a.output;out.mkdir(parents=True,exist_ok=True);checks=[]
def run(*args):return subprocess.check_output([str(adb),'-s',a.serial,*args],timeout=40).decode()
assert run('shell','getprop','ro.kernel.qemu').strip()=='1'
apk=root/('artifacts/Network-Lens-'+a.version+'.apk');digest=hashlib.sha256(apk.read_bytes()).hexdigest()
installed=run('shell','pm','path','dev.netlens').strip();assert installed.startswith('package:/data/app/') and installed.endswith('/base.apk')
assert run('shell','sha256sum',installed[len('package:'):]).split()[0]==digest,'Installed release differs from the APK under test' 
def nodes():
 s=run('shell','uiautomator','dump','/sdcard/lanwhisp-network-e2e.xml');assert 'dumped to' in s
 r=E.fromstring(run('exec-out','cat','/sdcard/lanwhisp-network-e2e.xml'));ns=[n for n in r.iter('node') if n.get('package')=='dev.netlens'];assert ns,'DeviceLens must be visible';return ns
def texts(ns):return [n.get('text') for n in ns if n.get('text')]
def click(label,desc=False):
 ns=nodes();found=[n for n in ns if (n.get('content-desc' if desc else 'text')==label or (not desc and label=='Scan Wi-Fi' and n.get('text','').startswith('Scan Wi-Fi\n')))];assert len(found)==1,label
 x1,y1,x2,y2=map(int,re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',found[0].get('bounds')).groups());run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.2)
def check(ok,label):
 checks.append({'status':'PASS' if ok else 'FAIL','requirement':label});print(checks[-1]['status']+': '+label,flush=True)
def connected():return 'Wifi is connected to ' in run('shell','cmd','wifi','status')
def wifi(enabled):
 run('shell','cmd','wifi','set-wifi-enabled','enabled' if enabled else 'disabled');end=time.monotonic()+35
 while time.monotonic()<end:
  if connected()==enabled:return
  time.sleep(.5)
 raise AssertionError('Virtual Wi-Fi did not change state')
def waitText(fragment,timeout=10):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  if any(fragment in t for t in texts(nodes())):return True
  time.sleep(.3)
 return False
def launch():run('shell','am','force-stop','dev.netlens');run('shell','am','start','-W','-n','dev.netlens/.MainActivity')
try:
 wifi(True);launch();check(not any(t.startswith('Stop scan') for t in texts(nodes())),'Opening on Wi-Fi starts no automatic scan')
 click('Options',True);click('Wi-Fi details');check('Router address' in texts(nodes()),'Connected Wi-Fi details exposes current addresses');click('Done')
 wifi(False);check(waitText('Your Wi-Fi comes first'),'Disconnect updates the visible network state without reopening')
 click('Options',True);click('Wi-Fi details');ts=texts(nodes());check(any('Connect to Wi-Fi' in t for t in ts) and 'Router address' not in ts,'Wi-Fi details never presents disconnected addresses as current');click('Done')
 click('Scan Wi-Fi');check(waitText('Connect to Wi-Fi'),'Scanning without Wi-Fi explains recovery');check(not any(t.startswith('Stop scan') for t in texts(nodes())),'No Wi-Fi never starts a cellular scan')
 wifi(True);check(waitText('Ready when you are'),'Reconnect updates the visible network state without reopening');check(not any(t.startswith('Stop scan') for t in texts(nodes())),'Reconnect starts no automatic scan')
 click('Options',True);click('Wi-Fi details');check('Router address' in texts(nodes()),'Reconnected Wi-Fi details exposes the new connection');click('Done')
 click('Scan Wi-Fi');check(any(t.startswith('Stop scan') for t in texts(nodes())),'A user can start a scan after reconnecting')
 wifi(False);check(waitText('Your Wi-Fi comes first'),'Losing Wi-Fi during a scan clears the old session from the screen');check(not any(t.startswith('Stop scan') for t in texts(nodes())),'Wi-Fi loss cancels the running scan rather than switching interfaces')
 wifi(True);check(waitText('Ready when you are'),'Recovery after an interrupted scan selects the current Wi-Fi session');check(not any(t.startswith('Stop scan') for t in texts(nodes())),'Recovery from an interrupted scan requires an explicit new scan')
 for permission in ['android.permission.ACCESS_FINE_LOCATION','android.permission.ACCESS_COARSE_LOCATION']:run('shell','pm','revoke','dev.netlens',permission)
 launch();wifi(False);check(waitText('Your Wi-Fi comes first'),'Disconnect state updates even when optional SSID access is denied')
 wifi(True);check(waitText('Ready when you are'),'Reconnect state updates even when optional SSID access is denied');check('Scan Wi-Fi' in texts(nodes()),'Denied optional location keeps Scan Wi-Fi available without a fake SSID')
except Exception as error:
 checks.append({'status':'FAIL','requirement':'Journey completes without an automation/runtime error','error':str(error)})
 print('FAIL: '+str(error),flush=True)
finally:
 wifi(True)
 check(hashlib.sha256(apk.read_bytes()).hexdigest()==digest,'Release APK remains unchanged during the journey')
 result={'version':a.version,'apkSha256':digest,'installedHashVerified':True,'androidAPI':int(run('shell','getprop','ro.build.version.sdk')),'status':'PASS' if checks and all(c['status']=='PASS' for c in checks) else 'FAIL','checks':checks,'scope':'Real virtual-Wi-Fi disconnect/reconnect through Android shell; user-visible app controls only'}
 (out/'network-journey.json').write_text(json.dumps(result,indent=2)+'\n')
if result['status']!='PASS':raise SystemExit(1)
