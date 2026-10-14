#!/usr/bin/env python3
"""Herramientas del fondo de vidrio de las pantallas de entrada (Fase 8, paso 3). Python 3 + Pillow + NumPy.

No forma parte de la app: sirve para repetir el trabajo cuando cambien las imágenes o la paleta (paso 6).

  prepare  Convierte una imagen a WebP (sin ampliarla; si es más ancha que 1080 px la reduce) y crea su copia borrosa.
           Salida: auth_bg_<modo>.webp y auth_bg_<modo>_blur.webp.
           La copia borrosa se hace con un RADIO gaussiano y una ESCALA (fracción del tamaño de la imagen) que se pasan
           como parámetros; el desenfoque equivalente a tamaño completo es radio / escala. Valores por defecto y usados
           hoy (Fase 8, paso 3, ajuste del desenfoque): radio 3, escala 1 (tamaño completo), o sea σ≈3 px sobre 736-768 de
           ancho; deja ~28 % del gradiente de la imagen nítida en modo claro (33 % en oscuro). Los valores anteriores
           eran radio 6 y escala 1/3 (σ≈18, ~5 %). Calidad WebP de la copia: 80.
             python3 backdrop_tools.py prepare light original_dia.jpg app/src/main/res/drawable-nodpi [--radio 3] [--escala 1]
             python3 backdrop_tools.py prepare dark  original_noche.jpg app/src/main/res/drawable-nodpi [--radio 3] [--escala 1]
           Con --solo-copia NO escribe auth_bg_<modo>.webp (la nítida) y solo regenera la copia borrosa; sirve para
           partir de la propia auth_bg_<modo>.webp del repo sin recomprimirla:
             python3 backdrop_tools.py prepare light app/src/main/res/drawable-nodpi/auth_bg_light.webp app/src/main/res/drawable-nodpi --solo-copia

           Con --prefijo home los archivos se llaman home_bg_<modo>*.webp (fondo de Home, Fase 8, paso 4) en vez de
           auth_bg_<modo>*.webp. Con --panel se escribe además <prefijo>_panel_<modo>.webp, la copia que se ve dentro de cada
           panel (radio 6 sobre 1/3 del tamaño, como auth_panel_*.webp). Home no usa la imagen nítida: va con --solo-copia.
             python3 backdrop_tools.py prepare light dia.jpg app/src/main/res/drawable-nodpi --prefijo home --panel --solo-copia

  veil     Calcula cuánto velo (oscuro o claro, según el modo) haría falta para dar texto >= 4,5:1 en cada franja del alto
           de la ventana con la copia borrosa REAL, peor caso entre varios tamaños de ventana. Es solo INFORMATIVO: el
           diseño actual usa un velo de 10 % parejo (VEIL_STOPS) a pedido de la persona, y la legibilidad la completa la
           sombra del texto (ver `GlassColors` en Color.kt). Con el modo claro actual (texto crema) los números salen
           altos: es justo lo que se está dejando de pedir al velo.
             python3 backdrop_tools.py veil app/src/main/res/drawable-nodpi

  measure  Con las paradas y los tintes de barra y botón que están en el código (VEIL_STOPS y MODES de abajo, que deben coincidir con Color.kt) mide, fila por
           fila, el contraste de cada color de texto y del borde de la barra de campos, y el PORCENTAJE de píxeles que
           llegan a 4,5:1 y a 3:1 (sin contar la sombra del texto, que esta cuenta no ve).
             python3 backdrop_tools.py measure app/src/main/res/drawable-nodpi
"""
import sys

import numpy as np
from PIL import Image, ImageFilter

