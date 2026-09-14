#!/usr/bin/env python3
"""Host-side certificate renewal and monitoring; compatible with Python 3.9."""

import argparse
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import socket
import ssl
import stat
import subprocess
import sys
import tempfile
import time
import urllib.request

if __package__:
    from .host_runtime import command, lock
else:
    from host_runtime import command, lock


CONFIG = Path('/etc/commonex/certificates/renewal.json')
DNS_CONFIG = Path('/etc/commonex/certificates/dns.json')
KEY = Path('/etc/commonex/certificates/yandex-key.json')
CERTIFICATES = Path('/etc/commonex/ssl/certbot')
STATE = Path('/var/lib/commonex/certificates/renewal-state.json')
RENEWAL_LOCK = Path('/run/commonex/certificate-renewal.lock')
MONITOR_LOCK = Path('/run/commonex/certificate-monitor.lock')
DEPLOYMENT_LOCK = Path('/run/commonex/deploy.lock')
ACTIVATION_INTENT = Path('/var/lib/commonex/activation-intent.json')
PUBLIC_HOSTS = ('commonex.ru', 'www.commonex.ru', 'dev-api.commonex.ru',
                'gf.commonex.ru', 'grpc.commonex.ru')
COMPOSE = ['docker', 'compose', '--project-directory', '/etc/commonex/app',
           '--env-file', '/etc/commonex/app/.env', '-f',
           '/etc/commonex/app/docker-compose-prod.yml']


def private_json(path):
    with os.fdopen(os.open(path, os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0))) as source:
        metadata = os.fstat(source.fileno())
        if (not stat.S_ISREG(metadata.st_mode) or metadata.st_uid != 0
                or stat.S_IMODE(metadata.st_mode) & 0o077):
            raise PermissionError('configuration/state must be a root-only regular file')
        return json.load(source)


