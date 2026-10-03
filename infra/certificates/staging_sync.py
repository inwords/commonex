"""Unattended staging certificate propagation from the production renewal host.

Addresses, identities and pinned host keys live in protected host configuration.
Source files are read under the renewal lock. Delivery accepts only the fixed
receiver command, keeps certificate material in memory, and never logs it.
"""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tarfile
import tempfile


FILES = ('cert.pem', 'chain.pem', 'fullchain.pem', 'privkey.pem')
MAX_ARCHIVE = 1024 * 1024
MAX_FILE = 128 * 1024
CONFIG = Path('/etc/commonex/certificates/staging.json')
CONFIG_FIELDS = {'target', 'identity', 'known_hosts', 'source_directory'}
DESTINATION = re.compile(r'(?:[A-Za-z0-9_][A-Za-z0-9_.-]*@)?[A-Za-z0-9][A-Za-z0-9.-]*')
RENEWAL_LOCK = Path('/run/commonex/certificate-renewal.lock')
CERTBOT = Path('/etc/commonex/ssl/certbot')
APP = Path('/etc/commonex/app')
LOCK = Path('/run/commonex/deploy.lock')
INTENT = Path('/var/lib/commonex/activation-intent.json')


def validate_archive(data):
    if not data or len(data) > MAX_ARCHIVE:
        raise ValueError('certificate archive exceeds the delivery bound')
    files = {}
    with tarfile.open(fileobj=io.BytesIO(data), mode='r:') as archive:
        for item in archive:
            if (item.name not in FILES or item.name in files or not item.isfile()
                    or item.size <= 0 or item.size > MAX_FILE):
                raise ValueError('certificate archive has invalid entries')
            files[item.name] = archive.extractfile(item).read(MAX_FILE + 1)
    if set(files) != set(FILES):
        raise ValueError('certificate archive must contain exactly four files')
    return files


def protected_file(path, private=False, maximum=MAX_ARCHIVE):
    path = Path(path)
    if not path.is_absolute():
        raise ValueError('certificate delivery paths must be absolute')
    descriptor = os.open(path, os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0))
    with os.fdopen(descriptor, 'rb') as handle:
        metadata = os.fstat(handle.fileno())
        forbidden = 0o077 if private else 0o022
        if (path.is_symlink() or not stat.S_ISREG(metadata.st_mode)
                or metadata.st_uid != 0 or metadata.st_mode & forbidden
                or metadata.st_size > maximum):
            raise ValueError('certificate delivery inputs must be root-owned protected files')
        data = handle.read(maximum + 1)
        if len(data) > maximum:
            raise ValueError('certificate delivery input exceeds its bound')
        return data


def load_config(path):
    config = json.loads(protected_file(path, private=True, maximum=16384))
    if (not isinstance(config, dict) or set(config) != CONFIG_FIELDS
            or any(not isinstance(value, str) or not value for value in config.values())):
        raise ValueError('certificate delivery configuration has invalid fields')
    if DESTINATION.fullmatch(config['target']) is None:
        raise ValueError('certificate delivery destination is invalid')
    directory = Path(config['source_directory'])
    if not directory.is_absolute() or '..' in directory.parts:
        raise ValueError('certificate source directory must be absolute')
    protected_file(config['identity'], private=True, maximum=MAX_FILE)
    protected_file(config['known_hosts'])
    return config


def ssh_command(config):
    return ['ssh', '-F', '/dev/null', '-o', 'BatchMode=yes', '-o', 'IdentitiesOnly=yes',
            '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=20',
            '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=2',
            '-o', 'UserKnownHostsFile=' + config['known_hosts'],
            '-o', 'GlobalKnownHostsFile=/dev/null', '-o', 'LogLevel=ERROR',
            '-i', config['identity'], config['target'], 'install']


