import csv,json,math,statistics,sys,hashlib,zipfile
from pathlib import Path
run=Path(sys.argv[1]);result={}
names={0:'setup',1:'link',2:'figure eights',3:'zigzag/sprint-stop',4:'repeated jumping',5:'cooperative diagonals',6:'rapid handovers',7:'reverse turns/crouching/jitter',8:'one-sided wall',9:'mixed terrain',10:'crouch/edge',11:'takeoffs/landings',12:'grab/release',13:'disconnect/reconnect',14:'teleport cleanup'}
def quant(v,q):
 s=sorted(v);return s[min(len(s)-1,int((len(s)-1)*q))] if s else None
for role in ('host','alpha','bravo'):
 path=run/(role+'-RTT.csv');groups={}
 if path.exists():
  for row in csv.DictReader(path.open()):groups.setdefault(int(row['phase']),[]).append(float(row['rtt_ms']))
 result[role+'_rtt']={str(p):{'movement':names.get(p,str(p)),'samples':len(v),'min_ms':min(v),'median_ms':statistics.median(v),'p95_ms':quant(v,.95),'max_ms':max(v)} for p,v in groups.items()}
 path=run/(role+'-CAMERA.csv')
 if path.exists():
  rows=list(csv.DictReader(path.open()));groups={}
  for a,b in zip(rows,rows[1:]):
   if a['phase']!=b['phase'] or int(b['tick'])-int(a['tick'])!=1:continue
   p=int(b['phase']);distance=math.sqrt(sum((float(b[k])-float(a[k]))**2 for k in ('x','y','z')));groups.setdefault(p,[]).append(distance)
  result[role+'_movement']={str(p):{'movement':names.get(p,str(p)),'samples':len(v),'median_step':statistics.median(v),'p95_step':quant(v,.95),'max_step':max(v)} for p,v in groups.items()}
path=run/'SERVER-TRACE.csv'
if path.exists():
 groups={}
 for row in csv.DictReader(path.open()):groups.setdefault(int(row['phase']),[]).append(row)
 result['server']={str(p):{'movement':names.get(p,str(p)),'ticks':len(rows),'gap_p95':quant([float(r['gap']) for r in rows],.95),'gap_max':max(float(r['gap']) for r in rows),'min_y':min(min(float(r['ay']),float(r['by'])) for r in rows),'max_y':max(max(float(r['ay']),float(r['by'])) for r in rows)} for p,rows in groups.items()}
path=run/'PROCESS.csv'
if path.exists():
 groups={}
 for r in csv.DictReader(path.open()):groups.setdefault(r['role'],[]).append(r)
 result['processes']={role:{'samples':len(v),'first_working_set_mb':int(v[0]['working_set'])/1048576,'last_working_set_mb':int(v[-1]['working_set'])/1048576,'peak_working_set_mb':max(int(x['working_set']) for x in v)/1048576,'first_handles':int(v[0]['handles']),'last_handles':int(v[-1]['handles']),'peak_handles':max(int(x['handles']) for x in v)} for role,v in groups.items()}
(run/'SUMMARY.json').write_text(json.dumps(result,indent=2))
print(json.dumps({k:v for k,v in result.items() if k in ('bravo_rtt','server','processes')},indent=2))
