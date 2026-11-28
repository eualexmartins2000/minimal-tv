"""Converte os logos (versão monocromática) do protótipo em VectorDrawables do Android.
Uso: python3 tools/logos_to_vector.py   (lê prototype/launcher.html, grava app/src/main/res/drawable/logo_*.xml)
Os logos são embutidos no app em tempo de build; a TV nunca busca nada na internet."""
import json, re, os, html
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
  'downloader': ('downloader', 'mono'), 'downloader_w': ('downloader', 'bmono'),
  'globoplay': ('globoplay', 'mono'), 'globoplay_w': ('globoplay', 'bmono'),
  'alexa': ('alexa', 'mono'),
}
def num(x): return ('%g' % float(x))

TOK = re.compile(r'([MmLlHhVvCcSsQqTtAaZz])|(-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?)')
ARGS = {'M':2,'L':2,'H':1,'V':1,'C':6,'S':4,'Q':4,'T':2,'A':7,'Z':0}
def bake(d, ts):
    """Path SVG (relativo ou absoluto) -> só comandos absolutos M/L/C/Q/Z, com a transformação ts=(a,d,e,f) aplicada
    (x' = a*x + e, y' = d*y + f). Deixa o resultado simples e igual em qualquer renderizador (Android incluso)."""
    a, dd, e, f = ts
    toks = [(m.group(1), m.group(2)) for m in TOK.finditer(d)]
    out = []; i = 0; cmd = None; x = y = sx = sy = 0.0; cx = cy = None
    def P(px, py): return '%s,%s' % (num(round(a*px+e, 2)), num(round(dd*py+f, 2)))
    while i < len(toks):
        if toks[i][0]: cmd = toks[i][0]; i += 1
        c = cmd.upper(); rel = cmd.islower()
        if c == 'Z':
            out.append('Z'); x, y = sx, sy; cx = cy = None; continue
        n = ARGS[c]
        v = [float(toks[i+k][1]) for k in range(n)]; i += n
        if c == 'A': raise ValueError('arco nao suportado')
        if c == 'M':
            nx, ny = (x+v[0], y+v[1]) if rel else (v[0], v[1])
            out.append('M'+P(nx, ny)); x, y = nx, ny; sx, sy = x, y; cmd = 'L' if not rel else 'l'; cx = cy = None
        elif c == 'L':
            nx, ny = (x+v[0], y+v[1]) if rel else (v[0], v[1]); out.append('L'+P(nx, ny)); x, y = nx, ny; cx = cy = None
        elif c == 'H':
            x = x+v[0] if rel else v[0]; out.append('L'+P(x, y)); cx = cy = None
        elif c == 'V':
            y = y+v[0] if rel else v[0]; out.append('L'+P(x, y)); cx = cy = None
        elif c == 'C':
            p = [(x+v[k], y+v[k+1]) if rel else (v[k], v[k+1]) for k in (0, 2, 4)]
            out.append('C'+' '.join(P(*q) for q in p)); x, y = p[2]; cx, cy = p[1]
        elif c == 'S':
            c1 = (2*x-cx, 2*y-cy) if cx is not None else (x, y)
            p = [(x+v[k], y+v[k+1]) if rel else (v[k], v[k+1]) for k in (0, 2)]
            out.append('C'+' '.join(P(*q) for q in [c1]+p)); x, y = p[1]; cx, cy = p[0]
        elif c == 'Q':
            p = [(x+v[k], y+v[k+1]) if rel else (v[k], v[k+1]) for k in (0, 2)]
            out.append('Q'+' '.join(P(*q) for q in p)); x, y = p[1]; cx, cy = p[0]
        elif c == 'T':
            raise ValueError('T nao suportado')
    return ''.join(out)
def transform_of(attrs):
    """translate(tx ty) scale(s) / matrix(a b c d e f) (sem rotação) -> (a, d, e, f) """
    m = re.search(r'transform="([^"]*)"', attrs)
    ts = (1.0, 1.0, 0.0, 0.0)
    if not m: return ts
    # aplica da direita pra esquerda no ponto: compõe na ordem em que aparecem (SVG)
    a, dd, e, f = ts
    for name, args in re.findall(r'(\w+)\(([^)]*)\)', m.group(1)):
        v = [float(t) for t in re.split(r'[ ,]+', args.strip()) if t]
        if name == 'translate': t = (1, 1, v[0], v[1] if len(v) > 1 else 0)
        elif name == 'scale': t = (v[0], v[1] if len(v) > 1 else v[0], 0, 0)
        elif name == 'matrix':
            assert abs(v[1]) < 1e-9 and abs(v[2]) < 1e-9; t = (v[0], v[3], v[4], v[5])
        else: raise ValueError(name)
        # (a,d,e,f) atual aplicado depois de t: x' = a*(t.a*x + t.e) + e
        a, dd, e, f = a*t[0], dd*t[1], a*t[2]+e, dd*t[3]+f
    return (a, dd, e, f)
for name, (k, f) in OUT.items():
    svg = LOGOS[k].get(f) or LOGOS[k]['mono']
    vb = [float(v) for v in re.search(r'viewBox="([^"]+)"', svg).group(1).split()]
    root_evenodd = 'evenodd' in re.search(r'<svg[^>]*>', svg).group(0)
    # transformações dos <g> ancestrais (só youtube usa) + a do próprio path
    gts = (1.0, 1.0, 0.0, 0.0)
    for g in re.findall(r'<g\b([^>]*)>', svg):
        gt = transform_of(g)
        gts = (gts[0]*gt[0], gts[1]*gt[1], gts[0]*gt[2]+gts[2], gts[1]*gt[3]+gts[3])
    body = ''
    for attrs in re.findall(r'<path\b([^>]*?)/?>', svg):
        d = html.unescape(re.search(r'\bd="([^"]+)"', attrs).group(1))
        pt = transform_of(attrs)
        t = (gts[0]*pt[0], gts[1]*pt[1], gts[0]*pt[2]+gts[2], gts[1]*pt[3]+gts[3])
        # origem do viewBox vira (0,0)
        t = (t[0], t[1], t[2]-vb[0], t[3]-vb[1])
        eo = root_evenodd or 'evenodd' in attrs
        body += '    <path android:fillColor="#FFFFFF"%s android:pathData="%s" />\n' % (' android:fillType="evenOdd"' if eo else '', bake(d, t))
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="%sdp" android:height="%sdp"\n'
           '    android:viewportWidth="%s" android:viewportHeight="%s">\n%s</vector>\n') % (num(vb[2]), num(vb[3]), num(vb[2]), num(vb[3]), body)
    open(os.path.join(ROOT, 'app/src/main/res/drawable/logo_%s.xml' % name), 'w', encoding='utf-8').write(xml)
    print(name, len(xml))
