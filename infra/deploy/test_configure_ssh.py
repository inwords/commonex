import os
from pathlib import Path
import tempfile
import unittest

from infra.deploy.configure_ssh import configure


class HostTrustTests(unittest.TestCase):
    def test_staging_requires_verified_pin_before_creating_files(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp) / 'ssh'
            with self.assertRaisesRegex(ValueError, 'operator-verified'):
                configure('application', directory, 'key', '192.0.2.20', 'staging')
            self.assertFalse(directory.exists())
            pins = Path(temp) / 'verified_hosts'
            pins.write_text('commonex-production ssh-ed25519 test-production-key\n')
            with self.assertRaisesRegex(ValueError, 'no pinned key for commonex-staging'):
                configure('application', directory, 'key', '192.0.2.20', 'staging', pins)
            self.assertFalse(directory.exists())


@unittest.skipUnless(os.name == 'posix', 'CI SSH permissions require Linux')
class SshConfigurationTests(unittest.TestCase):
    def test_staging_uses_separate_alias_and_operator_verified_host_pin(self):
        for service, alias in [('application', 'commonex-staging'),
                               ('certificates', 'commonex-staging-certificates')]:
            with self.subTest(service=service), tempfile.TemporaryDirectory() as temp:
                directory = Path(temp) / 'ssh'
                pins = Path(temp) / 'verified_hosts'
                known_hosts = 'commonex-staging ssh-ed25519 test-staging-key\n'
                pins.write_text(known_hosts)
                configure(service, directory, 'test-private-key', '192.0.2.20', 'staging', pins)
                config = (directory / 'config').read_text()
                self.assertIn(f'Host {alias}\n', config)
                self.assertIn('HostKeyAlias commonex-staging\n', config)
                self.assertIn('StrictHostKeyChecking yes\n', config)
                self.assertEqual((directory / 'known_hosts').read_text(), known_hosts)

    def test_identities_are_distinct_but_share_pinned_host_verification(self):
        for service, user in [('application', 'commonex-deploy'),
                              ('certificates', 'commonex-certificates-deploy')]:
            with self.subTest(service=service), tempfile.TemporaryDirectory() as temp:
                directory = Path(temp) / 'ssh'
                configure(service, directory, 'test-private-key', '192.0.2.10')
                config = (directory / 'config').read_text()
                self.assertIn(f'User {user}\n', config)
                self.assertIn('StrictHostKeyChecking yes', config)
                self.assertIn('HostKeyAlias commonex-production', config)
                self.assertNotIn('test-private-key', config)
                self.assertEqual((directory / 'id_ed25519').stat().st_mode & 0o777, 0o600)

    def test_invalid_host_and_missing_key_fail_before_creating_files(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp) / 'ssh'
            for address, key in [('192.0.2.10\nProxyCommand evil', 'key'), ('192.0.2.10', '')]:
                with self.assertRaises(ValueError):
                    configure('certificates', directory, key, address)
                self.assertFalse(directory.exists())
