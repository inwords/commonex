from __future__ import annotations

import io
import json
import shutil
import subprocess
import tempfile
import unittest
import unittest.mock as mock
from contextlib import redirect_stdout
from pathlib import Path

from infra.deploy import commonex_deploy as deploy
from infra.deploy.test_commonex_deploy import release_archive


RELEASE = "a" * 40


class ReleaseStatusTest(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        self.config = deploy.DeploymentConfig(
            app_dir=root / "app", release_root=root / "releases",
            rollback_root=root / "rollback", log_path=root / "logs" / "deploy.log",
            lock_path=root / "runtime" / "deploy.lock", enforce_root_ownership=False,
        )
        deploy.stage(RELEASE, self.config, release_archive())
        self.config.app_dir.mkdir()
        for name in deploy.FILES:
            shutil.copyfile(self.config.release_root / RELEASE / name, self.config.app_dir / name)
        (self.config.app_dir / ".env").chmod(0o600)
        deploy._write_activation_state(17, [RELEASE], self.config)
        self.images = {
            key: value for key, value in deploy._environment_values(
                self.config.app_dir / ".env",
            ).items() if key in deploy.IMMUTABLE_IMAGE_REPOSITORIES
        }
        self.backend_image = self.images["COMMONEX_BACKEND_IMAGE"]
        self.configuration = {"services": {
            "backend": {"image": self.backend_image, "healthcheck": {"test": ["CMD", "check"]}},
            "db": {"image": "postgres:17-alpine3.24"},
        }}
        self.containers = [
            {"Service": "backend", "State": "running", "Health": "healthy",
             "Image": self.backend_image, "ID": "b" * 64, "Command": "secret"},
            {"Service": "db", "State": "running", "Health": "", "Image": "postgres:17-alpine3.24"},
        ]
        self.runtime_image = self.backend_image
        self.ndjson = False
        self.commands: list[list[str]] = []

    def run_command(self, command, **kwargs):
        self.commands.append(command)
        self.assertEqual(kwargs["env"], deploy.SAFE_ENVIRONMENT)
        self.assertTrue(kwargs["check"])
        self.assertTrue(kwargs["capture_output"])
        if command[-2:] == ["config", "--services"]:
            output = "backend\ndb\n"
        elif command[-3:] == ["config", "--format", "json"]:
            output = json.dumps(self.configuration)
        elif command[-4:] == ["ps", "--all", "--format", "json"]:
            output = "\n".join(map(json.dumps, self.containers)) if self.ndjson else json.dumps(self.containers)
        elif command[:4] == ["docker", "inspect", "--format", "{{json .Config.Image}}"]:
            output = json.dumps(self.runtime_image)
        else:
            self.fail(f"unexpected command: {command}")
        return subprocess.CompletedProcess(command, 0, stdout=output, stderr="secret diagnostics")

    def status(self):
        output = io.StringIO()
        with mock.patch.object(deploy, "run_command"), mock.patch.object(
            deploy.subprocess, "run", side_effect=self.run_command,
        ), redirect_stdout(output):
            deploy.release_status(self.config)
        self.assertNotIn("secret", output.getvalue())
        return json.loads(output.getvalue())

    def test_reports_locked_verified_activation_without_mutating_documents(self) -> None:
        before = {path: path.read_bytes() for path in self.config.release_root.rglob("*") if path.is_file()}
        with mock.patch.object(deploy, "operation_lock", wraps=deploy.operation_lock) as lock:
            status = self.status()
        lock.assert_called_once_with(self.config)
        self.assertEqual(status, {
            "release_sha": RELEASE, "activation_number": 17, "images": self.images,
            "services": [
                {"service": "backend", "state": "running", "health": "healthy", "image": self.backend_image},
                {"service": "db", "state": "running", "health": "", "image": "postgres:17-alpine3.24"},
            ], "healthy": True,
        })
        self.assertEqual(before, {path: path.read_bytes() for path in self.config.release_root.rglob("*") if path.is_file()})

    def test_accepts_newline_delimited_compose_json(self) -> None:
        self.ndjson = True
        self.assertTrue(self.status()["healthy"])

    def test_missing_service_is_explicitly_unhealthy(self) -> None:
        self.containers = self.containers[:1]
        status = self.status()
        self.assertFalse(status["healthy"])
        self.assertIn({"service": "db", "state": "missing", "health": ""}, status["services"])

    def test_exited_unhealthy_starting_and_missing_required_health_fail_closed(self) -> None:
        for state, health in [("exited", "healthy"), ("running", "unhealthy"), ("running", "starting"), ("running", "")]:
            with self.subTest(state=state, health=health):
                self.containers[0].update(State=state, Health=health)
                self.assertFalse(self.status()["healthy"])

    def test_runtime_image_mismatch_fails_even_when_ps_reports_expected_image(self) -> None:
        self.runtime_image = "ruggedbl/commonex-nest-backend@sha256:" + "f" * 64
        status = self.status()
        self.assertFalse(status["healthy"])
        self.assertEqual(status["services"][0]["image"], self.runtime_image)

    def test_inspects_custom_image_when_ps_omits_image(self) -> None:
        self.containers[0].pop("Image")
        self.assertTrue(self.status()["healthy"])
        self.assertTrue(any(command[1] == "inspect" for command in self.commands))

    def test_inspected_config_image_is_authoritative_over_ps_display(self) -> None:
        self.containers[0]["Image"] = "ruggedbl/commonex-nest-backend:latest"
        status = self.status()
        self.assertTrue(status["healthy"])
        self.assertEqual(status["services"][0]["image"], self.backend_image)

    def test_disabled_healthcheck_allows_empty_health(self) -> None:
        self.configuration["services"]["backend"]["healthcheck"]["disable"] = True
        self.containers[0]["Health"] = ""
        self.assertTrue(self.status()["healthy"])

    def test_malformed_json_has_a_safe_error_and_no_status(self) -> None:
        output = io.StringIO()
        with mock.patch.object(deploy, "run_command"), mock.patch.object(
            deploy, "_status_output", side_effect=["backend\ndb\n", "secret invalid JSON"],
        ), redirect_stdout(output), self.assertRaisesRegex(
            ValueError, "^invalid release status response$",
        ):
            deploy.release_status(self.config)
        self.assertEqual(output.getvalue(), "")

    def test_pending_intent_blocks_status_before_runtime_queries(self) -> None:
        deploy._write_activation_intent("deploy", "c" * 40, 18, RELEASE, self.config.rollback_root / ("deploy-" + "c" * 40 + "-20260830T120000000000Z"), self.config)
        with self.assertRaises(deploy.UnresolvedActivationIntentError):
            self.status()
        self.assertEqual(self.commands, [])

    def test_active_and_retained_configuration_mismatches_are_rejected(self) -> None:
        for directory in [self.config.app_dir, self.config.release_root / RELEASE]:
            with self.subTest(directory=directory):
                path = directory / "docker-compose-prod.yml"
                original = path.read_bytes()
                path.write_bytes(original + b"# changed\n")
                with self.assertRaises((RuntimeError, ValueError)):
                    self.status()
                path.write_bytes(original)
        self.assertEqual(self.commands, [])

    def test_no_activation_history_requires_bootstrap(self) -> None:
        deploy._write_activation_state(17, [], self.config)
        with self.assertRaisesRegex(ValueError, "bootstrap required"):
            self.status()

    def test_invalid_runtime_output_emits_no_status(self) -> None:
        for container in [{"Service": "backend", "State": "secret", "Health": "healthy"}, {"Service": "backend", "State": "running", "Health": "healthy", "ID": "invalid"}]:
            with self.subTest(container=container):
                self.containers = [container]
                with self.assertRaises(ValueError):
                    self.status()

    def test_current_images_wire_format_is_unchanged(self) -> None:
        output = io.StringIO()
        with mock.patch.object(deploy, "run_command"), redirect_stdout(output):
            deploy.current_images(self.config)
        self.assertEqual(output.getvalue(), "".join(f"{key}={self.images[key]}\n" for key in sorted(self.images)))

    def test_cli_accepts_only_argument_free_status(self) -> None:
        self.assertEqual(deploy.parse_invocation(["release-status"], ""), ("release-status", "", None))
        self.assertEqual(deploy.parse_invocation(["forced"], "release-status"), ("release-status", "", None))
        for command in ["release-status extra", "release-status; id", "release-status --env"]:
            with self.subTest(command=command), self.assertRaises(ValueError):
                deploy.parse_invocation(["forced"], command)
        with mock.patch.object(deploy, "release_status") as status:
            deploy.execute("release-status", "", None, self.config, io.BytesIO())
        status.assert_called_once_with(self.config)


if __name__ == "__main__":
    unittest.main()
