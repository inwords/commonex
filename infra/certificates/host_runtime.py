"""Shared host subprocess and locking primitives; stable installer dependency."""

from contextlib import contextmanager
import os
import stat
import subprocess
import time


def command(arguments, timeout=60):
    return subprocess.run(arguments, check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, timeout=timeout).stdout


@contextmanager
def lock(path, timeout=300):
    import fcntl

    path.parent.mkdir(mode=0o755, parents=True, exist_ok=True)
    metadata = path.parent.lstat()
    if (not stat.S_ISDIR(metadata.st_mode) or metadata.st_uid != 0
            or metadata.st_mode & 0o022):
        raise PermissionError('untrusted lock directory')
    descriptor = os.open(path, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    try:
        metadata = os.fstat(descriptor)
        if (not stat.S_ISREG(metadata.st_mode) or metadata.st_uid != 0
                or stat.S_IMODE(metadata.st_mode) != 0o600):
            raise PermissionError('untrusted lock file')
        deadline = time.monotonic() + timeout
        while True:
            try:
                fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() >= deadline:
                    raise TimeoutError('timed out waiting for operation lock')
                time.sleep(1)
        current = path.lstat()
        if (metadata.st_dev, metadata.st_ino) != (current.st_dev, current.st_ino):
            raise PermissionError('lock path changed during acquisition')
        yield
    finally:
        os.close(descriptor)
