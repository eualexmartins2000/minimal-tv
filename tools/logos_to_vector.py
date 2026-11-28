"""Converte os logos (versão monocromática) do protótipo em VectorDrawables do Android.
Uso: python3 tools/logos_to_vector.py   (lê prototype/launcher.html, grava app/src/main/res/drawable/logo_*.xml)
Os logos são embutidos no app em tempo de build; a TV nunca busca nada na internet."""
import json, re, os
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = open(os.path.join(ROOT, 'prototype/launcher.html'), encoding='utf-8').read()
i = src.index('var LOGOS = ') + 12
LOGOS, _ = json.JSONDecoder().raw_decode(src[i:])
# nome do arquivo -> (logo, campo)
OUT = {
  'netflix': ('netflix', 'mono'), 'netflix_w': ('netflix', 'bmono'),
  'prime': ('prime', 'mono'), 'prime_w': ('prime', 'bmono'),
  'youtube': ('youtube', 'mono'),
  'play': ('play', 'mono'),
  'cdntv': ('cdntv', 'mono'), 'cdntv_w': ('cdntv', 'bmono'),
  'megafute': ('megafute', 'mono'), 'megafute_w': ('megafute', 'bmono'),
  'downloader': ('downloader', 'mono'),
  'globoplay': ('globoplay', 'mono'), 'globoplay_w': ('globoplay', 'bmono'),
  'alexa': ('alexa', 'mono'),
}
def num(x): return ('%g' % float(x))
for name, (k, f) in OUT.items():
    svg = LOGOS[k].get(f) or LOGOS[k]['mono']
    vb = [float(v) for v in re.search(r'viewBox="([^"]+)"', svg).group(1).split()]
    root_evenodd = 'evenodd' in re.search(r'<svg[^>]*>', svg).group(0)
    paths = re.findall(r'<path\b([^>]*?)/?>', svg)
    body = ''
    for attrs in paths:
        d = re.search(r'\bd="([^"]+)"', attrs).group(1)
        eo = root_evenodd or 'evenodd' in attrs
        body += '        <path android:fillColor="#FFFFFF"%s android:pathData="%s" />\n' % (' android:fillType="evenOdd"' if eo else '', d)
    # youtube: matrix(1.333,0,0,-1.333,0,165.33) + scale(.1) => escala e inversão vertical
    if 'matrix(1.3333333,0,0,-1.3333333,0,165.33333)' in svg:
        body = '        <group android:scaleX="0.13333333" android:scaleY="-0.13333333" android:translateY="165.33333">\n' + body + '        </group>\n'
    # viewBox com origem != 0: desloca
    if vb[0] or vb[1]:
        body = '        <group android:translateX="%s" android:translateY="%s">\n%s        </group>\n' % (num(-vb[0]), num(-vb[1]), body)
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="%sdp" android:height="%sdp"\n'
           '    android:viewportWidth="%s" android:viewportHeight="%s">\n%s</vector>\n') % (num(vb[2]), num(vb[3]), num(vb[2]), num(vb[3]), body)
    open(os.path.join(ROOT, 'app/src/main/res/drawable/logo_%s.xml' % name), 'w', encoding='utf-8').write(xml)
    print(name, len(xml))
