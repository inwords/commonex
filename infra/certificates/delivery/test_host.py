"""Certificate host transaction integration tests with a disposable filesystem."""

from contextlib import nullcontext
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch


HERE = Path(__file__).resolve().parent
CERTIFICATES = HERE.parent
sys.path.insert(0, str(CERTIFICATES.parent / 'deploy'))
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(CERTIFICATES))
import contract
from host import CertificateDelivery, TIMERS, SERVICES, run_cli
from commonex_host.activation import ConfigurationRestoreError


OLD = '1' * 40
NEW = '2' * 40
OTHER = '3' * 40
IMAGE = 'ruggedbl/commonex-certificates@sha256:' + 'a' * 64


class FakeHost(CertificateDelivery):
    def __init__(self, root):
        self.commands = []
        self.timers = {timer: {'enabled': True, 'active': True} for timer in TIMERS}
        self.service_states = {service: 'inactive' for service in SERVICES}
        self.fail_candidate = False
        self.fail_restore = False
        self.clock = 0
        self.locks = []
        self.lock_events = []
        super().__init__(root, command=self.execute, lock=self.acquire,
                         sleep=self.advance, monotonic=lambda: self.clock,
                         enforce_root=False)

    def advance(self, seconds):
        self.clock += seconds

    def acquire(self, path):
        self.locks.append(path.name)
        self.lock_events.append((path.name, len(self.commands)))
        return nullcontext()

    def execute(self, command, timeout=60):
        self.commands.append(command)
        if command[:2] == ['systemctl', 'show']:
            name = command[2]
            if name in self.timers:
                state = self.timers[name]
                return ('UnitFileState=' + ('enabled' if state['enabled'] else 'disabled')
                        + '\nActiveState=' + ('active' if state['active'] else 'inactive') + '\n').encode()
            return self.service_states[name].encode()
        if command[:2] == ['systemctl', 'disable'] and '--now' in command:
            for timer in TIMERS:
                self.timers[timer] = {'enabled': False, 'active': False}
        elif command[0] == 'systemctl' and command[1] in ('enable', 'disable', 'start', 'stop'):
            field = 'enabled' if command[1] in ('enable', 'disable') else 'active'
            self.timers[command[2]][field] = command[1] in ('enable', 'start')
        if command[0] == '/usr/bin/python3':
            revision = Path(command[2]).parent.name
            if self.fail_candidate and revision == NEW:
                raise RuntimeError('candidate verification failed')
            if self.fail_restore and revision == OLD:
                raise RuntimeError('restoration verification failed')
        return b''


