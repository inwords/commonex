"""Prepare/install the fixed certificate SSH command using the shared tool installer."""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

DEPLOY = Path(__file__).resolve().parents[2] / 'deploy'
if (DEPLOY / 'install_commonex_deploy.py').is_file():
    sys.path.insert(0, str(DEPLOY))
import install_commonex_deploy as installer

USER = 'commonex-certificates-deploy'
DEPLOY_HOME = Path('/home/commonex-certificates-deploy')
BASE = Path('/opt/commonex/certificate-delivery')
ENTRYPOINT = Path('/usr/local/sbin/commonex-certificates-deploy')
CONDITION = '[Unit]\nConditionPathExists=!/var/lib/commonex/certificates/delivery/activation-intent.json\n'
LAUNCHER = b'from host import run_cli\n\nif __name__ == "__main__":\n    raise SystemExit(run_cli())\n'


def bundle_files():
    source = Path(__file__).resolve().parent
    repository = (DEPLOY / 'install_commonex_deploy.py').is_file()
    files = {name: (source / name).read_bytes() for name in ('host.py', 'contract.py', 'bootstrap.py')}
    files['host_runtime.py'] = (source.parent / 'host_runtime.py' if repository else source / 'host_runtime.py').read_bytes()
    files['install_commonex_deploy.py'] = ((DEPLOY if repository else source) / 'install_commonex_deploy.py').read_bytes()
    for name in ('__init__.py', 'archive.py', 'activation.py', 'trusted_files.py'):
        files['commonex_host/' + name] = ((DEPLOY if repository else source) / 'commonex_host' / name).read_bytes()
    files['commonex_deploy.py'] = LAUNCHER if repository else (source / 'commonex_deploy.py').read_bytes()
    return files


def fingerprint():
    digest = hashlib.sha256()
    for name, content in sorted(bundle_files().items()):
        digest.update(name.encode() + b'\0' + hashlib.sha256(content).digest())
    return digest.hexdigest()


def prepare(destination):
    destination.mkdir(parents=True, exist_ok=False)
    for name, content in bundle_files().items():
        path = destination / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
    for path in destination.rglob('*'):
        path.chmod(0o755 if path.is_dir() else 0o644)


def public_key(path):
    lines = path.read_text().strip().splitlines()
    if len(lines) != 1:
        raise ValueError('provide exactly one deployment public key')
    parts = lines[0].split()
    if len(parts) < 2 or parts[0] != 'ssh-ed25519':
        raise ValueError('deployment public key must be Ed25519')
    base64.b64decode(parts[1], validate=True)
    subprocess.run(['ssh-keygen', '-l', '-f', str(path)], check=True, capture_output=True)
    return ' '.join(parts[:2])


def configure_sshd():
    def allowed_users():
        output = subprocess.run(['sshd', '-T'], check=True, capture_output=True, text=True).stdout
        return {line.split(' ', 1)[1] for line in output.splitlines() if line.startswith('allowusers ')}
    before = allowed_users()
    if not before or USER in before:
        return
    path = Path('/etc/ssh/sshd_config.d/61-commonex-certificates.conf')
    previous = installer._read_trusted_file(path, enforce_root_ownership=True)[0] if path.exists() else None
    try:
        installer._atomic_write(path, f'AllowUsers {USER}\n'.encode(), 0o644)
        subprocess.run(['sshd', '-t'], check=True, capture_output=True)
        after = allowed_users()
        if not before.issubset(after) or USER not in after:
            raise RuntimeError('SSH allowlist did not preserve existing accounts and add certificate delivery')
        subprocess.run(['systemctl', 'reload', 'sshd'], check=True)
    except Exception:
        if previous is None:
            path.unlink(missing_ok=True)
        else:
            installer._atomic_write(path, previous, 0o644)
        raise


