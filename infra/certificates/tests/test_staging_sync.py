import io
import os
import json
import subprocess
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch

from infra.certificates import staging_sync as delivery


CONFIG = {'target': 'receiver@staging.example.test', 'identity': '/etc/cert-sync/identity',
          'known_hosts': '/etc/cert-sync/known_hosts', 'source_directory': '/etc/certificates/live/example.test'}


def bundle(entries=None):
    entries = entries or [(name, b'certificate\nchain\n' if name == 'fullchain.pem'
                           else b'chain\n' if name == 'chain.pem' else b'certificate\n')
                          for name in delivery.FILES]
    stream = io.BytesIO()
    with tarfile.open(fileobj=stream, mode='w') as archive:
        for name, data in entries:
            item = tarfile.TarInfo(name)
            if data is None:
                item.type = tarfile.SYMTYPE
                item.linkname = '../../etc/shadow'
                archive.addfile(item)
            else:
                item.size = len(data)
                archive.addfile(item, io.BytesIO(data))
    return stream.getvalue()


class ArchiveTests(unittest.TestCase):
    def test_exact_regular_files_are_accepted_without_extraction(self):
        self.assertEqual(set(delivery.validate_archive(bundle())), set(delivery.FILES))

    def test_rejects_missing_duplicate_traversal_symlink_and_oversized_entries(self):
        entries = [(name, b'contents') for name in delivery.FILES]
        cases = [entries[:-1], entries + [entries[0]],
                 entries[:-1] + [('../privkey.pem', b'contents')],
                 entries[:-1] + [('privkey.pem', None)],
                 entries[:-1] + [('privkey.pem', b'x' * (delivery.MAX_FILE + 1))]]
        for case in cases:
            with self.subTest(case=case[-1][0]), self.assertRaises(ValueError):
                delivery.validate_archive(bundle(case))
        with self.assertRaises(ValueError):
            delivery.validate_archive(b'x' * (delivery.MAX_ARCHIVE + 1))

    def test_invalid_archive_never_reaches_staging(self):
        with patch.object(delivery.subprocess, 'run') as execute:
            with self.assertRaises(ValueError):
                delivery.send_certificate(bundle([('privkey.pem', b'secret')]), CONFIG)
        execute.assert_not_called()

    def test_staging_transport_is_pinned_and_does_not_log_archive(self):
        data = bundle()
        with patch.object(delivery.subprocess, 'run') as execute:
            execute.return_value.returncode = 0
            delivery.send_certificate(data, CONFIG)
        command = execute.call_args.args[0]
        self.assertIn('StrictHostKeyChecking=yes', command)
        self.assertIn(CONFIG['target'], command)
        self.assertEqual(command[-1], 'install')
        self.assertEqual(command[1:3], ['-F', '/dev/null'])
        self.assertIn('UserKnownHostsFile=' + CONFIG['known_hosts'], command)
        self.assertIn('GlobalKnownHostsFile=/dev/null', command)
        self.assertEqual(execute.call_args.kwargs['timeout'], 240)
        self.assertEqual(execute.call_args.kwargs['input'], data)
        self.assertEqual(execute.call_args.kwargs['stdout'], delivery.subprocess.DEVNULL)
        self.assertEqual(execute.call_args.kwargs['stderr'], delivery.subprocess.DEVNULL)

    def test_source_failure_never_attempts_delivery(self):
        with patch.object(delivery, 'read_certificate', side_effect=ValueError('invalid source')), patch.object(delivery, 'send_certificate') as send:
            with self.assertRaisesRegex(ValueError, 'invalid source'):
                delivery.synchronize(CONFIG)
        send.assert_not_called()

    def test_failed_delivery_reports_failure_without_subprocess_diagnostics(self):
        with patch.object(delivery.subprocess, 'run') as execute:
            execute.return_value.returncode = 1
            with self.assertRaisesRegex(RuntimeError, 'installation failed'):
                delivery.send_certificate(bundle(), CONFIG)
        self.assertEqual(execute.call_args.kwargs['stderr'], subprocess.DEVNULL)

    def test_wildcard_first_san_is_accepted_and_key_mismatch_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, contents in delivery.validate_archive(bundle()).items():
                (directory / name).write_bytes(contents)

            def runner(command):
                if '-ext' in command:
                    return b'X509v3 Subject Alternative Name:\n DNS:*.commonex.ru, DNS:commonex.ru\n'
                if '-pubkey' in command or '-pubout' in command:
                    return b'matching-public-key'
                return b''

            delivery.validate_certificate(directory, runner)
            def mismatched(command):
                return b'different-key' if '-pubout' in command else runner(command)
            with self.assertRaisesRegex(ValueError, 'do not match'):
                delivery.validate_certificate(directory, mismatched)


