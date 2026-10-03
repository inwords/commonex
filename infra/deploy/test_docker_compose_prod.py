import os
import json
from pathlib import Path
import subprocess
import unittest


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
COMPOSE_FILE = REPOSITORY_ROOT / "infra" / "docker-compose-prod.yml"


class ProductionComposeTests(unittest.TestCase):
    def test_environment_routing_preserves_topology_and_persistent_storage(self) -> None:
        environment = os.environ.copy()
        for key in ("COMMONEX_WEB_HOSTS", "COMMONEX_API_HOST", "COMMONEX_GRPC_HOST",
                    "COMMONEX_GRAFANA_HOST", "GF_SERVER_ROOT_URL"):
            environment.pop(key, None)
        for key in ("BACKEND", "FRONTEND", "NGINX", "OTEL_COLLECTOR"):
            environment[f"COMMONEX_{key}_IMAGE"] = "example.invalid/image@sha256:" + "a" * 64

        def render():
            result = subprocess.run(
                ["docker", "compose", "--env-file", os.devnull, "-f", str(COMPOSE_FILE),
                 "config", "--format", "json"],
                cwd=REPOSITORY_ROOT, env=environment, check=True,
                capture_output=True, text=True,
            )
            return json.loads(result.stdout)

        production = render()
        self.assertEqual(production["services"]["nginx"]["environment"]["COMMONEX_WEB_HOSTS"],
                         "commonex.ru www.commonex.ru")
        self.assertEqual(production["services"]["grafana"]["environment"]["GF_SERVER_ROOT_URL"],
                         "https://gf.commonex.ru/")
        environment.update({
            "COMMONEX_WEB_HOSTS": "staging.commonex.ru",
            "COMMONEX_API_HOST": "staging.commonex.ru",
            "COMMONEX_GRPC_HOST": "staging-grpc.commonex.ru",
            "COMMONEX_GRAFANA_HOST": "staging-gf.commonex.ru",
            "GF_SERVER_ROOT_URL": "https://staging-gf.commonex.ru/",
        })
        staging = render()
        self.assertEqual(staging["services"].keys(), production["services"].keys())
        self.assertEqual(len(staging["services"]), 9)
        for service, definition in production["services"].items():
            self.assertEqual(staging["services"][service].get("volumes"), definition.get("volumes"))
            self.assertEqual(staging["services"][service]["image"], definition["image"])
        self.assertEqual(staging["services"]["nginx"]["environment"]["COMMONEX_GRPC_HOST"],
                         "staging-grpc.commonex.ru")
        self.assertEqual(staging["services"]["grafana"]["environment"]["GF_SERVER_ROOT_URL"],
                         "https://staging-gf.commonex.ru/")
        self.assertEqual(staging["services"]["nginx"]["ports"], production["services"]["nginx"]["ports"])

    def test_stack_has_no_certbot_service_in_any_profile(self) -> None:
        environment = os.environ.copy()
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
                "POSTGRES_DB": "test",
                "POSTGRES_HOST": "db",
                "POSTGRES_PASSWORD": "test",
                "POSTGRES_PORT": "5432",
                "POSTGRES_SCHEMA": "public",
                "POSTGRES_USER": "test",
                "POSTGRES_USER_NAME": "test",
            }
        )

        result = subprocess.run(
            [
                "docker",
                "compose",
                "-f",
                str(COMPOSE_FILE),
                "--profile",
                "*",
                "config",
                "--services",
            ],
            cwd=REPOSITORY_ROOT,
            env=environment,
            check=True,
            capture_output=True,
            text=True,
        )

        self.assertNotIn("certbot", result.stdout.splitlines())


if __name__ == "__main__":
    unittest.main()
