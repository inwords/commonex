"""Configure a CI-only SSH identity with the repository's pinned host key."""

import argparse
import ipaddress
import os
from pathlib import Path


def configure(service, directory, key, address):
    users = {'application': ('commonex-production', 'commonex-deploy'),
             'certificates': ('commonex-certificates', 'commonex-certificates-deploy')}
    alias, user = users[service]
    address = str(ipaddress.ip_address(address))
    if not key.strip():
        raise ValueError('deployment SSH key secret is not configured')
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    directory.chmod(0o700)
    files = {
        'id_ed25519': key.rstrip() + '\n',
        'known_hosts': (Path(__file__).parent / 'known_hosts').read_text(),
        'config': (f'Host {alias}\n    HostName {address}\n    User {user}\n'
                   '    IdentityFile ~/.ssh/id_ed25519\n    IdentitiesOnly yes\n'
                   '    BatchMode yes\n    ConnectTimeout 20\n'
                   '    HostKeyAlias commonex-production\n    StrictHostKeyChecking yes\n'
                   '    UserKnownHostsFile ~/.ssh/known_hosts\n'),
    }
    for name, content in files.items():
        path = directory / name
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC | getattr(os, 'O_NOFOLLOW', 0), 0o600)
        with os.fdopen(descriptor, 'w', newline='\n') as output:
            os.fchmod(output.fileno(), 0o600)
            output.write(content)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('service', choices=('application', 'certificates'))
    options = parser.parse_args()
    configure(options.service, Path.home() / '.ssh', os.environ['DEPLOY_SSH_KEY'], os.environ['SERVER_IP'])
