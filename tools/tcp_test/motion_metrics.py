import csv,json,math,statistics,sys
from pathlib import Path
run=Path(sys.argv[1]);report={}
def percentile(values,q):
 v=sorted(values);return v[min(len(v)-1,int((len(v)-1)*q))] if v else None
for view in ('alpha','bravo'):
 rows=list(csv.DictReader((run/(view+'-trace.csv')).open()));stages={}
 for stage in (2,3,4,5,6,63,64,65,66,11):
  part=[r for r in rows if int(r['stage'])==stage and 35<=int(r['serverStep'])<=85]
  if len(part)<10:continue
  velocity=[]
  for a,b in zip(part,part[1:]):
   elapsed=int(b['localTick'])-int(a['localTick'])
   if elapsed==1:velocity.append(tuple(float(b['own'+axis])-float(a['own'+axis]) for axis in ('X','Z')))
  speed=[math.hypot(*v) for v in velocity]
  accel=[math.hypot(b[0]-a[0],b[1]-a[1]) for a,b in zip(velocity,velocity[1:])]
  gaps=[math.sqrt(sum((float(r['other'+axis])-float(r['own'+axis]))**2 for axis in ('X','Y','Z'))) for r in part]
  stages[stage]={'samples':len(part),'mean_horizontal_step':statistics.mean(speed) if speed else 0,'step_std':statistics.pstdev(speed) if speed else 0,'step_max':max(speed,default=0),'acceleration_p95':percentile(accel,.95),'pair_distance_p95':percentile(gaps,.95)}
 report[view]=stages
result=run/'MOTION.json';result.write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
