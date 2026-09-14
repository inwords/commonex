import hashlib
import io
from contextlib import contextmanager
import json
import os
from pathlib import Path
import ssl
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from infra.certificates import renew


IMAGE = 'registry.example/certbot@sha256:' + 'a' * 64
INITIAL_STATE = {'last_attempt': 100, 'last_success': 90, 'success': 1}
LINUX_ROOT = sys.platform == 'linux' and os.geteuid() == 0


class CertbotCommandTests(unittest.TestCase):
    def test_only_selected_lineage_uses_owned_hooks_without_host_socket(self):
        arguments = renew.certbot_command(IMAGE)
        self.assertEqual(arguments[arguments.index('--cert-name') + 1], 'commonex.ru')
        self.assertEqual(arguments.count('--cert-name'), 1)
        self.assertEqual(arguments[arguments.index(IMAGE) + 1], 'renew')
        self.assertIn('--no-directory-hooks', arguments)
        for option in ('--pre-hook', '--post-hook', '--deploy-hook'):
            self.assertEqual(arguments[arguments.index(option) + 1], '')
        self.assertIn('commonex_certificates.dns_hook auth',
                      arguments[arguments.index('--manual-auth-hook') + 1])
        self.assertIn('commonex_certificates.dns_hook cleanup',
                      arguments[arguments.index('--manual-cleanup-hook') + 1])
        mounts = [arguments[i + 1] for i, value in enumerate(arguments) if value == '--mount']
        self.assertEqual(len(mounts), 3)
        self.assertFalse(any('docker.sock' in value for value in arguments))
        self.assertIn('--cap-drop=ALL', arguments)
        self.assertIn('--read-only', arguments)

    def test_mutable_or_malformed_image_is_rejected_before_execution(self):
        for image in ('certbot:latest', 'certbot:v1', 'sha256:abc', IMAGE + 'extra'):
            with self.subTest(image=image), self.assertRaises(ValueError):
                renew.certbot_command(image)

    def test_rehearsal_requests_certbot_dry_run(self):
        self.assertIn('--dry-run', renew.certbot_command(IMAGE, dry_run=True))
        self.assertNotIn('--dry-run', renew.certbot_command(IMAGE))


class ServedCertificateTests(unittest.TestCase):
    def test_probe_requires_modern_tls_and_keeps_default_certificate_validation(self):
        context = ssl.create_default_context()
        # Reproduce older Python defaults even when tests run on a newer interpreter.
        context.minimum_version = ssl.TLSVersion.MINIMUM_SUPPORTED
        with patch.object(renew.ssl, 'create_default_context', return_value=context), patch.object(renew.socket, 'create_connection') as connect, patch.object(context, 'wrap_socket') as wrap:
            wrap.return_value.__enter__.return_value.getpeercert.return_value = b'certificate'
            self.assertEqual(renew.served_certificate('commonex.ru'),
                             hashlib.sha256(b'certificate').hexdigest())
        self.assertEqual(context.minimum_version, ssl.TLSVersion.TLSv1_3)
        self.assertEqual(context.verify_mode, ssl.CERT_REQUIRED)
        self.assertTrue(context.check_hostname)
        self.assertGreater(context.cert_store_stats()['x509_ca'], 0)
        wrap.assert_called_once_with(connect.return_value.__enter__.return_value,
                                     server_hostname='commonex.ru')

    def test_tls_negotiation_and_certificate_validation_errors_fail_the_probe(self):
        for error in (ssl.SSLError('unsupported protocol'),
                      ssl.SSLCertVerificationError('certificate validation failed')):
            with self.subTest(error=type(error).__name__), patch.object(renew.socket, 'create_connection'), patch.object(ssl.SSLContext, 'wrap_socket', side_effect=error), patch.object(renew, 'PUBLIC_HOSTS', ('commonex.ru',)):
                self.assertEqual(renew.probe('expected'), (0, 0))


