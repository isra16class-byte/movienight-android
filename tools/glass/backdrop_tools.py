#!/usr/bin/env python3
"""Herramientas del fondo de vidrio de las pantallas de entrada (Fase 8, paso 3). Python 3 + Pillow + NumPy.

No forma parte de la app: sirve para repetir el trabajo cuando cambien las imágenes o la paleta (paso 6).

  prepare  Convierte una imagen a WebP (sin ampliarla; si es más ancha que 1080 px la reduce) y crea su copia borrosa
           (1/3 del tamaño, desenfoque gaussiano de radio 6). Salida: auth_bg_<modo>.webp y auth_bg_<modo>_blur.webp.
             python3 backdrop_tools.py prepare light original_dia.jpg app/src/main/res/drawable-nodpi
             python3 backdrop_tools.py prepare dark  original_noche.jpg app/src/main/res/drawable-nodpi

  veil     Calcula el velo en degradado vertical de la hoja de vidrio de pantalla completa: para cada franja del alto
           de la ventana, el alfa mínimo que da texto >= 4,5:1 con la copia borrosa REAL, peor caso entre varios
           tamaños de ventana (más un margen). Imprime las paradas listas para pegar en ui/theme/Color.kt.
             python3 backdrop_tools.py veil app/src/main/res/drawable-nodpi

  measure  Verifica las paradas que están en el código (VEIL_STOPS de abajo, que deben coincidir con Color.kt): contraste
           de cada color de texto y del borde de la barra de campos, fila por fila, con la misma interpolación lineal
           que dibuja la app (net/GlassLogic.kt, `veilAlphaAt`) y la misma cuenta WCAG.
             python3 backdrop_tools.py measure app/src/main/res/drawable-nodpi
"""
import sys

import numpy as np
from PIL import Image, ImageFilter

# Tokens de ui/theme/Color.kt (provisionales). RGB sin alfa.
MODES = {
    "light": dict(
        blur="auth_bg_light_blur.webp", veil=0xF5FBF5, bar_tint=0.25,
        # Texto de GlassColors.content (el de la hoja de vidrio en modo claro), no el de la paleta normal.
        text={"onSurface": 0x0B100C, "onSurfaceVariant": 0x121915, "primary": 0x053A1C, "error": 0x6B0000},
        outline=0x2F3A33,
    ),
    "dark": dict(
        blur="auth_bg_dark_blur.webp", veil=0x0E1511, bar_tint=0.25,
        text={"onSurface": 0xDEE4DE, "onSurfaceVariant": 0xBFC9C1, "primary": 0x79DA9C, "error": 0xFFB4AB},
        outline=0xA6B0A8,
    ),
}
# Alfas del velo en N paradas igualmente espaciadas de arriba (0) a abajo (1). Copiar aquí lo que imprime `veil`.
VEIL_STOPS = {
    "light": [0.31, 0.44, 0.46, 0.46, 0.46, 0.32, 0.37, 0.53, 0.53, 0.52, 0.08, 0.31, 0.43, 0.43, 0.51, 0.55, 0.55, 0.55, 0.52, 0.46, 0.43],
    "dark": [0.29, 0.32, 0.35, 0.39, 0.39, 0.38, 0.31, 0.30, 0.30, 0.32, 0.32, 0.32, 0.32, 0.32, 0.20, 0.21, 0.29, 0.32, 0.32, 0.32, 0.32],
}
# (nombre, ancho dp, alto dp). El texto ocupa la columna de min(480, ancho) - 48 de ancho y puede estar a cualquier altura.
WINDOWS = [("9:16 vertical", 360, 640), ("9:19.5 vertical", 360, 780), ("9:21 vertical", 360, 840),
           ("tablet 5:8", 600, 960), ("horizontal 16:9", 640, 360), ("horizontal 20:9", 800, 360)]
CONTRAST_TEXT = 4.5
CONTRAST_OUTLINE = 3.0
N_STOPS = 21        # paradas k/20
MARGIN = 0.03       # margen sobre el mínimo medido


def prepare(mode, src, outdir):
    im = Image.open(src).convert("RGB")
    if im.width > 1080:
        im = im.resize((1080, round(im.height * 1080 / im.width)), Image.LANCZOS)
    im.save(f"{outdir}/auth_bg_{mode}.webp", "WEBP", quality=82, method=6)
    b = im.resize((max(1, round(im.width / 3)), max(1, round(im.height / 3))), Image.LANCZOS)
    b.filter(ImageFilter.GaussianBlur(radius=6)).save(f"{outdir}/auth_bg_{mode}_blur.webp", "WEBP", quality=80, method=6)
    print(mode, im.size, "-> copia borrosa", b.size)


def _lin(c):
    c = c / 255.0
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def _lum(rgb):
    l = _lin(np.asarray(rgb, dtype=float))
    return 0.2126 * l[..., 0] + 0.7152 * l[..., 1] + 0.0722 * l[..., 2]


def _rgb(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], dtype=float)


def _contrast(l1, l2):
    return (np.maximum(l1, l2) + 0.05) / (np.minimum(l1, l2) + 0.05)