@unittest.skipUnless(os.name == 'posix' and os.geteuid() == 0,
                     'source configuration requires Linux root ownership')
class ConfigurationAndSourceTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.config_path = self.root / 'staging.json'
        self.source = self.root / 'source'
        self.source.mkdir()
        self.config = dict(CONFIG, identity=str(self.root / 'identity'),
                           known_hosts=str(self.root / 'known_hosts'),
                           source_directory=str(self.source))
        for field in ('identity', 'known_hosts'):
            path = Path(self.config[field])
            path.write_bytes(b'host-only-input')
            path.chmod(0o600)
        self.write_config(self.config)
        for name, data in delivery.validate_archive(bundle()).items():
            path = self.source / name
            path.write_bytes(data)
            path.chmod(0o600)

    def write_config(self, config):
        self.config_path.write_text(json.dumps(config))
        self.config_path.chmod(0o600)

    def test_protected_configuration_has_no_host_or_identity_defaults(self):
        self.assertEqual(delivery.load_config(self.config_path), self.config)
        for value in ({}, dict(self.config, extra='input'),
                      dict(self.config, target='-oProxyCommand=bad'),
                      dict(self.config, source_directory='../source'),
                      dict(self.config, identity='relative-key')):
            self.write_config(value)
            with self.subTest(config=value), self.assertRaises(ValueError):
                delivery.load_config(self.config_path)

    def test_configuration_and_identity_reject_symlinks_and_public_permissions(self):
        for field in (None, 'identity', 'known_hosts'):
            path = self.config_path if field is None else Path(self.config[field])
            path.chmod(0o666)
            with self.subTest(field=field), self.assertRaises(ValueError):
                delivery.load_config(self.config_path)
            path.chmod(0o600)
        actual = self.config_path.with_suffix('.actual')
        self.config_path.rename(actual)
        self.config_path.symlink_to(actual)
        with self.assertRaises(OSError):
            delivery.load_config(self.config_path)

    def test_source_archive_is_exact_and_bounded_with_certbot_symlinks(self):
        actual = self.source / 'version.pem'
        (self.source / 'cert.pem').rename(actual)
        (self.source / 'cert.pem').symlink_to(actual)
        files = delivery.validate_archive(delivery.read_certificate(self.source, self.root / 'renewal.lock'))
        self.assertEqual(set(files), set(delivery.FILES))
        self.assertEqual(files['cert.pem'], b'certificate\n')
        actual.write_bytes(b'x' * (delivery.MAX_FILE + 1))
        with self.assertRaisesRegex(ValueError, 'source file'):
            delivery.read_certificate(self.source, self.root / 'renewal.lock')

    def test_untrusted_source_and_lock_are_rejected(self):
        (self.source / 'privkey.pem').chmod(0o666)
        with self.assertRaisesRegex(ValueError, 'source file'):
            delivery.read_certificate(self.source, self.root / 'renewal.lock')
        (self.source / 'privkey.pem').chmod(0o600)
        lock = self.root / 'renewal.lock'
        lock.chmod(0o666)
        with self.assertRaisesRegex(PermissionError, 'renewal lock'):
            delivery.read_certificate(self.source, lock)


