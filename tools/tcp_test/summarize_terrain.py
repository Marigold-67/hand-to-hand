from pathlib import Path
import csv,json,statistics,math,sys
r=Path(__file__).resolve().parents[2]
def pct(values,f):
    values=sorted(values);return values[min(len(values)-1,int((len(values)-1)*f))]
for name in sys.argv[1:]:
    folder=r/name
    status=(folder/'DONE.txt').read_text().strip() if (folder/'DONE.txt').exists() else (folder/'FAIL.txt').read_text().strip() if (folder/'FAIL.txt').exists() else 'RUNNING'
    out={'run':folder.name,'status':status,'rtt':{},'server':{},'clients':{}}
    for actor in ['alpha','bravo']:
        f=folder/(actor+'-RTT.csv')
        if f.exists():
            rows=[row for row in csv.DictReader(f.open()) if row.get('phase')=='10' and int(row['step'])>250]
            values=[float(row['rtt_ms']) for row in rows]
            if values:out['rtt'][actor]={'samples':len(values),'median_ms':statistics.median(values),'p95_ms':pct(values,.95),'max_ms':max(values)}
        f=folder/(actor+'-trace.csv')
        if f.exists():
            rows=list(csv.DictReader(f.open()));values=[]
            for prev,row in zip(rows,rows[1:]):
                if prev['stage']==row['stage']=='10' and int(row['serverStep'])>250:
                    values.append(math.dist([float(prev['own'+a]) for a in 'XYZ'],[float(row['own'+a]) for a in 'XYZ']))
            if values:out['clients'][actor]={'physical_step_p95':pct(values,.95),'physical_step_max':max(values),'samples':len(values)}
    f=folder/'TERRAIN-TRACE.csv'
    if f.exists():
        groups={}
        for row in csv.DictReader(f.open()):groups.setdefault(row['phase'],[]).append(row)
        for phase,rows in groups.items():
            if phase not in {'2','4','6','8','9','10','12','14'}:continue
            if phase in {'9','10'}:rows=[row for row in rows if int(row['step'])>121]
            if not rows:continue
            out['server'][phase]={'ticks':len(rows),'horizontal_gap_max':max(float(row['horizontalGap']) for row in rows),'peak_a':max(float(row['ay']) for row in rows),'peak_b':max(float(row['by']) for row in rows),'min_y':min(min(float(row['ay']),float(row['by'])) for row in rows),'end_z_a':float(rows[-1]['az']),'end_z_b':float(rows[-1]['bz'])}
    warnings=[];resources=[]
    for actor in ['host','alpha','bravo']:
        f=folder/actor/'logs/latest.log'
        if not f.exists():continue
        for line in f.read_text(errors='replace').splitlines():
            if any(term in line for term in ['moved too quickly!','moved wrongly!','floating too long','Hand in Hand could not apply','Caught error loading resourcepacks']):warnings.append(actor+': '+line)
            if 'Reloading ResourceManager:' in line:resources.append(actor+': '+line)
    out['movement_warnings']=warnings
    (folder/'SUMMARY.json').write_text(json.dumps(out,indent=2))
    (folder/'movement-warnings.txt').write_text('\n'.join(warnings)+'\n')
    (folder/'resource-pack-load.txt').write_text('\n'.join(resources)+'\n')
    print(json.dumps(out,indent=2))
