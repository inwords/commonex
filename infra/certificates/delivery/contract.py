"""One release format used by the certificate publisher and fixed host installer."""

import io
import json
import re
import tarfile

from commonex_host.archive import read_members


RUNNER_FILES = ('renew.py', 'host_runtime.py')
MANAGED_FILES = (
    'systemd/commonex-certificate-renew.service',
    'systemd/commonex-certificate-renew.timer',
    'systemd/commonex-certificate-monitor.service',
    'systemd/commonex-certificate-monitor.timer',
)
FILES = RUNNER_FILES + MANAGED_FILES
MAX_ARCHIVE_BYTES = 1024 * 1024
REVISION = re.compile(r'[a-f0-9]{40}')
IMAGE = re.compile(r'ruggedbl/commonex-certificates@sha256:[a-f0-9]{64}')


def validate_manifest(payload, revision, allow_local=False):
    if not isinstance(revision, str) or REVISION.fullmatch(revision) is None:
        raise ValueError('release must be a lowercase 40-character Git SHA')
    manifest = json.loads(payload)
    if (not isinstance(manifest, dict) or set(manifest) != {'schema', 'revision', 'image'}
            or type(manifest['schema']) is not int or manifest['schema'] != 1
            or manifest['revision'] != revision):
        raise ValueError('invalid certificate release manifest')
    image = manifest['image']
    if not isinstance(image, str) or not (
            IMAGE.fullmatch(image)
            or allow_local and re.fullmatch(r'sha256:[a-f0-9]{64}', image)):
        raise ValueError('certificate image must use the allowed repository and immutable digest')
    return manifest


def read_archive(stream, revision):
    files = read_members(stream, (*FILES, 'release.json'), MAX_ARCHIVE_BYTES, mode='r|')
    validate_manifest(files['release.json'], revision)
    return files


def encode_archive(files, revision, image):
    if set(files) != set(FILES):
        raise ValueError('certificate release payload does not match the allowlist')
    manifest = json.dumps({'schema': 1, 'revision': revision, 'image': image}, sort_keys=True).encode()
    validate_manifest(manifest, revision)
    output = io.BytesIO()
    with tarfile.open(fileobj=output, mode='w') as archive:
        for name, data in sorted(dict(files, **{'release.json': manifest}).items()):
            member = tarfile.TarInfo(name)
            member.size = len(data)
            member.mode = 0o644
            archive.addfile(member, io.BytesIO(data))
    payload = output.getvalue()
    read_archive(io.BytesIO(payload), revision)
    return payload
