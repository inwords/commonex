from __future__ import annotations

from contextlib import redirect_stderr
from io import StringIO
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from infra.deploy import commonex_deploy
from infra.deploy import write_runtime_environment as writer


class WriteRuntimeEnvironmentTest(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.path = Path(temporary.name) / "release" / ".env"
        self.values = {key: "private-value-" + key for key in writer.RUNTIME_KEYS}

    def test_shared_inputs_match_the_host_required_keys(self) -> None:
        self.assertEqual(
            set(writer.RUNTIME_KEYS),
            commonex_deploy.REQUIRED_ENV_KEYS - commonex_deploy.IMMUTABLE_IMAGE_REPOSITORIES.keys(),
        )

    def test_production_keeps_exact_inputs_and_has_no_staging_overrides(self) -> None:
        self.values["OPEN_EXCHANGE_RATES_API_ID"] = ""
        writer.write_runtime_environment(self.path, "production", self.values)
        self.assertEqual(
            self.path.read_bytes(),
            "".join(f"{key}={value}\n" for key, value in self.values.items()).encode(),
        )
        self.assert_private_file()

    def test_staging_adds_fixed_routes_and_private_directory(self) -> None:
        self.path.parent.mkdir(mode=0o755)
        self.path.write_text("old content")
        self.path.chmod(0o644)
        writer.write_runtime_environment(self.path, "staging", self.values)
        actual = dict(line.split("=", 1) for line in self.path.read_text().splitlines())
        self.assertEqual(actual, {**self.values, **writer.STAGING_ROUTES})
        self.assert_private_file()
        if os.name == "posix":
            self.assertEqual(self.path.parent.stat().st_mode & 0o777, 0o700)

    def assert_private_file(self) -> None:
        if os.name == "posix":
            self.assertEqual(self.path.stat().st_mode & 0o777, 0o600)

    def test_invalid_inputs_fail_before_creating_file_without_echoing_secrets(self) -> None:
        key = "POSTGRES_PASSWORD"
        cases = [
            ("staging", {**self.values, key: ""}),
            ("production", {name: value for name, value in self.values.items() if name != key}),
        ]
        cases.extend(
            (environment, {**self.values, key: "private-value" + forbidden + "injected"})
            for environment in ("staging", "production")
            for forbidden in ("\r", "\n", "\x00")
        )
        for environment, values in cases:
            with self.subTest(environment=environment, password=values.get(key)):
                stderr = StringIO()
                with patch.object(writer.os, "environ", values), redirect_stderr(stderr):
                    result = writer.main(["--environment", environment, str(self.path)])
                self.assertEqual(result, 1)
                self.assertIn(key, stderr.getvalue())
                self.assertNotIn("private-value", stderr.getvalue())
                self.assertFalse(self.path.exists())

    def test_cli_uses_environment_without_interpolating_private_values_as_code(self) -> None:
        self.values["POSTGRES_PASSWORD"] = "literal $(command) `token` = value"
        with patch.dict(os.environ, self.values, clear=True):
            result = writer.main(["--environment", "production", str(self.path)])
        self.assertEqual(result, 0)
        self.assertIn(self.values["POSTGRES_PASSWORD"], self.path.read_text())


if __name__ == "__main__":
    unittest.main()
