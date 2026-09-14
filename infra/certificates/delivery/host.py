#!/usr/bin/env python3
"""Fixed host adapter for certificate releases; installed separately from releases."""

import base64
from contextlib import ExitStack
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import stat
import sys
import time
import uuid

import contract
import install_commonex_deploy as storage
import host_runtime as runner
from commonex_host.activation import (
    ActivationRequest, ActivationTransaction, _ActivationDependencies,
    ActivationCommittedAuditError, AmbiguousActivationCommitError, ConfigurationRestoreError,
)
from commonex_host.trusted_files import TrustedDurableFiles, _TrustedFileLocations


TIMERS = ('commonex-certificate-renew.timer', 'commonex-certificate-monitor.timer')
SERVICES = ('commonex-certificate-renew.service', 'commonex-certificate-monitor.service')


def timestamp():
    return datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')


class CertificateDelivery:
    def __init__(self, root=Path('/'), *, command=runner.command,
                 lock=runner.lock, sleep=time.sleep, monotonic=time.monotonic,
                 enforce_root=True):
        self.root = root
        self.command = command
        self.lock = lock
        self.sleep = sleep
        self.monotonic = monotonic
        self.enforce_root = enforce_root
        self.base = self.path('/var/lib/commonex/certificates/delivery')
        self.versions = self.path('/opt/commonex/certificates/versions')
        self.current = self.path('/opt/commonex/certificates/current')
        self.config = self.path('/etc/commonex/certificates/renewal.json')
        self.restoring = False
        self.timer_state = None
        self.files = TrustedDurableFiles(
            _TrustedFileLocations(self.base, enforce_root,
                log_path=self.base / 'delivery.log', rollback_root=self.base / 'backups'),
            sync_directory=storage._fsync_directory, clock=timestamp)

    def path(self, name):
        return self.root / name.lstrip('/')

    def trusted_directory(self, path, mode=0o755):
        if not path.exists() and not path.is_symlink():
            self.trusted_directory(path.parent)
            storage._ensure_durable_directory(path, mode=mode)
        storage._assert_trusted_install_path(path, directory=True,
                                             enforce_root_ownership=self.enforce_root)
        if path != self.root and self.root in path.parents:
            self.trusted_directory(path.parent)

    def read(self, path):
        self.trusted_directory(path.parent)
        return storage._read_trusted_file(path,
            enforce_root_ownership=self.enforce_root)[0]

    def write(self, path, content, mode=0o644):
        self.trusted_directory(path.parent)
        if path.exists() or path.is_symlink():
            self.read(path)
        storage._atomic_write(path, content, mode)

    def write_json(self, path, value):
        self.write(path, json.dumps(value, sort_keys=True).encode() + b'\n', 0o600)

    def read_json(self, path):
        return json.loads(self.read(path))

    def managed_path(self, name):
        if name.startswith('systemd/') and name in contract.FILES:
            return self.path('/etc/systemd/system') / Path(name).name
        raise ValueError('file is not a managed host configuration')

    def current_target(self):
        self.trusted_directory(self.current.parent)
        metadata = self.current.lstat()
        if not stat.S_ISLNK(metadata.st_mode) or (self.enforce_root and metadata.st_uid != 0):
            raise PermissionError('current must be a root-owned version symlink')
        target = self.current.resolve(strict=True)
        if target.parent != self.versions or re.fullmatch('[0-9a-f]{40}', target.name) is None:
            raise ValueError('current points outside the certificate version directory')
        self.trusted_directory(target)
        self.read(target / 'renew.py')
        return target

    def select(self, target):
        if target.parent != self.versions:
            raise ValueError('invalid version target')
        self.trusted_directory(target)
        self.current_target()
        temporary = self.current.parent / ('.current-' + uuid.uuid4().hex)
        try:
            temporary.symlink_to(target)
            os.replace(temporary, self.current)
            storage._fsync_directory(self.current.parent)
        finally:
            temporary.unlink(missing_ok=True)

    def ensure_no_intent(self):
        if self.files.read_activation_intent() is not None:
            raise RuntimeError('certificate delivery requires manual reconciliation')
        for name in ('/var/lib/commonex/activation-intent.json',
                     '/etc/commonex/ssl/certbot/.commonex-dns-reconciliation-required'):
            path = self.path(name)
            if path.exists() or path.is_symlink():
                raise RuntimeError('production has an unfinished activation or DNS operation')

    def capture_timers(self):
        result = {}
        for timer in TIMERS:
            # show reports disabled/inactive states without a failing exit status.
            values = self.command(['systemctl', 'show', timer,
                '--property=UnitFileState', '--property=ActiveState']).decode().splitlines()
            fields = dict(line.split('=', 1) for line in values if '=' in line)
            if fields.get('UnitFileState') not in ('enabled', 'disabled') or fields.get('ActiveState') not in ('active', 'inactive'):
                raise RuntimeError('certificate timer is not in a stable enabled/disabled state')
            result[timer] = {'enabled': fields['UnitFileState'] == 'enabled',
                             'active': fields['ActiveState'] == 'active'}
        return result

    def stop_timers(self):
        self.command(['systemctl', 'disable', '--now', *TIMERS])

    def wait_services(self):
        deadline = self.monotonic() + 45 * 60
        while True:
            states = [self.command(['systemctl', 'show', service,
                '--property=ActiveState', '--value']).decode().strip() for service in SERVICES]
            if all(state in ('inactive', 'failed') for state in states):
                return
            if self.monotonic() >= deadline:
                raise TimeoutError('waiting for an in-flight certificate operation timed out')
            self.sleep(5)

    def restore_timers(self):
        if self.timer_state is None:
            raise RuntimeError('timer state was not captured')
        for timer, state in self.timer_state.items():
            self.command(['systemctl', 'enable' if state['enabled'] else 'disable', timer])
            self.command(['systemctl', 'start' if state['active'] else 'stop', timer])
        if self.capture_timers() != self.timer_state:
            raise RuntimeError('certificate timer restoration did not take effect')

    def configuration_locks(self):
        stack = ExitStack()
        try:
            for name in ('certificate-renewal.lock', 'certificate-monitor.lock', 'deploy.lock'):
                stack.enter_context(self.lock(self.path('/run/commonex') / name))
            if self.path('/var/lib/commonex/activation-intent.json').exists():
                raise RuntimeError('application activation requires reconciliation')
            return stack
        except BaseException:
            stack.close()
            raise

    def snapshot(self):
        target = self.current_target()
        payload = {name: base64.b64encode(self.read(self.managed_path(name))).decode()
                   for name in contract.MANAGED_FILES}
        return {'revision': target.name, 'target': str(target), 'files': payload,
                'renewal': base64.b64encode(self.read(self.config)).decode(),
                'timers': self.capture_timers()}

    def read_state(self):
        run, history = self.files.read_activation_state()
        if history:
            if self.current_target().name != history[0]:
                raise RuntimeError('current certificate version differs from activation history')
            return run, history
        # The initial SSH installation is a genuine rollback target, not overwritten.
        snapshot = self.snapshot()
        release = snapshot['revision']
        baseline = self.base / 'releases' / (release + '.json')
        if baseline.exists():
            if self.read_json(baseline) != {'baseline': snapshot}:
                raise RuntimeError('initial certificate installation changed during adoption')
        else:
            self.write_json(baseline, {'baseline': snapshot})
        return run, [release]

    def stage(self, revision, payload):
        target = self.versions / revision
        if target.exists() or target.is_symlink():
            self.trusted_directory(target)
            actual = {str(path.relative_to(target)).replace('\\', '/')
                      for path in target.rglob('*') if not path.is_dir()}
            if actual != set(payload) or any(self.read(target / name) != data for name, data in payload.items()):
                raise ValueError('immutable certificate revision already has different contents')
        else:
            self.trusted_directory(self.versions)
            staging = self.versions / ('.staging-' + revision + '-' + uuid.uuid4().hex)
            try:
                self.trusted_directory(staging)
                for name, data in payload.items():
                    self.write(staging / name, data)
                os.rename(staging, target)
                storage._fsync_directory(self.versions)
            finally:
                if staging.exists() or staging.is_symlink():
                    if (staging.is_symlink()
                            or staging.resolve().parent != self.versions.resolve()):
                        raise PermissionError('staging cleanup target is not trusted')
                    shutil.rmtree(staging)
                    storage._fsync_directory(self.versions)
        record = self.base / 'releases' / (revision + '.json')
        value = {'target': str(target)}
        if record.exists() and self.read_json(record) != value:
            raise ValueError('release identity conflicts with retained baseline')
        self.write_json(record, value)

    def release_record(self, revision):
        return self.read_json(self.base / 'releases' / (revision + '.json'))

    def validate_release(self, revision):
        record = self.release_record(revision)
        if 'baseline' in record:
            target = Path(record['baseline']['target'])
            if target != self.versions / revision:
                raise ValueError('retained baseline target is invalid')
            self.trusted_directory(target)
            self.read(target / 'renew.py')
            config = base64.b64decode(record['baseline']['renewal'])
            image = json.loads(config)['image']
            contract.validate_manifest(json.dumps({'schema': 1, 'revision': revision,
                'image': image}).encode(), revision, allow_local=True)
            return target
        target = Path(record['target'])
        if target != self.versions / revision:
            raise ValueError('retained release target is invalid')
        payload = {name: self.read(target / name) for name in (*contract.FILES, 'release.json')}
        # Reuse the wire validator for retained files as well as incoming releases.
        manifest = contract.validate_manifest(payload['release.json'], revision)
        contract.encode_archive(
            {name: payload[name] for name in contract.FILES}, revision, manifest['image'])
        for name in contract.RUNNER_FILES:
            compile(payload[name], name, 'exec')
        self.command(['systemd-analyze', 'verify',
            *(str(target / 'systemd' / name) for name in (*SERVICES, *TIMERS))])
        return target

    def release_image(self, target):
        record = self.release_record(target.name)
        if 'baseline' in record:
            return json.loads(base64.b64decode(record['baseline']['renewal']))['image']
        return contract.validate_manifest(self.read(target / 'release.json'), target.name)['image']

    def pull_release(self, target):
        image = self.release_image(target)
        if image.startswith('sha256:'):
            self.command(['docker', 'image', 'inspect', image])
        else:
            self.command(['docker', 'pull', image], timeout=600)

    def backup_configuration(self, directory):
        self.timer_state = self.capture_timers()
        # Renewal changes certificate/state files, not this managed configuration.
        # Its locks are acquired only after timers stop and active services finish.
        self.write_json(directory / 'configuration.json', self.snapshot())

    def write_intent(self, operation, release, run, previous, backup):
        intent = {'operation': operation, 'candidate_release': release,
                  'run_number': run, 'previous_release': previous,
                  'rollback_backup': backup.name}
        self.files.persist_activation_intent(intent)
        return intent

    def apply_snapshot(self, snapshot):
        with self.configuration_locks():
            for name, content in snapshot['files'].items():
                self.write(self.managed_path(name), base64.b64decode(content))
            self.write(self.config, base64.b64decode(snapshot['renewal']), 0o600)
            self.select(Path(snapshot['target']))
        self.command(['systemctl', 'daemon-reload'])

    def install_release(self, target):
        self.stop_timers()
        self.wait_services()
        record = self.release_record(target.name)
        if 'baseline' in record:
            self.apply_snapshot(record['baseline'])
        else:
            with self.configuration_locks():
                for name in contract.MANAGED_FILES:
                    self.write(self.managed_path(name), self.read(target / name))
                config = self.read_json(self.config)
                config['image'] = self.release_image(target)
                self.write_json(self.config, config)
                self.select(target)
            self.command(['systemctl', 'daemon-reload'])

    def restore_configuration(self, backup):
        self.restoring = True
        self.stop_timers()
        self.wait_services()
        snapshot = self.read_json(backup / 'configuration.json')
        self.timer_state = snapshot['timers']
        self.apply_snapshot(snapshot)

    def reconcile_active(self):
        target = self.current_target()
        operations = ('renew', 'monitor') if self.restoring else ('rehearse', 'renew', 'monitor')
        for operation in operations:
            self.command(['/usr/bin/python3', '-B', str(target / 'renew.py'), operation], timeout=45 * 60)
        self.restore_timers()

    def audit_best_effort(self, message):
        try:
            self.files.append_audit(message)
        except Exception:
            # Audit storage failure must not replace the original activation error.
            pass

    def activate(self, operation, revision, run, stream=None):
        self.trusted_directory(self.base, 0o700)
        with self.lock(self.path('/run/commonex/certificate-delivery.lock')):
            self.ensure_no_intent()
            if operation == 'deploy':
                payload = contract.read_archive(stream, revision)
                # Replay checks precede durable staging as well as activation.
                last_run, history = self.files.read_activation_state()
                if run <= last_run:
                    raise ValueError('activation run is older than or equal to last success')
                if not history and self.current_target().name == revision:
                    raise ValueError('initial CI deployment requires a reviewed revision distinct from the manual baseline')
                self.stage(revision, payload)
            transaction = ActivationTransaction(_ActivationDependencies(
                ensure_no_intent=self.ensure_no_intent, read_state=self.read_state,
                rollback_path=lambda sha: self.base / 'backups' / ('deploy-' + sha + '-' + timestamp()),
                validate_release=self.validate_release, pull_release=self.pull_release,
                prepare_rollback=lambda path: self.trusted_directory(path, 0o700),
                backup_configuration=self.backup_configuration, write_intent=self.write_intent,
                install_release=self.install_release, reconcile_active=self.reconcile_active,
                write_state=self.files.write_activation_state, clear_intent=self.files.clear_activation_intent,
                restore_configuration=self.restore_configuration, cleanup_releases=lambda history: None,
                audit=self.files.append_audit, audit_best_effort=self.audit_best_effort))
            try:
                return transaction.activate(ActivationRequest(operation, revision, run))
            except BaseException:
                if self.files.read_activation_intent() is not None:
                    try:
                        self.stop_timers()
                    except Exception:
                        # Fixed service guards still block new starts while the intent exists.
                        pass
                raise


