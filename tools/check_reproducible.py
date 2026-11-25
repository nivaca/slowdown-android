#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Comprueba que una versión publicada se pueda compilar de nuevo, igual, como
lo hace F-Droid: clona la etiqueta en una carpeta temporal (otra ruta, otra
máquina para el compilador), la compila sin la clave de firma, baja de GitHub
el APK publicado y compara los dos, entrada por entrada: los nombres, el
orden, la compresión, el contenido y los datos del zip (fechas, campos
extra, posiciones). Solo pueden diferir los archivos de la firma.

    python3 tools/check_reproducible.py [VERSIÓN]

Sin VERSIÓN, la de app/build.gradle.kts. Necesita git, gh y el SDK de
Android (local.properties o ANDROID_HOME).
"""

import hashlib
import os
import re
import subprocess
import sys
import tempfile
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = 'nivaca/slowdown-android'
FIELDS = ('filename', 'date_time', 'compress_type', 'extra', 'external_attr', 'create_system', 'flag_bits',
          'CRC', 'compress_size', 'file_size', 'header_offset')


def signature(name: str) -> bool:
    # (los archivos de la firma v1, si los hubiera; la v2 y la v3 van fuera de las entradas)
    return name.startswith('META-INF/') and name.rsplit('.', 1)[-1] in ('SF', 'RSA', 'EC', 'DSA', 'MF')


def compare(published: str, rebuilt: str) -> list[str]:
    """What differs between the two APKs, apart from the signature."""
    a, b = zipfile.ZipFile(published), zipfile.ZipFile(rebuilt)
    ea = [i for i in a.infolist() if not signature(i.filename)]
    eb = [i for i in b.infolist() if not signature(i.filename)]
    problems = []
    if [i.filename for i in ea] != [i.filename for i in eb]:
        problems.append('the entries are not the same, or not in the same order')
    for x, y in zip(ea, eb):
        fields = [f for f in FIELDS if getattr(x, f) != getattr(y, f)]
        if hashlib.sha256(a.read(x)).digest() != hashlib.sha256(b.read(y)).digest():
            fields.append('contents')
        if fields:
            problems.append(f'{x.filename}: {", ".join(fields)}')
    return problems


def main() -> int:
    if len(sys.argv) > 1:
        version = sys.argv[1]
    else:
        text = open(os.path.join(ROOT, 'app', 'build.gradle.kts'), encoding='utf-8').read()
        version = re.search(r'versionName = "([\d.]+)"', text)[1]
    sdk = os.environ.get('ANDROID_HOME', '')
    props = os.path.join(ROOT, 'local.properties')
    if os.path.isfile(props):
        m = re.search(r'^sdk\.dir=(.+)$', open(props, encoding='utf-8').read(), re.MULTILINE)
        sdk = m[1].strip() if m else sdk
    with tempfile.TemporaryDirectory(prefix='slowdown-repro-') as tmp:
        src = os.path.join(tmp, 'src')
        print(f'Cloning v{version} into {src}…', flush=True)
        subprocess.run(['git', 'clone', '--quiet', '--branch', f'v{version}', f'https://github.com/{REPO}.git', src],
                       check=True)
        with open(os.path.join(src, 'local.properties'), 'w', encoding='utf-8') as f:
            f.write(f'sdk.dir={sdk}\n')
        print('Building it, without the signing key…', flush=True)
        subprocess.run([os.path.join(src, 'gradlew'), '--quiet', 'assembleRelease'], cwd=src, check=True)
        rebuilt = os.path.join(src, 'app', 'build', 'outputs', 'apk', 'release', 'app-release-unsigned.apk')
        subprocess.run(['gh', 'release', 'download', f'v{version}', '--repo', REPO, '--pattern',
                        f'slowdown-{version}.apk', '--dir', tmp], check=True)
        problems = compare(os.path.join(tmp, f'slowdown-{version}.apk'), rebuilt)
    if problems:
        print(f'v{version} is NOT reproducible:')
        for p in problems[:20]:
            print('  ' + p)
        return 1
    print(f'v{version} is reproducible: the published APK is the rebuilt one, with its signature.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
