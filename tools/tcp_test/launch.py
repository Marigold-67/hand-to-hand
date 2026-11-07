import json, pathlib, subprocess, zipfile, shutil, sys, os
os.environ.setdefault("HANDINHAND_PROJECT","hand-holding-alpha10")
from build_local import ROOT,PROJECT,INSTALL,JAVA,PAL,VERSION,manifest,argfile
role=sys.argv[sys.argv.index('--role')+1] if '--role' in sys.argv else ''
shared=ROOT/os.environ.get('PAIR_RUN',('work/pair-network-emf' if '--emf' in sys.argv else 'work/pair-network'))
demo='--demo' in sys.argv
emf='--emf' in sys.argv
baseline='--baseline' in sys.argv
tether='--tether' in sys.argv
game=ROOT/('work/minecraft-playground' if demo else 'work/minecraft-test')
if emf:game=ROOT/('work/minecraft-playground-emf' if demo else 'work/minecraft-emf-test')
if baseline:game=ROOT/'work/minecraft-emf-baseline'
if tether:game=ROOT/('work/minecraft-tether-emf-test' if emf else 'work/minecraft-tether-test')
if role:game=shared/role
if role=="host" and os.environ.get("PAIR_GAME_HOST"):game=ROOT/os.environ["PAIR_GAME_HOST"]
game.mkdir(parents=True,exist_ok=True)
if demo:
    for name in ['DEMO-READY.txt','DEMO-ERROR.txt']:
        marker=game/name
        assert marker.resolve().is_relative_to((ROOT/'work').resolve())
        if marker.exists():marker.unlink()
(game/'TEST-RESULTS.txt').write_text('RUNNING\n')
if tether:(game/'TETHER-RESULTS.txt').write_text('RUNNING\n')
(game/'mods').mkdir(exist_ok=True)
natives=game/'natives';natives.mkdir(exist_ok=True)
shutil.copy2(PAL,game/'mods'/PAL.name)
mod=PROJECT/('build/libs/hand-in-hand-1.21.1-'+VERSION+'-smoke.jar')
for previous in (game/'mods').glob('hand-in-hand-*.jar'):
    assert previous.resolve().is_relative_to((ROOT/'work').resolve())
    if previous.name!=mod.name:previous.unlink()
shutil.copy2(mod,game/'mods'/mod.name)
if baseline:
    # A regression control: identical fixture with only the optional bridge disabled.
    with zipfile.ZipFile(mod) as src,zipfile.ZipFile(game/'mods'/mod.name,'w',zipfile.ZIP_DEFLATED) as dst:
        for name in src.namelist():
            data=src.read(name)
            if name=='handinhand.mixins.json':
                config=json.loads(data);config['client'].remove('EmfModelRootMixin');data=json.dumps(config).encode()
            dst.writestr(name,data)
if emf:
    instance=pathlib.Path(os.environ['HANDINHAND_ANIMATION_INSTANCE'])
    for name in ['entity_model_features-3.3.12-1.21-neoforge.jar','entity_texture_features-7.2.6-1.21-neoforge.jar','notenoughanimations-neoforge-1.12.6-mc1.21.1.jar','skinlayers3d-neoforge-1.11.4-mc1.21.1.jar']:
        shutil.copy2(instance/'mods'/name,game/'mods'/name)
    (game/'resourcepacks').mkdir(exist_ok=True)
    packs=list((instance/'resourcepacks').glob('*Fresh Moves*'))+[instance/'resourcepacks'/'FA+Player-v1.1.zip']
    for pack in packs:shutil.copy2(pack,game/'resourcepacks'/pack.name)
if '--pehkui' in sys.argv:
    dep=pathlib.Path(os.environ['HANDINHAND_PEHKUI_JAR'])
    shutil.copy2(dep,game/'mods'/dep.name)
d=manifest('neoforge-21.1.250')
def allowed(x):
    rules=x.get('rules')
    if not rules:return True
    good=False
    for r in rules:
        os=r.get('os',{})
        if os.get('name','windows')!='windows' or os.get('arch','x86_64') not in ('x86_64','amd64'):continue
        if 'features' in r:continue
        good=r['action']=='allow'
    return good
cp=[]
missing=[]
for lib in d['libraries']:
    if not allowed(lib):continue
    art=lib.get('downloads',{}).get('artifact',{})
    if not art or not art.get('path'):continue
    p=INSTALL/'libraries'/art['path']
    if not p.is_file():missing.append(str(p));continue
    cp.append(p)
    if lib['name'].split(':')[-1]=='natives-windows':
        with zipfile.ZipFile(p) as z:
            for n in z.namelist():
                if n.endswith('.dll'):(natives/pathlib.Path(n).name).write_bytes(z.read(n))
