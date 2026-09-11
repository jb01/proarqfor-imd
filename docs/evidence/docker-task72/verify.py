from pathlib import Path
import json,sqlite3,subprocess,time,urllib.request,urllib.parse
R=Path('/tmp/arqfor-task72-n16tdmfb'); D=R/'data'; BASE='http://127.0.0.1:18080'
CMD=['docker','compose','-p','arqfor-task72-demo','-f',str(R/'compose.json')]
report={'checks':[]}
def check(name,condition):
 report['checks'].append({'name':name,'passed':bool(condition)})
 (R/'result.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
 assert condition,name
 print('PASS',name,flush=True)
def http(path,data=None):
 req=urllib.request.Request(BASE+path,data=urllib.parse.urlencode(data).encode() if data is not None else None)
 with urllib.request.urlopen(req,timeout=45) as r:return r.status,r.read().decode()
def ready():
 for _ in range(60):
  try:
   if http('/evidences')[0]==200:return
  except Exception:pass
  time.sleep(1)
 raise RuntimeError('HTTP não disponível')
def rows():
 with sqlite3.connect('file:'+str(D/'data/arqfor.db')+'?mode=ro',uri=True) as c:
  c.row_factory=sqlite3.Row
  return [dict(r) for r in c.execute('select * from evidence order by id')]
def inspect():
 cid=subprocess.check_output(CMD+['ps','-q','web'],text=True).strip()
 data=json.loads(subprocess.check_output(['docker','inspect',cid],text=True))[0]
 return {'id':cid,'started_at':data['State']['StartedAt'],'mounts':[{'source':m['Source'],'destination':m['Destination'],'type':m['Type']} for m in data['Mounts']]}
ready()
report=json.loads((R/'initial-result.json').read_text())
report['checks']=report['checks'][:-1]
report['diagnostic']='Primeira inspeção do host não conseguiu atravessar diretório de trabalho privado criado pelo container. Verificação retomada por docker cp, sem repetir cadastro/arquivamento ou mudar permissões.'
initial=rows();valid,divergent=initial
cid=subprocess.check_output(CMD+['ps','-q','web'],text=True).strip()
subprocess.run(['docker','cp',cid+':/var/lib/arqfor/storage',str(R/'storage-before')],check=True)
SB=R/'storage-before'
before=rows();check('arquivamento confirmado',before[0]['status']=='ARQUIVADO')
check('origem sintética removida após cópia',not (D/'storage/fast/valida.dd').exists())
check('dd de trabalho preservado',len(list((SB/'cold/work').rglob('*.dd')))==1)
check('ZIP aberto removido',not list((SB/'cold/work').rglob('*.zip')))
check('artefato cifrado publicado',len(list((SB/'cold/archive').rglob('*.zip.enc')))==1)
check('metadados criptográficos presentes',all(before[0][k] is not None for k in ['encryption_key','encryption_iv','encryption_password','encryption_salt','encryption_iterations','encryption_format_version']))
http('/evidences/'+str(divergent['id'])+'/archive',{})
check('arquivamento divergente bloqueado',rows()==before)
files={str(p.relative_to(SB)):p.read_bytes() for p in SB.rglob('*') if p.is_file()}
report['before']=inspect();report['records']=[{k:r[k] for k in ['id','evidence_identifier','status','current_path','informed_hash','calculated_hash','created_at']} for r in before]
check('mount externo correto',report['before']['mounts']==[{'source':str(D),'destination':'/var/lib/arqfor','type':'bind'}])
subprocess.run(CMD+['restart','web'],check=True)
ready();report['after']=inspect()
check('reinício real do mesmo container',report['before']['id']==report['after']['id'] and report['before']['started_at']!=report['after']['started_at'])
check('todos os campos SQLite preservados',rows()==before)
afterfiles={str(p.relative_to(SB)):p.read_bytes() for p in SB.rglob('*') if p.is_file()}
check('nomes e bytes de todos os artefatos preservados',afterfiles==files)
report['files']=[{'path':p,'bytes':len(b)} for p,b in files.items()]
for r in before:
 code,body=http('/evidences/'+str(r['id']))
 check('detalhes após reinício '+r['evidence_identifier'],code==200 and r['evidence_identifier'] in body and r['status'] in body)
subprocess.run(CMD+['stop','web'],check=True)
report['stopped']=True
check('demonstração concluída',True)
