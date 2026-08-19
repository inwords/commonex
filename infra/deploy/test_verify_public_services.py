from contextlib import contextmanager, redirect_stderr, redirect_stdout
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import StringIO
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
from threading import Thread
from typing import Iterator, Type
import unittest
from unittest.mock import patch

from infra.deploy import verify_public_services


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPOSITORY_ROOT / "infra" / "deploy" / "verify_public_services.py"


@contextmanager
def endpoint_server(
    failures_before_success: int,
) -> Iterator[tuple[str, Type[BaseHTTPRequestHandler]]]:
    class Handler(BaseHTTPRequestHandler):
        request_count = 0

        def do_GET(self) -> None:
            type(self).request_count += 1
            status = (
                503
                if type(self).request_count <= failures_before_success
                else 204
            )
            self.send_response(status)
            self.send_header("Content-Length", "0")
            self.end_headers()

        def log_message(self, _format: str, *args: object) -> None:
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        host, port = server.server_address
        yield f"http://{host}:{port}/health", Handler
    finally:
        server.shutdown()
        server.server_close()
        thread.join()


@contextmanager
def redirecting_endpoint_server(
    redirect_target: str = "/ready",
) -> Iterator[tuple[str, Type[BaseHTTPRequestHandler]]]:
    class Handler(BaseHTTPRequestHandler):
        health_request_count = 0

        def do_GET(self) -> None:
            if self.path == "/health":
                type(self).health_request_count += 1
                self.send_response(302)
                self.send_header("Location", redirect_target)
            else:
                self.send_response(204)
            self.send_header("Content-Length", "0")
            self.end_headers()

        def log_message(self, _format: str, *args: object) -> None:
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        host, port = server.server_address
        yield f"http://{host}:{port}/health", Handler
    finally:
        server.shutdown()
        server.server_close()
        thread.join()


@contextmanager
def redirect_policy(url: str, allowed_target: str) -> Iterator[None]:
    with patch.dict(
        verify_public_services.ALLOWED_REDIRECT_TARGETS,
        {url: frozenset((allowed_target,))},
        clear=True,
    ):
        yield


@contextmanager
def fake_curl(response_headers: bytes) -> Iterator[tuple[str, ...]]:
    with TemporaryDirectory() as directory:
        script = Path(directory) / "fake_curl.py"
        script.write_text(
            "import sys\n"
            "request = sys.stdin.buffer.read()\n"
            "expected_target = "
            "'https://grpc.commonex.ru/grpc.health.v1.Health/Check'\n"
            "if request != b'\\x00\\x00\\x00\\x00\\x00':\n"
            "    raise SystemExit(2)\n"
            "if '--http2' not in sys.argv or sys.argv[-1] != expected_target:\n"
            "    raise SystemExit(2)\n"
            f"sys.stdout.buffer.write({response_headers!r})\n",
            encoding="utf-8",
        )
        yield (sys.executable, str(script))


class VerifyPublicServicesTests(unittest.TestCase):
    def run_verifier(self, url: str, attempts: int) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--attempts",
                str(attempts),
                "--retry-delay",
                "0",
                "--timeout",
                "1",
                url,
            ],
            cwd=REPOSITORY_ROOT,
            capture_output=True,
            text=True,
            timeout=10,
        )

    def test_retries_transient_failures_until_endpoint_succeeds(self) -> None:
        with endpoint_server(failures_before_success=2) as (url, handler):
            result = self.run_verifier(url, attempts=3)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(handler.request_count, 3)

    def test_reports_failure_after_attempt_limit(self) -> None:
        with endpoint_server(failures_before_success=10) as (url, handler):
            result = self.run_verifier(url, attempts=2)

        self.assertEqual(result.returncode, 1)
        self.assertEqual(handler.request_count, 2)
        self.assertIn(url, result.stderr)
        self.assertIn("failed after 2 attempts", result.stderr)

    def test_defaults_cover_each_public_service_route(self) -> None:
        self.assertEqual(
            verify_public_services.DEFAULT_ENDPOINTS,
            (
                "https://commonex.ru/",
                "https://commonex.ru/api/health",
                "https://dev-api.commonex.ru/api/health",
                "https://gf.commonex.ru/",
            ),
        )

    def test_defaults_probe_public_grpc_ingress(self) -> None:
        grpc_response = (
            b"HTTP/2 200 \r\n"
            b"content-type: application/grpc+proto\r\n"
            b"grpc-status: 12\r\n\r\n"
        )
        with endpoint_server(failures_before_success=0) as (url, handler):
            with fake_curl(grpc_response) as curl_command:
                with patch.object(
                    verify_public_services,
                    "DEFAULT_ENDPOINTS",
                    (url,),
                ), patch.object(
                    verify_public_services,
                    "CURL_COMMAND",
                    curl_command,
                ):
                    output = StringIO()
                    with redirect_stdout(output):
                        result = verify_public_services.main(
                            [
                                "--attempts",
                                "1",
                                "--retry-delay",
                                "0",
                                "--timeout",
                                "1",
                            ]
                        )

        self.assertEqual(result, 0)
        self.assertEqual(handler.request_count, 1)
        self.assertIn(
            "Verified gRPC ingress https://grpc.commonex.ru/",
            output.getvalue(),
        )

    def test_rejects_non_grpc_ingress_response(self) -> None:
        with endpoint_server(failures_before_success=0) as (url, _handler):
            response = b"HTTP/2 502 \r\ncontent-type: text/html\r\n\r\n"
            with fake_curl(response) as curl_command:
                with patch.object(
                    verify_public_services,
                    "DEFAULT_ENDPOINTS",
                    (url,),
                ), patch.object(
                    verify_public_services,
                    "CURL_COMMAND",
                    curl_command,
                ):
                    errors = StringIO()
                    with redirect_stderr(errors):
                        result = verify_public_services.main(
                            [
                                "--attempts",
                                "1",
                                "--retry-delay",
                                "0",
                                "--timeout",
                                "1",
                            ]
                        )

        self.assertEqual(result, 1)
        self.assertIn("gRPC ingress", errors.getvalue())

    def test_rejects_redirects_for_health_endpoint(self) -> None:
        with redirecting_endpoint_server() as (url, handler):
            result = self.run_verifier(url, attempts=2)

        self.assertEqual(result.returncode, 1)
        self.assertEqual(handler.health_request_count, 2)
        self.assertIn("redirected", result.stderr)

    def test_allows_configured_redirect_target(self) -> None:
        with redirecting_endpoint_server() as (url, _handler):
            allowed_target = url.replace("/health", "/ready")
            with redirect_policy(url, allowed_target):
                verify_public_services.verify_endpoint(
                    url,
                    attempts=1,
                    retry_delay=0,
                    timeout=1,
                )

    def test_rejects_unconfigured_redirect_target(self) -> None:
        with endpoint_server(failures_before_success=0) as (
            destination_url,
            destination_handler,
        ):
            with redirecting_endpoint_server(destination_url) as (url, _handler):
                allowed_target = url.replace("/health", "/ready")
                with redirect_policy(url, allowed_target):
                    with self.assertRaises(verify_public_services.VerificationError):
                        verify_public_services.verify_endpoint(
                            url,
                            attempts=1,
                            retry_delay=0,
                            timeout=1,
                        )

        self.assertEqual(destination_handler.request_count, 0)


if __name__ == "__main__":
    unittest.main()