def install(bundle, revision, key_path, apply):
    key = public_key(key_path)
    layout = installer.InstallLayout(BASE, BASE / 'versions', BASE / 'current', ENTRYPOINT, BASE / 'rollbacks')
    if not apply:
        print(json.dumps(installer.install_version(bundle, revision, layout), indent=2))
        print('Also installs the dedicated SSH identity, sudo command, and fixed systemd recovery guards.')
        return
    if os.geteuid() != 0:
        raise PermissionError('host bootstrap requires root')
    installer.plan_install(bundle, revision, layout, enforce_root_ownership=True)
    # Serialize upgrades with the same lock as the installed fixed host adapter.
    sys.path.insert(0, str(bundle))
    import host_runtime
    with host_runtime.lock(Path('/run/commonex/certificate-delivery.lock')):
        if Path('/var/lib/commonex/certificates/delivery/activation-intent.json').exists():
            raise RuntimeError('reconcile the interrupted certificate deployment before bootstrap')
        delivery_state = Path('/var/lib/commonex/certificates/delivery')
        delivery_state.mkdir(mode=0o700, exist_ok=True)
        installer._assert_trusted_install_path(delivery_state, directory=True, enforce_root_ownership=True)
        delivery_state.chmod(0o700)
        import pwd
        try:
            account = pwd.getpwnam(USER)
        except KeyError:
            subprocess.run(['useradd', '--system', '--home-dir', str(DEPLOY_HOME), '--shell', '/bin/bash',
                            '--no-create-home', USER], check=True)
            account = pwd.getpwnam(USER)
        if account.pw_dir == '/var/lib/commonex/certificate-deploy-user' and account.pw_uid != 0:
            subprocess.run(['usermod', '--home', str(DEPLOY_HOME), USER], check=True)
            account = pwd.getpwnam(USER)
        if account.pw_uid == 0 or account.pw_dir != str(DEPLOY_HOME):
            raise RuntimeError('existing certificate deployment account has an unexpected identity')
        for path in (DEPLOY_HOME, DEPLOY_HOME / '.ssh'):
            if path.is_symlink():
                raise PermissionError('untrusted deployment home')
            path.mkdir(mode=0o755, exist_ok=True)
            os.chown(path, 0, 0)
            path.chmod(0o755)
        result = installer.install_version(bundle, revision, layout, apply=True)
        forced = f'restrict,command="sudo -n {ENTRYPOINT} forced" {key}\n'
        installer._atomic_write(DEPLOY_HOME / '.ssh/authorized_keys', forced.encode(), 0o644)
        policy = (f'Defaults:{USER} env_reset\n'
                  f'Defaults:{USER} env_keep = "SSH_ORIGINAL_COMMAND"\n'
                  f'Defaults:{USER} secure_path="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"\n'
                  f'{USER} ALL=(root) NOPASSWD: {ENTRYPOINT} forced\n')
        temporary = Path('/etc/sudoers.d/.commonex-certificates-candidate')
        installer._atomic_write(temporary, policy.encode(), 0o440)
        try:
            subprocess.run(['visudo', '-cf', str(temporary)], check=True, capture_output=True)
            installer._atomic_write(Path('/etc/sudoers.d/commonex-certificates-deploy'), policy.encode(), 0o440)
        finally:
            temporary.unlink(missing_ok=True)
        for service in ('renew', 'monitor'):
            directory = Path(f'/etc/systemd/system/commonex-certificate-{service}.service.d')
            directory.mkdir(mode=0o755, exist_ok=True)
            installer._atomic_write(directory / 'delivery-guard.conf', CONDITION.encode(), 0o644)
        subprocess.run(['systemctl', 'daemon-reload'], check=True)
        configure_sshd()
        print(json.dumps(result, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    prepare_parser = commands.add_parser('prepare')
    prepare_parser.add_argument('--output', required=True, type=Path)
    install_parser = commands.add_parser('install')
    install_parser.add_argument('--bundle', required=True, type=Path)
    install_parser.add_argument('--revision', required=True)
    install_parser.add_argument('--public-key', required=True, type=Path)
    install_parser.add_argument('--apply', action='store_true')
    options = parser.parse_args()
    if options.command == 'prepare':
        prepare(options.output)
    else:
        install(options.bundle, options.revision, options.public_key, options.apply)
