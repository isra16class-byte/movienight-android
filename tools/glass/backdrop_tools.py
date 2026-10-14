#!/usr/bin/env python3
"""Herramientas del fondo de vidrio de las pantallas de entrada (Fase 8, paso 3). Python 3 + Pillow + NumPy.

No forma parte de la app: sirve para repetir el trabajo cuando cambien las imágenes o la paleta (paso 6).

  prepare  Convierte una imagen a WebP (sin ampliarla; si es más ancha que 1080 px la reduce) y crea su copia borrosa
           (1/3 del tamaño, desenfoque gaussiano de radio 6). Salida: auth_bg_<modo>.webp y auth_bg_<modo>_blur.webp.
             python3 backdrop_tools.py prepare light original_dia.jpg app/src/main/res/drawable-nodpi
             python3 backdrop_tools.py prepare dark  original_noche.jpg app/src/main/res/drawable-nodpi

  measure  Mide el contraste del texto sobre el vidrio con la copia borrosa REAL (peor caso entre varios tamaños de
           ventana). Usa la misma pila de capas que la app: imagen -> velo -> tinte, y la misma cuenta WCAG que
           net/GlassLogic.kt. Los colores y alfas de abajo deben coincidir con ui/theme/Color.kt.
             python3 backdrop_tools.py measure app/src/main/res/drawable-nodpi
"""
import sys

import numpy as np
from PIL import Image, ImageFilter

# Tokens de ui/theme/Color.kt (provisionales). RGB sin alfa; los alfas son los de GlassColors.
MODES = {
    "light": dict(
        blur="auth_bg_light_blur.webp", scrim=(0xF5FBF5, 0.15), tint=(0xF5FBF5, 0.55),
        # Texto de GlassColors.content (solo dentro del panel), no el de la paleta normal.
        text={"onSurface": 0x0B100C, "onSurfaceVariant": 0x1A221D, "primary": 0x0B4A27, "error": 0x8A0000},
        outline=0x2F3A33,
    ),
    "dark": dict(
        blur="auth_bg_dark_blur.webp", scrim=(0x0E1511, 0.15), tint=(0x0E1511, 0.50),
        text={"onSurface": 0xDEE4DE, "onSurfaceVariant": 0xBFC9C1, "primary": 0x79DA9C, "error": 0xFFB4AB},
        outline=0x8A938B,
    ),
}
# (nombre, ancho dp, alto dp). El panel mide min(480, ancho) - 48 de ancho y puede estar a cualquier altura (scroll).
WINDOWS = [("9:16 vertical", 360, 640), ("9:19.5 vertical", 360, 780), ("9:21 vertical", 360, 840),
           ("tablet 5:8", 600, 960), ("horizontal 16:9", 640, 360), ("horizontal 20:9", 800, 360)]


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


def _band(img, w, h):
    """Píxeles de la copia borrosa bajo la banda donde puede caer el panel (recorte centrado, como la app)."""
    ih, iw = img.shape[:2]
    s = max(w / iw, h / ih)
    ox, oy = (w - iw * s) / 2, (h - ih * s) / 2
    pw = min(480, w) - 48
    x0, x1 = (w - pw) / 2, (w + pw) / 2
    ix0, ix1 = max(int(np.floor((x0 - ox) / s)), 0), min(int(np.ceil((x1 - ox) / s)), iw)
    iy0, iy1 = max(int(np.floor(-oy / s)), 0), min(int(np.ceil((h - oy) / s)), ih)
    return img[iy0:iy1, ix0:ix1].reshape(-1, 3)


def measure(resdir):
    for mode, m in MODES.items():
        img = np.asarray(Image.open(f"{resdir}/{m['blur']}").convert("RGB"), dtype=float)
        (sc, sa), (tc, ta) = m["scrim"], m["tint"]
        print(f"\n== {mode}: velo {sa}, tinte {ta} (texto >= 4,5; contorno >= 3)")
        worst = {}
        for name, w, h in WINDOWS:
            px = _band(img, w, h)
            c = _rgb(tc) * ta + (_rgb(sc) * sa + px * (1 - sa)) * (1 - ta)
            L = _lum(c)
            row = {k: float(_contrast(L, _lum(_rgb(v))).min()) for k, v in m["text"].items()}
            row["contorno"] = float(_contrast(L, _lum(_rgb(m["outline"]))).min())
            print(f"  {name:16s}", " ".join(f"{k}={v:.2f}" for k, v in row.items()),
                  f"| luminancia {L.min():.3f}..{L.max():.3f}")
            for k, v in row.items():
                worst[k] = min(worst.get(k, 99), v)
        print("  peor caso:   ", " ".join(f"{k}={v:.2f}" for k, v in worst.items()))


if __name__ == "__main__":
    if len(sys.argv) == 5 and sys.argv[1] == "prepare":
        prepare(sys.argv[2], sys.argv[3], sys.argv[4])
    elif len(sys.argv) == 3 and sys.argv[1] == "measure":
        measure(sys.argv[2])
    else:
        print(__doc__)
        sys.exit(2)
