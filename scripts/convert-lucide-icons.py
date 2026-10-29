#!/usr/bin/env python3
"""Convert the vendored Lucide geometry to native Android stroked vectors."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
ICONS = {'router': 'router', 'smartphone': 'smartphone', 'printer': 'printer',
         'cast': 'cast', 'speaker': 'speaker', 'folder-symlink': 'file_share', 'network': 'network', 'cctv': 'cctv',
         'lightbulb': 'lightbulb', 'lock-keyhole': 'lock', 'plug': 'plug',
         'toggle-left': 'switch', 'thermometer': 'thermostat', 'activity': 'sensor',
         'bell-ring': 'doorbell', 'fan': 'fan'}
ANDROID = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', ANDROID)


def number(value):
    return format(float(value), '.12g')


def geometry(node):
    tag = node.tag.rsplit('}', 1)[-1]
    a = node.attrib
    if tag == 'path':
        return a['d']
    if tag == 'line':
        return f"M{a['x1']},{a['y1']} L{a['x2']},{a['y2']}"
    if tag == 'circle':
        x, y, r = (float(a[key]) for key in ('cx', 'cy', 'r'))
        return f'M{number(x-r)},{number(y)} A{number(r)},{number(r)} 0 1 0 {number(x+r)},{number(y)} A{number(r)},{number(r)} 0 1 0 {number(x-r)},{number(y)} Z'
    if tag == 'rect':
        x, y = float(a.get('x', 0)), float(a.get('y', 0))
        w, h = float(a['width']), float(a['height'])
        rx = min(float(a.get('rx', a.get('ry', 0))), w/2)
        ry = min(float(a.get('ry', a.get('rx', 0))), h/2)
        if rx == 0 or ry == 0:
            return f'M{number(x)},{number(y)} h{number(w)} v{number(h)} h-{number(w)} Z'
        n = number
        return (f'M{n(x+rx)},{n(y)} H{n(x+w-rx)} A{n(rx)},{n(ry)} 0 0 1 {n(x+w)},{n(y+ry)} '
                f'V{n(y+h-ry)} A{n(rx)},{n(ry)} 0 0 1 {n(x+w-rx)},{n(y+h)} '
                f'H{n(x+rx)} A{n(rx)},{n(ry)} 0 0 1 {n(x)},{n(y+h-ry)} '
                f'V{n(y+ry)} A{n(rx)},{n(ry)} 0 0 1 {n(x+rx)},{n(y)} Z')
    raise ValueError('Unsupported SVG geometry: ' + tag)


def main():
    for source, name in ICONS.items():
        svg = ET.parse(ROOT / 'android/lucide-original' / (source + '.svg')).getroot()
        assert svg.attrib['viewBox'] == '0 0 24 24'
        assert svg.attrib['fill'] == 'none' and svg.attrib['stroke-width'] == '2'
        assert svg.attrib['stroke-linecap'] == 'round' and svg.attrib['stroke-linejoin'] == 'round'
        vector = ET.Element('vector', {
            '{'+ANDROID+'}width': '24dp', '{'+ANDROID+'}height': '24dp',
            '{'+ANDROID+'}viewportWidth': '24', '{'+ANDROID+'}viewportHeight': '24'})
        for node in svg:
            ET.SubElement(vector, 'path', {
                '{'+ANDROID+'}fillColor': '#00000000', '{'+ANDROID+'}strokeColor': '#FFFFFFFF',
                '{'+ANDROID+'}strokeWidth': '2', '{'+ANDROID+'}strokeLineCap': 'round',
                '{'+ANDROID+'}strokeLineJoin': 'round', '{'+ANDROID+'}pathData': geometry(node)})
        ET.indent(vector)
        content = '<!-- Lucide: ISC/MIT; see raw/lucide_license.txt. Converted from vendored SVG; geometry retained. -->\n' + ET.tostring(vector, encoding='unicode') + '\n'
        (ROOT / 'android/app/src/main/res/drawable' / ('ic_device_' + name + '.xml')).write_text(content)
    print(f'Converted {len(ICONS)} Lucide icons to native Android vectors.')


if __name__ == '__main__':
    main()
