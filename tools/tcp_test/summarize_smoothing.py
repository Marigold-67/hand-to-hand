from pathlib import Path
import csv,json,math,statistics,sys,bisect,uuid,hashlib
r=Path(__file__).resolve().parents[2]
def pct(v,f):return sorted(v)[min(len(v)-1,int((len(v)-1)*f))] if v else None
def stats(v):return {'n':len(v),'median':statistics.median(v),'p95':pct(v,.95),'max':max(v)} if v else {}
def quat(x,y,z):
    sx,cx=math.sin(x/2),math.cos(x/2);sy,cy=math.sin(y/2),math.cos(y/2);sz,cz=math.sin(z/2),math.cos(z/2)
    return (sx*cy*cz-cx*sy*sz,cx*sy*cz+sx*cy*sz,cx*cy*sz-sx*sy*cz,cx*cy*cz+sx*sy*sz)
aid=str(uuid.UUID(bytes=hashlib.md5(b'OfflinePlayer:Pair_Alpha').digest(),version=3))
bid=str(uuid.UUID(bytes=hashlib.md5(b'OfflinePlayer:Pair_Bravo').digest(),version=3))
for name in sys.argv[1:]:
    d=r/name;out={'run':d.name,'status':(d/'DONE.txt').read_text().strip() if (d/'DONE.txt').exists() else (d/'FAIL.txt').read_text().strip() if (d/'FAIL.txt').exists() else 'RUNNING','phases':{}}
    cam=list(csv.DictReader((d/'bravo-camera.csv').open()));sampling=list(csv.DictReader((d/'bravo-sampling.csv').open()));pose=list(csv.DictReader((d/'host-pose.csv').open()));rtt=list(csv.DictReader((d/'bravo-RTT.csv').open()));gaps=list(csv.DictReader((d/'host-render.csv').open()))
    for phase in ['2','4','6','8','10']:
        s={};select=lambda rows:[a for a in rows if a['phase']==phase and 25<int(a['step'])<290]
        rows=select(cam);times=[int(a['nanos'])/1e9 for a in rows]
        values=[]
        if len(rows)>3:
            t=times[0]+.05
            while t<times[-1]-.05:
                i=bisect.bisect_right(times,t);a,b=rows[i-1],rows[i];f=(t-times[i-1])/max(1e-9,times[i]-times[i-1]);values.append([float(a[k])+(float(b[k])-float(a[k]))*f for k in ['x','y','z']]);t+=.05
        acc=[math.dist([b[k]-a[k] for k in range(3)],[c[k]-b[k] for k in range(3)]) for a,b,c in zip(values,values[1:],values[2:])]
        s['camera_second_difference_50ms']=stats(acc)
        if rows and 'rawX' in rows[0]:
            raw=[];t=times[0]+.05
            while t<times[-1]-.05:
                i=bisect.bisect_right(times,t);a,b=rows[i-1],rows[i];f=(t-times[i-1])/max(1e-9,times[i]-times[i-1]);raw.append([float(a[k])+(float(b[k])-float(a[k]))*f for k in ['rawX','rawY','rawZ']]);t+=.05
            s['raw_camera_second_difference_50ms']=stats([math.dist([b[k]-a[k] for k in range(3)],[c[k]-b[k] for k in range(3)]) for a,b,c in zip(raw,raw[1:],raw[2:])])
            s['view_filter_offset']=stats([math.dist([float(a[k]) for k in ['x','y','z']],[float(a[k]) for k in ['rawX','rawY','rawZ']]) for a in rows])
        sample=select(sampling);s['vertical_tracking_error']=stats([abs(float(a['ownY'])-float(a['sampledY'])) for a in sample]);s['newest_sample_ground_disagreements']=sum(a['latestGround']!=a['sampleGround'] for a in sample)
        angular=[]
        ids={a['id'] for a in pose}&{aid,bid}
        for pid in ids:
            rows=[a for a in select(pose) if a['id']==pid];side='l' if pid==aid else 'r'
            for a,b in zip(rows,rows[1:]):
                dt=(int(b['nanos'])-int(a['nanos']))/1e9
                if .005<dt<.1:
                    q=quat(*[float(a[side+k]) for k in 'xyz']);q2=quat(*[float(b[side+k]) for k in 'xyz']);angular.append(math.degrees(2*math.acos(min(1,abs(sum(v*w for v,w in zip(q,q2))))))/dt)
        s['held_arm_degrees_per_second']=stats(angular)
        s['rtt_ms']=stats([float(a['rtt_ms']) for a in select(rtt)])
        s['rendered_hand_gap']=stats([float(a['gap']) for a in gaps if a['stage']==phase and 25<int(a['step'])<290])
        out['phases'][phase]=s
    warnings=[]
    for role in ['host','alpha','bravo']:
        f=d/role/'logs/latest.log'
        for line in f.read_text(errors='replace').splitlines():
            if any(t in line for t in ['moved wrongly!','moved too quickly!','floating too long','Hand in Hand could not apply','Caught error loading resourcepacks']):warnings.append(role+': '+line)
    out['movement_warnings']=warnings
    (d/'SMOOTHING-SUMMARY.json').write_text(json.dumps(out,indent=2));print(json.dumps(out,indent=2))