@unittest.skipUnless(LINUX_ROOT, 'host runner requires Linux root-owned files and flock')
class RunnerTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        certificates = patch.object(renew, 'CERTIFICATES', self.root)
        certificates.start()
        self.addCleanup(certificates.stop)
        for name, filename in (('CONFIG', 'renewal.json'), ('DNS_CONFIG', 'dns.json'),
                               ('KEY', 'key.json'), ('STATE', 'state.json'),
                               ('RENEWAL_LOCK', 'renewal.lock'),
                               ('DEPLOYMENT_LOCK', 'deploy.lock'),
                               ('ACTIVATION_INTENT', 'activation.json')):
            replacement = patch.object(renew, name, self.root / filename)
            replacement.start()
            self.addCleanup(replacement.stop)
        for path, value in ((renew.CONFIG, {'image': IMAGE}), (renew.DNS_CONFIG, {}),
                            (renew.KEY, {}), (renew.STATE, INITIAL_STATE)):
            path.write_text(json.dumps(value))
            path.chmod(0o600)
        self.command = self.mock('command', return_value=b'')
        self.reload = self.mock('reload_nginx')
        self.monitor = self.mock('monitor')

    def mock(self, name, **kwargs):
        replacement = patch.object(renew, name, **kwargs)
        result = replacement.start()
        self.addCleanup(replacement.stop)
        return result

    def state(self):
        return json.loads(renew.STATE.read_text())

    def test_success_records_attempt_and_success_after_reload(self):
        self.reload.side_effect = lambda: self.assertEqual(self.state()['success'], 0)
        with patch.object(renew.time, 'time', side_effect=[200, 210]):
            renew.renew()
        self.assertEqual(self.state(), {'last_attempt': 200, 'last_success': 210, 'success': 1})
        self.reload.assert_called_once()
        self.monitor.assert_called_once()

    def test_rehearsal_preserves_production_state_and_does_not_publish_renewal(self):
        before = renew.STATE.read_bytes()
        renew.renew(dry_run=True)
        self.assertEqual(renew.STATE.read_bytes(), before)
        self.assertIn('--dry-run', self.command.call_args.args[0])
        self.monitor.assert_not_called()

    def test_settings_load_failure_records_failed_attempt(self):
        renew.CONFIG.unlink()
        with patch.object(renew.time, 'time', return_value=200), self.assertRaises(FileNotFoundError):
            renew.renew()
        self.assertEqual(self.state(), {'last_attempt': 200, 'last_success': 90, 'success': 0})
        self.command.assert_not_called()
        self.reload.assert_not_called()
        self.monitor.assert_called_once()

    def test_dns_hook_failure_prevents_reload_and_retains_last_success(self):
        self.command.side_effect = subprocess.CalledProcessError(1, ['docker', 'run'])
        with patch.object(renew.time, 'time', return_value=200), self.assertRaises(subprocess.CalledProcessError):
            renew.renew()
        self.reload.assert_not_called()
        self.assertEqual(self.state(), {'last_attempt': 200, 'last_success': 90, 'success': 0})

    def test_certbot_timeout_stops_container_before_failing(self):
        def run(arguments, **kwargs):
            if arguments[1] == 'run':
                raise subprocess.TimeoutExpired('docker', 1200)
            self.assertTrue((renew.CERTIFICATES / '.commonex-dns-reconciliation-required').is_file())
            return b''
        self.command.side_effect = run
        with self.assertRaises(subprocess.TimeoutExpired):
            renew.renew()
        self.assertEqual(self.command.call_args.args[0],
                         ['docker', 'stop', '--time', '30', 'commonex-certificate-renewal'])
        self.reload.assert_not_called()

    def test_existing_reconciliation_marker_blocks_certbot(self):
        renew.mark_interrupted()
        with self.assertRaisesRegex(RuntimeError, 'reconciliation'):
            renew.renew()
        self.command.assert_not_called()
        self.reload.assert_not_called()

    def test_cleanup_marker_prevents_success_even_when_certbot_exits_zero(self):
        self.command.side_effect = lambda *args, **kwargs: renew.mark_interrupted()
        with self.assertRaisesRegex(RuntimeError, 'reconciliation'):
            renew.renew()
        self.reload.assert_not_called()
        self.assertEqual(self.state()['success'], 0)

    def test_monitor_failure_does_not_replace_primary_renewal_error(self):
        self.command.side_effect = subprocess.CalledProcessError(1, 'docker')
        self.monitor.side_effect = RuntimeError('monitor failed')
        with patch('sys.stderr', io.StringIO()), self.assertRaises(subprocess.CalledProcessError):
            renew.renew()

    def test_rehearsal_failure_preserves_production_state(self):
        before = renew.STATE.read_bytes()
        self.command.side_effect = subprocess.CalledProcessError(1, ['docker', 'run'])
        with self.assertRaises(subprocess.CalledProcessError):
            renew.renew(dry_run=True)
        self.assertEqual(renew.STATE.read_bytes(), before)
        self.reload.assert_not_called()
        self.monitor.assert_not_called()

    def test_monitoring_failure_returns_nonzero_without_leaking_error_details(self):
        self.monitor.side_effect = RuntimeError('private response body')
        output = io.StringIO()
        with patch.object(sys, 'argv', ['renew.py', 'renew']), patch('sys.stderr', output), patch('sys.stdout', io.StringIO()):
            self.assertEqual(renew.main(), 1)
        self.assertNotIn('private response body', output.getvalue())


