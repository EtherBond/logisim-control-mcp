"""Deterministic, allowlisted ZIP packaging; requires only Python standard library."""
import argparse
import hashlib
import json
import pathlib
import sys
import zipfile
from privacy_check import ROOT, SPEC, source_files, scan

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--with-build', action='store_true', help='Include only the four locally compiled bridge class files, never the external JAR.')
    args = parser.parse_args()
    files = list(source_files())
    findings, manifest = scan(files)
    if findings:
        for name, reason in findings:
            print(f'BLOCKED {name}: {reason}', file=sys.stderr)
        return 1
    if args.with_build:
        for name in SPEC['compiledClasses']:
            item = ROOT / 'build' / name
            if item.is_symlink() or not item.is_file():
                raise ValueError('Missing bridge classes. Run npm run build first.')
            data = item.read_bytes()
            files.append(('build/' + name, data))
            manifest.append({'path': 'build/' + name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
    version = json.loads((ROOT / 'package.json').read_text(encoding='utf-8'))['version']
    name = json.loads((ROOT / 'plugin.json').read_text(encoding='utf-8'))['name']
    destination = ROOT / 'dist'
    destination.mkdir(exist_ok=True)
    output = destination / f'{name}-{version}.zip'
    archive_manifest = {'version': version, 'includesBuild': args.with_build, 'files': sorted(manifest, key=lambda x: x['path'])}
    files.append(('RELEASE-MANIFEST.json', (json.dumps(archive_manifest, indent=2) + '\n').encode()))
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for relative, data in sorted(files):
            info = zipfile.ZipInfo(name + '/' + relative, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    (destination / (output.name + '.sha256')).write_text(digest + '  ' + output.name + '\n', encoding='utf8')
    print(f'Created {output.name}: {len(files)} files; SHA256 {digest}')
    return 0

if __name__ == '__main__':
    sys.exit(main())
