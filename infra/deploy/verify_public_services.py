#!/usr/bin/env python3

import argparse
from http.client import HTTPException
import os
import subprocess
import sys
import time
from typing import Optional, Sequence
from urllib.error import HTTPError
from urllib.parse import urljoin
from urllib.request import HTTPRedirectHandler, build_opener


DEFAULT_ENDPOINTS = (
    "https://commonex.ru/",
    "https://commonex.ru/api/health",
    "https://dev-api.commonex.ru/api/health",
    "https://gf.commonex.ru/",
)
DEFAULT_GRPC_ENDPOINT = "https://grpc.commonex.ru/"
GRPC_HEALTH_METHOD = "grpc.health.v1.Health/Check"
CURL_COMMAND = ("curl",)
ALLOWED_REDIRECT_TARGETS = {
    "https://gf.commonex.ru/": frozenset(
        ("https://gf.commonex.ru/login",)
    ),
}
DEFAULT_ATTEMPTS = 6
DEFAULT_RETRY_DELAY_SECONDS = 3.0
DEFAULT_TIMEOUT_SECONDS = 15.0
REDIRECT_STATUS_CODES = frozenset((301, 302, 303, 307, 308))


class NoRedirectHandler(HTTPRedirectHandler):
    def redirect_request(self, *_args: object, **_kwargs: object) -> None:
        return None


URL_OPENER = build_opener(NoRedirectHandler())


class VerificationError(RuntimeError):
    """Raised when a public endpoint does not recover within the retry budget."""


def positive_integer(value: str) -> int:
    parsed = int(value)
    if parsed < 1:
        raise argparse.ArgumentTypeError("must be a positive integer")
    return parsed


def positive_float(value: str) -> float:
    parsed = float(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be greater than zero")
    return parsed


def non_negative_float(value: str) -> float:
    parsed = float(value)
    if parsed < 0:
        raise argparse.ArgumentTypeError("must not be negative")
    return parsed


def read_endpoint(endpoint: str, timeout: float) -> None:
    try:
        response = URL_OPENER.open(endpoint, timeout=timeout)
    except HTTPError as error:
        try:
            if error.code not in REDIRECT_STATUS_CODES:
                raise
            location = error.headers.get("Location")
            if location is None:
                raise ValueError(f"redirect from {endpoint} has no location")
            target = urljoin(endpoint, location)
            if target not in ALLOWED_REDIRECT_TARGETS.get(endpoint, ()):
                raise ValueError(f"redirected from {endpoint} to {target}")
        finally:
            error.close()
        try:
            response = URL_OPENER.open(target, timeout=timeout)
        except HTTPError as target_error:
            target_error.close()
            raise

    with response:
        response.read(1)


def verify_endpoint(
    endpoint: str,
    *,
    attempts: int,
    retry_delay: float,
    timeout: float,
) -> None:
    errors: list[Exception] = []
    for attempt in range(1, attempts + 1):
        try:
            read_endpoint(endpoint, timeout)
            print(f"Verified {endpoint}")
            return
        except (HTTPException, OSError, ValueError) as error:
            errors.append(error)
            if attempt < attempts:
                time.sleep(retry_delay)

    raise VerificationError(
        f"{endpoint} failed after {attempts} attempts: {errors[-1]}"
    )


def read_grpc_ingress(endpoint: str, timeout: float) -> None:
    target = urljoin(endpoint, GRPC_HEALTH_METHOD)
    result = subprocess.run(
        [
            *CURL_COMMAND,
            "--http2",
            "--silent",
            "--show-error",
            "--dump-header",
            "-",
            "--output",
            os.devnull,
            "--max-time",
            str(timeout),
            "--header",
            "Content-Type: application/grpc",
            "--header",
            "TE: trailers",
            "--data-binary",
            "@-",
            target,
        ],
        input=b"\x00\x00\x00\x00\x00",
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout + 1,
        check=False,
    )
    if result.returncode != 0:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise OSError(detail or f"curl exited with status {result.returncode}")

    header_lines = result.stdout.decode("iso-8859-1").splitlines()
    status_indices = [
        index
        for index, line in enumerate(header_lines)
        if line.startswith("HTTP/")
    ]
    if not status_indices:
        raise ValueError("gRPC probe returned no HTTP status")

    response_lines = header_lines[status_indices[-1] :]
    if response_lines[0].strip() != "HTTP/2 200":
        raise ValueError(f"gRPC probe returned {response_lines[0].strip()}")

    headers = {
        name.strip().lower(): value.strip().lower()
        for line in response_lines[1:]
        if ":" in line
        for name, value in (line.split(":", 1),)
    }
    if not headers.get("content-type", "").startswith("application/grpc"):
        raise ValueError("gRPC probe returned a non-gRPC content type")
    if headers.get("grpc-status") != "12":
        raise ValueError(
            "gRPC probe did not reach the backend's unimplemented health method"
        )


def verify_grpc_ingress(
    endpoint: str,
    *,
    attempts: int,
    retry_delay: float,
    timeout: float,
) -> None:
    errors: list[Exception] = []
    for attempt in range(1, attempts + 1):
        try:
            read_grpc_ingress(endpoint, timeout)
            print(f"Verified gRPC ingress {endpoint}")
            return
        except (OSError, subprocess.SubprocessError, ValueError) as error:
            errors.append(error)
            if attempt < attempts:
                time.sleep(retry_delay)

    raise VerificationError(
        f"gRPC ingress {endpoint} failed after {attempts} attempts: "
        f"{errors[-1]}"
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Verify CommonEx public services after an activation."
    )
    parser.add_argument(
        "endpoints",
        nargs="*",
        help="Endpoints to verify; defaults to the production public services.",
    )
    parser.add_argument(
        "--attempts",
        type=positive_integer,
        default=DEFAULT_ATTEMPTS,
    )
    parser.add_argument(
        "--retry-delay",
        type=non_negative_float,
        default=DEFAULT_RETRY_DELAY_SECONDS,
        metavar="SECONDS",
    )
    parser.add_argument(
        "--timeout",
        type=positive_float,
        default=DEFAULT_TIMEOUT_SECONDS,
        metavar="SECONDS",
    )
    return parser


def main(argv: Optional[Sequence[str]] = None) -> int:
    arguments = build_parser().parse_args(argv)
    using_default_endpoints = not arguments.endpoints
    endpoints = arguments.endpoints or DEFAULT_ENDPOINTS
    try:
        for endpoint in endpoints:
            verify_endpoint(
                endpoint,
                attempts=arguments.attempts,
                retry_delay=arguments.retry_delay,
                timeout=arguments.timeout,
            )
        if using_default_endpoints:
            verify_grpc_ingress(
                DEFAULT_GRPC_ENDPOINT,
                attempts=arguments.attempts,
                retry_delay=arguments.retry_delay,
                timeout=arguments.timeout,
            )
    except VerificationError as error:
        print(
            f"::error::Activation committed, but public health verification {error}.",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
