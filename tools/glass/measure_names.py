"""Contraste de los colores de nombre del chat (Fase 8, paso 5n) sobre la copia borrosa real. Uso, desde la raiz del repo:
  python3 tools/glass/measure_names.py
Mide cada color de nombre sobre la hoja (copia borrosa + tinte 0,70) + el tinte de la burbuja PROPIA y AJENA, en claro y oscuro,
con TODOS los pixeles de home_panel_*.webp. Los tintes y colores estan copiados de ui/theme/Color.kt: si cambian alla, cambian aqui.
No mide la sombra del texto ni lo que hace el telefono.
Tambien da la distancia minima entre pares de colores (CIE76 en Lab): la paleta se eligio con minimo 28 (criterio nuestro, no un estandar)
empujando la luminancia hacia el lado que da mas contraste (claro: Y~0,03; oscuro: Y~0,81) con saturacion maxima; piso de contraste 3,0."""
import numpy as np
from PIL import Image
R = 'app/src/main/res/drawable-nodpi/'
def rgb(h): return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], float)
def lin(c):
    c = c / 255.0; return np.where(c <= 0.03928, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
def lum(c): l = lin(c); return 0.2126 * l[..., 0] + 0.7152 * l[..., 1] + 0.0722 * l[..., 2]
def cr(a, b): hi = np.maximum(a, b); lo = np.minimum(a, b); return (hi + 0.05) / (lo + 0.05)
def over(top, a, below): return top * a + below * (1 - a)
TINT = 0.70
CFG = {
    'CLARO': dict(panel='home_panel_light.webp', tint=0xFFFFFF, mine=(0x0A3D24, 0.18), other=(0xFFFFFF, 0.65),
                  names=[0x432D00, 0x00390A, 0x003640, 0x003161, 0x1C00A6, 0x4F0077, 0x620042, 0x680011]),
    'OSCURO': dict(panel='home_panel_dark.webp', tint=0x0E1511, mine=(0xFFFFFF, 0.12), other=(0xFFFFFF, 0.06),
                   names=[0xFFE5B2, 0xFFE978, 0xFFFF00, 0xADFF0A, 0x94FFA5, 0x6CFFE7, 0xB4F2FF, 0xFFE1E6]),
}
def lab(h):
    c = lin(rgb(h)); M = np.array([[.4124, .3576, .1805], [.2126, .7152, .0722], [.0193, .1192, .9505]])
    x = M @ c / np.array([.95047, 1, 1.08883]); f = np.where(x > 0.008856, np.cbrt(x), 7.787 * x + 16 / 116)
    return np.array([116 * f[1] - 16, 500 * (f[0] - f[1]), 200 * (f[1] - f[2])])
ok = True
floor = True
for mode, c in CFG.items():
    img = np.asarray(Image.open(R + c['panel']).convert('RGB'), float).reshape(-1, 3)
    sheet = over(rgb(c['tint']), TINT, img)
    mine = over(rgb(c['mine'][0]), c['mine'][1], sheet)
    other = over(rgb(c['other'][0]), c['other'][1], sheet)
    print(f"== {mode} ({img.shape[0]} pixeles)")
    for i, n in enumerate(c['names']):
        a, b = cr(lum(mine), lum(rgb(n))), cr(lum(other), lum(rgb(n)))
        good = a.min() >= 4.5 and b.min() >= 4.5
        ok &= good
        floor &= min(a.min(), b.min()) >= 3.0
        print(f"   {i} {n:06X}  propia peor={a.min():5.2f} ({100*(a>=4.5).mean():5.1f}% >=4,5)  ajena peor={b.min():5.2f} ({100*(b>=4.5).mean():5.1f}% >=4,5)  {'OK' if good else 'FALLA en el peor pixel'}")
    d = min(np.linalg.norm(lab(x) - lab(y)) for i, x in enumerate(c['names']) for y in c['names'][i + 1:])
    print(f"   distancia minima entre pares (Lab CIE76): {d:.1f}")
print('TODOS >= 4,5 en el peor pixel' if ok else 'HAY COLORES < 4,5 en el peor pixel (ver %)')
print('piso 3,0 cumplido por todos' if floor else 'ALGUN COLOR BAJA DE 3,0')
