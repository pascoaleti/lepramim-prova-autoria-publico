
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from pathlib import Path
import datetime, shutil

ROOT = Path(__file__).resolve().parents[1]
PLAY = ROOT / 'play-store'
BACKUP = PLAY / 'archive-old-screenshots' / ('backup-clean-final-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
BACKUP.mkdir(parents=True, exist_ok=True)
NAMES = [
 'screenshot-00-capa-divertida.png', 'screenshot-01-inicio.png', 'screenshot-02-recursos.png',
 'screenshot-03-botao-em-outros-apps.png', 'screenshot-04-camera-prints.png', 'screenshot-05-familia-cuidadores.png']
for n in NAMES:
    p=PLAY/n
    if p.exists(): shutil.copy2(p, BACKUP/n)

W,H=1080,1920
BG='#F4FAFF'; BLUE='#0057D9'; BLUE2='#003B8F'; GREEN='#16B957'; GREEN2='#0A8F3D'; TEXT='#0A2342'; MUTED='#596779'; LINE='#CFE6FF'; SOFT_BLUE='#E9F4FF'; SOFT_GREEN='#E8F8EF'; YELLOW='#FFD66B'
FONT_DIR=Path(r'C:\Windows\Fonts')
def font(kind,size):
    opts={'bold':['segoeuib.ttf','arialbd.ttf'], 'semibold':['seguisb.ttf','segoeuib.ttf','arialbd.ttf'], 'regular':['segoeui.ttf','arial.ttf']}[kind]
    for o in opts:
        p=FONT_DIR/o
        if p.exists(): return ImageFont.truetype(str(p),size)
    return ImageFont.load_default()

def t(s): return s
FB=font('bold',60); FS=font('semibold',34); FR=font('regular',32); FRS=font('regular',28); FSB=font('semibold',34); FH=font('bold',66); FH2=font('bold',56); FCARD=font('bold',42)
ICON_PATH=PLAY/'lepramim-icon-final-512.png'
if not ICON_PATH.exists(): ICON_PATH=PLAY/'icon-512-celular-azul-correto.png'
ICON=Image.open(ICON_PATH).convert('RGBA')

def size(d,s,f):
    b=d.textbbox((0,0),s,font=f); return b[2]-b[0], b[3]-b[1]

def wrap(d,text,f,maxw):
    words=text.split(); lines=[]; cur=''
    for w in words:
        trial=w if not cur else cur+' '+w
        if size(d,trial,f)[0] <= maxw: cur=trial
        else:
            if cur: lines.append(cur)
            cur=w
    if cur: lines.append(cur)
    return lines

def draw_wrap(d,xy,text,f,fill,maxw,lh=None):
    x,y=xy; lh=lh or size(d,'Ag',f)[1]+12
    for line in wrap(d,text,f,maxw):
        d.text((x,y),line,font=f,fill=fill); y+=lh
    return y

def shadow(img,box,r=36,dy=12,blur=22):
    sh=Image.new('RGBA',img.size,(0,0,0,0)); sd=ImageDraw.Draw(sh); x1,y1,x2,y2=box
    sd.rounded_rectangle((x1,y1+dy,x2,y2+dy),radius=r,fill=(0,45,120,34)); img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(blur)))

def grad_box(img,box,r=46):
    x1,y1,x2,y2=box; w=x2-x1; h=y2-y1
    g=Image.new('RGBA',(w,h)); px=g.load(); a=(0,87,217); b=(0,59,143)
    for y in range(h):
        for x in range(w):
            q=(x*.25+y)/(w*.25+h); px[x,y]=tuple(int(a[i]*(1-q)+b[i]*q) for i in range(3))+(255,)
    m=Image.new('L',(w,h),0); md=ImageDraw.Draw(m); md.rounded_rectangle((0,0,w-1,h-1),radius=r,fill=255); img.paste(g,(x1,y1),m)

def header(img):
    d=ImageDraw.Draw(img); shadow(img,(42,48,1038,300),52,12,24); grad_box(img,(42,48,1038,300),52)
    d.rounded_rectangle((76,92,226,242),radius=32,fill='white')
    img.alpha_composite(ICON.resize((118,118),Image.Resampling.LANCZOS),(92,108))
    d.text((252,106),'LePraMim',font=font('bold',60),fill='white')
    d.text((254,178),t('Leitura em voz alta'),font=font('semibold',30),fill='white')

def canvas(): return Image.new('RGBA',(W,H),BG)
def save(img,name): img.convert('RGB').save(PLAY/name,optimize=True)

def speaker(d,cx,cy,s=1,color=GREEN,wave=GREEN2):
    d.rounded_rectangle((cx-60*s,cy-28*s,cx-22*s,cy+28*s),radius=int(10*s),fill=color)
    d.polygon([(cx-22*s,cy-28*s),(cx+42*s,cy-72*s),(cx+42*s,cy+72*s),(cx-22*s,cy+28*s)],fill=color)
    d.arc((cx+55*s,cy-44*s,cx+116*s,cy+44*s),-55,55,fill=wave,width=max(5,int(10*s)))
    d.arc((cx+83*s,cy-76*s,cx+166*s,cy+76*s),-55,55,fill=wave,width=max(5,int(10*s)))

