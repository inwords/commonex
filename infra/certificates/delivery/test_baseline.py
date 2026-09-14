"""Manual baseline identity stays immutable during initial CI adoption."""

import io
import sys
import unittest

import test_host


@unittest.skipIf(sys.platform == 'win32', 'requires Linux ownership and symlink semantics')
class BaselineTests(unittest.TestCase):
    def test_same_revision_rejected_without_blocking_distinct_release(self):
        for exact_payload in (False, True):
            with self.subTest(exact_payload=exact_payload):
                fixture = test_host.HostTests('test_success_adopts_baseline_and_commits_candidate')
                fixture.setUp()
                self.addCleanup(fixture.doCleanups)
                host = fixture.host
                if exact_payload:
                    archive = test_host.contract.encode_archive(fixture.payload, test_host.OLD, test_host.IMAGE)
                    payload = test_host.contract.read_archive(io.BytesIO(archive), test_host.OLD)
                    for name, data in payload.items():
                        host.write(host.versions / test_host.OLD / name, data)
                original_runner = (host.current_target() / 'renew.py').read_bytes()
                original_config = host.config.read_bytes()

                with self.assertRaisesRegex(ValueError, 'reviewed revision distinct from the manual baseline'):
                    fixture.deploy(test_host.OLD)

                self.assertFalse((host.base / 'releases').exists())
                self.assertEqual(host.files.read_activation_state(), (0, []))
                self.assertIsNone(host.files.read_activation_intent())
                self.assertEqual(host.commands, [])
                self.assertEqual(host.config.read_bytes(), original_config)
                self.assertEqual((host.current_target() / 'renew.py').read_bytes(), original_runner)

                fixture.deploy()
                host.activate('rollback', test_host.OLD, 2)
                self.assertEqual(host.current_target().name, test_host.OLD)
                self.assertEqual(host.config.read_bytes(), original_config)
                self.assertEqual((host.current_target() / 'renew.py').read_bytes(), original_runner)


if __name__ == '__main__':
    unittest.main()