def run_cli():
    try:
        if os.geteuid() != 0 or sys.argv[1:] != ['forced']:
            raise ValueError('requires the installed root forced command')
        arguments = shlex.split(os.environ.get('SSH_ORIGINAL_COMMAND', ''))
        host = CertificateDelivery()
        if arguments == ['status']:
            from bootstrap import fingerprint
            host.ensure_no_intent()
            run, history = host.files.read_activation_state()
            print(json.dumps({'last_successful_run': run, 'history': history,
                              'current': host.current_target().name,
                              'installer_fingerprint': fingerprint()}))
        elif (len(arguments) == 3 and arguments[0] in ('deploy', 'rollback')
              and re.fullmatch('[0-9a-f]{40}', arguments[1])
              and re.fullmatch('[1-9][0-9]{0,19}', arguments[2])):
            result = host.activate(arguments[0], arguments[1], int(arguments[2]), sys.stdin.buffer)
            print(json.dumps({'revision': result.release, 'run': result.run_number, 'status': 'PASS'}))
        else:
            raise ValueError('unsupported certificate delivery command')
        return 0
    except ActivationCommittedAuditError:
        print('certificate activation committed; final audit failed; configuration remains active', file=sys.stderr)
        return 2
    except (AmbiguousActivationCommitError, ConfigurationRestoreError):
        print('certificate delivery requires manual reconciliation; timers remain disabled', file=sys.stderr)
        return 3
    except Exception as error:
        # Never expose subprocess output, HTTP payloads, environment, or credentials.
        print('certificate delivery failed: ' + type(error).__name__, file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(run_cli())
