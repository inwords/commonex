from __future__ import annotations

import gzip
import io
import os
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


def valid_environment(marker: str = "value") -> bytes:
    return "".join(
        f"{key}={marker}-{key.lower()}\n" for key in sorted(deploy.REQUIRED_ENV_KEYS)
    ).encode()


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
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.config = deploy.DeploymentConfig(
            app_dir=self.root / "app",
            release_root=self.root / "releases",
            rollback_root=self.root / "rollback",
            log_path=self.root / "logs" / "deploy.log",
            lock_path=self.root / "deploy.lock",
            enforce_root_ownership=False,
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

        invalid_commands = [
            (["forced"], f"stage {RELEASE_ID}; id"),
            (["forced"], "cat /etc/shadow"),
            (["deploy", "not-a-sha"], ""),
            (["deploy", RELEASE_ID], ""),
            (["deploy", RELEASE_ID, "0"], ""),
            (["deploy", RELEASE_ID, "01"], ""),
            (["validate", RELEASE_ID, "42"], ""),
            (["stage", RELEASE_ID, "extra"], ""),
        ]
        for arguments, original_command in invalid_commands:
            with (
                self.subTest(arguments=arguments, original_command=original_command),
                self.assertRaises(ValueError),
            ):
                deploy.parse_invocation(arguments, original_command)

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

    def test_deploy_restores_configuration_when_compose_fails(self) -> None:
        self.config.app_dir.mkdir()
        self.config.rollback_root.mkdir()
        old_compose = b"services:\n  old:\n    image: busybox\n"
        old_environment = valid_environment("old")
        (self.config.app_dir / "docker-compose-prod.yml").write_bytes(old_compose)
        (self.config.app_dir / ".env").write_bytes(old_environment)
        deploy.stage(
            RELEASE_ID,
            self.config,
            release_archive(environment=valid_environment("new")),
        )

        def run_command(command: list[str], _cwd: Path) -> None:
            if "up" in command:
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
            (self.config.release_root / "last-successful-run").read_text(
                encoding="utf-8"
            ),
            "20\n",
        )

    def test_deploy_reports_when_configuration_restoration_fails(self) -> None:
        self.config.app_dir.mkdir()
        self.config.rollback_root.mkdir()
        (self.config.app_dir / "docker-compose-prod.yml").write_bytes(
            b"services:\n  old:\n    image: busybox\n"
        )
        (self.config.app_dir / ".env").write_bytes(valid_environment("old"))
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
