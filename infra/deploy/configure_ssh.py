"""Configure a CI-only SSH identity with the repository's pinned host key."""

import argparse
import ipaddress
import os
from pathlib import Path


def configure(service, directory, key, address, environment='production', known_hosts_path=None):
    users = {'application': ('commonex-production', 'commonex-deploy'),
             'certificates': ('commonex-certificates', 'commonex-certificates-deploy')}
    alias, user = users[service]
    if environment not in ('production', 'staging'):
        raise ValueError('unknown deployment environment')
    host_key_alias = f'commonex-{environment}'
    if environment == 'staging':
        alias = 'commonex-staging' if service == 'application' else 'commonex-staging-certificates'
    address = str(ipaddress.ip_address(address))
    if not key.strip():
        raise ValueError('deployment SSH key secret is not configured')
    if environment == 'staging' and known_hosts_path is None:
        raise ValueError('staging requires an operator-verified known_hosts file')
    known_hosts = (known_hosts_path or Path(__file__).parent / 'known_hosts').read_text()
    if not any(
        len(fields) >= 3 and host_key_alias in fields[0].split(',')
        for line in known_hosts.splitlines()
        if (fields := line.split()) and not line.lstrip().startswith('#')
    ):
        raise ValueError(f'known_hosts has no pinned key for {host_key_alias}')
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    directory.chmod(0o700)
    files = {
        'id_ed25519': key.rstrip() + '\n',
        'known_hosts': known_hosts,
        'config': (f'Host {alias}\n    HostName {address}\n    User {user}\n'
                   '    IdentityFile ~/.ssh/id_ed25519\n    IdentitiesOnly yes\n'
                   '    BatchMode yes\n    ConnectTimeout 20\n'
                   f'    HostKeyAlias {host_key_alias}\n    StrictHostKeyChecking yes\n'
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
    parser.add_argument('--environment', choices=('production', 'staging'), default='production')
    parser.add_argument('--known-hosts', type=Path,
                        help='Operator-verified host pins; required for staging')
    options = parser.parse_args()
    configure(options.service, Path.home() / '.ssh', os.environ['DEPLOY_SSH_KEY'],
              os.environ['SERVER_IP'], options.environment, options.known_hosts)
