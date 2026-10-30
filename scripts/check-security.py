#!/usr/bin/env python3
"""Check specific packaged Android protections; not a general vulnerability scanner."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import zipfile

def require(condition, message):
    if not condition:
        raise SystemExit('FAIL: ' + message)

def tree(output):
    stack = []
    root = None
    for line in output.splitlines():
        element = re.match(r'^(\s*)E: ([\w-]+)', line)
        if element:
            depth = len(element[1])
            node = {'name': element[2], 'attributes': {}, 'children': []}
            while stack and stack[-1][0] >= depth:
                stack.pop()
            if stack:
                stack[-1][1]['children'].append(node)
            else:
                require(root is None, 'Unexpected multiple XML roots')
                root = node
            stack.append((depth, node))
        attribute = re.match(r'^\s*A: ([\w:]+)(?:\(0x[0-9a-f]+\))?=(.*)$', line)
        if attribute:
            require(bool(stack), 'XML attribute outside an element')
            raw = attribute[2]
            if raw.startswith('"'):
                value, _ = json.JSONDecoder().raw_decode(raw)
            elif raw.startswith('(type '):
                match = re.fullmatch(r'\(type 0x[0-9a-f]+\)0x([0-9a-f]+)', raw)
                require(match is not None, 'Unrecognized typed XML value')
                value = int(match[1], 16)
            else:
                value = raw
            stack[-1][1]['attributes'][attribute[1]] = value
    require(root is not None, 'Could not parse packaged XML')
    return root

def child(node, name):
    values = [n for n in node['children'] if n['name'] == name]
    require(len(values) == 1, 'Expected exactly one ' + name)
    return values[0]

def dex_classes(data):
    require(data.startswith(b'dex\n') and len(data) >= 112, 'Invalid DEX file')
    def uint(offset):
        return struct.unpack_from('<I', data, offset)[0]
    string_count, string_offset = uint(56), uint(60)
    type_count, type_offset = uint(64), uint(68)
    class_count, class_offset = uint(96), uint(100)
    def string(index):
        require(index < string_count, 'Invalid DEX string index')
        offset = uint(string_offset + index * 4)
        while data[offset] & 0x80:
            offset += 1
        offset += 1
        return data[offset:data.index(0, offset)].decode('utf-8')
    names = []
    for i in range(class_count):
        type_index = uint(class_offset + i * 32)
        require(type_index < type_count, 'Invalid DEX type index')
        names.append(string(uint(type_offset + type_index * 4)))
    return names

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk')
    parser.add_argument('--aapt', required=True)
    parser.add_argument('--output')
    args = parser.parse_args()
    apk = Path(args.apk)
    def dump(*arguments):
        return subprocess.check_output([args.aapt, 'dump', *arguments], text=True)
    manifest = tree(dump('xmltree', str(apk), 'AndroidManifest.xml'))
    require(manifest['attributes'].get('package') == 'io.github.kazands.networklens', 'Unexpected application package')
    app = child(manifest, 'application')
    for flag in ('debuggable', 'allowBackup', 'fullBackupContent', 'usesCleartextTraffic'):
        require(app['attributes'].get('android:' + flag) == 0, flag + ' must be explicitly false')
    components = [n for n in app['children'] if n['name'] in ('activity', 'activity-alias', 'service', 'provider', 'receiver')]
    require(len(components) == 1, 'Only the launcher activity is allowed; background components were removed')
    activity = child(app, 'activity')
    require(activity['attributes'].get('android:name') == '.MainActivity' and activity['attributes'].get('android:exported') == 0xffffffff, 'Unexpected activity exposure')
    intent = child(activity, 'intent-filter')
    require(child(intent, 'action')['attributes'].get('android:name') == 'android.intent.action.MAIN', 'Unexpected external action')
    require(child(intent, 'category')['attributes'].get('android:name') == 'android.intent.category.LAUNCHER' and len(intent['children']) == 2, 'Unexpected external intent filter')
    resource = app['attributes'].get('android:dataExtractionRules', '')
    require(isinstance(resource, str) and re.fullmatch(r'@0x[0-9a-f]+', resource), 'Missing extraction rules reference')
    resources = dump('resources', str(apk))
    require(re.search(r'resource ' + re.escape(resource[1:]) + r' io\.github\.kazands\.networklens:xml/data_extraction_rules\b', resources) is not None, 'Extraction rules must reference the audited resource')
    rules = tree(dump('xmltree', str(apk), 'res/xml/data_extraction_rules.xml'))
    domains = {'root', 'file', 'database', 'sharedpref', 'external', 'device_root', 'device_file', 'device_database', 'device_sharedpref'}
    require(rules['name'] == 'data-extraction-rules', 'Unexpected extraction rules root')
    for mode in ('cloud-backup', 'device-transfer'):
        exclusions = child(rules, mode)['children']
        require(len(exclusions) == len(domains) and all(n['name'] == 'exclude' and n['attributes'].get('path') == '.' for n in exclusions), 'Complete exclusions required for ' + mode)
        require({n['attributes'].get('domain') for n in exclusions} == domains, 'All storage domains must be excluded from ' + mode)
    with zipfile.ZipFile(apk) as archive:
        entries = archive.namelist()
        require(not any(n.lower().endswith(('.so', '.jks', '.keystore', '.p12', '.key', '.pem')) for n in entries), 'Unexpected native library or key material in APK')
        names = []
        for name in entries:
            if re.fullmatch(r'classes\d*\.dex', name):
                names.extend(dex_classes(archive.read(name)))
        provenance = json.loads((Path(__file__).resolve().parent.parent / 'android/kotlin-runtime-provenance.json').read_text())
        runtime = set(provenance['classes'])
        require(hashlib.sha256(archive.read(provenance['licenseResource'])).hexdigest() == provenance['licenseSha256'], 'Kotlin runtime license/notices must match the reviewed resource')
        def approved(name):
            if name.startswith('Lio/github/kazands/networklens/') or name in runtime:
                return True
            parent = name.split('$$ExternalSynthetic', 1)[0] + ';'
            return '$$ExternalSynthetic' in name and parent in runtime
        require(bool(names) and all(approved(n) for n in names), 'Unreviewed application/runtime class')
        require(not any(n in names for n in ('Lio/github/kazands/networklens/MonitorJob;', 'Lio/github/kazands/networklens/Diagnostics;')), 'Removed monitoring/tool classes must not ship')
    report = {'status': 'PASS', 'apk': apk.name, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
              'scope': 'Packaged component exposure, explicit debug/backup/cleartext flags, cloud/transfer exclusions, class inventory and common private-key/native-library file extensions. Not a vulnerability certification.',
              'application_class_count': sum(n.startswith('Lio/github/kazands/networklens/') for n in names), 'third_party_class_namespaces': ['kotlin (pinned official standard library)'],
              'kotlin_stdlib_sha256': provenance['sha256'], 'kotlin_stdlib_version': provenance['version'], 'total_class_count': len(names),
              'kotlin_notices_verified': True,
              'exported_components': ['io.github.kazands.networklens.MainActivity (launcher)'],
              'private_components': [],
              'debuggable': False, 'allow_backup': False, 'full_backup_content': False,
              'cleartext_http': False, 'cloud_backup_and_device_transfer_exclusions': sorted(domains)}
    serialized = json.dumps(report, indent=2) + '\n'
    if args.output:
        Path(args.output).write_text(serialized)
    print(serialized, end='')

if __name__ == '__main__':
    main()