def _rows(img, w, h):
    """Por cada fila de la ventana, los píxeles de la copia borrosa bajo la columna de texto (recorte centrado, como la app)."""
    ih, iw = img.shape[:2]
    s = max(w / iw, h / ih)
    ox, oy = (w - iw * s) / 2, (h - ih * s) / 2
    pw = min(480, w) - 48
    x0, x1 = (w - pw) / 2, (w + pw) / 2
    ix0 = min(max(int(np.floor((x0 - ox) / s)), 0), iw - 1)
    ix1 = min(max(int(np.ceil((x1 - ox) / s)), ix0 + 1), iw)
    return [img[min(max(int((y + 0.5 - oy) / s), 0), ih - 1), ix0:ix1] for y in range(h)]


def _required_alpha(px, veil_rgb, text_lum, target):
    """Alfa mínimo (pasos de 0,005) del velo para que TODOS los píxeles de la fila den contraste >= target."""
    for a in np.arange(0.0, 1.0001, 0.005):
        c = veil_rgb * a + px * (1 - a)
        if _contrast(_lum(c), text_lum).min() >= target:
            return float(a)
    return 1.0


def veil(resdir):
    steps = N_STOPS - 1
    for mode, m in MODES.items():
        img = np.asarray(Image.open(f"{resdir}/{m['blur']}").convert("RGB"), dtype=float)
        veil_rgb = _rgb(m["veil"])
        text_lums = [_lum(_rgb(v)) for v in m["text"].values()]
        # Necesidad por cada 1/40 del alto, peor caso entre ventanas y colores de texto.
        bins = 2 * steps
        need = np.zeros(bins)
        for _, w, h in WINDOWS:
            rows = _rows(img, w, h)
            for b in range(bins):
                px = np.concatenate(rows[int(b * h / bins):max(int((b + 1) * h / bins), int(b * h / bins) + 1)])
                for tl in text_lums:
                    need[b] = max(need[b], _required_alpha(px, veil_rgb, tl, CONTRAST_TEXT))
        # Parada k = máximo de las franjas que tocan [(k-1)/steps, (k+1)/steps]: así la interpolación lineal entre
        # paradas nunca queda por debajo de lo que pide ninguna franja.
        stops = []
        for k in range(N_STOPS):
            lo, hi = max(0, 2 * (k - 1)), min(bins, 2 * (k + 1))
            stops.append(min(1.0, float(need[lo:hi].max()) + MARGIN))
        stops = [float(np.ceil(s * 100) / 100) for s in stops]
        print(f"\n== {mode}: alfas del velo ({N_STOPS} paradas, de arriba a abajo; mínimo medido + {MARGIN})")
        print("  " + ", ".join(f"{s:.2f}f" for s in stops))


def _alpha_at(stops, y):
    pos = y * (len(stops) - 1)
    i = min(int(pos), len(stops) - 2)
    return stops[i] + (stops[i + 1] - stops[i]) * (pos - i)


def measure(resdir):
    for mode, m in MODES.items():
        stops = VEIL_STOPS[mode]
        if stops is None:
            print(f"{mode}: VEIL_STOPS sin llenar (corre `veil` y pega las paradas)")
            continue
        img = np.asarray(Image.open(f"{resdir}/{m['blur']}").convert("RGB"), dtype=float)
        veil_rgb = _rgb(m["veil"])
        print(f"\n== {mode}: texto >= {CONTRAST_TEXT}, borde de la barra >= {CONTRAST_OUTLINE} (fila por fila, interpolado)")
        worst = {}
        for name, w, h in WINDOWS:
            rows = _rows(img, w, h)
            row = {}
            for y in range(h):
                a = _alpha_at(stops, (y + 0.5) / h)
                c = veil_rgb * a + rows[y] * (1 - a)
                for k, v in m["text"].items():
                    row[k] = min(row.get(k, 99), float(_contrast(_lum(c), _lum(_rgb(v))).min()))
                # El borde de la barra se mide sobre el vidrio sin y con el tinte de la barra.
                ct = veil_rgb * m["bar_tint"] + c * (1 - m["bar_tint"])
                for tag, cc in (("borde", c), ("borde+tinte", ct)):
                    row[tag] = min(row.get(tag, 99), float(_contrast(_lum(cc), _lum(_rgb(m["outline"]))).min()))
                # Texto sobre la barra tintada.
                for k, v in m["text"].items():
                    key = k + "+tinte"
                    row[key] = min(row.get(key, 99), float(_contrast(_lum(ct), _lum(_rgb(v))).min()))
            print(f"  {name:16s}", " ".join(f"{k}={v:.2f}" for k, v in row.items() if "+tinte" not in k or k == "borde+tinte"))
            for k, v in row.items():
                worst[k] = min(worst.get(k, 99), v)
        print("  peor caso:   ", " ".join(f"{k}={v:.2f}" for k, v in worst.items()))


if __name__ == "__main__":
    if len(sys.argv) == 5 and sys.argv[1] == "prepare":
        prepare(sys.argv[2], sys.argv[3], sys.argv[4])
    elif len(sys.argv) == 3 and sys.argv[1] == "veil":
        veil(sys.argv[2])
    elif len(sys.argv) == 3 and sys.argv[1] == "measure":
        measure(sys.argv[2])
    else:
        print(__doc__)
        sys.exit(2)