def read_certificate(directory, lock=RENEWAL_LOCK):
    import fcntl

    descriptor = os.open(lock, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, 'r+b') as handle:
        metadata = os.fstat(handle.fileno())
        if (not stat.S_ISREG(metadata.st_mode) or metadata.st_uid != 0
                or stat.S_IMODE(metadata.st_mode) != 0o600):
            raise PermissionError('untrusted certificate renewal lock')
        fcntl.flock(handle, fcntl.LOCK_EX)
        stream = io.BytesIO()
        with tarfile.open(fileobj=stream, mode='w') as archive:
            for name in FILES:
                # Certbot lineage entries are symlinks to its root-managed archive.
                with (Path(directory) / name).open('rb') as source:
                    metadata = os.fstat(source.fileno())
                    if (not stat.S_ISREG(metadata.st_mode) or metadata.st_uid != 0
                            or metadata.st_mode & 0o022 or not 0 < metadata.st_size <= MAX_FILE):
                        raise ValueError('invalid certificate source file')
                    contents = source.read(MAX_FILE + 1)
                    if not 0 < len(contents) <= MAX_FILE:
                        raise ValueError('certificate source file exceeds its bound')
                member = tarfile.TarInfo(name)
                member.size = len(contents)
                member.mode = 0o600
                archive.addfile(member, io.BytesIO(contents))
        data = stream.getvalue()
        validate_archive(data)
        return data


def send_certificate(data, config):
    validate_archive(data)
    result = subprocess.run(ssh_command(config), input=data, stdout=subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL, check=False, timeout=240)
    if result.returncode != 0:
        raise RuntimeError('staging certificate installation failed; inspect staging locally')


def synchronize(config):
    send_certificate(read_certificate(config['source_directory']), config)


def run_checked(command, cwd=None):
    result = subprocess.run(command, cwd=cwd, stdout=subprocess.PIPE,
                            stderr=subprocess.DEVNULL, check=False)
    if result.returncode:
        raise RuntimeError('certificate validation or nginx reconciliation failed')
    return result.stdout


def validate_certificate(directory, runner):
    cert = str(directory / 'cert.pem')
    chain = str(directory / 'chain.pem')
    key = str(directory / 'privkey.pem')
    runner(['openssl', 'x509', '-in', cert, '-checkend', '0', '-noout'])
    names = runner(['openssl', 'x509', '-in', cert, '-noout', '-ext', 'subjectAltName'])
    if b'DNS:*.commonex.ru' not in names.replace(b',', b' ').split():
        raise ValueError('certificate does not cover the staging domains')
    public_cert = runner(['openssl', 'x509', '-in', cert, '-pubkey', '-noout'])
    public_key = runner(['openssl', 'pkey', '-in', key, '-pubout'])
    if public_cert != public_key:
        raise ValueError('certificate and private key do not match')
    runner(['openssl', 'verify', '-CApath', '/etc/ssl/certs', '-untrusted', chain, cert])
    expected_fullchain = (directory / 'cert.pem').read_bytes().strip() + b'\n' + (
        directory / 'chain.pem').read_bytes().strip()
    if (directory / 'fullchain.pem').read_bytes().strip() != expected_fullchain:
        raise ValueError('fullchain does not match the certificate and chain')


def trusted_directory(path):
    if path.is_symlink():
        raise ValueError('certificate directory must not be a symlink')
    path.mkdir(mode=0o755, parents=True, exist_ok=True)
    current = path
    while current != current.parent:
        metadata = current.lstat()
        if (not stat.S_ISDIR(metadata.st_mode) or metadata.st_uid != 0
                or metadata.st_mode & 0o022):
            raise ValueError('certificate path must be root-owned and protected')
        current = current.parent


def durable_directory(path):
    descriptor = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def replace_link(path, target):
    temporary = path.with_name('.' + path.name + '.delivery')
    if temporary.exists() or temporary.is_symlink():
        temporary.unlink()
    temporary.symlink_to(target)
    os.replace(temporary, path)