# Tokens de ui/theme/Color.kt (provisionales). RGB sin alfa.
MODES = {
    "light": dict(
        blur="auth_bg_light_blur.webp", veil=0x0E1511, bar_tint=0.25, bar_rgb=0xFFF8E7, button_tint=0.12,
        # Texto de GlassColors.content (crema, solo en el modo claro), no el de la paleta normal.
        text={"onSurface": 0xFFF8E7, "onSurfaceVariant": 0xFFF8E7, "primary": 0xFFF8E7, "error": 0xFFB4AB},
        outline=0xFFF8E7,
        # GlassBarInk de Color.kt: texto DENTRO de la barra y del botón (oscuro, sobre el tinte crema).
        bar_text={"onSurface": 0x0E1511, "onSurfaceVariant": 0x1F2923, "primary": 0x0E1511, "error": 0x5C0005},
    ),
    "dark": dict(
        blur="auth_bg_dark_blur.webp", veil=0x0E1511, bar_tint=0.25, bar_rgb=0x0E1511, button_tint=0.0,
        text={"onSurface": 0xDEE4DE, "onSurfaceVariant": 0xBFC9C1, "primary": 0x79DA9C, "error": 0xFFB4AB},
        outline=0xD8E0DA,
    ),
}
# Alfas del velo en N paradas igualmente espaciadas de arriba (0) a abajo (1). Deben coincidir con Color.kt.
VEIL_STOPS = {
    "light": [0.10] * 21,
    "dark": [0.10] * 21,
}
# (nombre, ancho dp, alto dp). El texto ocupa la columna de min(480, ancho) - 48 de ancho y puede estar a cualquier altura.
WINDOWS = [("9:16 vertical", 360, 640), ("9:19.5 vertical", 360, 780), ("9:21 vertical", 360, 840),
           ("tablet 5:8", 600, 960), ("horizontal 16:9", 640, 360), ("horizontal 20:9", 800, 360)]
CONTRAST_TEXT = 4.5
CONTRAST_OUTLINE = 3.0
N_STOPS = 21        # paradas k/20
MARGIN = 0.03       # margen sobre el mínimo medido


DEFAULT_BLUR_RADIUS = 3.0   # radio gaussiano aplicado a la copia ya reducida (en px de la copia)
DEFAULT_BLUR_SCALE = 1.0    # fracción del tamaño de la imagen nítida a la que se genera la copia


PANEL_BLUR_RADIUS = 6.0     # copia que se ve DENTRO de cada panel (<prefijo>_panel_<modo>.webp): radio 6 sobre 1/3 del tamaño.
PANEL_BLUR_SCALE = 1 / 3    # (medido sobre auth_panel_*.webp: el mejor ajuste es radio 6 a escala 1/3)


def prepare(mode, src, outdir, radius=DEFAULT_BLUR_RADIUS, scale=DEFAULT_BLUR_SCALE, sharp=True, prefix="auth", panel=False):
    im = Image.open(src).convert("RGB")
    if im.width > 1080:
        im = im.resize((1080, round(im.height * 1080 / im.width)), Image.LANCZOS)
    if sharp:
        im.save(f"{outdir}/{prefix}_bg_{mode}.webp", "WEBP", quality=82, method=6)
    if panel:
        small = im.resize((max(1, round(im.width * PANEL_BLUR_SCALE)), max(1, round(im.height * PANEL_BLUR_SCALE))), Image.LANCZOS)
        small.filter(ImageFilter.GaussianBlur(radius=PANEL_BLUR_RADIUS)).save(f"{outdir}/{prefix}_panel_{mode}.webp", "WEBP", quality=80, method=6)
        print(mode, "-> copia del panel", small.size, f"radio {PANEL_BLUR_RADIUS}")
    if scale == 1.0:
        b = im
    else:
        b = im.resize((max(1, round(im.width * scale)), max(1, round(im.height * scale))), Image.LANCZOS)
    b.filter(ImageFilter.GaussianBlur(radius=radius)).save(f"{outdir}/{prefix}_bg_{mode}_blur.webp", "WEBP", quality=80, method=6)
    print(mode, im.size, "-> copia borrosa", b.size, f"radio {radius}, escala {scale} (sigma equivalente a tamaño completo ~{radius / scale:.1f} px)")


