#!/usr/bin/env python3
"""Write the private runtime inputs shared by staging and production delivery."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import sys
from typing import Mapping, Optional, Sequence


RUNTIME_KEYS = (
    "POSTGRES_PORT",
    "POSTGRES_USER_NAME",
    "POSTGRES_PASSWORD",
    "POSTGRES_DATABASE",
    "POSTGRES_HOST",
    "POSTGRES_SCHEMA",
    "OPEN_EXCHANGE_RATES_API_ID",
    "DEVTOOLS_SECRET",
    "GF_SECURITY_ADMIN_USER",
    "GF_SECURITY_ADMIN_PASSWORD",
)
STAGING_ROUTES = {
    "COMMONEX_WEB_HOSTS": "staging.commonex.ru",
    "COMMONEX_API_HOST": "staging.commonex.ru",
    "COMMONEX_GRPC_HOST": "staging-grpc.commonex.ru",
    "COMMONEX_GRAFANA_HOST": "staging-gf.commonex.ru",
    "GF_SERVER_ROOT_URL": "https://staging-gf.commonex.ru/",
}


def write_runtime_environment(
    destination: Path, environment: str, values: Mapping[str, str]
) -> None:
    if environment not in {"staging", "production"}:
        raise ValueError("invalid deployment environment")
    entries = {}
    for key in RUNTIME_KEYS:
        value = values.get(key)
        if value is None or (environment == "staging" and not value):
            raise ValueError(f"missing runtime input: {key}")
        if any(character in value for character in ("\r", "\n", "\x00")):
            raise ValueError(f"invalid runtime input: {key}")
        entries[key] = value
    if environment == "staging":
        entries.update(STAGING_ROUTES)
        if destination.parent.resolve() == Path.cwd().resolve():
            raise ValueError("staging runtime inputs require a private directory")
    destination.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    if environment == "staging":
        destination.parent.chmod(0o700)
    descriptor = os.open(
        destination,
        os.O_WRONLY | os.O_CREAT | os.O_TRUNC | getattr(os, "O_NOFOLLOW", 0),
        0o600,
    )
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as output:
        destination.chmod(0o600)
        output.write("".join(f"{key}={value}\n" for key, value in entries.items()))


def main(arguments: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--environment", choices=("staging", "production"), required=True)
    parser.add_argument("output", type=Path)
    options = parser.parse_args(arguments)
    try:
        write_runtime_environment(options.output, options.environment, os.environ)
    except ValueError as error:
        print(f"runtime-environment: {error}", file=sys.stderr)
        return 1
    except OSError:
        print("runtime-environment: unable to write private inputs", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