print('Missing launcher libraries:',missing,flush=True)
values={'natives_directory':str(natives),'library_directory':str(INSTALL/'libraries'),'classpath_separator':';','classpath':';'.join(map(str,cp)),
 'version_name':'neoforge-21.1.250','launcher_name':'HandInHandTest','launcher_version':'1','auth_player_name':{'host':'Pair_Observer','alpha':'Pair_Alpha','bravo':'Pair_Bravo'}.get(role,'HandTester'),'version_type':'release',
 'game_directory':str(game),'assets_root':str(INSTALL/'assets'),'assets_index_name':d['assetIndex']['id'],
 'auth_uuid':'cfe70579b78947168bfbf5f723812408','auth_access_token':'0','user_type':'legacy','clientid':'0','auth_xuid':'0'}
def expand(v):
    for k,x in values.items():v=v.replace('${'+k+'}',x)
    return v
def args(kind):
    out=[]
    for value in d['arguments'][kind]:
        if isinstance(value,dict):
            if not allowed(value):continue
            value=value['value']
        out.extend(expand(x) for x in (value if isinstance(value,list) else [value]))
    return out
cmd=['-Xmx2G','-Dhandinhand.smoothingStairs='+str('--smoothing-stairs' in sys.argv).lower(),'-Dhandinhand.legacyCamera='+str('--legacy-camera' in sys.argv).lower(),'-Dhandinhand.legacyPose='+str('--legacy-pose' in sys.argv).lower(),'-Dhandinhand.legacyGait='+str('--legacy-gait' in sys.argv).lower(),'-Dhandinhand.legacyPresentation='+str('--legacy-presentation' in sys.argv).lower(),'-Dhandinhand.legacyTimeline='+str('--legacy-timeline' in sys.argv).lower(),'-Dhandinhand.grabRestart='+str('--grab-restart' in sys.argv).lower(),'-Dhandinhand.grabConfigCheck='+str('--grab-config' in sys.argv).lower(),'-Dhandinhand.smoothingCheck='+str('--smoothing' in sys.argv).lower(),'-Dhandinhand.terrainCheck='+str('--terrain' in sys.argv).lower(),'-Dhandinhand.mobilityCheck='+str('--mobility' in sys.argv).lower(),'-Dhandinhand.pairRole='+role,'-Dhandinhand.pairDir='+str(shared),'-Dhandinhand.pairPort='+str(os.environ.get('PAIR_PROXY_PORT','49182')),'-Dhandinhand.hostPort='+str(os.environ.get('PAIR_HOST_PORT','49181')),'-Dhandinhand.smoke='+('false' if role or demo or tether else 'true'),'-Dhandinhand.demo='+('true' if demo else 'false'),'-Dhandinhand.tethertest='+str(tether).lower(),'-Dhandinhand.tetherDelay='+('3' if '--delay' in sys.argv else '0'),'-Dhandinhand.tetherScaled='+str('--scaled' in sys.argv).lower(),'-Dhandinhand.expectEmf='+str(emf).lower()]+args('jvm')+[d['mainClass']]+args('game')+['--width','1100','--height','750']
cmd.insert(0,'-Dhandinhand.terrainDirected='+str('--terrain-directed' in sys.argv).lower())
(game/'options.txt').write_text('onboardAccessibility:false\npauseOnLostFocus:false\nrenderDistance:3\nsimulationDistance:5\nmaxFps:40\n')
if emf:
    with (game/'options.txt').open('a') as options:options.write('resourcePacks:'+json.dumps(['vanilla']+['file/'+p.name for p in packs])+'\n')
log=(shared/(role+'.log') if role else ROOT/('work/minecraft-playground-emf-launch.log' if demo and emf else 'work/minecraft-playground-launch.log' if demo else 'work/minecraft-tether-emf-launch.log' if tether and emf else 'work/minecraft-tether-launch.log' if tether else 'work/minecraft-emf-baseline.log' if baseline else 'work/minecraft-emf-launch.log' if emf else 'work/minecraft-launch.log')).open('w')
p=subprocess.Popen([str(JAVA/'java.exe'),argfile(('pair-emf-' if emf else 'pair-')+game.name+'.args',cmd)],cwd=game,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
(ROOT/'work/minecraft.pid').write_text(str(p.pid))
print('Launched isolated Minecraft, PID',p.pid,'game',game,flush=True)
if '--wait' in sys.argv:sys.exit(p.wait())
