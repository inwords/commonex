"""Publish a certificate release through the fixed certificate SSH command."""

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'deploy'))
from contract import FILES, REVISION, encode_archive
from bootstrap import fingerprint


def deliver(operation, revision=None, run_number=None, image=None):
    if operation == 'status':
        result = subprocess.run(['ssh', 'commonex-certificates', 'status'],
                                check=True, capture_output=True, text=True)
        status = json.loads(result.stdout)
        if status.get('installer_fingerprint') != fingerprint():
            raise RuntimeError('certificate host bootstrap is outdated; install the reviewed delivery bundle first')
        print(json.dumps(status, sort_keys=True))
        return status
    if not revision or not REVISION.fullmatch(revision):
        raise ValueError('release must be a lowercase 40-character Git SHA')
    if not run_number or not re.fullmatch(r'[1-9][0-9]{0,19}', str(run_number)):
        raise ValueError('workflow run number must be a positive integer')
    payload = None
    if operation == 'deploy':
        source = Path(__file__).resolve().parents[1]
        payload = encode_archive({name: (source / name).read_bytes() for name in FILES}, revision, image)
    elif operation != 'rollback':
        raise ValueError('unsupported certificate operation')
    subprocess.run(['ssh', 'commonex-certificates', f'{operation} {revision} {run_number}'],
                   input=payload, check=True)
    return None


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=('deploy', 'rollback', 'status'))
    parser.add_argument('--revision')
    parser.add_argument('--run-number')
    parser.add_argument('--image')
    options = parser.parse_args()
    deliver(options.operation, options.revision, options.run_number, options.image)
