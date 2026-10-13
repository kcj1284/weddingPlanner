"""Convert captured Figma reference JSX into local HTML/CSS; no React dependency."""
import json, pathlib, re, urllib.request
root=pathlib.Path(__file__).resolve().parents[1]
static=root/'src/main/resources/static'
assets=static/'newspaper-assets'; assets.mkdir(exist_ok=True)
rules={}; pages=[]
fixed={'absolute':'position:absolute','relative':'position:relative','contents':'display:contents','bg-white':'background:#fff','text-black':'color:#000','font-bold':'font-weight:700','font-black':'font-weight:900','not-italic':'font-style:normal','whitespace-nowrap':'white-space:nowrap','whitespace-pre':'white-space:pre','whitespace-pre-wrap':'white-space:pre-wrap','leading-[0]':'line-height:0','size-full':'width:100%;height:100%','inset-0':'inset:0','block':'display:block','max-w-none':'max-width:none','object-cover':'object-fit:cover','pointer-events-none':'pointer-events:none','border-solid':'border-style:solid','border-black':'border-color:#000','h-0':'height:0','w-0':'width:0','w-full':'width:100%','shrink-0':'flex-shrink:0','flex-none':'flex:none','flex':'display:flex','flex-col':'flex-direction:column','items-center':'align-items:center','items-start':'align-items:flex-start','justify-center':'justify-content:center','content-stretch':'align-content:stretch','rotate-90':'transform:rotate(90deg)','rotate-180':'transform:rotate(180deg)','mb-0':'margin-bottom:0','-translate-y-1/2':'transform:translateY(-50%)'}
fonts={'.New_York:Regular_G1':'NewspaperTitle','Libre_Bodoni:Bold':'NewspaperBodoni','NanumMyeongjo:ExtraBold':'CheongmoMyeongjo','Nanum_Myeongjo:Bold':'CheongmoMyeongjo','Diphylleia:Regular':'NewspaperDiphylleia','Pretendard:Bold':'CheongmoGothic'}
fixed.update({'list-decimal':'list-style-type:decimal','list-disc':'list-style-type:disc','list-inside':'list-style-position:inside','list-outside':'list-style-position:outside'})
fixed['-translate-x-1/2']='transform:translateX(-50%)'
fixed.update({'left-1/2':'left:50%','text-center':'text-align:center'})
def styles(classes):
 out=[]
 for t in classes.split():
  if t in fixed:out.append(fixed[t]);continue
  if t.startswith('['):out.append(t[1:-1].replace('word-break:break-word','overflow-wrap:anywhere'));continue
  if t.startswith('font-['):
   name=t[7:-2];out.append('font-family:'+fonts.get(name,'CheongmoMyeongjo')+',serif')
   if name.endswith(':Bold'):out.append('font-weight:700')
   if name.endswith(':ExtraBold'):out.append('font-weight:800')
   continue
  m=re.fullmatch(r'([a-z-]+)-\[(.*)\]',t)
  if not m:raise ValueError('Unhandled class '+t)
  key,v=m.groups();v=v.replace('_',' ')
  if 'calc(' in v:v=re.sub(r'(%|px)([+-])',r'\1 \2 ',v)
  if key=='rotate':out.append('transform:rotate('+v+')');continue
  if key=='inset':out.append('inset:'+v)
  elif key in ['left','right','top','bottom','leading','w','h','size','px','py','text','border','bg','ms','ps']:
   props={'ms':'margin-inline-start','ps':'padding-inline-start','leading':'line-height','w':'width','h':'height','text':'color' if v.startswith('#') else 'font-size','border':'border-color' if v.startswith('#') else 'border-width','bg':'background'}
   if key=='size':out.extend(['width:'+v,'height:'+v])
   elif key in ['px','py']:out.extend([('padding-left:' if key=='px' else 'padding-top:')+v,('padding-right:' if key=='px' else 'padding-bottom:')+v])
   else:out.append(props.get(key,key)+':'+v)
  elif key=='':out.append(v.replace('word-break:break-word','overflow-wrap:anywhere'))
  else:raise ValueError(t)
 return ';'.join(out)
for i in range(1,5):
 code=(root/f'docs/figma-newspaper/page-{i}-context.txt').read_text(encoding='utf8')
 prefix=re.search(r'const assetPathPrefix = "([^"]+)"',code).group(1)
 refs={}
 for name,file in re.findall(r'const (\w+) = `\$\{assetPathPrefix\}/([^`]+)`',code.split('export default',1)[0]):
  refs[name]='/newspaper-assets/'+file
  if not (assets/file).exists():urllib.request.urlretrieve(prefix+'/'+file,assets/file)
  if not (assets/file).stat().st_size:raise ValueError('Empty source asset: '+file)
 body=code.split('return (',1)[1].split('\n  );',1)[0].strip()
 def cls(m):
  value=m.group(1)
  if value not in rules:rules[value]=('np-f'+str(len(rules)),styles(value))
  return 'class="'+rules[value][0]+'"'
 body=re.sub(r'className="([^"]*)"',cls,body)
 body=re.sub(r' style=\{\{.*?\}\}','',body)
 body=re.sub(r'src=\{(\w+)\}',lambda m:'src="'+refs[m.group(1)]+'"',body)
 body=re.sub(r'\{`([^`]+)`\}',lambda m:m.group(1),body)
 body=re.sub(r'\{"([^\"]+)"\}',lambda m:m.group(1),body)
 body=re.sub(r'<(div|span|p)([^>]*?)\s*/>',r'<\1\2></\1>',body)
 body=body.replace('class="np-f0"','class="np-f0 np-sheet"',1)
 body=''.join(line.strip() for line in body.splitlines())
 if re.search(r'\{[^}]*\}',body):raise ValueError('Unconverted JSX')
 pages.append(body)
out='// Generated from the user-owned Figma copy; see docs/figma-newspaper.\nexport const newspaperPages='+json.dumps(pages,ensure_ascii=False)+';\n'
(static/'newspaper-pages.js').write_text(out,encoding='utf8')
(static/'newspaper-source.css').write_text('\n'.join('.'+name+'{'+rule+'}' for name,rule in rules.values()),encoding='utf8')
print('Converted four pages;',len(rules),'style rules;',len(list(assets.iterdir())),'local source assets')