@unittest.skipUnless(LINUX_ROOT, 'host runner requires Linux root-owned files and flock')
class ReloadTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        root = Path(directory.name)
        for name, value in (('DEPLOYMENT_LOCK', root / 'deploy.lock'),
                            ('CERTIFICATES', root),
                            ('ACTIVATION_INTENT', root / 'activation.json')):
            replacement = patch.object(renew, name, value)
            replacement.start()
            self.addCleanup(replacement.stop)

    def test_pending_activation_prevents_all_nginx_commands(self):
        renew.ACTIVATION_INTENT.write_text('{}')
        with patch.object(renew, 'command') as command, self.assertRaisesRegex(RuntimeError, 'unfinished'):
            renew.reload_nginx()
        command.assert_not_called()

    def test_invalid_nginx_config_is_never_reloaded(self):
        with patch.object(renew, 'command', side_effect=subprocess.CalledProcessError(1, 'nginx')) as command:
            with self.assertRaises(subprocess.CalledProcessError):
                renew.reload_nginx()
        self.assertEqual(command.call_args.args[0][-2:], ['nginx', '-t'])
        self.assertEqual(command.call_count, 1)

    def test_served_certificate_mismatch_fails_after_bounded_retries(self):
        with patch.object(renew, 'command') as command, patch.object(renew, 'local_certificate', return_value=('expected', 9999999999)), patch.object(renew, 'probe', return_value=(1, 0)), patch.object(renew.time, 'sleep'):
            with self.assertRaisesRegex(RuntimeError, 'do not match'):
                renew.reload_nginx()
        self.assertEqual(command.call_args_list[0].args[0][-2:], ['nginx', '-t'])
        self.assertEqual(command.call_args_list[1].args[0][-3:], ['nginx', '-s', 'reload'])

    def test_deployment_lock_excludes_another_process_until_release(self):
        script = ('import sys; from pathlib import Path; from infra.certificates.renew import lock\n'
                  'try:\n with lock(Path(sys.argv[1]), timeout=0): pass\n'
                  'except TimeoutError: sys.exit(7)\n')
        arguments = [sys.executable, '-c', script, str(renew.DEPLOYMENT_LOCK)]
        with renew.lock(renew.DEPLOYMENT_LOCK):
            blocked = subprocess.run(arguments, capture_output=True, timeout=5)
        released = subprocess.run(arguments, capture_output=True, timeout=5)
        self.assertEqual(blocked.returncode, 7, blocked.stderr.decode())
        self.assertEqual(released.returncode, 0, released.stderr.decode())

    def test_cleanup_stops_only_an_existing_renewal_container(self):
        def run(arguments, **kwargs):
            if arguments[1] == 'ps':
                return b'container-id\n'
            self.assertTrue((renew.CERTIFICATES / '.commonex-dns-reconciliation-required').is_file())
            return b''
        with patch.object(renew, 'RENEWAL_LOCK', renew.DEPLOYMENT_LOCK), patch.object(renew, 'command', side_effect=run) as command:
            renew.cleanup_container()
        self.assertIn('name=^/commonex-certificate-renewal$', command.call_args_list[0].args[0])
        self.assertEqual(command.call_args_list[1].args[0],
                         ['docker', 'stop', '--time', '30', 'commonex-certificate-renewal'])

    def test_cleanup_without_container_does_not_stop_anything(self):
        with patch.object(renew, 'RENEWAL_LOCK', renew.DEPLOYMENT_LOCK), patch.object(renew, 'command', return_value=b'') as command:
            renew.cleanup_container()
        self.assertEqual(command.call_count, 1)

    def test_forced_service_termination_requires_reconciliation_even_without_container(self):
        with patch.dict(os.environ, {'SERVICE_RESULT': 'signal'}), patch.object(renew, 'RENEWAL_LOCK', renew.DEPLOYMENT_LOCK), patch.object(renew, 'command', return_value=b'') as command:
            renew.cleanup_container()
        self.assertTrue((renew.CERTIFICATES / '.commonex-dns-reconciliation-required').is_file())
        self.assertEqual(command.call_count, 1)


