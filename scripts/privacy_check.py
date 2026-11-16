"""Scan publishable source or the exact Git index. Never prints matching secret text."""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = json.loads((ROOT / 'release-files.json').read_text(encoding='utf-8'))
EXTENSIONS = {'.md', '.json', '.mjs', '.java', '.py', '.ps1', '.yml', '.yaml', '.circ'}
PATTERNS = {
    'personal Windows home path': re.compile(r'[A-Za-z]:[\\/](?:Users|Documents and Settings)[\\/][^\s"<>]+', re.I),
    'personal Unix home path': re.compile(r'/(?:home|Users)/[A-Za-z0-9_.-]+/'),
    'GitHub credential': re.compile(r'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})'),
    'OpenAI-style secret': re.compile(r'\bsk-(?:proj-)?[A-Za-z0-9_-]{30,}'),
    'private key': re.compile(r'-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----'),
    'credential assignment': re.compile(r'''(?im)^\s*(?:GH_TOKEN|GITHUB_TOKEN|OPENAI_API_KEY|password)\s*[=:]\s*["']?[A-Za-z0-9_./+-]{12,}'''),
}

def source_files():
    paths = {ROOT / name for name in SPEC['files']}
    for folder in SPEC['directories']:
        base = ROOT / folder
        for item in base.rglob('*'):
            if item.is_symlink():
                raise ValueError(f'Symlink is not publishable: {item.relative_to(ROOT)}')
            if item.is_file() and '__pycache__' not in item.parts:
                paths.add(item)
    for item in sorted(paths):
        if item.is_symlink() or not item.is_file():
            raise ValueError(f'Missing/non-regular publish file: {item.relative_to(ROOT)}')
        if item.suffix and item.suffix not in EXTENSIONS and item.name not in SPEC['files']:
            raise ValueError(f'Unexpected publish file type: {item.relative_to(ROOT)}')
        yield item.relative_to(ROOT).as_posix(), item.read_bytes()

def indexed_files():
    names = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode('utf-8').split('\0')
    allowed = {name for name, _ in source_files()}
    for name in filter(None, names):
        if name not in allowed:
            raise ValueError(f'Unexpected indexed file outside release allowlist: {name}')
        mode = subprocess.check_output(['git', 'ls-files', '-s', '--', name], cwd=ROOT).decode().split()[0]
        if mode == '120000':
            raise ValueError(f'Indexed symlink: {name}')
        yield name, subprocess.check_output(['git', 'show', ':' + name], cwd=ROOT)

def scan(files):
    findings = []
    manifest = []
    for name, data in files:
        if name.endswith(('.log', '.pyc', '.jar', '.class', '.zip', '.docx', '.pdf', '.png', '.mp4')) or pathlib.PurePosixPath(name).name.startswith('.env'):
            findings.append((name, 'prohibited file type'))
        try:
            text = data.decode('utf-8-sig')
        except UnicodeDecodeError:
            findings.append((name, 'non-text source file'))
            continue
        for title, pattern in PATTERNS.items():
            if pattern.search(text):
                findings.append((name, title))
        manifest.append({'path': name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
    return findings, manifest

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--git', action='store_true', help='Scan actual staged/index bytes, not just working files.')
    args = parser.parse_args()
    try:
        findings, manifest = scan(indexed_files() if args.git else source_files())
    except (ValueError, subprocess.CalledProcessError) as error:
        print(str(error), file=sys.stderr)
        return 1
    if findings:
        for name, reason in findings:
            print(f'BLOCKED {name}: {reason}', file=sys.stderr)
        return 1
    print(f'Privacy pattern scan passed: {len(manifest)} allowlisted text files; no matching credentials/home paths or prohibited artifacts.')
    print('Review the staged diff/archive manifest too; pattern scanning is not a universal privacy guarantee.')
    return 0

if __name__ == '__main__':
    sys.exit(main())
