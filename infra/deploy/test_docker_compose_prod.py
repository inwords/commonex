import os
from pathlib import Path
import subprocess
import unittest


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
COMPOSE_FILE = REPOSITORY_ROOT / "infra" / "docker-compose-prod.yml"


class ProductionComposeTests(unittest.TestCase):
    def test_default_stack_excludes_manual_certbot_service(self) -> None:
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
