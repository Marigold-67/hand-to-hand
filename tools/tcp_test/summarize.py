import csv,json,math,sys
from pathlib import Path

root=Path(__file__).resolve().parents[2]
run=root/sys.argv[1]
result={"run":run.name,"views":{}}
def quantile(values,q):
    values=sorted(values)
    return values[min(len(values)-1,int((len(values)-1)*q))] if values else None
for view in ('host','alpha','bravo'):
    rows=list(csv.DictReader((run/(view+'-render.csv')).open()))
    phases={}
    for stage in (2,3,4,5,6,61,62,9,11):
        selected=[r for r in rows if int(r['stage'])==stage and int(r['step'])>=30]
        if not selected:continue
        gaps=[float(r['gap']) for r in selected]
        phases[stage]={"samples":len(selected),"hand_gap_p95_blocks":quantile(gaps,.95),"hand_gap_max_blocks":max(gaps)}
        for actor in ('A','B'):
            angles=[float(r['leg'+actor]) for r in selected]
            phases[stage]['leg'+actor+'_angle_span_radians']=max(angles)-min(angles)
            yaw=[float(r['yaw'+actor]) for r in selected]
            phases[stage]['body'+actor+'_max_turn_per_render_degrees']=max((abs((b-a+180)%360-180) for a,b in zip(yaw,yaw[1:])),default=0)
    result['views'][view]=phases
for view in ('alpha','bravo'):
    rows=list(csv.DictReader((run/(view+'-trace.csv')).open()))
    result['views'][view]['grounded_fraction']={str(stage):sum(r['ownGround']=='true' for r in rows if int(r['stage'])==stage and int(r['serverStep'])>=30)/max(1,sum(int(r['stage'])==stage and int(r['serverStep'])>=30 for r in rows)) for stage in (2,3,4,5,6)}
(run/'METRICS.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result,indent=2))