def save_state(state):
    STATE.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix='.renewal-', dir=STATE.parent)
    try:
        with os.fdopen(descriptor, 'w') as output:
            json.dump(state, output)
            output.write('\n')
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, STATE)
        directory = os.open(STATE.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def load_state():
    try:
        state = private_json(STATE)
    except FileNotFoundError:
        return {'last_attempt': 0, 'last_success': 0, 'success': 0}
    for key in ('last_attempt', 'last_success', 'success'):
        if not isinstance(state.get(key), (int, float)) or state[key] < 0:
            raise ValueError('invalid renewal state')
    if state['success'] not in (0, 1):
        raise ValueError('invalid renewal result')
    return state


def certbot_command(image, dry_run=False):
    if not re.fullmatch(r'(?:[A-Za-z0-9._:/-]+@)?sha256:[a-f0-9]{64}', image):
        raise ValueError('Certbot image must be an immutable digest or local image ID')
    arguments = ['docker', 'run', '--rm', '--name', 'commonex-certificate-renewal',
                 '--cap-drop=ALL', '--security-opt=no-new-privileges:true',
                 '--read-only', '--tmpfs', '/tmp:rw,noexec,nosuid,size=64m',
                 '--tmpfs', '/var/lib/letsencrypt:rw,nosuid,size=32m',
                 '--tmpfs', '/var/log/letsencrypt:rw,nosuid,size=32m',
                 '--mount', f'type=bind,src={CERTIFICATES},dst=/etc/letsencrypt',
                 '--mount', f'type=bind,src={DNS_CONFIG},dst=/run/secrets/config.json,readonly',
                 '--mount', f'type=bind,src={KEY},dst=/run/secrets/yandex-key.json,readonly',
                 image, 'renew', '--non-interactive', '--cert-name', 'commonex.ru',
                 '--authenticator', 'manual', '--preferred-challenges', 'dns',
                 '--manual-auth-hook',
                 'python -m commonex_certificates.dns_hook auth --config /run/secrets/config.json',
                 '--manual-cleanup-hook',
                 'python -m commonex_certificates.dns_hook cleanup --config /run/secrets/config.json',
                 '--no-directory-hooks', '--pre-hook', '', '--post-hook', '', '--deploy-hook', '']
    if dry_run:
        arguments.append('--dry-run')
    return arguments


def mark_interrupted():
    marker = CERTIFICATES / '.commonex-dns-reconciliation-required'
    try:
        descriptor = os.open(marker, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        return
    with os.fdopen(descriptor, 'w') as output:
        json.dump({'reason': 'renewal_interrupted; inspect outstanding ACME TXT values',
                   'timestamp': time.time()}, output)
        output.flush()
        os.fsync(output.fileno())
    directory = os.open(CERTIFICATES, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(directory)
    finally:
        os.close(directory)


def local_certificate():
    path = str(CERTIFICATES / 'live/commonex.ru/cert.pem')
    der = command(['openssl', 'x509', '-in', path, '-outform', 'DER'])
    expiry = command(['openssl', 'x509', '-in', path, '-noout', '-enddate'])
    expires = ssl.cert_time_to_seconds(expiry.decode().strip().split('=', 1)[1])
    return hashlib.sha256(der).hexdigest(), expires


def served_certificate(host):
    context = ssl.create_default_context()
    context.minimum_version = ssl.TLSVersion.TLSv1_3
    with socket.create_connection((host, 443), timeout=10) as connection:
        with context.wrap_socket(connection, server_hostname=host) as secure:
            return hashlib.sha256(secure.getpeercert(binary_form=True)).hexdigest()


def probe(expected):
    success, matches = True, True
    for host in PUBLIC_HOSTS:
        try:
            matches = (served_certificate(host) == expected) and matches
        except (OSError, ValueError):
            success, matches = False, False
    return int(success), int(matches)


def reload_nginx():
    with lock(DEPLOYMENT_LOCK):
        if ACTIVATION_INTENT.exists() or ACTIVATION_INTENT.is_symlink():
            raise RuntimeError('unfinished application activation; do not reload nginx')
        print('certificate renewal: validating and reloading nginx', file=sys.stderr)
        command(COMPOSE + ['exec', '-T', 'nginx', 'nginx', '-t'])
        command(COMPOSE + ['exec', '-T', 'nginx', 'nginx', '-s', 'reload'])
        expected, expires = local_certificate()
        if expires <= time.time():
            raise RuntimeError('local certificate has expired')
        for attempt in range(6):
            if probe(expected) == (1, 1):
                return
            if attempt < 5:
                time.sleep(5)
        raise RuntimeError('served certificates do not match the renewed certificate')


def metrics(state, now, expires, success, matches):
    values = {
        'monitor_timestamp_seconds': now,
        'renewal_last_attempt_timestamp_seconds': state['last_attempt'],
        'renewal_last_success_timestamp_seconds': state['last_success'],
        'renewal_success': state['success'],
        'not_after_timestamp_seconds': expires,
        'probe_success': success,
        'served_matches': matches,
    }
    return ''.join(f'commonex_certificate_{name}{{certificate="commonex.ru"}} {value}\n'
                   for name, value in values.items()).encode()


def publish_metrics(payload):
    info = json.loads(command(['docker', 'inspect', 'victoriametrics']))[0]
    address = info['NetworkSettings']['Networks']['app_monitoring-network']['IPAddress']
    address = str(ipaddress.IPv4Address(address))
    request = urllib.request.Request(
        f'http://{address}:8428/api/v1/import/prometheus', data=payload,
        headers={'Content-Type': 'text/plain'})
    # The host can reach the private bridge; no monitoring port is published.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(request, timeout=15) as response:
        if response.status not in (200, 204):
            raise RuntimeError('metrics ingestion failed')


def monitor():
    # Serialize probes and publication so an older monitor cannot publish last.
    with lock(MONITOR_LOCK, timeout=120):
        expires, success, matches = 0, 0, 0
        try:
            expected, expires = local_certificate()
            success, matches = probe(expected)
        except (OSError, ValueError, subprocess.SubprocessError):
            # Preserve the failed probe values so missing/invalid certificates publish failure metrics.
            pass
        try:
            state = load_state()
        except (OSError, ValueError):
            state = {'last_attempt': 0, 'last_success': 0, 'success': 0}
        publish_metrics(metrics(state, time.time(), expires, success, matches))
        if not success or not matches or expires <= time.time():
            raise RuntimeError('certificate health check failed; failure metrics published')


def renew(dry_run=False):
    with lock(RENEWAL_LOCK, timeout=5):
        state = load_state()
        if not dry_run:
            state.update(last_attempt=time.time(), success=0)
            save_state(state)
        try:
            settings = private_json(CONFIG)
            private_json(DNS_CONFIG)
            private_json(KEY)
            reconciliation = CERTIFICATES / '.commonex-dns-reconciliation-required'
            if reconciliation.exists() or reconciliation.is_symlink():
                raise RuntimeError('DNS reconciliation is required before renewal')
            try:
                print('certificate renewal: running Certbot DNS hooks', file=sys.stderr)
                command(certbot_command(settings['image'], dry_run), timeout=1200)
            except (subprocess.TimeoutExpired, KeyboardInterrupt):
                # docker CLI timeout alone does not stop its container or hooks.
                mark_interrupted()
                command(['docker', 'stop', '--time', '30', 'commonex-certificate-renewal'])
                raise
            if reconciliation.exists() or reconciliation.is_symlink():
                raise RuntimeError('DNS cleanup requires reconciliation')
            # Reconcile even if renewal was not due: a previous reload may have failed.
            reload_nginx()
        except Exception:
            if not dry_run:
                save_state(state)
            raise
        else:
            if not dry_run:
                state.update(last_success=time.time(), success=1)
                save_state(state)
        finally:
            if not dry_run:
                already_failing = sys.exc_info()[0] is not None
                try:
                    monitor()
                except Exception as error:
                    print(f'certificate monitoring failed: {type(error).__name__}', file=sys.stderr)
                    if not already_failing:
                        raise


def cleanup_container():
    # ExecStopPost also runs after forced termination. Never stop a newer job.
    with lock(RENEWAL_LOCK):
        remaining = command(['docker', 'ps', '--all', '--filter',
                             'name=^/commonex-certificate-renewal$', '--format', '{{.ID}}'])
        interrupted = os.environ.get('SERVICE_RESULT') in (
            'timeout', 'signal', 'core-dump', 'watchdog', 'oom-kill')
        if remaining.strip() or interrupted:
            mark_interrupted()
        if remaining.strip():
            command(['docker', 'stop', '--time', '30', 'commonex-certificate-renewal'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=('renew', 'rehearse', 'monitor', 'cleanup'))
    arguments = parser.parse_args()
    if os.name != 'posix' or os.geteuid() != 0:
        parser.error('run on the production Linux host as root')
    os.umask(0o077)
    try:
        if arguments.operation == 'cleanup':
            cleanup_container()
        elif arguments.operation == 'monitor':
            monitor()
        else:
            renew(dry_run=arguments.operation == 'rehearse')
    except Exception as error:
        # API response bodies and subprocess output may contain challenge credentials.
        if isinstance(error, subprocess.CalledProcessError):
            detail = f'command exited with status {error.returncode}; see preceding operation'
        else:
            detail = type(error).__name__
        print(f'certificate {arguments.operation} failed: {detail}', file=sys.stderr)
        return 1
    print(f'certificate {arguments.operation} succeeded')
    return 0


if __name__ == '__main__':
    sys.exit(main())