def _prepare_cli(args):
    """prepare <modo> <origen> <carpeta> [--radio N] [--escala N] [--solo-copia] [--prefijo auth|home] [--panel]"""
    if len(args) < 3 or args[0] not in MODES:
        return False
    radius, scale, sharp, prefix, panel = DEFAULT_BLUR_RADIUS, DEFAULT_BLUR_SCALE, True, "auth", False
    rest = args[3:]
    i = 0
    while i < len(rest):
        if rest[i] == "--radio" and i + 1 < len(rest):
            radius = float(rest[i + 1]); i += 2
        elif rest[i] == "--escala" and i + 1 < len(rest):
            scale = float(rest[i + 1]); i += 2
        elif rest[i] == "--solo-copia":
            sharp = False; i += 1
        elif rest[i] == "--prefijo" and i + 1 < len(rest) and rest[i + 1] in ("auth", "home"):
            prefix = rest[i + 1]; i += 2
        elif rest[i] == "--panel":
            panel = True; i += 1
        else:
            return False
    if radius < 0 or not (0 < scale <= 1):
        return False
    prepare(args[0], args[1], args[2], radius, scale, sharp, prefix, panel)
    return True


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
        img = np.asarray(Image.open(f"{resdir}/{m['blur']}").convert("RGB"), dtype=float)
        veil_rgb = _rgb(m["veil"])
        print(f"\n== {mode}: contraste fila por fila (sin sombra del texto); objetivo texto {CONTRAST_TEXT}, borde {CONTRAST_OUTLINE}")
        for name, w, h in WINDOWS:
            rows = _rows(img, w, h)
            worst = {}
            total = 0
            ok45 = {k: 0 for k in m["text"]}
            ok3 = {k: 0 for k in m["text"]}
            for y in range(h):
                a = _alpha_at(stops, (y + 0.5) / h)
                c = veil_rgb * a + rows[y] * (1 - a)
                total += len(c)
                for k, v in m["text"].items():
                    cr = _contrast(_lum(c), _lum(_rgb(v)))
                    worst[k] = min(worst.get(k, 99), float(cr.min()))
                    ok45[k] += int((cr >= CONTRAST_TEXT).sum())
                    ok3[k] += int((cr >= 3.0).sum())
                ct = _rgb(m["bar_rgb"]) * m["bar_tint"] + c * (1 - m["bar_tint"])
                cb = _rgb(m["bar_rgb"]) * m["button_tint"] + c * (1 - m["button_tint"])
                worst["boton primary"] = min(worst.get("boton primary", 99), float(_contrast(_lum(cb), _lum(_rgb((m.get("bar_text") or m["text"])["primary"]))).min()))
                worst["boton borde"] = min(worst.get("boton borde", 99), float(_contrast(_lum(cb), _lum(_rgb(m["outline"]))).min()))
                for tag, cc in (("borde", c), ("borde+barra", ct)):
                    worst[tag] = min(worst.get(tag, 99), float(_contrast(_lum(cc), _lum(_rgb(m["outline"]))).min()))
                for k, v in (m.get("bar_text") or m["text"]).items():
                    key = k + " en barra"
                    worst[key] = min(worst.get(key, 99), float(_contrast(_lum(ct), _lum(_rgb(v))).min()))
            print(f"  {name:16s} peor: " + " ".join(f"{k}={v:.2f}" for k, v in worst.items() if "en barra" not in k and not k.startswith("boton")))
            print(f"  {'':16s} onSurface sobre vidrio: >=4,5 en {100 * ok45['onSurface'] / total:.0f}% de los pixeles, >=3 en {100 * ok3['onSurface'] / total:.0f}%"
                  f" | en la barra, peor onSurface={worst['onSurface en barra']:.2f}"
                  f" | en el botón, peor texto={worst['boton primary']:.2f}, borde={worst['boton borde']:.2f}")


if __name__ == "__main__":
    if len(sys.argv) >= 2 and sys.argv[1] == "prepare":
        if not _prepare_cli(sys.argv[2:]):
            print(__doc__)
            sys.exit(2)
    elif len(sys.argv) == 3 and sys.argv[1] == "veil":
        veil(sys.argv[2])
    elif len(sys.argv) == 3 and sys.argv[1] == "measure":
        measure(sys.argv[2])
    else:
        print(__doc__)
        sys.exit(2)
