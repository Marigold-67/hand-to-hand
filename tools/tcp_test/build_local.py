import json, os, pathlib, subprocess, sys, zipfile, shutil
ROOT=pathlib.Path(__file__).resolve().parents[2]
PROJECT=ROOT
(ROOT/'work').mkdir(exist_ok=True)
VERSION=__import__('re').search(r'version="([^"]+)"',(PROJECT/'src/main/resources/META-INF/neoforge.mods.toml').read_text()).group(1)
INSTALL=pathlib.Path(os.environ['HANDINHAND_MC_INSTALL'])
JAVA=pathlib.Path(os.environ['JAVA_HOME'])/'bin'
CACHE=pathlib.Path(os.environ.get('GRADLE_USER_HOME',str(pathlib.Path.home()/'.gradle')))/'caches/modules-2/files-2.1'
PAL=pathlib.Path(os.environ['HANDINHAND_PAL_JAR'])

def manifest(version):
    data=json.loads((INSTALL/f'versions/{version}/{version}.json').read_text())
    if 'inheritsFrom' not in data:return data
    parent=manifest(data['inheritsFrom'])
    def key(x):
        bits=x['name'].split(':');return ':'.join(bits[:2]+bits[3:])
    libs={key(x):x for x in parent.get('libraries',[])}
    libs.update({key(x):x for x in data.get('libraries',[])})
    parent['libraries']=list(libs.values())
    parent['mainClass']=data['mainClass']
    for k in ['game','jvm']:parent.setdefault('arguments',{}).setdefault(k,[]).extend(data.get('arguments',{}).get(k,[]))
    parent['id']=data['id'];return parent

def classpath():
    data=manifest('neoforge-21.1.250')
    paths=[INSTALL/'libraries/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-client.jar',INSTALL/'libraries/net/minecraft/client/1.21.1-20240808.144430/client-1.21.1-20240808.144430-srg.jar',INSTALL/'libraries/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-universal.jar',PAL]
    for lib in data['libraries']:
        art=lib.get('downloads',{}).get('artifact',{})
        if 'path' in art:
            p=INSTALL/'libraries'/art['path']
            if p.is_file():paths.append(p)
    for group in ['org.junit.jupiter','org.junit.platform','org.opentest4j','org.apiguardian']:
        paths.extend(p for p in (CACHE/group).rglob('*.jar') if not p.name.endswith('-sources.jar'))
    return list(dict.fromkeys(paths))

def argfile(name,args):
    p=ROOT/'work'/name
    p.write_text('\n'.join('"'+str(x).replace('\\','/').replace('"','\\"')+'"' for x in args),encoding='utf8')
    return '@'+str(p)

def compile(smoke=False):
    out=PROJECT/('build/local-smoke' if smoke else 'build/local-classes')
    assert out.resolve().is_relative_to((PROJECT/'build').resolve())
    if out.exists():shutil.rmtree(out)
    out.mkdir(parents=True,exist_ok=True)
    cp=';'.join(map(str,classpath()))
    files=list((PROJECT/'src/main/java').rglob('*.java'))
    if smoke:files+=list((PROJECT/'src/smoke/java').rglob('*.java'))
    args=['--release','21','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(out)]+list(map(str,files))
    subprocess.run([str(JAVA/'javac.exe'),argfile('compile.args',args)],check=True)
    return out,cp

def package(out,smoke=False):
    jar=PROJECT/'build/libs'/(('hand-in-hand-1.21.1-'+VERSION)+('-smoke' if smoke else '')+'.jar')
    jar.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\nImplementation-Version: '+VERSION+'\n\n')
        bases=[out,PROJECT/'src/main/resources']+([PROJECT/'src/smoke/resources'] if smoke else [])
        for base in bases:
            for f in base.rglob('*'):
                if f.is_file():
                    name=f.relative_to(base).as_posix()
                    if smoke and name=='META-INF/neoforge.mods.toml':z.writestr(name,f.read_text()+'\n[[mixins]]\nconfig="handinhand-smoke.mixins.json"\n')
                    else:z.write(f,name)
        if (PROJECT/'LICENSE').is_file():z.write(PROJECT/'LICENSE','LICENSE')
    print('BUILT',jar,flush=True)
    return jar

if __name__=='__main__':
    smoke='--smoke' in sys.argv
    out,cp=compile(smoke)
    package(out,smoke)
    if '--tests' in sys.argv:
        testout=PROJECT/'build/local-tests';testout.mkdir(parents=True,exist_ok=True)
        tests=list((PROJECT/'src/test/java').rglob('*.java'))
        subprocess.run([str(JAVA/'javac.exe'),argfile('tests.args',['--release','21','-proc:none','-cp',str(out)+';'+cp,'-d',str(testout)]+list(map(str,tests)))],check=True)
        subprocess.run([str(JAVA/'java.exe'),argfile('run-tests.args',['-cp',str(out)+';'+str(testout)+';'+cp,'dev.marigold.handinhand.LocalTestRunner'])],check=True)