def install_certificate(data, certbot=CERTBOT, app=APP, lock=LOCK, intent=INTENT,
                        runner=run_checked):
    import fcntl

    if os.geteuid() != 0:
        raise PermissionError('certificate installation requires root')
    files = validate_archive(data)
    if not lock.parent.is_dir() or lock.parent.is_symlink():
        raise ValueError('bootstrap the deployment lock directory first')
    descriptor = os.open(lock, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, 'r+b') as handle:
        fcntl.flock(handle, fcntl.LOCK_EX)
        if intent.exists() or intent.is_symlink():
            raise ValueError('unfinished application activation blocks certificate delivery')
        live = certbot / 'live/commonex.ru'
        archive = certbot / 'archive/commonex.ru'
        trusted_directory(live)
        trusted_directory(archive)
        with tempfile.TemporaryDirectory(prefix='.delivery-', dir=archive) as temporary:
            staging = Path(temporary)
            for name, contents in files.items():
                path = staging / name
                with path.open('xb') as output:
                    os.fchmod(output.fileno(), 0o600)
                    output.write(contents)
                    output.flush()
                    os.fsync(output.fileno())
            validate_certificate(staging, runner)
        version = hashlib.sha256(data).hexdigest()
        previous = {}
        for name in FILES:
            path = live / name
            if path.is_symlink():
                previous[name] = ('link', os.readlink(path))
            elif path.exists():
                metadata = path.lstat()
                if not stat.S_ISREG(metadata.st_mode) or metadata.st_size > MAX_FILE:
                    raise ValueError('existing certificate file is invalid')
                previous[name] = ('file', path.read_bytes(), metadata)
            else:
                previous[name] = ('absent',)
        compose = ['docker', 'compose', '--env-file', '.env', '-f', 'docker-compose-prod.yml']
        running = bool(runner(compose + ['ps', '--status', 'running', '-q', 'nginx'], cwd=app).strip())
        try:
            targets = {}
            for name, contents in files.items():
                versioned = archive / (name[:-4] + '-' + version + '.pem')
                descriptor = os.open(versioned, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600) if not versioned.exists() else None
                if descriptor is not None:
                    with os.fdopen(descriptor, 'wb') as output:
                        os.fchown(output.fileno(), 0, 1001)
                        os.fchmod(output.fileno(), 0o640 if name == 'privkey.pem' else 0o644)
                        output.write(contents)
                        output.flush()
                        os.fsync(output.fileno())
                elif (versioned.is_symlink() or versioned.read_bytes() != contents
                      or versioned.stat().st_uid != 0 or versioned.stat().st_gid != 1001
                      or stat.S_IMODE(versioned.stat().st_mode) != (0o640 if name == 'privkey.pem' else 0o644)):
                    raise ValueError('retained certificate version conflicts with incoming material')
                targets[name] = '../../archive/commonex.ru/' + versioned.name
            durable_directory(archive)
            for name, target in targets.items():
                replace_link(live / name, target)
            durable_directory(live)
            if running:
                runner(compose + ['exec', '-T', 'nginx', 'nginx', '-t', '-c', '/tmp/commonex-nginx.conf'], cwd=app)
                runner(compose + ['exec', '-T', 'nginx', 'nginx', '-s', 'reload'], cwd=app)
        except Exception:
            for name, backup in previous.items():
                path = live / name
                if backup[0] == 'link':
                    replace_link(path, backup[1])
                elif backup[0] == 'file':
                    descriptor, filename = tempfile.mkstemp(prefix='.' + name + '.restore-', dir=live)
                    temporary = Path(filename)
                    with os.fdopen(descriptor, 'wb') as output:
                        os.fchown(output.fileno(), backup[2].st_uid, backup[2].st_gid)
                        os.fchmod(output.fileno(), stat.S_IMODE(backup[2].st_mode))
                        output.write(backup[1])
                        output.flush()
                        os.fsync(output.fileno())
                    os.replace(temporary, path)
                elif path.exists() or path.is_symlink():
                    path.unlink()
            durable_directory(live)
            if running:
                runner(compose + ['exec', '-T', 'nginx', 'nginx', '-t', '-c', '/tmp/commonex-nginx.conf'], cwd=app)
                runner(compose + ['exec', '-T', 'nginx', 'nginx', '-s', 'reload'], cwd=app)
            raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, default=CONFIG)
    parser.add_argument('--install', action='store_true', help=argparse.SUPPRESS)
    options = parser.parse_args()
    try:
        if options.install:
            install_certificate(sys.stdin.buffer.read(MAX_ARCHIVE + 1))
        else:
            synchronize(load_config(options.config))
            print('Staging wildcard certificate synchronized.')
    except Exception:
        print('Certificate delivery failed; no certificate material was logged.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
