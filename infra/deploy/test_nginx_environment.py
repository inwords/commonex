import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


NGINX_DIRECTORY = Path(__file__).resolve().parents[1] / "nginx"
TEMPLATES = NGINX_DIRECTORY / "templates"
ENTRYPOINT = NGINX_DIRECTORY / "docker-entrypoint.sh"
HOST_VARIABLES = (
    "COMMONEX_WEB_HOSTS",
    "COMMONEX_API_HOST",
    "COMMONEX_GRPC_HOST",
    "COMMONEX_GRAFANA_HOST",
    "COMMONEX_API_CONFIG",
)


class NginxEnvironmentTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.shell = shutil.which("sh")
        if not cls.shell or not shutil.which("envsubst"):
            raise unittest.SkipTest("POSIX sh and GNU envsubst are required")

    def render(self, overrides: dict[str, str]) -> tuple[subprocess.CompletedProcess, str, str]:
        environment = os.environ.copy()
        for variable in HOST_VARIABLES:
            environment.pop(variable, None)
        environment.update(overrides)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "nginx.conf"
            result = subprocess.run(
                [self.shell, str(ENTRYPOINT), "--render-only", str(TEMPLATES), str(output)],
                env=environment,
                text=True,
                capture_output=True,
                check=False,
            )
            rendered = output.read_text() if output.exists() else ""
            fragments = list(Path(directory).glob("commonex-nginx.*/api.conf"))
            api = fragments[0].read_text() if fragments else ""
            if fragments:
                self.assertEqual(fragments[0].parent.stat().st_mode & 0o777, 0o700)
                self.assertEqual(fragments[0].stat().st_mode & 0o777, 0o600)
        return result, rendered, api

    def test_defaults_preserve_production_routing(self) -> None:
        result, rendered, api = self.render({})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("server_name commonex.ru www.commonex.ru;", rendered)
        self.assertIn("server_name gf.commonex.ru;", rendered)
        self.assertIn("server_name grpc.commonex.ru;", rendered)
        self.assertIn("server_name dev-api.commonex.ru;", api)
        self.assertIn("include \"", rendered)
        self.assertEqual(rendered.count("    server {"), 3)
        self.assertEqual(api.count("    server {"), 1)
        for configuration in (rendered, api):
            self.assertIn("location /api/ {", configuration)
            self.assertIn("proxy_pass http://keepalive-nest-backend/;", configuration)
            self.assertIn("proxy_set_header Host $host;", configuration)
        self.assertIn("proxy_pass http://keepalive-next-web;", rendered)
        self.assertIn("grpc_pass grpc://keepalive-nest-backend-grpc;", rendered)
        self.assertIn("proxy_pass http://grafana;", rendered)

    def test_staging_shares_web_and_api_without_duplicate_virtual_host(self) -> None:
        result, rendered, api = self.render(
            {
                "COMMONEX_WEB_HOSTS": "preview.example.test",
                "COMMONEX_API_HOST": "preview.example.test",
                "COMMONEX_GRPC_HOST": "rpc.example.test",
                "COMMONEX_GRAFANA_HOST": "metrics.example.test",
            }
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(api, "")
        self.assertEqual(rendered.count("server_name preview.example.test;"), 1)
        self.assertIn("server_name rpc.example.test;", rendered)
        self.assertIn("server_name metrics.example.test;", rendered)
        self.assertIn("location /api/ {", rendered)
        self.assertIn("/etc/nginx/ssl/live/commonex.ru/fullchain.pem", rendered)
        self.assertIn("listen 443 quic reuseport;", rendered)

    def test_distinct_api_and_multiple_web_hosts_are_normalized(self) -> None:
        result, rendered, api = self.render(
            {
                "COMMONEX_WEB_HOSTS": "  PREVIEW.example.test\twww.preview.example.test  ",
                "COMMONEX_API_HOST": "API.example.test",
            }
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("server_name preview.example.test www.preview.example.test;", rendered)
        self.assertIn("server_name api.example.test;", api)

    def test_api_sharing_any_web_alias_skips_separate_virtual_host(self) -> None:
        result, _, api = self.render({"COMMONEX_API_HOST": "WWW.COMMONEX.RU"})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(api, "")

    def test_substitution_preserves_nginx_variables_and_ignores_external_fragment_path(self) -> None:
        result, rendered, _ = self.render({"COMMONEX_API_CONFIG": "/tmp/untrusted.conf"})
        self.assertEqual(result.returncode, 0, result.stderr)
        for variable in ("$host", "$http_upgrade", "$connection_upgrade"):
            self.assertIn(variable, rendered)
        self.assertNotIn("/tmp/untrusted.conf", rendered)
        self.assertNotIn("${COMMONEX_", rendered)

    def test_hostnames_cannot_inject_nginx_configuration(self) -> None:
        invalid = (
            "", "preview.example.test; include /tmp/evil;", "*.example.test",
            "preview.example.test\nserver {}", "preview.example.test\nother.example.test",
            "preview.example.test\r", "-preview.example.test", "preview-.example.test",
            "preview..example.test", "a" * 64 + ".example.test", "preview.example.test/",
            ".example.test", "preview.example.test.", "a." * 127 + "a",
        )
        for variable in HOST_VARIABLES[:-1]:
            for value in invalid:
                with self.subTest(variable=variable, value=value):
                    result, rendered, api = self.render({variable: value})
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn("nginx environment configuration:", result.stderr)
                    self.assertEqual(rendered, "")
                    self.assertEqual(api, "")

    def test_conflicting_service_hosts_are_rejected(self) -> None:
        for overrides in (
            {"COMMONEX_GRPC_HOST": "commonex.ru"},
            {"COMMONEX_GRAFANA_HOST": "dev-api.commonex.ru"},
            {"COMMONEX_GRPC_HOST": "gf.commonex.ru"},
            {"COMMONEX_API_HOST": "grpc.commonex.ru"},
            {"COMMONEX_WEB_HOSTS": "commonex.ru COMMONEX.ru"},
            {"COMMONEX_API_HOST": "api.example.test api2.example.test"},
        ):
            with self.subTest(overrides=overrides):
                result, _, _ = self.render(overrides)
                self.assertNotEqual(result.returncode, 0)

    def test_command_override_runs_without_rendering_or_validation(self) -> None:
        result = subprocess.run(
            [self.shell, str(ENTRYPOINT), "sh", "-c", "printf override"],
            env={**os.environ, "COMMONEX_WEB_HOSTS": "invalid;"},
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "override")

    def test_nginx_version_override_runs_without_rendering(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fake_nginx = Path(directory) / "nginx"
            fake_nginx.write_text('#!/bin/sh\nprintf "%s" "$*"\n')
            fake_nginx.chmod(0o755)
            result = subprocess.run(
                [self.shell, str(ENTRYPOINT), "nginx", "-V"],
                env={**os.environ, "PATH": directory + os.pathsep + os.environ["PATH"],
                     "COMMONEX_WEB_HOSTS": "invalid;"},
                capture_output=True,
                text=True,
            )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "-V")


if __name__ == "__main__":
    unittest.main()