class MonitoringTests(unittest.TestCase):
    def setUp(self):
        self.lock_held = False

        @contextmanager
        def operation_lock(path, timeout):
            self.assertEqual(path, renew.MONITOR_LOCK)
            self.assertEqual(timeout, 120)
            self.assertFalse(self.lock_held)
            self.lock_held = True
            try:
                yield
            finally:
                self.lock_held = False

        replacement = patch.object(renew, 'lock', side_effect=operation_lock)
        replacement.start()
        self.addCleanup(replacement.stop)

    def test_state_is_read_after_probes_and_publication_stays_inside_lock(self):
        state = dict(INITIAL_STATE, success=0)

        def probe(expected):
            self.assertTrue(self.lock_held)
            state.update(last_success=250, success=1)
            return 1, 1

        def publish(payload):
            self.assertTrue(self.lock_held)
            self.assertIn(b'renewal_success{certificate="commonex.ru"} 1\n', payload)
            self.assertIn(b'renewal_last_success_timestamp_seconds{certificate="commonex.ru"} 250\n', payload)

        with patch.object(renew, 'load_state', side_effect=lambda: dict(state)), patch.object(renew, 'local_certificate', return_value=('expected', 9999999999)), patch.object(renew, 'probe', side_effect=probe), patch.object(renew, 'publish_metrics', side_effect=publish):
            renew.monitor()
        self.assertFalse(self.lock_held)

    def test_monitor_heartbeat_preserves_renewal_timestamps(self):
        state = dict(INITIAL_STATE)
        with patch.object(renew, 'load_state', return_value=state), patch.object(renew, 'local_certificate', return_value=('expected', 1000)), patch.object(renew, 'probe', return_value=(1, 1)), patch.object(renew.time, 'time', return_value=300), patch.object(renew, 'publish_metrics') as publish, patch.object(renew, 'save_state') as save:
            renew.monitor()
        payload = publish.call_args.args[0].decode()
        self.assertIn('monitor_timestamp_seconds{certificate="commonex.ru"} 300\n', payload)
        self.assertIn('renewal_last_attempt_timestamp_seconds{certificate="commonex.ru"} 100\n', payload)
        self.assertIn('renewal_last_success_timestamp_seconds{certificate="commonex.ru"} 90\n', payload)
        self.assertEqual(state, INITIAL_STATE)
        save.assert_not_called()

    def test_metrics_delivery_failure_is_reported(self):
        with patch.object(renew, 'load_state', return_value=dict(INITIAL_STATE)), patch.object(renew, 'local_certificate', return_value=('expected', 9999999999)), patch.object(renew, 'probe', return_value=(1, 1)), patch.object(renew, 'publish_metrics', side_effect=OSError('unavailable')):
            with self.assertRaises(OSError):
                renew.monitor()


if __name__ == '__main__':
    unittest.main()
