"""Bounded, allowlisted release archives shared by host delivery commands."""

import io
import tarfile


def read_members(stream, allowed_names, max_bytes, mode='r|gz'):
    payload = stream.read(max_bytes + 1)
    if len(payload) > max_bytes:
        raise ValueError('release archive exceeds size limit')
    files = {}
    total = 0
    with tarfile.open(fileobj=io.BytesIO(payload), mode=mode) as archive:
        for member in archive:
            name = member.name.removeprefix('./')
            if not member.isfile() or member.size < 0 or name not in allowed_names:
                raise ValueError('invalid release member: ' + member.name)
            if name in files:
                raise ValueError('duplicate release member: ' + name)
            total += member.size
            if total > max_bytes:
                raise ValueError('extracted release exceeds size limit')
            source = archive.extractfile(member)
            if source is None:
                raise ValueError('cannot read release member: ' + name)
            with source:
                files[name] = source.read()
    if set(files) != set(allowed_names):
        raise ValueError('release is missing files: ' + str(sorted(set(allowed_names) - set(files))))
    return files