def play(d,cx,cy,r=120):
    d.ellipse((cx-r-18,cy-r-18,cx+r+18,cy+r+18),fill='#D7EAFF')
    d.ellipse((cx-r,cy-r,cx+r,cy+r),fill=BLUE)
    d.polygon([(cx-34,cy-56),(cx-34,cy+56),(cx+64,cy)],fill='white')

def card(d,img,box,r=34,fill='white'):
    shadow(img,box,r,10,18); d.rounded_rectangle(box,radius=r,fill=fill,outline=LINE,width=3)

def doc_to_audio(d,x,y):
    d.rounded_rectangle((x,y,x+300,y+350),radius=24,fill='white',outline='#C8DEF5',width=4)
    d.text((x+46,y+44),'A',font=font('bold',72),fill=TEXT)
    for i,w in enumerate([190,220,205,150]): d.rounded_rectangle((x+48,y+138+i*48,x+48+w,y+158+i*48),radius=10,fill='#A5AFBB')
    d.line((x+370,y+176,x+460,y+176),fill=BLUE,width=9); d.polygon([(x+460,y+176),(x+430,y+152),(x+430,y+200)],fill=BLUE)
    speaker(d,x+610,y+176,.9)

def bottom_badge(d,img,title,sub,icon='speaker'):
    d.rounded_rectangle((64,1430,1016,1685),radius=36,fill=SOFT_GREEN)
    if icon=='speaker': speaker(d,170,1556,.62)
    elif icon=='shield':
        d.ellipse((120,1500,235,1615),fill=BLUE); d.polygon([(178,1516),(222,1534),(214,1592),(178,1618),(142,1592),(134,1534)],fill='white')
    d.text((330,1486),title,font=font('bold',40),fill=GREEN2)
    draw_wrap(d,(332,1550),sub,font('regular',30),TEXT,610,42)

# 00 cover
img=canvas(); d=ImageDraw.Draw(img)
# upper hero
grad_box(img,(0,0,1080,1050),0); d.ellipse((850,660,1280,1160),fill=(255,255,255,32))
img.alpha_composite(ICON.resize((250,250),Image.Resampling.LANCZOS),(92,94))
d.text((92,376),'LePraMim',font=font('bold',84),fill='white'); d.rounded_rectangle((96,470,520,540),radius=22,fill=GREEN); d.text((132,485),t('Leitura em voz alta'),font=font('bold',38),fill='white')
draw_wrap(d,(96,620),t('Para ouvir mensagens, pap\u00e9is, prints e textos com bot\u00f5es grandes.'),font('bold',44),'white',440,58)
# hero right simple phone card
card(d,img,(575,95,1015,950),44,'white')
d.rounded_rectangle((610,132,980,230),radius=28,fill=BLUE); d.text((660,155),'LePraMim',font=font('bold',40),fill='white')
doc_to_audio(d,645,280); play(d,795,680,90)
# lower card
d.rectangle((0,1050,1080,1920),fill=BG); card(d,img,(64,1150,1016,1740),38,'white')
d.text((112,1222),'LePraMim',font=font('bold',66),fill=BLUE2); d.text((114,1302),t('Leitura em voz alta'),font=font('bold',44),fill=GREEN2)
draw_wrap(d,(112,1410),t('Mais autonomia para quem precisa ouvir o celular.'),font('bold',46),TEXT,820,62)
d.rounded_rectangle((112,1620,525,1702),radius=26,fill=BLUE); d.text((195,1636),t('Toque. Ou\u00e7a.'),font=font('bold',38),fill='white')
save(img,'screenshot-00-capa-divertida.png')

# 01 home
img=canvas(); d=ImageDraw.Draw(img); header(img)
d.text((64,360),t('O celular l\u00ea em voz alta'),font=FH,fill=TEXT); draw_wrap(d,(68,505),t('Para quem precisa ouvir mensagens, pap\u00e9is, prints e textos do dia a dia.'),FR,MUTED,900,48)
card(d,img,(72,650,1008,1260),34,'white'); doc_to_audio(d,150,760); play(d,720,940,105)
bottom_badge(d,img,t('Ou\u00e7a antes. Entenda com calma.'),t('Bot\u00f5es grandes, visual simples e leitura em portugu\u00eas.'))
save(img,'screenshot-01-inicio.png')

