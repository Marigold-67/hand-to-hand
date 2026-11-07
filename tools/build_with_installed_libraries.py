#!/usr/bin/env python3
"""Optional offline compiler. Uses an existing 1.21.1 / NeoForge 21.1.250 installation.

Normal users should prefer gradlew build. This fallback opens no network sockets.
It compiles the same original sources; no Minecraft or dependency binaries are copied into the JAR.
"""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--minecraft-install', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--pal-jar', type=Path, required=True)
    args = parser.parse_args()
    project = Path(__file__).resolve().parents[1]
    install = args.minecraft_install.resolve()
    build = project / 'build' / 'offline'
    classes = build / 'classes'
    if classes.exists():
        assert classes.resolve().is_relative_to((project / 'build').resolve())
        shutil.rmtree(classes)
    classes.mkdir(parents=True)

    def manifest(version):
        data = json.loads((install / 'versions' / version / (version + '.json')).read_text())
        parent = manifest(data['inheritsFrom']) if 'inheritsFrom' in data else {'libraries': []}
        def key(lib):
            bits = lib['name'].split(':')
            return tuple(bits[:2] + bits[3:])
        libraries = {key(lib): lib for lib in parent.get('libraries', [])}
        libraries.update({key(lib): lib for lib in data.get('libraries', [])})
        return {'libraries': list(libraries.values())}

    libraries = install / 'libraries'
    cp = [
        libraries / 'net/neoforged/neoforge/21.1.250/neoforge-21.1.250-client.jar',
        libraries / 'net/minecraft/client/1.21.1-20240808.144430/client-1.21.1-20240808.144430-srg.jar',
        libraries / 'net/neoforged/neoforge/21.1.250/neoforge-21.1.250-universal.jar',
        args.pal_jar.resolve(),
    ]
    for lib in manifest('neoforge-21.1.250')['libraries']:
        artifact = (lib.get('downloads') or {}).get('artifact') or {}
        if artifact.get('path'):
            path = libraries / artifact['path']
            if path.is_file():
                cp.append(path)
    for required in cp[:4]:
        if not required.is_file():
            parser.error('Required installed file is missing: ' + str(required))
    import os
    compile_args = ['--release', '21', '-encoding', 'UTF-8', '-proc:none', '-cp', os.pathsep.join(map(str, cp)), '-d', str(classes)]
    compile_args.extend(map(str, (project / 'src/main/java').rglob('*.java')))
    argfile = build / 'compile.args'
    argfile.write_text('\n'.join('"' + arg.replace('\\', '/').replace('"', '\\"') + '"' for arg in compile_args), encoding='utf-8')
    javac = args.java_home / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac')
    subprocess.run([str(javac), '@' + str(argfile)], check=True)
    destination = project / 'build/libs/hand-in-hand-1.21.1-0.1.0-alpha.10.jar'
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as archive:
        def put(name, contents):
            info = zipfile.ZipInfo(name, (2026, 11, 4, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, contents)
        put('META-INF/MANIFEST.MF', b'Manifest-Version: 1.0\nImplementation-Version: 0.1.0-alpha.10\n\n')
        for root in [classes, project / 'src/main/resources']:
            for path in sorted(root.rglob('*')):
                if path.is_file():
                    put(path.relative_to(root).as_posix(), path.read_bytes())
        put('LICENSE', (project / 'LICENSE').read_bytes())
    print(destination)


if __name__ == '__main__':
    main()
