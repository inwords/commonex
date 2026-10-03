import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


NGINX_DIRECTORY = Path(__file__).resolve().parents[1] / "nginx"
TEMPLATE = NGINX_DIRECTORY / "nginx-prod.conf"
COMPOSE_FILE = NGINX_DIRECTORY.parent / "docker-compose-prod.yml"
HOST_VARIABLES = (
    "COMMONEX_WEB_HOSTS",
    "COMMONEX_API_HOST",
    "COMMONEX_GRPC_HOST",
    "COMMONEX_GRAFANA_HOST",
)


def find_shell() -> str | None:
    shell = shutil.which("sh")
    if shell:
        return shell
    git_shell = Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git/bin/sh.exe"
    return str(git_shell) if git_shell.is_file() else None


class NginxEnvironmentTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        environment = os.environ.copy()
        for variable in HOST_VARIABLES:
            environment.pop(variable, None)
        environment.update(
            {
                "COMMONEX_BACKEND_IMAGE": "example.invalid/backend:test",
                "COMMONEX_FRONTEND_IMAGE": "example.invalid/frontend:test",
                "COMMONEX_NGINX_IMAGE": "example.invalid/nginx:test",
                "COMMONEX_OTEL_COLLECTOR_IMAGE": "example.invalid/otel:test",
                "DEVTOOLS_SECRET": "test",
                "GF_SECURITY_ADMIN_PASSWORD": "test",
                "GF_SECURITY_ADMIN_USER": "test",
                "OPEN_EXCHANGE_RATES_API_ID": "test",
                "POSTGRES_DATABASE": "test",
                "POSTGRES_HOST": "db",
                "POSTGRES_PASSWORD": "test",
                "POSTGRES_PORT": "5432",
                "POSTGRES_SCHEMA": "public",
                "POSTGRES_USER_NAME": "test",
            }
        )
        result = subprocess.run(
            ["docker", "compose", "-f", str(COMPOSE_FILE), "config", "--format", "json"],
            env=environment,
            check=True,
            capture_output=True,
            text=True,
        )
        nginx = json.loads(result.stdout)["services"]["nginx"]
        # Compose serializes literal dollars escaped so its config output remains reusable.
        cls.command = [part.replace("$$", "$") for part in nginx["command"]]
        cls.defaults = nginx["environment"]

    def render(self, overrides: dict[str, str]) -> tuple[subprocess.CompletedProcess, str]:
        shell = find_shell()
        if shell is None:
            self.skipTest("POSIX sh is required to exercise nginx configuration rendering")
        environment = os.environ.copy()
        for variable in HOST_VARIABLES:
            environment[variable] = self.defaults[variable]
        environment.update(overrides)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "nginx.conf"
            script = self.command[2]
            script = script.replace("template=/etc/nginx/nginx.conf", f"template='{TEMPLATE.as_posix()}'")
            script = script.replace("output=/tmp/commonex-nginx.conf", f"output='{output.as_posix()}'")
            script = script.split("\nnginx -t -c", 1)[0]
            result = subprocess.run(
                [shell, "-ec", script],
                env=environment,
                text=True,
                capture_output=True,
                check=False,
            )
            rendered = output.read_text() if output.exists() else ""
        return result, rendered

    def test_runtime_uses_rendered_config_for_validation_and_start(self) -> None:
        self.assertEqual(self.command[:2], ["sh", "-ec"])
        self.assertIn("nginx -t -c \"$output\"", self.command[2])
        self.assertIn("exec nginx -c \"$output\" -g 'daemon off;'", self.command[2])

    def test_defaults_preserve_production_configuration(self) -> None:
        result, rendered = self.render({})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(rendered, TEMPLATE.read_text())

    def test_staging_shares_web_and_api_without_duplicate_virtual_host(self) -> None:
        result, rendered = self.render(
            {
                "COMMONEX_WEB_HOSTS": "staging.commonex.ru",
                "COMMONEX_API_HOST": "staging.commonex.ru",
                "COMMONEX_GRPC_HOST": "staging-grpc.commonex.ru",
                "COMMONEX_GRAFANA_HOST": "staging-gf.commonex.ru",
            }
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(rendered.count("server_name staging.commonex.ru;"), 1)
        self.assertIn("server_name staging-grpc.commonex.ru;", rendered)
        self.assertIn("server_name staging-gf.commonex.ru;", rendered)
        self.assertEqual(rendered.count("    server {"), 3)
        self.assertIn("location /api/ {", rendered)
        self.assertIn("proxy_pass http://keepalive-nest-backend/;", rendered)
        self.assertIn("proxy_pass http://keepalive-next-web;", rendered)
        self.assertIn("grpc_pass grpc://keepalive-nest-backend-grpc;", rendered)
        self.assertIn("proxy_pass http://grafana;", rendered)
        for variable in ("$host", "$http_upgrade", "$connection_upgrade"):
            self.assertIn(variable, rendered)
        self.assertIn("/etc/nginx/ssl/live/commonex.ru/fullchain.pem", rendered)
        self.assertIn("listen 443 quic reuseport;", rendered)

    def test_distinct_api_and_multiple_web_hosts_remain_supported(self) -> None:
        result, rendered = self.render(
            {
                "COMMONEX_WEB_HOSTS": "preview.commonex.ru www.preview.commonex.ru",
                "COMMONEX_API_HOST": "preview-api.commonex.ru",
            }
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("server_name preview.commonex.ru www.preview.commonex.ru;", rendered)
        self.assertIn("server_name preview-api.commonex.ru;", rendered)
        self.assertEqual(rendered.count("    server {"), 4)

    def test_hostnames_cannot_inject_nginx_configuration(self) -> None:
        invalid = (
            "",
            "staging.commonex.ru; include /tmp/evil;",
            "*.commonex.ru",
            "staging.commonex.ru\nserver {}",
            "-staging.commonex.ru",
            "staging-.commonex.ru",
            "staging..commonex.ru",
            "a" * 64 + ".commonex.ru",
            "staging.commonex.ru/",
        )
        for variable in HOST_VARIABLES:
            for value in invalid:
                with self.subTest(variable=variable, value=value):
                    result, rendered = self.render({variable: value})
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn("nginx environment configuration:", result.stderr)
                    self.assertEqual(rendered, "")

    def test_conflicting_service_hosts_are_rejected(self) -> None:
        for overrides in (
            {"COMMONEX_GRPC_HOST": "commonex.ru"},
            {"COMMONEX_GRAFANA_HOST": "dev-api.commonex.ru"},
            {"COMMONEX_GRPC_HOST": "gf.commonex.ru"},
            {"COMMONEX_WEB_HOSTS": "commonex.ru COMMONEX.ru"},
        ):
            with self.subTest(overrides=overrides):
                result, _ = self.render(overrides)
                self.assertNotEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main()
