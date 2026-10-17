"""Contraste de la tarjeta de vidrio de los estados de la sala (Fase 8, paso 5h) sobre la copia borrosa real. Uso, desde la raiz del repo:
  python3 tools/glass/measure_state_card.py
La tarjeta es `GlassPanel` con la copia borrosa (home_panel_*.webp) + tinte de panel con alpha 0,70 (GLASS_CARD_TINT_ALPHA); los colores
son los de GlassColors/GlassBarInk de Color.kt (copiados aqui: si cambian alla, cambian aqui). Mide TODOS los pixeles de la copia.
No mide la sombra (el texto dentro del panel no lleva) ni el telefono ni el borde. El boton se mide como relleno translucido sobre la tarjeta."""
import numpy as np
from PIL import Image
R='app/src/main/res/drawable-nodpi/'
def rgb(h): return np.array([(h>>16)&255,(h>>8)&255,h&255],float)
def lin(c):
    c=c/255.0; return np.where(c<=0.03928,c/12.92,((c+0.055)/1.055)**2.4)
def lum(c): l=lin(c); return 0.2126*l[...,0]+0.7152*l[...,1]+0.0722*l[...,2]
def cr(a,b): hi=np.maximum(a,b); lo=np.minimum(a,b); return (hi+0.05)/(lo+0.05)
def over(top,a,below): return top*a+below*(1-a)
def rep(name,bg,txt):
    c=cr(lum(bg),lum(rgb(txt))); print(f"   {name:44s} peor={c.min():5.2f}  media={c.mean():5.2f}  >=4,5: {100*(c>=4.5).mean():5.1f}%")
TINT=0.70
cfg={
 'CLARO':dict(img='home_panel_light.webp',tint=0xFFFFFF,ink=0x0E1511,err=0x5C0005,btn=(0xFFFFFF,0.38)),
 'OSCURO':dict(img='home_panel_dark.webp',tint=0x0E1511,ink=0xFFFFFF,err=0xFFB4AB,btn=(0xFFFFFF,0.08)),
}
for mode,c in cfg.items():
    img=np.asarray(Image.open(R+c['img']).convert('RGB'),float).reshape(-1,3)
    card=over(rgb(c['tint']),TINT,img)
    print(f"== {mode}: {img.shape[0]} pixeles; tarjeta = copia borrosa + tinte {TINT:.2f}")
    rep("texto (titulo, mensaje, aviso) sobre la tarjeta",card,c['ink'])
    rep("error (mensaje de contrasena) sobre la tarjeta",card,c['err'])
    rep("texto del boton (relleno translucido)",over(rgb(c['btn'][0]),c['btn'][1],card),c['ink'])