@unittest.skipUnless(os.name == 'posix', 'receiver wrapper requires POSIX shell')
class ReceiverCommandTests(unittest.TestCase):
    def test_unknown_or_missing_command_cannot_reach_sudo(self):
        wrapper = Path(__file__).resolve().parents[1] / 'staging-sync/commonex-staging-certificate-receive'
        for command in ('', 'status', 'install --extra', 'install; id'):
            result = subprocess.run(['sh', str(wrapper)], input=b'private input',
                                    env=dict(os.environ, SSH_ORIGINAL_COMMAND=command), capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(result.stdout, b'')
            self.assertNotIn(b'private input', result.stderr)

@unittest.skipUnless(os.name == 'posix' and os.geteuid() == 0,
                     'host certificate installation requires Linux root')
class InstallationTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.certbot = self.root / 'certbot'
        self.live = self.certbot / 'live/commonex.ru'
        self.live.mkdir(parents=True)
        self.lock = self.root / 'deploy.lock'
        self.intent = self.root / 'activation-intent.json'
        self.commands = []
        self.fail_once = None

    def runner(self, command, cwd=None):
        self.commands.append(command)
        if command == self.fail_once:
            self.fail_once = None
            raise RuntimeError('nginx rejected candidate')
        if command[:3] == ['openssl', 'x509', '-in']:
            if '-ext' in command:
                return b'X509v3 Subject Alternative Name:\n DNS:commonex.ru, DNS:*.commonex.ru\n'
            if '-pubkey' in command:
                return b'public-key'
        if command[:2] == ['openssl', 'pkey']:
            return b'public-key'
        if 'ps' in command:
            return b'nginx-container\n'
        return b''

    def install(self, data):
        # Temporary test ancestors are not an installed root-owned host layout.
        with patch.object(delivery, 'trusted_directory', side_effect=lambda path: path.mkdir(parents=True, exist_ok=True)):
            delivery.install_certificate(data, self.certbot, self.root, self.lock,
                                         self.intent, self.runner)

    def test_install_preserves_bind_mount_directory_and_repeat_delivery(self):
        inode = self.live.stat().st_ino
        self.install(bundle())
        before = {name: os.readlink(self.live / name) for name in delivery.FILES}
        self.install(bundle())
        self.assertEqual(self.live.stat().st_ino, inode)
        self.assertEqual((self.live / 'privkey.pem').stat().st_mode & 0o777, 0o640)
        self.assertEqual(set(before), set(delivery.FILES))
        self.assertEqual({name: os.readlink(self.live / name) for name in delivery.FILES}, before)
        self.assertTrue(any('-t' in command for command in self.commands))
        self.assertTrue(any('reload' in command for command in self.commands))

    def test_unfinished_activation_blocks_changes(self):
        self.intent.write_text('{}')
        with self.assertRaisesRegex(ValueError, 'unfinished'):
            self.install(bundle())
        self.assertEqual(list(self.live.iterdir()), [])
        self.assertFalse(self.commands)

    def test_invalid_candidate_preserves_existing_certificate_without_nginx_changes(self):
        for name in delivery.FILES:
            (self.live / name).write_bytes(b'previous-' + name.encode())
        with patch.object(delivery, 'validate_certificate', side_effect=ValueError('invalid candidate')):
            with self.assertRaisesRegex(ValueError, 'invalid candidate'):
                self.install(bundle())
        for name in delivery.FILES:
            self.assertEqual((self.live / name).read_bytes(), b'previous-' + name.encode())
        self.assertFalse(self.commands)

    def test_nginx_failure_restores_previous_files(self):
        for name in delivery.FILES:
            (self.live / name).write_bytes(b'previous-' + name.encode())
        self.fail_once = ['docker', 'compose', '--env-file', '.env', '-f', 'docker-compose-prod.yml',
                          'exec', '-T', 'nginx', 'nginx', '-t', '-c', '/tmp/commonex-nginx.conf']
        with self.assertRaisesRegex(RuntimeError, 'rejected'):
            self.install(bundle())
        for name in delivery.FILES:
            self.assertEqual((self.live / name).read_bytes(), b'previous-' + name.encode())
        self.assertTrue(any('reload' in command for command in self.commands))


if __name__ == '__main__':
    unittest.main()
