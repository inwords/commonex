import os
from pathlib import Path
import subprocess
import unittest


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
COMPOSE_FILE = REPOSITORY_ROOT / "infra" / "docker-compose-prod.yml"


class ProductionComposeTests(unittest.TestCase):
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
                "GRAFANA_DATASOURCE_B64": "YXBpVmVyc2lvbjogMQo=",
                "GRAFANA_PROVISIONING_REVISION": "0" * 64,
                "GRAFANA_PROVISIONING_SOURCE": "grafana_provisioning",
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

    def test_grafana_datasource_is_materialized_before_grafana_starts(self) -> None:
        compose = COMPOSE_FILE.read_text(encoding="utf-8")

        self.assertIn("GRAFANA_DATASOURCE_B64=${GRAFANA_DATASOURCE_B64}", compose)
        self.assertIn("condition: service_healthy", compose)
        self.assertIn(
            "COMMONEX_GRAFANA_PROVISIONING_REVISION=${GRAFANA_PROVISIONING_REVISION:-local}",
            compose,
        )
        self.assertIn(
            "${GRAFANA_PROVISIONING_SOURCE:-./grafana/provisioning/datasources}:/etc/grafana/provisioning/datasources:ro",
            compose,
        )


if __name__ == "__main__":
    unittest.main()
