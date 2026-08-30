from __future__ import annotations

import gzip
import io
import json
import os
import stat
import subprocess
import sys
import tarfile
import tempfile
import time
import unittest
from pathlib import Path
from typing import Optional
from unittest import mock


DEPLOY_DIRECTORY = Path(__file__).resolve().parent
sys.path.insert(0, str(DEPLOY_DIRECTORY))

import commonex_deploy as deploy  # noqa: E402


RELEASE_ID = "a" * 40
OLDER_RELEASE_ID = "b" * 40
THIRD_RELEASE_ID = "c" * 40
FOURTH_RELEASE_ID = "d" * 40
VALID_IMAGE_REFERENCES = {
    "COMMONEX_BACKEND_IMAGE": "ruggedbl/commonex-nest-backend@sha256:" + "a" * 64,
    "COMMONEX_FRONTEND_IMAGE": "ruggedbl/commonex-next-web@sha256:" + "b" * 64,
    "COMMONEX_OTEL_COLLECTOR_IMAGE": "ruggedbl/opentelemetry-collector-custom@sha256:"
    + "c" * 64,
    "COMMONEX_NGINX_IMAGE": "ruggedbl/nginx-http3@sha256:" + "d" * 64,
}
IMMUTABLE_IMAGE_KEYS = frozenset(VALID_IMAGE_REFERENCES)


def valid_environment(marker: str = "value") -> bytes:
    values = {
        key: f"{marker}-{key.lower()}"
        for key in deploy.REQUIRED_ENV_KEYS - IMMUTABLE_IMAGE_KEYS
    }
    values.update(VALID_IMAGE_REFERENCES)
    return "".join(f"{key}={value}\n" for key, value in sorted(values.items())).encode()


def environment_with_images(marker: str, digest_character: str) -> bytes:
    values = {
        key: f"{marker}-{key.lower()}"
        for key in deploy.REQUIRED_ENV_KEYS - IMMUTABLE_IMAGE_KEYS
    }
    values.update(
        {
            key: f"{repository}@sha256:{digest_character * 64}"
            for key, repository in deploy.IMMUTABLE_IMAGE_REPOSITORIES.items()
        }
    )
    return "".join(f"{key}={value}\n" for key, value in sorted(values.items())).encode()


def release_archive(
    *,
    compose: bytes = b"services:\n  app:\n    image: busybox\n",
    environment: bytes = valid_environment(),
    extra_files: Optional[dict[str, bytes]] = None,
) -> io.BytesIO:
    files = {
        "docker-compose-prod.yml": compose,
        ".env": environment,
        **(extra_files or {}),
    }
    archive = io.BytesIO()
    with tarfile.open(fileobj=archive, mode="w:gz") as bundle:
        for name, content in files.items():
            member = tarfile.TarInfo(name)
            member.size = len(content)
            bundle.addfile(member, io.BytesIO(content))
    archive.seek(0)
    return archive


def oversized_member_archive(size: int) -> io.BytesIO:
    member = tarfile.TarInfo("docker-compose-prod.yml")
    member.size = size
    archive = io.BytesIO(gzip.compress(member.tobuf() + (b"\0" * 1024)))
    archive.seek(0)
    return archive


def archive_with_members(members: list[tuple[tarfile.TarInfo, bytes]]) -> io.BytesIO:
    archive = io.BytesIO()
    with tarfile.open(fileobj=archive, mode="w:gz") as bundle:
        for member, content in members:
            bundle.addfile(member, io.BytesIO(content))
    archive.seek(0)
    return archive


def regular_member(name: str, content: bytes) -> tuple[tarfile.TarInfo, bytes]:
    member = tarfile.TarInfo(name)
    member.size = len(content)
    return member, content


