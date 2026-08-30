import json
import os
from pathlib import Path
import stat
import tempfile
import unittest
import unittest.mock as mock

from infra.deploy import install_commonex_deploy as installer


TOOL_SHA = "0123456789abcdef0123456789abcdef01234567"
NEXT_TOOL_SHA = "89abcdef0123456789abcdef0123456789abcdef"


class InstallCommonExDeployTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)
        self.bundle = self.root / "bundle"
        self.bundle.mkdir()
        (self.bundle / "commonex_deploy.py").write_text(
            "#!/usr/bin/env python3\nprint('tool')\n", encoding="utf-8"
        )
        package = self.bundle / "commonex_host"
        package.mkdir()
        (package / "__init__.py").write_text("", encoding="utf-8")
        self.layout = installer.InstallLayout.under(self.root / "host")

    def test_install_is_a_dry_run_unless_apply_is_explicit(self) -> None:
        result = installer.install_version(
            self.bundle,
            TOOL_SHA,
            self.layout,
            apply=False,
            require_root=False,
        )

        self.assertEqual(result["status"], "planned")
        self.assertFalse(self.layout.base.exists())

    def test_install_rejects_non_repository_git_sha(self) -> None:
        with self.assertRaisesRegex(installer.InstallError, "Git SHA"):
            installer.install_version(
                self.bundle,
                "main",
                self.layout,
                apply=False,
                require_root=False,
            )

    def test_install_rejects_bundle_without_stable_module(self) -> None:
        (self.bundle / "commonex_deploy.py").unlink()

        with self.assertRaisesRegex(installer.InstallError, "commonex_deploy.py"):
            installer.install_version(
                self.bundle,
                TOOL_SHA,
                self.layout,
                apply=False,
                require_root=False,
            )

    def test_install_rejects_symlinks_in_bundle(self) -> None:
        try:
            (self.bundle / "unsafe").symlink_to(self.bundle / "commonex_deploy.py")
        except OSError:
            self.skipTest("symlinks are unavailable")

        with self.assertRaisesRegex(installer.InstallError, "symlink"):
            installer.install_version(
                self.bundle,
                TOOL_SHA,
                self.layout,
                apply=False,
                require_root=False,
            )

    def test_stage_rejects_tool_with_missing_runtime_import(self) -> None:
        (self.bundle / "commonex_deploy.py").write_text(
            "import missing_commonex_runtime\n",
            encoding="utf-8",
        )

        with self.assertRaisesRegex(installer.InstallError, "import smoke test"):
            installer.stage_version(
                self.bundle, TOOL_SHA, self.layout, require_root=False
            )

    def test_stage_cannot_borrow_commonex_host_from_repository_checkout(self) -> None:
        (self.bundle / "commonex_host" / "__init__.py").unlink()
        (self.bundle / "commonex_host").rmdir()
        (self.bundle / "commonex_deploy.py").write_text(
            "try:\n"
            "    from infra.deploy.commonex_host import activation\n"
            "except ModuleNotFoundError as error:\n"
            "    if error.name != 'infra':\n"
            "        raise\n"
            "    from commonex_host import activation\n",
            encoding="utf-8",
        )

        with self.assertRaisesRegex(installer.InstallError, "import smoke test"):
            installer.stage_version(
                self.bundle, TOOL_SHA, self.layout, require_root=False
            )

    def test_stage_copies_and_verifies_version_without_switching_authority(self) -> None:
        result = installer.stage_version(
            self.bundle, TOOL_SHA, self.layout, require_root=False
        )

        installed = self.layout.versions / TOOL_SHA
        self.assertEqual(result["status"], "staged")
        self.assertEqual(
            (installed / "commonex_deploy.py").read_text(encoding="utf-8"),
            (self.bundle / "commonex_deploy.py").read_text(encoding="utf-8"),
        )
        self.assertFalse(self.layout.current.exists())
        self.assertFalse(self.layout.entrypoint.exists())
        manifest = json.loads(
            (installed / ".tool-install.json").read_text(encoding="utf-8")
        )
        self.assertEqual(manifest["tool_git_sha"], TOOL_SHA)

    @unittest.skipUnless(os.name == "posix", "POSIX permission semantics")
    def test_stage_rejects_group_writable_non_python_bundle_file(self) -> None:
        data = self.bundle / "release-images.json"
        data.write_text("{}\n", encoding="utf-8")
        data.chmod(0o664)

        with self.assertRaisesRegex(installer.InstallError, "group/world-writable"):
            installer.stage_version(
                self.bundle,
                TOOL_SHA,
                self.layout,
                require_root=False,
            )

    @unittest.skipUnless(os.name == "posix", "POSIX permission semantics")
    def test_stage_rejects_group_writable_bundle_directory(self) -> None:
        package = self.bundle / "commonex_host"
        package.chmod(0o775)
        self.addCleanup(package.chmod, 0o755)

        with self.assertRaisesRegex(installer.InstallError, "group/world-writable"):
            installer.stage_version(
                self.bundle,
                TOOL_SHA,
                self.layout,
                require_root=False,
            )

    @unittest.skipUnless(os.name == "posix", "POSIX permission semantics")
    def test_stage_rejects_a_group_writable_bundle_root(self) -> None:
        self.bundle.chmod(0o775)
        self.addCleanup(self.bundle.chmod, 0o755)

        with self.assertRaisesRegex(installer.InstallError, "group/world-writable"):
            installer.stage_version(
                self.bundle,
                TOOL_SHA,
                self.layout,
                require_root=False,
            )

    def test_install_rejects_a_symlink_inside_ignored_bytecode_cache(self) -> None:
        cache = self.bundle / "__pycache__"
        cache.mkdir()
        try:
            (cache / "ignored.pyc").symlink_to(self.bundle / "commonex_deploy.py")
        except OSError:
            self.skipTest("symlinks are unavailable")

        with self.assertRaisesRegex(installer.InstallError, "symlink"):
            installer.install_version(
                self.bundle,
                TOOL_SHA,
                self.layout,
                apply=False,
                require_root=False,
            )

    @unittest.skipUnless(os.name == "posix", "POSIX ownership semantics")
    def test_bundle_trust_check_rejects_a_non_root_owned_path(self) -> None:
        metadata = mock.Mock(
            st_mode=stat.S_IFDIR | 0o755,
            st_uid=1000,
            st_gid=1000,
        )

        with (
            mock.patch.object(Path, "lstat", return_value=metadata),
            self.assertRaisesRegex(installer.InstallError, "not root-owned"),
        ):
            installer._assert_trusted_bundle_path(
                self.bundle,
                ".",
                directory=True,
                enforce_root_ownership=True,
            )

    def test_stage_validates_bundle_ancestors_before_copying_or_importing(self) -> None:
        real_assert = installer._assert_trusted_bundle_path

        def reject_bundle_parent(
            path: Path,
            label: str,
            *,
            directory: object,
            enforce_root_ownership: bool,
        ) -> os.stat_result:
            if path == self.bundle.parent and enforce_root_ownership:
                raise installer.InstallError("tool bundle path is not root-owned")
            return real_assert(
                path,
                label,
                directory=directory,
                enforce_root_ownership=enforce_root_ownership,
            )

        with (
            mock.patch.object(installer, "_require_root"),
            mock.patch.object(
                installer,
                "_assert_trusted_bundle_path",
                side_effect=reject_bundle_parent,
            ),
            mock.patch.object(installer, "_copy_bundle") as copy_bundle,
            mock.patch.object(installer, "_validate_importable_tool") as validate_import,
            self.assertRaisesRegex(
                installer.InstallError,
                "group/world-writable|not root-owned",
            ),
        ):
            installer.stage_version(
                self.bundle,
                TOOL_SHA,
                self.layout,
                require_root=True,
            )

        copy_bundle.assert_not_called()
        validate_import.assert_not_called()

    @unittest.skipUnless(os.name == "posix", "POSIX permission semantics")
    def test_stage_rejects_a_mutable_existing_version_directory(self) -> None:
        installer.stage_version(
            self.bundle,
            TOOL_SHA,
            self.layout,
            require_root=False,
        )
        version = self.layout.versions / TOOL_SHA
        version.chmod(0o775)
        self.addCleanup(version.chmod, 0o755)

        with self.assertRaisesRegex(installer.InstallError, "root-owned and immutable"):
            installer.stage_version(
                self.bundle,
                TOOL_SHA,
                self.layout,
                require_root=False,
            )

    @unittest.skipUnless(os.name == "posix", "POSIX permission semantics")
    def test_activation_rejects_a_mutable_versions_ancestor(self) -> None:
        installer.stage_version(
            self.bundle,
            TOOL_SHA,
            self.layout,
            require_root=False,
        )
        self.layout.versions.chmod(0o775)
        self.addCleanup(self.layout.versions.chmod, 0o755)

        with self.assertRaisesRegex(installer.InstallError, "root-owned and immutable"):
            installer.activate_version(
                TOOL_SHA,
                self.layout,
                require_root=False,
            )

    @unittest.skipUnless(os.name == "posix", "POSIX permission semantics")
    def test_activation_rejects_a_mutable_install_manifest(self) -> None:
        installer.stage_version(
            self.bundle,
            TOOL_SHA,
            self.layout,
            require_root=False,
        )
        manifest = self.layout.versions / TOOL_SHA / installer.INSTALL_MANIFEST
        manifest.chmod(0o664)
        self.addCleanup(manifest.chmod, 0o644)

        with self.assertRaisesRegex(installer.InstallError, "root-owned and immutable"):
            installer.activate_version(
                TOOL_SHA,
                self.layout,
                require_root=False,
            )

    @unittest.skipUnless(os.name == "posix", "POSIX permission semantics")
    def test_activation_rejects_a_mutable_previous_version(self) -> None:
        previous_sha = "f" * 40
        installer.stage_version(
            self.bundle,
            previous_sha,
            self.layout,
            require_root=False,
        )
        previous = self.layout.versions / previous_sha
        self.layout.current.symlink_to(
            "versions/" + previous_sha,
            target_is_directory=True,
        )
        installer.stage_version(
            self.bundle,
            TOOL_SHA,
            self.layout,
            require_root=False,
        )
        previous.chmod(0o775)
        self.addCleanup(previous.chmod, 0o755)

        with self.assertRaisesRegex(installer.InstallError, "root-owned and immutable"):
            installer.activate_version(
                TOOL_SHA,
                self.layout,
                require_root=False,
            )

        self.assertEqual(os.readlink(self.layout.current), "versions/" + previous_sha)

    @unittest.skipUnless(os.name == "posix", "POSIX ownership semantics")
    def test_trust_check_rejects_a_non_root_owned_path(self) -> None:
        metadata = mock.Mock(
            st_mode=stat.S_IFREG | 0o644,
            st_uid=1000,
            st_gid=0,
        )

        with (
            mock.patch.object(Path, "lstat", return_value=metadata),
            self.assertRaisesRegex(installer.InstallError, "root-owned and immutable"),
        ):
            installer._assert_trusted_install_path(
                self.root / "tool.py",
                directory=False,
                enforce_root_ownership=True,
            )

    def test_activation_atomically_selects_version_and_retains_rollback_input(self) -> None:
        self.layout.entrypoint.parent.mkdir(parents=True)
        self.layout.entrypoint.write_text("old command\n", encoding="utf-8")
        installer.stage_version(
            self.bundle, TOOL_SHA, self.layout, require_root=False
        )

        result = installer.activate_version(
            TOOL_SHA, self.layout, require_root=False
        )

        self.assertEqual(self.layout.current.resolve(), self.layout.versions / TOOL_SHA)
        launcher = self.layout.entrypoint.read_text(encoding="utf-8")
        self.assertIn(str(self.layout.current / "commonex_deploy.py"), launcher)
        rollback = Path(result["rollback_directory"])
        self.assertEqual((rollback / "entrypoint").read_text(), "old command\n")
        self.assertTrue((rollback / "activation.json").is_file())

    def test_activation_retries_rollback_sync_before_switching_authority(
        self,
    ) -> None:
        installer.stage_version(
            self.bundle,
            TOOL_SHA,
            self.layout,
            require_root=False,
        )
        real_fsync_directory = installer._fsync_directory

        def fail_rollback_sync(path: Path) -> None:
            if path == self.layout.base:
                raise OSError("rollback root fsync failure")
            real_fsync_directory(path)

        with (
            mock.patch.object(
                installer,
                "_fsync_directory",
                side_effect=fail_rollback_sync,
            ),
            mock.patch.object(installer, "_replace_current") as replace_current,
            self.assertRaisesRegex(OSError, "rollback root fsync failure"),
        ):
            installer.activate_version(
                TOOL_SHA,
                self.layout,
                require_root=False,
            )

        replace_current.assert_not_called()
        self.assertFalse(self.layout.current.exists())
        self.assertFalse(self.layout.entrypoint.exists())

        events = []
        real_replace_current = installer._replace_current

        def record_fsync(path: Path) -> None:
            events.append(("fsync", path))
            real_fsync_directory(path)

        def record_replace(*args: object, **kwargs: object) -> None:
            events.append(("replace_current", self.layout.current))
            real_replace_current(*args, **kwargs)

        with (
            mock.patch.object(
                installer,
                "_fsync_directory",
                side_effect=record_fsync,
            ),
            mock.patch.object(
                installer,
                "_replace_current",
                side_effect=record_replace,
            ),
        ):
            installer.activate_version(
                TOOL_SHA,
                self.layout,
                require_root=False,
            )

        switch_index = events.index(("replace_current", self.layout.current))
        self.assertLess(events.index(("fsync", self.layout.base)), switch_index)
        self.assertLess(events.index(("fsync", self.layout.rollbacks)), switch_index)

    def test_activation_restores_both_authority_files_after_entrypoint_fsync_fault(
        self,
    ) -> None:
        self.layout.entrypoint.parent.mkdir(parents=True)
        self.layout.entrypoint.write_text("old command\n", encoding="utf-8")
        installer.stage_version(
            self.bundle,
            "f" * 40,
            self.layout,
            require_root=False,
        )
        prior = self.layout.versions / ("f" * 40)
        self.layout.current.symlink_to(
            "versions/" + "f" * 40,
            target_is_directory=True,
        )
        installer.stage_version(
            self.bundle, TOOL_SHA, self.layout, require_root=False
        )
        real_atomic_write = installer._atomic_write
        failed = False

        def fail_after_entrypoint_replace(path: Path, content: bytes, mode: int) -> None:
            nonlocal failed
            real_atomic_write(path, content, mode)
            if path == self.layout.entrypoint and not failed:
                failed = True
                raise OSError("post-replace entrypoint fsync failure")

        with (
            mock.patch.object(
                installer,
                "_atomic_write",
                side_effect=fail_after_entrypoint_replace,
            ),
            self.assertRaisesRegex(OSError, "entrypoint fsync failure"),
        ):
            installer.activate_version(
                TOOL_SHA,
                self.layout,
                require_root=False,
            )

        self.assertTrue(failed)
        self.assertEqual(self.layout.entrypoint.read_text(), "old command\n")
        self.assertEqual(self.layout.current.resolve(), prior)

    def test_activation_restores_prior_selector_after_selector_fsync_fault(
        self,
    ) -> None:
        self.layout.entrypoint.parent.mkdir(parents=True)
        self.layout.entrypoint.write_text("old command\n", encoding="utf-8")
        installer.stage_version(
            self.bundle,
            "f" * 40,
            self.layout,
            require_root=False,
        )
        self.layout.current.symlink_to(
            "versions/" + "f" * 40,
            target_is_directory=True,
        )
        installer.stage_version(
            self.bundle,
            TOOL_SHA,
            self.layout,
            require_root=False,
        )
        real_fsync_directory = installer._fsync_directory
        failed = False

        def fail_after_selector_replace(path: Path) -> None:
            nonlocal failed
            if (
                path == self.layout.current.parent
                and not failed
                and self.layout.current.is_symlink()
                and os.readlink(self.layout.current) == "versions/" + TOOL_SHA
            ):
                failed = True
                raise OSError("post-replace selector fsync failure")
            real_fsync_directory(path)

        with (
            mock.patch.object(
                installer,
                "_fsync_directory",
                side_effect=fail_after_selector_replace,
            ),
            self.assertRaisesRegex(OSError, "selector fsync failure"),
        ):
            installer.activate_version(
                TOOL_SHA,
                self.layout,
                require_root=False,
            )

        self.assertTrue(failed)
        self.assertEqual(os.readlink(self.layout.current), "versions/" + "f" * 40)
        self.assertEqual(self.layout.entrypoint.read_text(), "old command\n")

    def test_activation_removes_new_selector_after_selector_fsync_fault(self) -> None:
        installer.stage_version(
            self.bundle,
            TOOL_SHA,
            self.layout,
            require_root=False,
        )
        real_fsync_directory = installer._fsync_directory
        failed = False

        def fail_after_selector_replace(path: Path) -> None:
            nonlocal failed
            if (
                path == self.layout.current.parent
                and not failed
                and self.layout.current.is_symlink()
            ):
                failed = True
                raise OSError("post-replace selector fsync failure")
            real_fsync_directory(path)

        with (
            mock.patch.object(
                installer,
                "_fsync_directory",
                side_effect=fail_after_selector_replace,
            ),
            self.assertRaisesRegex(OSError, "selector fsync failure"),
        ):
            installer.activate_version(
                TOOL_SHA,
                self.layout,
                require_root=False,
            )

        self.assertTrue(failed)
        self.assertFalse(self.layout.current.exists())
        self.assertFalse(self.layout.current.is_symlink())
        self.assertFalse(self.layout.entrypoint.exists())

    def test_activation_rejects_a_tampered_staged_version(self) -> None:
        installer.stage_version(
            self.bundle, TOOL_SHA, self.layout, require_root=False
        )
        (self.layout.versions / TOOL_SHA / "commonex_deploy.py").write_text(
            "print('tampered')\n", encoding="utf-8"
        )

        with self.assertRaisesRegex(installer.InstallError, "content verification"):
            installer.activate_version(TOOL_SHA, self.layout, require_root=False)

    def test_install_rolls_back_to_previous_entrypoint_and_current_version(self) -> None:
        installer.install_version(
            self.bundle,
            TOOL_SHA,
            self.layout,
            apply=True,
            require_root=False,
        )
        (self.bundle / "commonex_deploy.py").write_text(
            "#!/usr/bin/env python3\nprint('next')\n", encoding="utf-8"
        )
        result = installer.install_version(
            self.bundle,
            NEXT_TOOL_SHA,
            self.layout,
            apply=True,
            require_root=False,
        )

        installer.rollback_activation(
            Path(result["rollback_directory"]),
            self.layout,
            apply=True,
            require_root=False,
        )

        self.assertEqual(self.layout.current.resolve(), self.layout.versions / TOOL_SHA)
        self.assertIn(
            str(self.layout.current / "commonex_deploy.py"),
            self.layout.entrypoint.read_text(encoding="utf-8"),
        )
        self.assertTrue((self.layout.versions / NEXT_TOOL_SHA).is_dir())

    def test_rollback_validates_entrypoint_backup_before_switching_current(self) -> None:
        installer.install_version(
            self.bundle,
            TOOL_SHA,
            self.layout,
            apply=True,
            require_root=False,
        )
        (self.bundle / "commonex_deploy.py").write_text(
            "#!/usr/bin/env python3\nprint('next')\n", encoding="utf-8"
        )
        result = installer.install_version(
            self.bundle,
            NEXT_TOOL_SHA,
            self.layout,
            apply=True,
            require_root=False,
        )
        rollback = Path(result["rollback_directory"])
        (rollback / "entrypoint").unlink()

        with self.assertRaisesRegex(installer.InstallError, "backup is missing"):
            installer.rollback_activation(
                rollback,
                self.layout,
                apply=True,
                require_root=False,
            )

        self.assertEqual(
            self.layout.current.resolve(), self.layout.versions / NEXT_TOOL_SHA
        )

    def test_rollback_rejects_a_record_outside_the_trusted_root(self) -> None:
        attacker_rollback = self.root / "attacker-rollback"
        attacker_rollback.mkdir()
        (attacker_rollback / "activation.json").write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "new_tool_git_sha": TOOL_SHA,
                    "previous_current_target": None,
                    "entrypoint_existed": False,
                }
            ),
            encoding="utf-8",
        )

        with self.assertRaisesRegex(installer.InstallError, "outside the trusted root"):
            installer.rollback_activation(
                attacker_rollback,
                self.layout,
                apply=False,
                require_root=False,
            )

    @unittest.skipUnless(os.name == "posix", "POSIX permission semantics")
    def test_rollback_requires_exact_activation_record_mode(self) -> None:
        installer.install_version(
            self.bundle,
            TOOL_SHA,
            self.layout,
            apply=True,
            require_root=False,
        )
        (self.bundle / "commonex_deploy.py").write_text(
            "#!/usr/bin/env python3\nprint('next')\n",
            encoding="utf-8",
        )
        result = installer.install_version(
            self.bundle,
            NEXT_TOOL_SHA,
            self.layout,
            apply=True,
            require_root=False,
        )
        activation_record = Path(result["rollback_directory"]) / "activation.json"
        activation_record.chmod(0o644)
        self.addCleanup(activation_record.chmod, 0o600)

        with self.assertRaisesRegex(installer.InstallError, "trusted regular file"):
            installer.rollback_activation(
                Path(result["rollback_directory"]),
                self.layout,
                apply=False,
                require_root=False,
            )

        self.assertEqual(
            self.layout.current.resolve(),
            self.layout.versions / NEXT_TOOL_SHA,
        )

    def test_rollback_rejects_a_symlinked_slot(self) -> None:
        installer.install_version(
            self.bundle,
            TOOL_SHA,
            self.layout,
            apply=True,
            require_root=False,
        )
        real_rollback = self.layout.rollbacks / "real"
        real_rollback.mkdir(mode=0o700)
        alias = self.layout.rollbacks / "alias"
        try:
            alias.symlink_to(real_rollback, target_is_directory=True)
        except OSError as error:
            self.skipTest(f"directory symlinks are unavailable: {error}")

        with self.assertRaisesRegex(installer.InstallError, "root-owned and immutable"):
            installer.rollback_activation(
                alias,
                self.layout,
                apply=False,
                require_root=False,
            )

    def test_rollback_rejects_boolean_schema_version_during_plan(self) -> None:
        result = installer.install_version(
            self.bundle,
            TOOL_SHA,
            self.layout,
            apply=True,
            require_root=False,
        )
        rollback = Path(result["rollback_directory"])
        activation_path = rollback / "activation.json"
        activation = json.loads(activation_path.read_text(encoding="utf-8"))
        activation["schema_version"] = True
        activation_path.write_text(json.dumps(activation), encoding="utf-8")

        with self.assertRaisesRegex(installer.InstallError, "unsupported"):
            installer.rollback_activation(
                rollback,
                self.layout,
                apply=False,
                require_root=False,
            )

    def test_rollback_restores_new_authority_after_selector_fsync_fault(self) -> None:
        installer.install_version(
            self.bundle,
            TOOL_SHA,
            self.layout,
            apply=True,
            require_root=False,
        )
        (self.bundle / "commonex_deploy.py").write_text(
            "#!/usr/bin/env python3\nprint('next')\n", encoding="utf-8"
        )
        result = installer.install_version(
            self.bundle,
            NEXT_TOOL_SHA,
            self.layout,
            apply=True,
            require_root=False,
        )
        entrypoint_before = self.layout.entrypoint.read_bytes()
        real_fsync_directory = installer._fsync_directory
        failed = False

        def fail_after_selector_replace(path: Path) -> None:
            nonlocal failed
            if (
                path == self.layout.current.parent
                and not failed
                and self.layout.current.is_symlink()
                and os.readlink(self.layout.current) == "versions/" + TOOL_SHA
            ):
                failed = True
                raise OSError("post-replace selector fsync failure")
            real_fsync_directory(path)

        with (
            mock.patch.object(
                installer,
                "_fsync_directory",
                side_effect=fail_after_selector_replace,
            ),
            self.assertRaisesRegex(OSError, "selector fsync failure"),
        ):
            installer.rollback_activation(
                Path(result["rollback_directory"]),
                self.layout,
                apply=True,
                require_root=False,
            )

        self.assertTrue(failed)
        self.assertEqual(
            os.readlink(self.layout.current), "versions/" + NEXT_TOOL_SHA
        )
        self.assertEqual(self.layout.entrypoint.read_bytes(), entrypoint_before)


if __name__ == "__main__":
    unittest.main()