# 02 touch
img=canvas(); d=ImageDraw.Draw(img); header(img)
d.text((64,360),t('Toque para ouvir'),font=FH,fill=TEXT); draw_wrap(d,(68,505),t('Um bot\u00e3o grande para come\u00e7ar. Sem menus dif\u00edceis.'),FR,MUTED,900,48)
card(d,img,(68,625,1012,1280),34,'white'); play(d,540,835,135); tw,_=size(d,'Tocar',font('bold',62)); d.text((540-tw//2,1010),'Tocar',font=font('bold',62),fill=BLUE2); draw_wrap(d,(160,1148),t('Abra outro app e toque no bot\u00e3o amarelo para ouvir o que aparece na tela.'),font('semibold',36),MUTED,760,50)
d.rounded_rectangle((108,1380,972,1605),radius=36,fill=SOFT_BLUE); d.text((166,1442),t('F\u00e1cil para quem precisa ouvir'),font=font('bold',40),fill=BLUE2); draw_wrap(d,(168,1512),t('Pouco texto, alto contraste e comandos diretos.'),FRS,TEXT,760,42)
save(img,'screenshot-02-recursos.png')

# 03 messages
img=canvas(); d=ImageDraw.Draw(img); header(img)
d.text((64,360),t('Mensagens em voz alta'),font=font('bold',58),fill=TEXT); draw_wrap(d,(68,505),t('Fala o aplicativo, quem enviou e depois a mensagem \u00fatil.'),FR,MUTED,900,48)
card(d,img,(70,620,1010,1485),34,'white'); d.text((112,670),'WhatsApp',font=font('bold',40),fill=GREEN2); d.text((112,734),'Nanda mandou:',font=font('bold',36),fill=BLUE2)
for i,msg in enumerate([t('Mensagem recebida sobre consulta.'),t('Aviso importante da fam\u00edlia.'),t('Texto lido sem emoji ou v\u00eddeo.')]):
    yy=830+i*130; d.rounded_rectangle((120,yy,860,yy+86),radius=24,fill=SOFT_GREEN); d.text((150,yy+24),msg,font=font('regular',30),fill=TEXT)
d.ellipse((742,1250,968,1476),fill=YELLOW); speaker(d,842,1364,.72,color=BLUE2,wave=GREEN2)
d.rounded_rectangle((84,1578,996,1738),radius=28,fill=SOFT_BLUE); draw_wrap(d,(120,1618),t('L\u00ea texto. Ignora emoji, v\u00eddeo e controles.'),font('bold',38),TEXT,850,52)
save(img,'screenshot-03-botao-em-outros-apps.png')

# 04 camera
img=canvas(); d=ImageDraw.Draw(img); header(img)
d.text((64,360),t('Leia pap\u00e9is, fotos e prints'),font=font('bold',58),fill=TEXT); draw_wrap(d,(68,505),t('Use a c\u00e2mera ou escolha uma imagem. O LePraMim procura o texto e l\u00ea para voc\u00ea.'),FR,MUTED,900,48)
card(d,img,(78,650,1002,1325),34,'white'); doc_to_audio(d,150,775); d.rounded_rectangle((148,1138,932,1282),radius=28,fill=SOFT_GREEN); d.text((200,1170),t('Exemplo de leitura:'),font=font('bold',34),fill=GREEN2); draw_wrap(d,(200,1216),t('\u201cIsso parece um boleto. Confira valor e vencimento.\u201d'),font('regular',30),TEXT,700,40)
d.rounded_rectangle((94,1445,986,1648),radius=34,fill='white',outline=LINE,width=3); d.text((150,1495),t('C\u00e2mera + \u00e1udio'),font=font('bold',42),fill=BLUE2); draw_wrap(d,(152,1560),t('Ajuda em contas, avisos, receitas e documentos simples.'),FRS,MUTED,760,42)
save(img,'screenshot-04-camera-prints.png')

# 05 family
img=canvas(); d=ImageDraw.Draw(img); header(img)
d.text((64,360),t('Fam\u00edlia e cuidadores'),font=FH2,fill=TEXT); draw_wrap(d,(68,505),t('Configure a voz, o modo seguro e entregue o celular pronto para uso.'),FR,MUTED,900,48)
items=[(GREEN,t('Voz confort\u00e1vel'),t('Escolha velocidade e teste a fala.'),'speaker'),(BLUE,t('Modo seguro'),t('Evita ler senhas e c\u00f3digos sem cuidado.'),'shield'),(GREEN2,t('Plus opcional'),t('Uso sem limite, sem an\u00fancios.'),'plus')]
y=650
for color,title,sub,kind in items:
    card(d,img,(70,y,1010,y+210),28,'white'); d.ellipse((112,y+52,222,y+162),fill=color)
    if kind=='speaker': speaker(d,164,y+108,.45,color='white',wave='#DDF7E7')
    elif kind=='shield': d.polygon([(166,y+58),(214,y+78),(206,y+140),(166,y+164),(126,y+140),(118,y+78)],fill='white'); d.line((146,y+112,160,y+128,190,y+92),fill=color,width=8)
    else: d.rounded_rectangle((132,y+72,196,y+136),radius=16,fill='white'); d.text((156,y+76),'P',font=font('bold',42),fill=color)
    d.text((260,y+58),title,font=font('bold',42),fill=color); d.text((262,y+116),sub,font=font('regular',30),fill=TEXT); y+=285
d.text((90,1548),t('Mais autonomia para quem precisa'),font=font('bold',44),fill=BLUE2); d.text((90,1604),t('ouvir o celular.'),font=font('bold',44),fill=BLUE2)
save(img,'screenshot-05-familia-cuidadores.png')

print('Backup:', BACKUP)
for n in NAMES:
    im=Image.open(PLAY/n); print(n, im.size, im.mode, (PLAY/n).stat().st_size)