class DeployScriptTest(unittest.TestCase):
    def test_default_config_uses_namespaced_host_layout(self) -> None:
        self.assertEqual(deploy.DEFAULT_CONFIG.app_dir, Path("/etc/commonex/app"))
        self.assertEqual(deploy.DEFAULT_CONFIG.release_root, Path("/var/lib/commonex"))
        self.assertEqual(
            deploy.DEFAULT_CONFIG.rollback_root, Path("/var/lib/commonex/rollback")
        )
        self.assertEqual(
            deploy.DEFAULT_CONFIG.log_path, Path("/var/log/commonex/deploy.log")
        )
        self.assertEqual(deploy.DEFAULT_CONFIG.lock_path, Path("/run/commonex/deploy.lock"))

    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.config = deploy.DeploymentConfig(
            app_dir=self.root / "app",
            release_root=self.root / "releases",
            rollback_root=self.root / "rollback",
            log_path=self.root / "logs" / "deploy.log",
            lock_path=self.root / "runtime" / "deploy.lock",
            enforce_root_ownership=False,
        )

    def prepare_active_configuration(self, marker: str = "old") -> None:
        self.config.app_dir.mkdir(exist_ok=True)
        self.config.rollback_root.mkdir(exist_ok=True)
        (self.config.app_dir / "docker-compose-prod.yml").write_bytes(
            f"services:\n  {marker}:\n    image: busybox\n".encode()
        )
        (self.config.app_dir / ".env").write_bytes(valid_environment(marker))
        (self.config.app_dir / ".env").chmod(0o600)

    def prepare_two_activation_history(self) -> tuple[bytes, bytes]:
        self.prepare_active_configuration()
        first_compose = b"services:\n  first:\n    image: busybox\n"
        second_compose = b"services:\n  second:\n    image: busybox\n"
        deploy.stage(RELEASE_ID, self.config, release_archive(compose=first_compose))
        with mock.patch.object(deploy, "run_command"):
            deploy.deploy(RELEASE_ID, 1, self.config)
        deploy.stage(
            OLDER_RELEASE_ID,
            self.config,
            release_archive(compose=second_compose),
        )
        with mock.patch.object(deploy, "run_command"):
            deploy.deploy(OLDER_RELEASE_ID, 2, self.config)
        return first_compose, second_compose

    def test_best_effort_audit_preserves_the_primary_outcome(self) -> None:
        with mock.patch.object(
            deploy,
            "audit",
            side_effect=OSError("simulated secondary audit failure"),
        ) as audit:
            deploy._audit_best_effort("RESULT cleanup status=FAILED", self.config)

        audit.assert_called_once_with(
            "RESULT cleanup status=FAILED",
            self.config,
        )

    def test_parse_invocation_accepts_only_explicit_commands(self) -> None:
        self.assertEqual(
            deploy.parse_invocation(["forced"], f"stage {RELEASE_ID}"),
            ("stage", RELEASE_ID, None),
        )
        self.assertEqual(
            deploy.parse_invocation(["validate", RELEASE_ID], ""),
            ("validate", RELEASE_ID, None),
        )
        self.assertEqual(
            deploy.parse_invocation(["forced"], f"deploy {RELEASE_ID} 42"),
            ("deploy", RELEASE_ID, 42),
        )
        self.assertEqual(
            deploy.parse_invocation(["forced"], f"rollback {RELEASE_ID} 42"),
            ("rollback", RELEASE_ID, 42),
        )
        self.assertEqual(
            deploy.parse_invocation(["current-images"], ""),
            ("current-images", "", None),
        )

        invalid_commands = [
            (["forced"], f"stage {RELEASE_ID}; id"),
            (["forced"], "cat /etc/shadow"),
            (["deploy", "not-a-sha"], ""),
            (["deploy", RELEASE_ID], ""),
            (["deploy", RELEASE_ID, "0"], ""),
            (["deploy", RELEASE_ID, "01"], ""),
            (["validate", RELEASE_ID, "42"], ""),
            (["stage", RELEASE_ID, "extra"], ""),
            (["rollback", "not-a-sha", "42"], ""),
            (["rollback", RELEASE_ID], ""),
            (["rollback", RELEASE_ID, "0"], ""),
            (["rollback", RELEASE_ID, "01"], ""),
            (["rollback", RELEASE_ID, "42", "extra"], ""),
            (["current-images", "extra"], ""),
        ]
        for arguments, original_command in invalid_commands:
            with (
                self.subTest(arguments=arguments, original_command=original_command),
                self.assertRaises(ValueError),
            ):
                deploy.parse_invocation(arguments, original_command)

    def test_current_images_reports_only_validated_images_from_active_release(
        self,
    ) -> None:
        self.prepare_active_configuration()
        environment = environment_with_images("top-secret", "e")
        deploy.stage(RELEASE_ID, self.config, release_archive(environment=environment))
        with mock.patch.object(deploy, "run_command"):
            deploy.deploy(RELEASE_ID, 1, self.config)

        output = io.StringIO()
        with (
            mock.patch.object(deploy, "run_command") as run_command,
            mock.patch("sys.stdout", output),
        ):
            deploy.current_images(self.config)

        expected = "".join(
            f"{key}={deploy.IMMUTABLE_IMAGE_REPOSITORIES[key]}@sha256:{'e' * 64}\n"
            for key in sorted(IMMUTABLE_IMAGE_KEYS)
        )
        self.assertEqual(output.getvalue(), expected)
        self.assertNotIn("top-secret", output.getvalue())
        run_command.assert_called_once_with(
            deploy.compose_command(
                self.config.release_root / RELEASE_ID, "config", "--quiet"
            ),
            self.config.release_root / RELEASE_ID,
        )

    def test_current_images_rejects_active_configuration_mismatch(self) -> None:
        self.prepare_active_configuration()
        deploy.stage(RELEASE_ID, self.config, release_archive())
        with mock.patch.object(deploy, "run_command"):
            deploy.deploy(RELEASE_ID, 1, self.config)

        (self.config.app_dir / ".env").write_bytes(
            environment_with_images("different-active-release", "f")
        )

        with (
            mock.patch.object(deploy, "run_command"),
            self.assertRaisesRegex(
                RuntimeError,
                "active configuration does not match current release: .env",
            ),
        ):
            deploy.current_images(self.config)

    def test_current_images_fails_before_immutable_activation_history_exists(
        self,
    ) -> None:
        output = io.StringIO()
        with (
            mock.patch("sys.stdout", output),
            self.assertRaisesRegex(ValueError, "no immutable activation history"),
        ):
            deploy.current_images(self.config)

        self.assertEqual(output.getvalue(), "")

    def test_current_images_rejects_a_retained_release_modified_after_activation(
        self,
    ) -> None:
        self.prepare_active_configuration()
        deploy.stage(RELEASE_ID, self.config, release_archive())
        with mock.patch.object(deploy, "run_command"):
            deploy.deploy(RELEASE_ID, 1, self.config)
        (self.config.release_root / RELEASE_ID / ".env").write_bytes(
            valid_environment() + b"POSTGRES_PASSWORD=exposed\n"
        )

        output = io.StringIO()
        with (
            mock.patch.object(deploy, "run_command") as run_command,
            mock.patch("sys.stdout", output),
            self.assertRaises(ValueError),
        ):
            deploy.current_images(self.config)

        self.assertEqual(output.getvalue(), "")
        run_command.assert_not_called()

    def test_read_archive_removes_partial_file_when_limit_is_exceeded(self) -> None:
        config = deploy.DeploymentConfig(
            app_dir=self.config.app_dir,
            release_root=self.config.release_root,
            rollback_root=self.config.rollback_root,
            log_path=self.config.log_path,
            lock_path=self.config.lock_path,
            max_archive_bytes=8,
            enforce_root_ownership=False,
        )

        with self.assertRaises(ValueError):
            deploy.read_archive(config, io.BytesIO(b"123456789"))

        self.assertEqual(list(config.release_root.iterdir()), [])

    def test_stage_and_validate_exact_release(self) -> None:
        deploy.stage(RELEASE_ID, self.config, release_archive())

        with mock.patch.object(deploy, "run_command") as run_command:
            directory = deploy.validate(RELEASE_ID, self.config)

        self.assertEqual(directory, self.config.release_root / RELEASE_ID)
        self.assertEqual(
            {path.name for path in directory.iterdir()},
            {"docker-compose-prod.yml", ".env", "manifest.sha256"},
        )
        run_command.assert_called_once()

    def test_stage_rejects_unexpected_archive_member_and_cleans_up(self) -> None:
        archive = release_archive(extra_files={"unexpected.txt": b"not allowed"})

        with self.assertRaises(ValueError):
            deploy.stage(RELEASE_ID, self.config, archive)

        self.assertFalse((self.config.release_root / RELEASE_ID).exists())
        self.assertEqual(list(self.config.release_root.iterdir()), [])

    def test_stage_rejects_oversized_member_before_reading_its_payload(self) -> None:
        archive = oversized_member_archive(self.config.max_archive_bytes + 1)

        with self.assertRaisesRegex(ValueError, "extracted release exceeds"):
            deploy.stage(RELEASE_ID, self.config, archive)

        self.assertFalse((self.config.release_root / RELEASE_ID).exists())
        self.assertEqual(list(self.config.release_root.iterdir()), [])

    def test_stage_rejects_duplicate_archive_member(self) -> None:
        archive = archive_with_members(
            [
                regular_member(
                    "docker-compose-prod.yml",
                    b"services:\n  app:\n    image: busybox\n",
                ),
                regular_member(".env", valid_environment()),
                regular_member(".env", valid_environment("duplicate")),
            ]
        )

        with self.assertRaisesRegex(ValueError, "duplicate release member"):
            deploy.stage(RELEASE_ID, self.config, archive)

    def test_stage_rejects_archive_link(self) -> None:
        for link_type in (tarfile.SYMTYPE, tarfile.LNKTYPE):
            with self.subTest(link_type=link_type):
                link = tarfile.TarInfo(".env")
                link.type = link_type
                link.linkname = "/etc/shadow"
                archive = archive_with_members(
                    [
                        regular_member(
                            "docker-compose-prod.yml",
                            b"services:\n  app:\n    image: busybox\n",
                        ),
                        (link, b""),
                    ]
                )

                with self.assertRaisesRegex(ValueError, "invalid release member"):
                    deploy.stage(RELEASE_ID, self.config, archive)

    def test_validate_rejects_a_release_modified_after_staging(self) -> None:
        deploy.stage(RELEASE_ID, self.config, release_archive())
        release = self.config.release_root / RELEASE_ID
        (release / "docker-compose-prod.yml").write_text(
            "services: {}\n", encoding="utf-8"
        )

        with (
            mock.patch.object(deploy, "run_command") as run_command,
            self.assertRaises(ValueError),
        ):
            deploy.validate(RELEASE_ID, self.config)

        run_command.assert_not_called()

    @unittest.skipUnless(os.name == "posix", "POSIX modes are required")
    def test_validate_rejects_unsafe_staged_file_mode(self) -> None:
        deploy.stage(RELEASE_ID, self.config, release_archive())
        (self.config.release_root / RELEASE_ID / ".env").chmod(0o644)

        with (
            mock.patch.object(deploy, "run_command"),
            self.assertRaisesRegex(PermissionError, "unsafe mode"),
        ):
            deploy.validate(RELEASE_ID, self.config)

    @unittest.skipUnless(os.name == "posix", "POSIX symlinks are required")
    def test_validate_rejects_staged_file_symlink(self) -> None:
        deploy.stage(RELEASE_ID, self.config, release_archive())
        release_environment = self.config.release_root / RELEASE_ID / ".env"
        outside = self.root / "outside.env"
        outside.write_bytes(valid_environment())
        release_environment.unlink()
        release_environment.symlink_to(outside)

        with (
            mock.patch.object(deploy, "run_command"),
            self.assertRaisesRegex(ValueError, "release contains a symlink"),
        ):
            deploy.validate(RELEASE_ID, self.config)

    def test_validate_env_rejects_duplicate_and_missing_keys(self) -> None:
        environment = self.root / ".env"
        environment.write_bytes(valid_environment() + b"POSTGRES_PORT=duplicate\n")
        with self.assertRaises(ValueError):
            deploy.validate_env(environment)

        environment.write_text("POSTGRES_PORT=5432\n", encoding="utf-8")
        with self.assertRaises(ValueError):
            deploy.validate_env(environment)

    def test_validate_env_rejects_mutable_or_unapproved_image_references(self) -> None:
        environment = self.root / ".env"
        digest = "a" * 64
        invalid_references = {
            "missing backend image": ("COMMONEX_BACKEND_IMAGE", None),
            "missing frontend image": ("COMMONEX_FRONTEND_IMAGE", None),
            "missing otel collector image": ("COMMONEX_OTEL_COLLECTOR_IMAGE", None),
            "missing nginx image": ("COMMONEX_NGINX_IMAGE", None),
            "mutable tag": ("COMMONEX_BACKEND_IMAGE", "ruggedbl/commonex-nest-backend:latest"),
            "wrong repository": (
                "COMMONEX_BACKEND_IMAGE",
                f"ruggedbl/commonex-next-web@sha256:{digest}",
            ),
            "malformed digest": (
                "COMMONEX_BACKEND_IMAGE",
                "ruggedbl/commonex-nest-backend@sha256:abc",
            ),
            "uppercase digest": (
                "COMMONEX_BACKEND_IMAGE",
                f"ruggedbl/commonex-nest-backend@sha256:{digest.upper()}",
            ),
        }

        for name, (key, value) in invalid_references.items():
            with self.subTest(name=name):
                lines = valid_environment().decode().splitlines()
                lines = [line for line in lines if not line.startswith(f"{key}=")]
                if value is not None:
                    lines.append(f"{key}={value}")
                environment.write_text("\n".join(lines) + "\n", encoding="utf-8")

                with self.assertRaises(ValueError):
                    deploy.validate_env(environment)

        environment.write_bytes(valid_environment())
        deploy.validate_env(environment)

    def test_validate_calls_compose_only_after_immutable_image_environment_passes(
        self,
    ) -> None:
        compose = b"""services:
  nest-backend-green:
    image: ${COMMONEX_BACKEND_IMAGE}
  nest-backend-blue:
    image: ${COMMONEX_BACKEND_IMAGE}
  next-web:
    image: ${COMMONEX_FRONTEND_IMAGE}
  otel-collector:
    image: ${COMMONEX_OTEL_COLLECTOR_IMAGE}
  nginx:
    image: ${COMMONEX_NGINX_IMAGE}
"""
        invalid_environment = valid_environment().replace(
            VALID_IMAGE_REFERENCES["COMMONEX_BACKEND_IMAGE"].encode(),
            b"ruggedbl/commonex-nest-backend:latest",
        )
        deploy.stage(
            RELEASE_ID,
            self.config,
            release_archive(compose=compose, environment=invalid_environment),
        )

        with (
            mock.patch.object(deploy, "run_command") as run_command,
            self.assertRaises(ValueError),
        ):
            deploy.validate(RELEASE_ID, self.config)

        run_command.assert_not_called()

        deploy.stage(
            OLDER_RELEASE_ID,
            self.config,
            release_archive(compose=compose, environment=valid_environment()),
        )
        with mock.patch.object(deploy, "run_command") as run_command:
            deploy.validate(OLDER_RELEASE_ID, self.config)

        run_command.assert_called_once()

    def test_successful_deploy_retains_exactly_three_activation_releases(self) -> None:
        self.prepare_active_configuration()
        releases = [
            RELEASE_ID,
            OLDER_RELEASE_ID,
            THIRD_RELEASE_ID,
            FOURTH_RELEASE_ID,
        ]

        with mock.patch.object(deploy, "run_command"):
            for run_number, value in enumerate(releases, 1):
                deploy.stage(
                    value,
                    self.config,
                    release_archive(
                        compose=f"services:\n  release{run_number}:\n    image: busybox\n".encode(),
                        environment=environment_with_images(
                            f"release-{run_number}", str(run_number)
                        ),
                    ),
                )
                deploy.deploy(value, run_number, self.config)
                self.assertTrue((self.config.release_root / value).is_dir())

        state = json.loads(
            (self.config.release_root / "activation-state.json").read_text(
                encoding="utf-8"
            )
        )
        self.assertEqual(
            state,
            {"last_successful_run": 4, "history": list(reversed(releases[1:]))},
        )
        self.assertFalse((self.config.release_root / RELEASE_ID).exists())
        for value in releases[1:]:
            self.assertTrue((self.config.release_root / value).is_dir())

    def test_rollback_rejects_target_outside_history_before_validation(self) -> None:
        self.prepare_active_configuration()
        deploy.stage(RELEASE_ID, self.config, release_archive())
        with mock.patch.object(deploy, "run_command"):
            deploy.deploy(RELEASE_ID, 1, self.config)
        deploy.stage(OLDER_RELEASE_ID, self.config, release_archive())
        state_before = (self.config.release_root / "activation-state.json").read_bytes()

        with (
            mock.patch.object(deploy, "_validate_release_contents") as validate,
            mock.patch.object(deploy, "atomic_install") as install,
            self.assertRaisesRegex(ValueError, "not retained"),
        ):
            deploy.rollback(OLDER_RELEASE_ID, 2, self.config)

        validate.assert_not_called()
        install.assert_not_called()
        self.assertEqual(
            (self.config.release_root / "activation-state.json").read_bytes(),
            state_before,
        )

    def test_rollback_revalidates_retained_target_before_installation(self) -> None:
        self.prepare_active_configuration()
        deploy.stage(RELEASE_ID, self.config, release_archive())
        with mock.patch.object(deploy, "run_command"):
            deploy.deploy(RELEASE_ID, 1, self.config)
        state_before = (self.config.release_root / "activation-state.json").read_bytes()
        (self.config.release_root / RELEASE_ID / ".env").write_bytes(
            valid_environment() + b"POSTGRES_PASSWORD=duplicate\n"
        )

        with (
            mock.patch.object(deploy, "run_command") as run_command,
            mock.patch.object(deploy, "atomic_install") as install,
            self.assertRaises(ValueError),
        ):
            deploy.rollback(RELEASE_ID, 2, self.config)

        run_command.assert_not_called()
        install.assert_not_called()
        self.assertEqual(
            (self.config.release_root / "activation-state.json").read_bytes(),
            state_before,
        )

    def test_rollback_promotes_retained_release_and_advances_current_run(self) -> None:
        self.prepare_active_configuration()
        first_compose = b"services:\n  first:\n    image: busybox\n"
        second_compose = b"services:\n  second:\n    image: busybox\n"
        deploy.stage(
            RELEASE_ID,
            self.config,
            release_archive(
                compose=first_compose,
                environment=environment_with_images("first", "1"),
            ),
        )
        with mock.patch.object(deploy, "run_command"):
            deploy.deploy(RELEASE_ID, 1, self.config)
        deploy.stage(
            OLDER_RELEASE_ID,
            self.config,
            release_archive(
                compose=second_compose,
                environment=environment_with_images("second", "2"),
            ),
        )
        with mock.patch.object(deploy, "run_command"):
            deploy.deploy(OLDER_RELEASE_ID, 2, self.config)

        with mock.patch.object(deploy, "run_command") as run_command:
            deploy.rollback(RELEASE_ID, 3, self.config)

        self.assertEqual(
            (self.config.app_dir / "docker-compose-prod.yml").read_bytes(),
            first_compose,
        )
        self.assertEqual(
            deploy._read_activation_state(self.config),
            (3, [RELEASE_ID, OLDER_RELEASE_ID]),
        )
        self.assertTrue((self.config.release_root / RELEASE_ID).is_dir())
        self.assertTrue((self.config.release_root / OLDER_RELEASE_ID).is_dir())
        commands = [call.args for call in run_command.call_args_list]
        target = self.config.release_root / RELEASE_ID
        self.assertIn(
            (deploy.compose_command(target, "pull"), target),
            commands,
        )
        self.assertIn(
            (
                deploy.compose_command(
                    self.config.app_dir,
                    "up",
                    "-d",
                    "--pull",
                    "always",
                    "--remove-orphans",
                    "--wait",
                    "--wait-timeout",
                    "120",
                ),
                self.config.app_dir,
            ),
            commands,
        )
        log = self.config.log_path.read_text(encoding="utf-8")
        self.assertIn(
            f"ACTION rollback target={RELEASE_ID} run=3",
            log,
        )
        self.assertIn(
            f"RESULT rollback target={RELEASE_ID} run=3 "
            "configuration_restored=NOT_NEEDED status=PASS",
            log,
        )

    def test_rollback_compose_failure_restores_and_reconciles_previous_activation(
        self,
    ) -> None:
        self.prepare_active_configuration()
        first_compose = b"services:\n  first:\n    image: busybox\n"
        second_compose = b"services:\n  second:\n    image: busybox\n"
        deploy.stage(
            RELEASE_ID,
            self.config,
            release_archive(compose=first_compose),
        )
        with mock.patch.object(deploy, "run_command"):
            deploy.deploy(RELEASE_ID, 1, self.config)
        deploy.stage(
            OLDER_RELEASE_ID,
            self.config,
            release_archive(compose=second_compose),
        )
        with mock.patch.object(deploy, "run_command"):
            deploy.deploy(OLDER_RELEASE_ID, 2, self.config)
        state_before = (self.config.release_root / "activation-state.json").read_bytes()
        activation_attempts = 0

        def fail_candidate_reconciliation(command: list[str], cwd: Path) -> None:
            nonlocal activation_attempts
            if cwd == self.config.app_dir and "up" in command:
                activation_attempts += 1
                if activation_attempts == 1:
                    raise subprocess.CalledProcessError(1, command)

        with (
            mock.patch.object(
                deploy, "run_command", side_effect=fail_candidate_reconciliation
            ) as run_command,
            self.assertRaises(subprocess.CalledProcessError),
        ):
            deploy.rollback(RELEASE_ID, 3, self.config)

        self.assertEqual(activation_attempts, 2)
        self.assertEqual(
            (self.config.app_dir / "docker-compose-prod.yml").read_bytes(),
            second_compose,
        )
        self.assertEqual(
            (self.config.release_root / "activation-state.json").read_bytes(),
            state_before,
        )
        log = self.config.log_path.read_text(encoding="utf-8")
        self.assertIn(
            f"RESULT rollback target={RELEASE_ID} run=3 status=FAILED",
            log,
        )
        self.assertIn("configuration_restored=PASS", log)
        reconciliation_commands = [
            command
            for call in run_command.call_args_list
            if (command := call.args[0]) and "up" in command
        ]
        self.assertEqual(len(reconciliation_commands), 2)
        self.assertTrue(
            all("--remove-orphans" in command for command in reconciliation_commands)
        )

    def test_activation_intent_survives_abrupt_exit_and_blocks_later_commands(
        self,
    ) -> None:
        class SimulatedAbruptExit(BaseException):
            pass

        self.prepare_active_configuration()
        deploy.stage(RELEASE_ID, self.config, release_archive())

        with (
            mock.patch.object(deploy, "run_command"),
            mock.patch.object(
                deploy,
                "atomic_install",
                side_effect=SimulatedAbruptExit("simulated process death"),
            ),
            self.assertRaises(SimulatedAbruptExit),
        ):
            deploy.deploy(RELEASE_ID, 1, self.config)

        intent_path = self.config.release_root / "activation-intent.json"
        intent = json.loads(intent_path.read_text(encoding="utf-8"))
        self.assertEqual(intent["candidate_release"], RELEASE_ID)
        self.assertIsNone(intent["previous_release"])
        self.assertEqual(intent["operation"], "deploy")
        self.assertEqual(intent["run_number"], 1)
        self.assertRegex(intent["rollback_backup"], rf"^deploy-{RELEASE_ID}-")

        with (
            mock.patch.object(deploy, "run_command") as run_command,
            self.assertRaisesRegex(
                RuntimeError, "unresolved activation intent.*manually reconcile"
            ),
        ):
            deploy.deploy(RELEASE_ID, 2, self.config)
        run_command.assert_not_called()

    def test_activation_intent_survives_abrupt_exit_after_runtime_reconciliation(
        self,
    ) -> None:
        class SimulatedAbruptExit(BaseException):
            pass

        self.prepare_active_configuration()
        candidate_compose = b"services:\n  candidate:\n    image: busybox\n"
        deploy.stage(
            RELEASE_ID,
            self.config,
            release_archive(compose=candidate_compose),
        )

        with (
            mock.patch.object(deploy, "run_command"),
            mock.patch.object(
                deploy,
                "_write_activation_state",
                side_effect=SimulatedAbruptExit("simulated process death"),
            ),
            self.assertRaises(SimulatedAbruptExit),
        ):
            deploy.deploy(RELEASE_ID, 1, self.config)

        self.assertEqual(
            (self.config.app_dir / "docker-compose-prod.yml").read_bytes(),
            candidate_compose,
        )
        self.assertFalse(
            (self.config.release_root / "activation-state.json").exists()
        )
        self.assertTrue(
            (self.config.release_root / "activation-intent.json").exists()
        )

    def test_activation_intent_survives_abrupt_exit_after_state_commit(self) -> None:
        class SimulatedAbruptExit(BaseException):
            pass

        self.prepare_active_configuration()
        deploy.stage(RELEASE_ID, self.config, release_archive())

        with (
            mock.patch.object(deploy, "run_command"),
            mock.patch.object(
                deploy,
                "_clear_activation_intent",
                side_effect=SimulatedAbruptExit("simulated process death"),
            ),
            self.assertRaises(SimulatedAbruptExit),
        ):
            deploy.deploy(RELEASE_ID, 1, self.config)

        self.assertEqual(
            deploy._read_activation_state(self.config),
            (1, [RELEASE_ID]),
        )
        self.assertTrue(
            (self.config.release_root / "activation-intent.json").exists()
        )

    def test_successful_activation_durably_clears_intent(self) -> None:
        self.prepare_active_configuration()
        deploy.stage(RELEASE_ID, self.config, release_archive())

        with mock.patch.object(deploy, "run_command"):
            deploy.deploy(RELEASE_ID, 1, self.config)

        self.assertFalse(
            (self.config.release_root / "activation-intent.json").exists()
        )

    def test_intent_clear_fsync_failure_restores_marker_and_fails_closed(
        self,
    ) -> None:
        self.prepare_active_configuration()
        deploy.stage(RELEASE_ID, self.config, release_archive())
        intent_path = self.config.release_root / "activation-intent.json"
        real_fsync_directory = deploy.fsync_directory
        clear_fsync_failed = False

        def fail_first_clear_fsync(path: Path) -> None:
            nonlocal clear_fsync_failed
            if (
                path == self.config.release_root
                and not intent_path.exists()
                and not clear_fsync_failed
            ):
                clear_fsync_failed = True
                raise OSError("simulated intent clear fsync failure")
            real_fsync_directory(path)

        with (
            mock.patch.object(deploy, "run_command"),
            mock.patch.object(
                deploy, "fsync_directory", side_effect=fail_first_clear_fsync
            ),
            self.assertRaisesRegex(
                deploy.AmbiguousActivationCommitError,
                "intent could not be durably cleared",
            ),
        ):
            deploy.deploy(RELEASE_ID, 1, self.config)

        self.assertTrue(clear_fsync_failed)
        self.assertTrue(intent_path.exists())
        self.assertEqual(deploy._read_activation_state(self.config), (1, [RELEASE_ID]))
        with self.assertRaisesRegex(
            deploy.UnresolvedActivationIntentError,
            "unresolved activation intent",
        ):
            deploy.current_images(self.config)

    def test_restored_activation_failure_durably_clears_intent(self) -> None:
        self.prepare_active_configuration()
        deploy.stage(RELEASE_ID, self.config, release_archive())
        reconciliations = 0

        def fail_candidate_only(command: list[str], cwd: Path) -> None:
            nonlocal reconciliations
            if cwd == self.config.app_dir and "up" in command:
                reconciliations += 1
                if reconciliations == 1:
                    raise subprocess.CalledProcessError(1, command)

        with (
            mock.patch.object(deploy, "run_command", side_effect=fail_candidate_only),
            self.assertRaises(subprocess.CalledProcessError),
        ):
            deploy.deploy(RELEASE_ID, 1, self.config)

        self.assertEqual(reconciliations, 2)
        self.assertFalse(
            (self.config.release_root / "activation-intent.json").exists()
        )

    def test_unresolved_intent_blocks_current_images_without_exposing_state(
        self,
    ) -> None:
        self.config.release_root.mkdir(mode=0o700)
        intent_path = self.config.release_root / "activation-intent.json"
        intent_path.write_text("malformed secret\n", encoding="utf-8")
        intent_path.chmod(0o600)

        with self.assertRaisesRegex(
            RuntimeError, "unresolved activation intent.*manually reconcile"
        ) as raised:
            deploy.current_images(self.config)

        self.assertNotIn("malformed secret", str(raised.exception))

    def test_post_mutation_state_failure_restores_and_reconciles_prior_definition(
        self,
    ) -> None:
        self.prepare_active_configuration()
        first_compose = b"services:\n  first:\n    image: busybox\n"
        second_compose = b"services:\n  second:\n    image: busybox\n"
        deploy.stage(RELEASE_ID, self.config, release_archive(compose=first_compose))
        with mock.patch.object(deploy, "run_command"):
            deploy.deploy(RELEASE_ID, 1, self.config)
        deploy.stage(
            OLDER_RELEASE_ID, self.config, release_archive(compose=second_compose)
        )
        with mock.patch.object(deploy, "run_command"):
            deploy.deploy(OLDER_RELEASE_ID, 2, self.config)
        state_before = (self.config.release_root / "activation-state.json").read_bytes()
        app_reconciliations = 0

        def record_reconciliation(command: list[str], cwd: Path) -> None:
            nonlocal app_reconciliations
            if cwd == self.config.app_dir and "up" in command:
                app_reconciliations += 1

        with (
            mock.patch.object(deploy, "run_command", side_effect=record_reconciliation),
            mock.patch.object(
                deploy,
                "_write_activation_state",
                side_effect=OSError("simulated state commit failure"),
            ),
            self.assertRaises(OSError),
        ):
            deploy.rollback(RELEASE_ID, 3, self.config)

        self.assertEqual(app_reconciliations, 2)
        self.assertEqual(
            (self.config.app_dir / "docker-compose-prod.yml").read_bytes(),
            second_compose,
        )
        self.assertEqual(
            (self.config.release_root / "activation-state.json").read_bytes(),
            state_before,
        )

    def test_post_replace_state_fsync_failure_restores_state_before_configuration(
        self,
    ) -> None:
        first_compose, second_compose = self.prepare_two_activation_history()
        state_path = self.config.release_root / "activation-state.json"
        state_before = state_path.read_bytes()
        real_fsync_directory = deploy.fsync_directory
        failed_new_state_fsync = False
        app_reconciliations = 0

        def fail_new_state_fsync(path: Path) -> None:
            nonlocal failed_new_state_fsync
            if path == self.config.release_root and not failed_new_state_fsync:
                state = json.loads(state_path.read_text(encoding="utf-8"))
                if state["last_successful_run"] == 3:
                    failed_new_state_fsync = True
                    raise OSError("simulated post-replace state fsync failure")
            real_fsync_directory(path)

        def record_reconciliation(command: list[str], cwd: Path) -> None:
            nonlocal app_reconciliations
            if cwd == self.config.app_dir and "up" in command:
                app_reconciliations += 1

        with (
            mock.patch.object(
                deploy, "fsync_directory", side_effect=fail_new_state_fsync
            ),
            mock.patch.object(deploy, "run_command", side_effect=record_reconciliation),
            self.assertRaisesRegex(OSError, "post-replace state fsync failure"),
        ):
            deploy.rollback(RELEASE_ID, 3, self.config)

        self.assertTrue(failed_new_state_fsync)
        self.assertEqual(state_path.read_bytes(), state_before)
        self.assertEqual(
            (self.config.app_dir / "docker-compose-prod.yml").read_bytes(),
            second_compose,
        )
        self.assertNotEqual(
            (self.config.app_dir / "docker-compose-prod.yml").read_bytes(),
            first_compose,
        )
        self.assertEqual(app_reconciliations, 2)

    def test_unconfirmed_state_restoration_keeps_candidate_configuration_active(
        self,
    ) -> None:
        first_compose, second_compose = self.prepare_two_activation_history()
        state_path = self.config.release_root / "activation-state.json"
        release_root_fsyncs = 0
        state_commit_started = False
        app_reconciliations = 0

        def fail_commit_and_restore_fsync(path: Path) -> None:
            nonlocal release_root_fsyncs, state_commit_started
            if path == self.config.release_root:
                state = json.loads(state_path.read_text(encoding="utf-8"))
                if state["last_successful_run"] == 3:
                    state_commit_started = True
                if state_commit_started:
                    release_root_fsyncs += 1
                    raise OSError("simulated unconfirmed state restoration")

        def record_reconciliation(command: list[str], cwd: Path) -> None:
            nonlocal app_reconciliations
            if cwd == self.config.app_dir and "up" in command:
                app_reconciliations += 1

        with (
            mock.patch.object(
                deploy, "fsync_directory", side_effect=fail_commit_and_restore_fsync
            ),
            mock.patch.object(deploy, "run_command", side_effect=record_reconciliation),
            self.assertRaisesRegex(
                RuntimeError, "activation state commit is ambiguous"
            ) as raised,
        ):
            deploy.rollback(RELEASE_ID, 3, self.config)

        self.assertEqual(
            type(raised.exception).__name__, "AmbiguousActivationCommitError"
        )
        self.assertGreaterEqual(release_root_fsyncs, 2)
        self.assertEqual(app_reconciliations, 1)
        self.assertEqual(
            (self.config.app_dir / "docker-compose-prod.yml").read_bytes(),
            first_compose,
        )
        self.assertNotEqual(
            (self.config.app_dir / "docker-compose-prod.yml").read_bytes(),
            second_compose,
        )
        self.assertIn(
            json.loads(state_path.read_text(encoding="utf-8"))["last_successful_run"],
            {2, 3},
        )
        self.assertTrue(
            (self.config.release_root / "activation-intent.json").exists()
        )

    def test_cleanup_failure_is_audited_after_state_commit(self) -> None:
        self.prepare_active_configuration()
        deploy.stage(RELEASE_ID, self.config, release_archive())
        with mock.patch.object(deploy, "run_command"):
            deploy.deploy(RELEASE_ID, 1, self.config)
        deploy.stage(OLDER_RELEASE_ID, self.config, release_archive())
        deploy.stage(THIRD_RELEASE_ID, self.config, release_archive())

        with (
            mock.patch.object(deploy, "run_command"),
            mock.patch.object(
                deploy.shutil,
                "rmtree",
                side_effect=OSError("simulated cleanup failure"),
            ),
        ):
            deploy.deploy(OLDER_RELEASE_ID, 2, self.config)

        self.assertEqual(
            deploy._read_activation_state(self.config),
            (2, [OLDER_RELEASE_ID, RELEASE_ID]),
        )
        self.assertTrue((self.config.release_root / RELEASE_ID).is_dir())
        self.assertTrue((self.config.release_root / OLDER_RELEASE_ID).is_dir())
        self.assertTrue((self.config.release_root / THIRD_RELEASE_ID).is_dir())
        self.assertIn(
            "RESULT cleanup status=FAILED error=OSError",
            self.config.log_path.read_text(encoding="utf-8"),
        )

    def test_final_audit_failure_reports_committed_activation_without_recovery(
        self,
    ) -> None:
        self.prepare_active_configuration()
        candidate_compose = b"services:\n  candidate:\n    image: busybox\n"
        deploy.stage(
            RELEASE_ID,
            self.config,
            release_archive(compose=candidate_compose),
        )
        audit_messages: list[str] = []
        app_reconciliations = 0

        def fail_only_final_audit(
            message: str,
            _config: deploy.DeploymentConfig = deploy.DEFAULT_CONFIG,
        ) -> None:
            audit_messages.append(message)
            if message.startswith("RESULT deploy") and "status=PASS" in message:
                raise OSError("simulated final audit failure")

        def record_reconciliation(command: list[str], cwd: Path) -> None:
            nonlocal app_reconciliations
            if cwd == self.config.app_dir and "up" in command:
                app_reconciliations += 1

        stderr = io.StringIO()
        with (
            mock.patch.object(deploy, "audit", side_effect=fail_only_final_audit),
            mock.patch.object(deploy, "run_command", side_effect=record_reconciliation),
            mock.patch.object(
                deploy,
                "main",
                side_effect=lambda: deploy.deploy(RELEASE_ID, 1, self.config),
            ),
            mock.patch("sys.stderr", stderr),
        ):
            result = deploy.run_cli()

        self.assertEqual(result, 2)
        self.assertEqual(app_reconciliations, 1)
        self.assertEqual(
            (self.config.app_dir / "docker-compose-prod.yml").read_bytes(),
            candidate_compose,
        )
        self.assertEqual(
            deploy._read_activation_state(self.config),
            (1, [RELEASE_ID]),
        )
        self.assertFalse(
            any("RESULT command status=FAILED" in message for message in audit_messages)
        )
        self.assertNotIn("configuration_restored", "\n".join(audit_messages))
        self.assertIn("activation committed but final audit failed", stderr.getvalue())

    def test_activation_state_rejects_malformed_or_untrusted_content(self) -> None:
        self.config.release_root.mkdir(mode=0o700)
        state_path = self.config.release_root / "activation-state.json"
        invalid_states = [
            b"not-json\n",
            b'{"last_successful_run":0,"history":[]}\n',
            (
                b'{"last_successful_run":1,"last_successful_run":2,'
                b'"history":["' + RELEASE_ID.encode() + b'"]}\n'
            ),
            (
                b'{"last_successful_run":1,"history":["'
                + RELEASE_ID.encode()
                + b'"],"history":[]}\n'
            ),
            json.dumps(
                {"last_successful_run": 1, "history": ["not-a-sha"]}
            ).encode(),
            json.dumps(
                {"last_successful_run": 1, "history": [RELEASE_ID, RELEASE_ID]}
            ).encode(),
            json.dumps(
                {
                    "last_successful_run": 1,
                    "history": [
                        RELEASE_ID,
                        OLDER_RELEASE_ID,
                        THIRD_RELEASE_ID,
                        FOURTH_RELEASE_ID,
                    ],
                }
            ).encode(),
            json.dumps(
                {"last_successful_run": 1, "history": [RELEASE_ID], "extra": True}
            ).encode(),
        ]

        for content in invalid_states:
            with self.subTest(content=content):
                state_path.write_bytes(content)
                state_path.chmod(0o600)
                with self.assertRaises(ValueError):
                    deploy._read_activation_state(self.config)

    @unittest.skipUnless(os.name == "posix", "POSIX modes and symlinks are required")
    def test_activation_state_rejects_unsafe_mode_and_symlink(self) -> None:
        self.config.release_root.mkdir(mode=0o700)
        state_path = self.config.release_root / "activation-state.json"
        state_path.write_text(
            json.dumps({"last_successful_run": 1, "history": [RELEASE_ID]}),
            encoding="utf-8",
        )
        state_path.chmod(0o644)
        with self.assertRaisesRegex(PermissionError, "unsafe mode"):
            deploy._read_activation_state(self.config)

        state_path.unlink()
        outside = self.root / "outside-state.json"
        outside.write_text(
            json.dumps({"last_successful_run": 1, "history": [RELEASE_ID]}),
            encoding="utf-8",
        )
        state_path.symlink_to(outside)
        with self.assertRaises(PermissionError):
            deploy._read_activation_state(self.config)

    def test_deploy_restores_configuration_when_compose_fails(self) -> None:
        self.config.app_dir.mkdir()
        self.config.rollback_root.mkdir()
        old_compose = b"services:\n  old:\n    image: busybox\n"
        old_environment = valid_environment("old")
        (self.config.app_dir / "docker-compose-prod.yml").write_bytes(old_compose)
        (self.config.app_dir / ".env").write_bytes(old_environment)
        (self.config.app_dir / ".env").chmod(0o600)
        deploy.stage(
            RELEASE_ID,
            self.config,
            release_archive(environment=valid_environment("new")),
        )

        activation_attempts = 0

        def run_command(command: list[str], _cwd: Path) -> None:
            nonlocal activation_attempts
            if "up" in command:
                activation_attempts += 1
            if "up" in command and activation_attempts == 1:
                raise subprocess.CalledProcessError(1, command)

        with (
            mock.patch.object(deploy, "run_command", side_effect=run_command),
            self.assertRaises(subprocess.CalledProcessError),
        ):
            deploy.deploy(RELEASE_ID, 1, self.config)

        self.assertEqual(
            (self.config.app_dir / "docker-compose-prod.yml").read_bytes(), old_compose
        )
        self.assertEqual((self.config.app_dir / ".env").read_bytes(), old_environment)
        self.assertEqual(activation_attempts, 2)
        self.assertFalse(
            (self.config.release_root / "activation-state.json").exists()
        )
        log = self.config.log_path.read_text(encoding="utf-8")
        self.assertIn("configuration_restored=PASS", log)
        self.assertIn("runtime_may_have_changed=true", log)

    def test_deploy_restores_configuration_when_atomic_install_fails(self) -> None:
        self.config.app_dir.mkdir()
        self.config.rollback_root.mkdir()
        old_compose = b"services:\n  old:\n    image: busybox\n"
        old_environment = valid_environment("old")
        (self.config.app_dir / "docker-compose-prod.yml").write_bytes(old_compose)
        (self.config.app_dir / ".env").write_bytes(old_environment)
        (self.config.app_dir / ".env").chmod(0o600)
        deploy.stage(RELEASE_ID, self.config, release_archive())

        real_atomic_install = deploy.atomic_install
        install_calls = 0

        def failing_atomic_install(
            source: Path,
            destination: Path,
            mode: int,
            config: deploy.DeploymentConfig,
        ) -> None:
            nonlocal install_calls
            install_calls += 1
            real_atomic_install(source, destination, mode, config)
            if install_calls == 1:
                raise OSError("simulated post-replace failure")

        with (
            mock.patch.object(deploy, "run_command"),
            mock.patch.object(
                deploy, "atomic_install", side_effect=failing_atomic_install
            ),
            self.assertRaises(OSError),
        ):
            deploy.deploy(RELEASE_ID, 1, self.config)

        self.assertEqual(
            (self.config.app_dir / "docker-compose-prod.yml").read_bytes(), old_compose
        )
        self.assertEqual((self.config.app_dir / ".env").read_bytes(), old_environment)
        self.assertFalse(
            (self.config.release_root / "activation-state.json").exists()
        )
        log = self.config.log_path.read_text(encoding="utf-8")
        self.assertIn("configuration_restored=PASS", log)
        self.assertIn("runtime_may_have_changed=false", log)

    def test_deploy_rejects_run_older_than_last_successful_deployment(self) -> None:
        self.config.app_dir.mkdir()
        self.config.rollback_root.mkdir()
        (self.config.app_dir / "docker-compose-prod.yml").write_bytes(
            b"services:\n  old:\n    image: busybox\n"
        )
        (self.config.app_dir / ".env").write_bytes(valid_environment("old"))
        (self.config.app_dir / ".env").chmod(0o600)
        deploy.stage(
            RELEASE_ID,
            self.config,
            release_archive(
                compose=b"services:\n  newest:\n    image: busybox\n",
                environment=valid_environment("newest"),
            ),
        )
        deploy.stage(
            OLDER_RELEASE_ID,
            self.config,
            release_archive(
                compose=b"services:\n  stale:\n    image: busybox\n",
                environment=valid_environment("stale"),
            ),
        )

        with mock.patch.object(deploy, "run_command"):
            deploy.deploy(RELEASE_ID, 20, self.config)
            with self.assertRaisesRegex(ValueError, "older than or equal"):
                deploy.deploy(OLDER_RELEASE_ID, 19, self.config)

        self.assertEqual(
            (self.config.app_dir / "docker-compose-prod.yml").read_bytes(),
            b"services:\n  newest:\n    image: busybox\n",
        )
        self.assertEqual(
            deploy._read_activation_state(self.config),
            (20, [RELEASE_ID]),
        )
        self.assertFalse(
            (self.config.release_root / "last-successful-run").exists()
        )

    def test_legacy_run_blocks_stale_activation_then_migrates_to_canonical_state(
        self,
    ) -> None:
        self.prepare_active_configuration()
        candidate_compose = b"services:\n  migrated:\n    image: busybox\n"
        deploy.stage(
            RELEASE_ID,
            self.config,
            release_archive(compose=candidate_compose),
        )
        legacy_state = self.config.release_root / "last-successful-run"
        legacy_state.write_bytes(b"20\n")
        legacy_state.chmod(0o600)

        with mock.patch.object(deploy, "run_command"):
            with self.assertRaisesRegex(ValueError, "older than or equal"):
                deploy.deploy(RELEASE_ID, 20, self.config)
            self.assertFalse(
                (self.config.release_root / "activation-state.json").exists()
            )
            deploy.deploy(RELEASE_ID, 21, self.config)

        self.assertEqual(
            deploy._read_activation_state(self.config),
            (21, [RELEASE_ID]),
        )
        self.assertEqual(legacy_state.read_text(encoding="ascii"), "20\n")
        self.assertEqual(
            (self.config.app_dir / "docker-compose-prod.yml").read_bytes(),
            candidate_compose,
        )

    def test_deploy_reports_when_configuration_restoration_fails(self) -> None:
        self.config.app_dir.mkdir()
        self.config.rollback_root.mkdir()
        (self.config.app_dir / "docker-compose-prod.yml").write_bytes(
            b"services:\n  old:\n    image: busybox\n"
        )
        (self.config.app_dir / ".env").write_bytes(valid_environment("old"))
        (self.config.app_dir / ".env").chmod(0o600)
        deploy.stage(RELEASE_ID, self.config, release_archive())

        def run_command(command: list[str], _cwd: Path) -> None:
            if "up" in command:
                raise subprocess.CalledProcessError(1, command)

        with (
            mock.patch.object(deploy, "run_command", side_effect=run_command),
            mock.patch.object(
                deploy,
                "_restore_configuration",
                side_effect=OSError("simulated restoration failure"),
            ),
            self.assertRaises(deploy.ConfigurationRestoreError),
        ):
            deploy.deploy(RELEASE_ID, 1, self.config)

        log = self.config.log_path.read_text(encoding="utf-8")
        self.assertIn("configuration_restored=FAILED", log)
        self.assertIn("runtime_may_have_changed=true", log)

    @unittest.skipUnless(os.name == "posix", "fcntl locking is POSIX-only")
    def test_operation_lock_rejects_an_unsafe_runtime_directory(self) -> None:
        for unsafe_mode in (0o775, 0o777):
            with self.subTest(mode=oct(unsafe_mode)):
                runtime = self.config.lock_path.parent
                runtime.mkdir(mode=0o755, exist_ok=True)
                runtime.chmod(unsafe_mode)

                with self.assertRaisesRegex(PermissionError, "unsafe mode"):
                    with deploy.operation_lock(self.config):
                        self.fail("unsafe runtime directory acquired the operation lock")

                self.assertFalse(self.config.lock_path.exists())

    @unittest.skipUnless(os.name == "posix", "fcntl locking is POSIX-only")
    def test_operation_lock_rejects_a_symlinked_runtime_directory(self) -> None:
        real_runtime = self.root / "real-runtime"
        real_runtime.mkdir(mode=0o755)
        try:
            self.config.lock_path.parent.symlink_to(
                real_runtime,
                target_is_directory=True,
            )
        except OSError as error:
            self.skipTest(f"directory symlinks are unavailable: {error}")

        with self.assertRaisesRegex(PermissionError, "not a directory"):
            with deploy.operation_lock(self.config):
                self.fail("symlinked runtime directory acquired the operation lock")

    @unittest.skipUnless(os.name == "posix", "fcntl locking is POSIX-only")
    def test_operation_lock_normalizes_new_runtime_under_restrictive_umask(self) -> None:
        previous_umask = os.umask(0o077)
        try:
            with deploy.operation_lock(self.config):
                self.assertEqual(
                    stat.S_IMODE(self.config.lock_path.parent.stat().st_mode),
                    0o755,
                )
                self.assertEqual(
                    stat.S_IMODE(self.config.lock_path.stat().st_mode),
                    0o600,
                )
        finally:
            os.umask(previous_umask)

    @unittest.skipUnless(os.name == "posix", "fcntl locking is POSIX-only")
    def test_operation_lock_rejects_an_unsafe_existing_lock_mode(self) -> None:
        runtime = self.config.lock_path.parent
        runtime.mkdir(mode=0o755)
        self.config.lock_path.write_bytes(b"")
        self.config.lock_path.chmod(0o644)

        with self.assertRaisesRegex(PermissionError, "trusted regular file"):
            with deploy.operation_lock(self.config):
                self.fail("unsafe existing lock was acquired")

        self.assertEqual(stat.S_IMODE(self.config.lock_path.stat().st_mode), 0o644)

    @unittest.skipUnless(os.name == "posix", "fcntl locking is POSIX-only")
    def test_operation_lock_rejects_path_replacement_after_acquisition(self) -> None:
        real_flock = __import__("fcntl").flock

        def replace_lock(descriptor: int, operation: int) -> None:
            real_flock(descriptor, operation)
            self.config.lock_path.unlink()
            self.config.lock_path.write_bytes(b"")
            self.config.lock_path.chmod(0o600)

        with (
            mock.patch("fcntl.flock", side_effect=replace_lock),
            self.assertRaisesRegex(PermissionError, "changed while it was acquired"),
        ):
            with deploy.operation_lock(self.config):
                self.fail("replaced lock path was accepted")

    @unittest.skipUnless(os.name == "posix", "fcntl locking is POSIX-only")
    def test_current_images_rejects_unsafe_runtime_before_reading_state(self) -> None:
        runtime = self.config.lock_path.parent
        runtime.mkdir(mode=0o755)
        runtime.chmod(0o777)

        with (
            mock.patch.object(deploy, "_read_activation_state") as read_state,
            self.assertRaisesRegex(PermissionError, "unsafe mode"),
        ):
            deploy.current_images(self.config)

        read_state.assert_not_called()

    @unittest.skipUnless(os.name == "posix", "fcntl locking is POSIX-only")
    def test_operation_lock_serializes_processes(self) -> None:
        ready = self.root / "child-ready"
        acquired = self.root / "child-acquired"
        script = f"""
from pathlib import Path
from commonex_deploy import DeploymentConfig, operation_lock

config = DeploymentConfig(
    lock_path=Path({str(self.config.lock_path)!r}),
    enforce_root_ownership=False,
)
Path({str(ready)!r}).write_text("ready", encoding="utf-8")
with operation_lock(config):
    Path({str(acquired)!r}).write_text("acquired", encoding="utf-8")
"""

        with deploy.operation_lock(self.config):
            child = subprocess.Popen(  # noqa: S603
                [sys.executable, "-c", script],
                cwd=DEPLOY_DIRECTORY,
            )
            deadline = time.monotonic() + 5
            while not ready.exists() and time.monotonic() < deadline:
                time.sleep(0.01)
            self.assertTrue(ready.exists())
            self.assertFalse(acquired.exists())

        self.assertEqual(child.wait(timeout=5), 0)
        self.assertEqual(acquired.read_text(encoding="utf-8"), "acquired")


if __name__ == "__main__":
    unittest.main()
