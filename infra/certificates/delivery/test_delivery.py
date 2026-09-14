import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent.parent / 'deploy'))
sys.path.insert(0, str(HERE))
import bootstrap
import client
import contract
import install_commonex_deploy as installer

SHA = 'a' * 40
IMAGE = 'ruggedbl/commonex-certificates@sha256:' + 'b' * 64


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.files = {name: (HERE.parent / name).read_bytes() for name in contract.FILES}

    def test_actual_release_roundtrip_excludes_host_secrets_and_fixed_installer(self):
        payload = contract.encode_archive(self.files, SHA, IMAGE)
        unpacked = contract.read_archive(io.BytesIO(payload), SHA)
        self.assertEqual(set(unpacked), set(contract.FILES) | {'release.json'})
        self.assertEqual(json.loads(unpacked.pop('release.json'))['image'], IMAGE)
        self.assertEqual(unpacked, self.files)

    def test_release_rejects_grafana_configuration(self):
        for name in ('monitoring/datasources/certificates.yaml',
                     'monitoring/alerting/certificates.json'):
            with self.subTest(name=name):
                self.assertNotIn(name, contract.FILES)
                with self.assertRaises(ValueError):
                    contract.encode_archive(dict(self.files, **{name: b'{}'}), SHA, IMAGE)
                payload = contract.encode_archive(self.files, SHA, IMAGE)
                output = io.BytesIO(payload)
                with tarfile.open(fileobj=output, mode='a') as archive:
                    member = tarfile.TarInfo(name)
                    member.size = 2
                    archive.addfile(member, io.BytesIO(b'{}'))
                with self.assertRaises(ValueError):
                    contract.read_archive(io.BytesIO(output.getvalue()), SHA)

    def test_archive_rejects_duplicate_link_and_unexpected_paths(self):
        for name, kind in [('renew.py', tarfile.SYMTYPE), ('../renew.py', tarfile.REGTYPE),
                           ('yandex-key.json', tarfile.REGTYPE), ('renew.py', tarfile.REGTYPE)]:
            with self.subTest(name=name, kind=kind):
                output = io.BytesIO()
                with tarfile.open(fileobj=output, mode='w') as archive:
                    for entry in ('renew.py', name):
                        member = tarfile.TarInfo(entry)
                        member.type = kind if entry == name else tarfile.REGTYPE
                        member.size = 1 if member.isfile() else 0
                        member.linkname = '/etc/passwd' if member.issym() else ''
                        archive.addfile(member, io.BytesIO(b'x'))
                with self.assertRaises(ValueError):
                    contract.read_archive(io.BytesIO(output.getvalue()), SHA)

    def test_untrusted_manifest_cannot_select_mutable_other_repository_or_local_image(self):
        for image in ('ruggedbl/commonex-certificates:latest', 'evil/image@sha256:' + 'b' * 64,
                      'sha256:' + 'b' * 64):
            with self.subTest(image=image), self.assertRaises(ValueError):
                contract.encode_archive(self.files, SHA, image)

    def test_wrong_revision_missing_payload_and_oversized_input_are_rejected(self):
        payload = contract.encode_archive(self.files, SHA, IMAGE)
        with self.assertRaises(ValueError):
            contract.read_archive(io.BytesIO(payload), 'c' * 40)
        with self.assertRaises(ValueError):
            contract.encode_archive({}, SHA, IMAGE)
        with self.assertRaises(ValueError):
            contract.read_archive(io.BytesIO(b'x' * (contract.MAX_ARCHIVE_BYTES + 1)), SHA)

    def test_client_streams_validated_bundle_and_propagates_host_failure(self):
        with patch.object(client.subprocess, 'run') as run:
            client.deliver('deploy', SHA, '17', IMAGE)
            self.assertEqual(run.call_args.args[0], ['ssh', 'commonex-certificates', f'deploy {SHA} 17'])
            contract.read_archive(io.BytesIO(run.call_args.kwargs['input']), SHA)
            run.side_effect = subprocess.CalledProcessError(1, 'ssh')
            with self.assertRaises(subprocess.CalledProcessError):
                client.deliver('rollback', SHA, '18')

    def test_client_rejects_injected_arguments_before_ssh(self):
        with patch.object(client.subprocess, 'run') as run:
            for revision, number in [(SHA + ';id', '1'), (SHA, '1;id'), (SHA, '0')]:
                with self.assertRaises(ValueError):
                    client.deliver('rollback', revision, number)
            run.assert_not_called()

    def test_preflight_rejects_outdated_fixed_installer(self):
        with patch.object(client.subprocess, 'run') as run:
            run.return_value.stdout = json.dumps({'installer_fingerprint': 'old'})
            with self.assertRaisesRegex(RuntimeError, 'bootstrap is outdated'):
                client.deliver('status')

    def test_bootstrap_bundle_imports_and_uses_shared_versioned_installer(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            bundle = root / 'bundle'
            bootstrap.prepare(bundle)
            self.assertNotIn('yandex-key.json', [p.name for p in bundle.rglob('*')])
            result = subprocess.run([sys.executable, '-B', '-c',
                                     'import sys;sys.path.insert(0,sys.argv[1]);import commonex_deploy',
                                     str(bundle)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([sys.executable, '-B', '-c',
                                     'import sys;sys.path.insert(0,sys.argv[1]);import bootstrap;print(bootstrap.fingerprint())',
                                     str(bundle)], capture_output=True, text=True, check=True)
            self.assertEqual(result.stdout.strip(), bootstrap.fingerprint())
            layout = installer.InstallLayout.under(root / 'host')
            plan = installer.install_version(bundle, SHA, layout, require_root=False)
            self.assertEqual(plan['tool_git_sha'], SHA)
            if os.name == 'posix':
                result = installer.install_version(bundle, SHA, layout, apply=True, require_root=False)
                self.assertEqual(result['status'], 'installed')
                self.assertEqual(layout.current.resolve().name, SHA)

    def test_runner_update_does_not_require_fixed_installer_bootstrap(self):
        expected = bootstrap.fingerprint()
        actual_read = Path.read_bytes
        def changed_runner(path):
            data = actual_read(path)
            return data + b'\n# next runner revision\n' if path.name == 'renew.py' else data
        with patch.object(Path, 'read_bytes', changed_runner):
            self.assertEqual(bootstrap.fingerprint(), expected)


if __name__ == '__main__':
    unittest.main()
