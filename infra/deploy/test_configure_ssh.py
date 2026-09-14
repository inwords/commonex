import os
from pathlib import Path
import tempfile
import unittest

from configure_ssh import configure


@unittest.skipUnless(os.name == 'posix', 'CI SSH permissions require Linux')
class SshConfigurationTests(unittest.TestCase):
    def test_identities_are_distinct_but_share_pinned_host_verification(self):
        for service, user in [('application', 'commonex-deploy'),
                              ('certificates', 'commonex-certificates-deploy')]:
            with self.subTest(service=service), tempfile.TemporaryDirectory() as temp:
                directory = Path(temp) / 'ssh'
                configure(service, directory, 'test-private-key', '185.37.8.130')
                config = (directory / 'config').read_text()
                self.assertIn(f'User {user}\n', config)
                self.assertIn('StrictHostKeyChecking yes', config)
                self.assertIn('HostKeyAlias commonex-production', config)
                self.assertNotIn('test-private-key', config)
                self.assertEqual((directory / 'id_ed25519').stat().st_mode & 0o777, 0o600)

    def test_invalid_host_and_missing_key_fail_before_creating_files(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp) / 'ssh'
            for address, key in [('185.37.8.130\nProxyCommand evil', 'key'), ('185.37.8.130', '')]:
                with self.assertRaises(ValueError):
                    configure('certificates', directory, key, address)
                self.assertFalse(directory.exists())
