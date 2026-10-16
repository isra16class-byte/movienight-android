"""Contraste del chat de la sala (Fase 8, paso 5b) sobre la copia borrosa real. Uso, desde la raíz del repo:
  python3 tools/glass/measure_chat.py CLARO_PROPIO CLARO_AJENO OSCURO_FONDO_HEX OSCURO_PROPIO OSCURO_AJENO OSCURO_ERROR_HEX
  python3 tools/glass/measure_chat.py 0.18 0.65 0x0E1511 0.12 0.06 0xFFB4AB
Mide texto (tinta del vidrio) sobre hoja = copia borrosa + tinte 0,70 + tinte de burbuja. No mide la sombra ni el teléfono."""
import numpy as np, sys
from PIL import Image
R='app/src/main/res/drawable-nodpi/'
def rgb(h): return np.array([(h>>16)&255,(h>>8)&255,h&255],float)
def lin(c):
    c=c/255.0; return np.where(c<=0.03928,c/12.92,((c+0.055)/1.055)**2.4)
def lum(c): l=lin(c); return 0.2126*l[...,0]+0.7152*l[...,1]+0.0722*l[...,2]
def cr(a,b): hi=np.maximum(a,b); lo=np.minimum(a,b); return (hi+0.05)/(lo+0.05)
def over(top,a,below): return top*a+below*(1-a)
def rep(name,bg,txt):
    c=cr(lum(bg),lum(rgb(txt))); print(f"   {name:34s} peor={c.min():5.2f}  >=4,5: {100*(c>=4.5).mean():5.1f}%  >=3: {100*(c>=3).mean():5.1f}%")
img=np.asarray(Image.open(R+'home_panel_light.webp').convert('RGB'),float).reshape(-1,3)
print("pixeles de la copia borrosa:",img.shape[0])
TINT=0.70
cfg={
 'CLARO':dict(tint=0xFFFFFF, mine=(0x0A3D24,float(sys.argv[1])), other=(0xFFFFFF,float(sys.argv[2])), ink=0x0E1511, err=0x5C0005, field=(0xFFFFFF,0.22)),
 'OSCURO':dict(tint=int(sys.argv[3],16), mine=(0xFFFFFF,float(sys.argv[4])), other=(0xFFFFFF,float(sys.argv[5])), ink=0xFFFFFF, err=int(sys.argv[6],16), field=(int(sys.argv[3],16),0.25)),
}
for mode,c in cfg.items():
    print(f"== {mode} (hoja = copia borrosa + tinte {TINT:.2f}); tintes burbuja: propio {c['mine'][1]}, ajeno {c['other'][1]}")
    sheet=over(rgb(c['tint']),TINT,img)
    rep("texto sobre la hoja (sistema/escribiendo)",sheet,c['ink'])
    rep("aviso silenciado (error) sobre la hoja",sheet,c['err'])
    rep("texto en burbuja PROPIA",over(rgb(c['mine'][0]),c['mine'][1],sheet),c['ink'])
    rep("texto en burbuja AJENA",over(rgb(c['other'][0]),c['other'][1],sheet),c['ink'])
    rep("texto en el campo (barra sobre la hoja)",over(rgb(c['field'][0]),c['field'][1],sheet),c['ink'])
    # distincion propio/ajeno: razon de luminancia entre ambas burbujas
    d=cr(lum(over(rgb(c['mine'][0]),c['mine'][1],sheet)),lum(over(rgb(c['other'][0]),c['other'][1],sheet)))
    print(f"   {'distincion burbuja propia vs ajena':34s} razon de luminancia media={d.mean():.2f} (1,00 = iguales)")