@unittest.skipUnless(sys.platform != 'win32', 'requires Linux ownership and symlink semantics')
class HostTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.host = FakeHost(self.root)
        target = self.host.versions / OLD
        target.mkdir(parents=True)
        (target / 'renew.py').write_bytes(b'# original runner\n')
        self.host.current.symlink_to(target)
        self.host.config.parent.mkdir(parents=True)
        self.host.config.write_text(json.dumps({'image': 'sha256:' + 'b' * 64, 'preserve': True}))
        self.host.config.chmod(0o600)
        self.payload = {name: (CERTIFICATES / name).read_bytes() for name in contract.FILES}
        for name in contract.MANAGED_FILES:
            path = self.host.managed_path(name)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(self.payload[name])

    def deploy(self, revision=NEW, run=1):
        archive = contract.encode_archive(self.payload, revision, IMAGE)
        return self.host.activate('deploy', revision, run, io.BytesIO(archive))

    def test_success_adopts_baseline_and_commits_candidate(self):
        self.deploy()
        self.assertEqual(self.host.current_target().name, NEW)
        self.assertEqual(self.host.files.read_activation_state(), (1, [NEW, OLD]))
        self.assertIsNone(self.host.files.read_activation_intent())
        self.assertEqual(self.host.read_json(self.host.config), {'image': IMAGE, 'preserve': True})
        self.assertTrue(all(state == {'enabled': True, 'active': True} for state in self.host.timers.values()))
        operations = [cmd[-1] for cmd in self.host.commands if cmd[0] == '/usr/bin/python3']
        self.assertEqual(operations, ['rehearse', 'renew', 'monitor'])
        self.assertEqual((self.host.versions / OLD / 'renew.py').read_bytes(), b'# original runner\n')

    def test_grafana_state_survives_delivery_failure_and_rollback(self):
        provisioning = self.host.path('/etc/commonex/app/grafana/provisioning')
        originals = {
            'datasources/existing.yaml': b'apiVersion: 1\ndatasources: []\n',
            'alerting/certificates.json': b'{"existing": "operator-managed"}\n',
            'alerting/other.yaml': b'apiVersion: 1\ngroups: []\n',
        }
        for name, content in originals.items():
            path = provisioning / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)

        def assert_unchanged():
            self.assertEqual({str(path.relative_to(provisioning)): path.read_bytes()
                              for path in provisioning.rglob('*') if path.is_file()}, originals)
            self.assertFalse(any(command[:2] == ['docker', 'inspect']
                                 for command in self.host.commands))

        self.deploy()
        assert_unchanged()
        self.host.activate('rollback', OLD, 2)
        assert_unchanged()
        self.host.fail_candidate = True
        with self.assertRaises(RuntimeError):
            self.deploy(run=3)
        assert_unchanged()

    def test_committed_audit_failure_has_distinct_cli_result(self):
        archive = contract.encode_archive(self.payload, NEW, IMAGE)
        stderr = io.StringIO()
        append_audit = self.host.files.append_audit

        def audit(message):
            if message.startswith('RESULT deploy ') and 'status=PASS' in message:
                raise OSError('sensitive error')
            append_audit(message)

        with patch('host.os.geteuid', return_value=0), \
                patch('host.sys.argv', ['commonex-certificates-deploy', 'forced']), \
                patch.dict('host.os.environ', {'SSH_ORIGINAL_COMMAND': f'deploy {NEW} 1'}), \
                patch('host.CertificateDelivery', return_value=self.host), \
                patch('host.sys.stdin', io.TextIOWrapper(io.BytesIO(archive))), \
                patch('host.sys.stderr', stderr), \
                patch.object(self.host.files, 'append_audit', side_effect=audit):
            self.assertEqual(run_cli(), 2)
        self.assertEqual(self.host.current_target().name, NEW)
        self.assertEqual(self.host.files.read_activation_state(), (1, [NEW, OLD]))
        self.assertIsNone(self.host.files.read_activation_intent())
        self.assertTrue(all(state['active'] for state in self.host.timers.values()))
        self.assertIn('activation committed', stderr.getvalue())
        self.assertNotIn('sensitive error', stderr.getvalue())

    def test_replay_rejected_before_new_staging(self):
        self.deploy()
        with self.assertRaises(ValueError):
            self.deploy(OTHER, run=1)
        self.assertFalse((self.host.versions / OTHER).exists())

    def test_failed_candidate_restores_files_pointer_timers(self):
        old_config = self.host.config.read_bytes()
        self.host.fail_candidate = True
        with self.assertRaises(RuntimeError):
            self.deploy()
        self.assertEqual(self.host.current_target().name, OLD)
        self.assertEqual(self.host.config.read_bytes(), old_config)
        self.assertIsNone(self.host.files.read_activation_intent())
        self.assertEqual(self.host.files.read_activation_state(), (0, []))
        self.assertTrue(all(state['active'] for state in self.host.timers.values()))

    def test_failed_restore_preserves_intent_and_disables_timers(self):
        self.host.fail_candidate = True
        self.host.fail_restore = True
        with self.assertRaises(ConfigurationRestoreError):
            self.deploy()
        self.assertIsNotNone(self.host.files.read_activation_intent())
        self.assertTrue(all(not state['active'] and not state['enabled'] for state in self.host.timers.values()))
        self.host.fail_candidate = self.host.fail_restore = False
        with self.assertRaises(RuntimeError):
            self.deploy(OTHER, run=2)
        self.assertFalse((self.host.versions / OTHER).exists())

    def test_retained_baseline_can_be_rolled_back(self):
        self.deploy()
        self.host.activate('rollback', OLD, 2)
        self.assertEqual(self.host.current_target().name, OLD)
        self.assertEqual(self.host.files.read_activation_state(), (2, [OLD, NEW]))
        self.assertEqual(self.host.read_json(self.host.config)['image'], 'sha256:' + 'b' * 64)

    def test_unknown_rollback_is_rejected(self):
        with self.assertRaises(ValueError):
            self.host.activate('rollback', OTHER, 1)
        self.assertEqual(self.host.current_target().name, OLD)
        self.assertFalse(any(command[:2] == ['systemctl', 'disable'] for command in self.host.commands))

    def test_disabled_inactive_timer_state_is_preserved(self):
        self.host.timers[TIMERS[1]] = {'enabled': False, 'active': False}
        self.deploy()
        self.assertEqual(self.host.timers[TIMERS[1]], {'enabled': False, 'active': False})

    def test_immutable_revision_cannot_change(self):
        self.deploy()
        self.payload['renew.py'] += b'# changed\n'
        with self.assertRaises(ValueError):
            self.deploy(run=2)

    def test_interrupted_staging_does_not_publish_partial_revision(self):
        original_write = self.host.write
        staged_writes = 0

        def fail_mid_write(path, content, mode=0o644):
            nonlocal staged_writes
            if any(part.startswith('.staging-') for part in path.parts):
                staged_writes += 1
                if staged_writes == 2:
                    raise OSError('injected staging write failure')
            return original_write(path, content, mode)

        with patch.object(self.host, 'write', side_effect=fail_mid_write):
            with self.assertRaises(OSError):
                self.deploy()
        self.assertFalse((self.host.versions / NEW).exists())
        self.assertEqual(list(self.host.versions.glob('.staging-*')), [])
        self.assertEqual(self.host.current_target().name, OLD)
        self.assertIsNone(self.host.files.read_activation_intent())
        self.deploy()
        self.assertEqual(self.host.current_target().name, NEW)

    def test_existing_application_intent_prevents_delivery(self):
        intent = self.host.path('/var/lib/commonex/activation-intent.json')
        intent.parent.mkdir(parents=True)
        intent.write_text('{}')
        with self.assertRaises(RuntimeError):
            self.deploy()
        self.assertFalse((self.host.versions / NEW).exists())

    def test_active_services_are_waited_without_termination(self):
        self.host.service_states[SERVICES[0]] = 'activating'
        def complete(seconds):
            self.host.clock += seconds
            self.host.service_states[SERVICES[0]] = 'inactive'
        self.host.sleep = complete
        self.deploy()
        self.assertEqual(self.host.clock, 5)
        self.assertFalse(any(cmd[:2] == ['systemctl', 'stop'] and cmd[-1] in SERVICES
                             for cmd in self.host.commands))
        last_service_check = max(index for index, command in enumerate(self.host.commands)
                                 if command[:2] == ['systemctl', 'show']
                                 and command[2] in SERVICES)
        runner_locks = [count for name, count in self.host.lock_events
                        if name in ('certificate-renewal.lock', 'certificate-monitor.lock')]
        self.assertTrue(runner_locks)
        self.assertTrue(all(count > last_service_check for count in runner_locks))

    def test_symlink_managed_destination_is_rejected(self):
        path = self.host.managed_path(contract.MANAGED_FILES[0])
        original = path.read_bytes()
        path.unlink()
        outside = self.root / 'outside'
        outside.write_bytes(original)
        path.symlink_to(outside)
        with self.assertRaises(Exception):
            self.deploy()
        self.assertEqual(outside.read_bytes(), original)


if __name__ == '__main__':
    unittest.main()
