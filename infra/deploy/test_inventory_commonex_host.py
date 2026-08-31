import contextlib
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
import unittest.mock as mock

from infra.deploy import inventory_commonex_host as inventory


RELEASE_ID = "0123456789abcdef0123456789abcdef01234567"
IMAGE_REFERENCES = {
    "COMMONEX_BACKEND_IMAGE": "ruggedbl/commonex-nest-backend@sha256:" + "a" * 64,
    "COMMONEX_FRONTEND_IMAGE": "ruggedbl/commonex-next-web@sha256:" + "b" * 64,
    "COMMONEX_NGINX_IMAGE": "ruggedbl/nginx-http3@sha256:" + "c" * 64,
    "COMMONEX_OTEL_COLLECTOR_IMAGE": (
        "ruggedbl/opentelemetry-collector-custom@sha256:" + "d" * 64
    ),
}


CANONICAL_EFFECTIVE_DEFAULTS = (
    "env_reset",
    "mail_badpass",
    "secure_path=/usr/local/sbin\\:/usr/local/bin\\:/usr/sbin\\:/usr/bin\\:/sbin\\:/bin\\:/snap/bin",
    "use_pty",
    'env_keep="COLORS DISPLAY HOSTNAME HISTSIZE KDEDIR LS_COLORS"',
    'env_keep+="MAIL PS1 PS2 QTDIR USERNAME LANG LC_ALL LANGUAGE XAUTHORITY"',
    "env_reset",
    "secure_path=/usr/local/sbin\\:/usr/local/bin\\:/usr/sbin\\:/usr/bin\\:/sbin\\:/bin",
    "env_keep=SSH_ORIGINAL_COMMAND",
)


def sudo_list_output(
    *grants: str, defaults: tuple[str, ...] = CANONICAL_EFFECTIVE_DEFAULTS
) -> bytes:
    return (
        "Matching Defaults entries for commonex-deploy on production:\n"
        f"    {', '.join(defaults)}\n\n"
        "User commonex-deploy may run the following commands on production:\n"
        + "".join(f"    {grant}\n" for grant in grants)
    ).encode()


def effective_sudo_runner(
    *grants: str,
    returncode: int = 0,
    defaults: tuple[str, ...] = CANONICAL_EFFECTIVE_DEFAULTS,
) -> object:
    effective_grants = grants or (inventory.EXPECTED_EFFECTIVE_SUDO_GRANT,)

    def run(command: object, **_kwargs: object) -> object:
        return inventory.subprocess.CompletedProcess(
            command,
            returncode,
            stdout=sudo_list_output(*effective_grants, defaults=defaults),
            stderr=b"sudo diagnostic secret",
        )

    return run


def valid_environment(redaction_marker: str = "do-not-print") -> str:
    values = {
        "POSTGRES_PORT": "5432",
        "POSTGRES_USER_NAME": "postgres",
        "POSTGRES_PASSWORD": redaction_marker,
        "POSTGRES_DATABASE": "postgres",
        "POSTGRES_HOST": "db",
        "POSTGRES_SCHEMA": "public",
        "OPEN_EXCHANGE_RATES_API_ID": "exchange-secret",
        "DEVTOOLS_SECRET": "devtools-secret",
        "GF_SECURITY_ADMIN_USER": "admin",
        "GF_SECURITY_ADMIN_PASSWORD": "grafana-secret",
        **IMAGE_REFERENCES,
    }
    return "".join(f"{key}={value}\n" for key, value in values.items())


class InventoryCommonExHostTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)

    def config(self, *, max_entries: int = 100, max_hash_bytes: int = 1024) -> inventory.InventoryConfig:
        return inventory.InventoryConfig(
            targets=(
                inventory.InventoryTarget("configuration", self.root / "etc", True),
                inventory.InventoryTarget("state", self.root / "state", True),
                inventory.InventoryTarget("audit", self.root / "deploy.log"),
            ),
            activation_state_paths=(self.root / "state" / "activation-state.json",),
            activation_intent_paths=(self.root / "state" / "activation-intent.json",),
            legacy_run_paths=(self.root / "state" / "last-successful-run",),
            max_entries=max_entries,
            max_hash_bytes=max_hash_bytes,
        )

    def with_targets(
        self, config: inventory.InventoryConfig, *targets: inventory.InventoryTarget
    ) -> inventory.InventoryConfig:
        return inventory.InventoryConfig(
            targets=(*config.targets, *targets),
            activation_state_paths=config.activation_state_paths,
            activation_intent_paths=config.activation_intent_paths,
            legacy_run_paths=config.legacy_run_paths,
            max_entries=config.max_entries,
            max_hash_bytes=config.max_hash_bytes,
            operation_lock_paths=config.operation_lock_paths,
            required_target_names=config.required_target_names,
        )

    def test_inventory_reports_missing_and_present_paths_without_file_contents(self) -> None:
        configuration = self.root / "etc"
        configuration.mkdir()
        expected_sensitive_line = "POSTGRES_PASSWORD=do-not-print"
        environment = configuration / ".env"
        environment.write_text(valid_environment(), encoding="utf-8")

        report = inventory.collect_inventory(self.config(), generated_at="2026-08-30T00:00:00+00:00")
        serialized = inventory.serialize_report(report)

        self.assertNotIn(expected_sensitive_line, serialized)
        self.assertNotIn("do-not-print", serialized)
        self.assertEqual(report["schema_version"], 1)
        self.assertEqual(report["generated_at"], "2026-08-30T00:00:00+00:00")
        self.assertFalse(report["migration_blocked"])
        self.assertEqual(
            report["targets"][0]["entries"][0]["sha256"],
            hashlib.sha256(valid_environment().encode()).hexdigest(),
        )
        summary = report["environment_files"][0]
        self.assertTrue(summary["valid"])
        self.assertEqual(summary["image_references"], IMAGE_REFERENCES)
        self.assertIn("POSTGRES_PASSWORD", summary["keys"])
        self.assertFalse(report["targets"][1]["exists"])
        self.assertFalse(report["targets"][2]["exists"])

    def test_inventory_summarizes_valid_activation_documents(self) -> None:
        state = self.root / "state"
        state.mkdir()
        (state / "activation-state.json").write_text(
            json.dumps({"last_successful_run": 42, "history": [RELEASE_ID]}) + "\n",
            encoding="utf-8",
        )
        (state / "activation-intent.json").write_text(
            json.dumps(
                {
                    "candidate_release": RELEASE_ID,
                    "operation": "deploy",
                    "previous_release": None,
                    "rollback_backup": f"deploy-{RELEASE_ID}-20260830T000000000000Z",
                    "run_number": 43,
                }
            )
            + "\n",
            encoding="utf-8",
        )
        (state / "last-successful-run").write_text("41\n", encoding="ascii")

        report = inventory.collect_inventory(self.config(), generated_at="now")

        self.assertTrue(report["migration_blocked"])
        self.assertIn("activation_intent_present", report["blockers"])
        self.assertEqual(
            report["activation_states"],
            [
                {
                    "path": str(state / "activation-state.json"),
                    "exists": True,
                    "valid": True,
                    "last_successful_run": 42,
                    "history": [RELEASE_ID],
                }
            ],
        )
        self.assertEqual(report["activation_intents"][0]["operation"], "deploy")
        self.assertEqual(
            report["activation_intents"][0]["candidate_release"], RELEASE_ID
        )
        self.assertEqual(report["legacy_runs"][0]["run_number"], 41)

    def test_inventory_rejects_run_numbers_longer_than_twenty_digits(self) -> None:
        state = self.root / "state"
        state.mkdir()
        oversized = 10**20
        activation_state = state / "activation-state.json"
        activation_state.write_text(
            json.dumps({"last_successful_run": oversized, "history": [RELEASE_ID]})
            + "\n",
            encoding="utf-8",
        )
        activation_intent = state / "activation-intent.json"
        activation_intent.write_text(
            json.dumps(
                {
                    "candidate_release": RELEASE_ID,
                    "operation": "deploy",
                    "previous_release": None,
                    "rollback_backup": f"deploy-{RELEASE_ID}-20260830T000000000000Z",
                    "run_number": oversized,
                }
            )
            + "\n",
            encoding="utf-8",
        )
        legacy_run = state / "last-successful-run"
        legacy_run.write_text(f"{oversized}\n", encoding="ascii")

        self.assertFalse(
            inventory._activation_state_summary(activation_state)["valid"]
        )
        self.assertFalse(
            inventory._activation_intent_summary(activation_intent)["valid"]
        )
        self.assertFalse(inventory._legacy_run_summary(legacy_run)["valid"])

    def test_default_inventory_synchronizes_consolidated_operation_lock(self) -> None:
        self.assertIn(
            Path("/etc/commonex/deploy.lock"),
            inventory.DEFAULT_CONFIG.operation_lock_paths,
        )

    def test_invalid_state_is_reported_without_echoing_untrusted_content(self) -> None:
        state = self.root / "state"
        state.mkdir()
        untrusted = 'secret-value-{not-json}'
        (state / "activation-state.json").write_text(untrusted, encoding="utf-8")

        report = inventory.collect_inventory(self.config(), generated_at="now")
        serialized = inventory.serialize_report(report)

        self.assertNotIn(untrusted, serialized)
        self.assertEqual(
            report["activation_states"][0],
            {
                "path": str(state / "activation-state.json"),
                "exists": True,
                "valid": False,
                "error": "invalid_activation_state",
            },
        )

    def test_hash_and_entry_limits_are_explicit(self) -> None:
        configuration = self.root / "etc"
        configuration.mkdir()
        (configuration / "large").write_bytes(b"12345")
        (configuration / "small").write_bytes(b"1")

        report = inventory.collect_inventory(
            self.config(max_entries=1, max_hash_bytes=4), generated_at="now"
        )

        target = report["targets"][0]
        self.assertTrue(target["truncated"])
        self.assertEqual(len(target["entries"]), 1)
        self.assertEqual(target["entries"][0]["hash_status"], "omitted_size_limit")
        self.assertTrue(report["migration_blocked"])
        self.assertEqual(report["status"], "incomplete")
        self.assertTrue(report["inventory_issues"])

    def test_symlink_is_recorded_without_following_it(self) -> None:
        configuration = self.root / "etc"
        configuration.mkdir()
        outside = self.root / "outside"
        outside.mkdir()
        (outside / "secret").write_text("not-visible", encoding="utf-8")
        link = configuration / "linked"
        try:
            link.symlink_to(outside, target_is_directory=True)
        except OSError as error:
            self.skipTest(f"symlinks unavailable: {error}")

        report = inventory.collect_inventory(self.config(), generated_at="now")

        entries = report["targets"][0]["entries"]
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0]["kind"], "symlink")
        self.assertTrue(str(entries[0]["link_target"]).endswith(str(outside)))
        self.assertNotIn("not-visible", inventory.serialize_report(report))

    def test_intent_is_discovered_recursively_outside_configured_paths(self) -> None:
        unexpected = self.root / "etc" / "deploy" / "state"
        unexpected.mkdir(parents=True)
        (unexpected / "activation-intent.json").write_text(
            json.dumps(
                {
                    "candidate_release": RELEASE_ID,
                    "operation": "rollback",
                    "previous_release": RELEASE_ID,
                    "rollback_backup": f"deploy-{RELEASE_ID}-20260830T000000000000Z",
                    "run_number": 44,
                }
            )
            + "\n",
            encoding="utf-8",
        )

        report = inventory.collect_inventory(self.config(), generated_at="now")

        summaries = [
            item
            for item in report["activation_intents"]
            if item["path"] == str(unexpected / "activation-intent.json")
        ]
        self.assertEqual(len(summaries), 1)
        self.assertTrue(summaries[0]["valid"])
        self.assertTrue(report["migration_blocked"])
        self.assertIn("activation_intent_present", report["blockers"])

    def test_cli_outputs_deterministic_json_and_requires_root(self) -> None:
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            status = inventory.main(
                self.config(), geteuid=lambda: 0, generated_at=lambda: "now"
            )

        self.assertEqual(status, 0)
        self.assertEqual(json.loads(output.getvalue())["generated_at"], "now")
        self.assertEqual(json.loads(output.getvalue())["status"], "complete")
        self.assertTrue(output.getvalue().endswith("\n"))

        with self.assertRaisesRegex(PermissionError, "must run as root"):
            inventory.main(self.config(), geteuid=lambda: 1000, generated_at=lambda: "now")

    def test_cli_failure_is_non_secret_bearing(self) -> None:
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            status = inventory.run_cli(
                self.config(), geteuid=lambda: 1000, generated_at=lambda: "secret-time"
            )

        self.assertEqual(status, 1)
        self.assertEqual(
            stderr.getvalue(),
            "commonex-host-inventory: unable to collect inventory\n",
        )
        self.assertNotIn("secret-time", stderr.getvalue())

    def test_cli_returns_two_for_a_complete_but_blocked_report(self) -> None:
        state = self.root / "state"
        state.mkdir()
        (state / "activation-intent.json").write_text("{}\n", encoding="utf-8")
        output = io.StringIO()

        with contextlib.redirect_stdout(output):
            status = inventory.run_cli(
                self.config(), geteuid=lambda: 0, generated_at=lambda: "now"
            )

        self.assertEqual(status, 2)
        self.assertEqual(json.loads(output.getvalue())["status"], "blocked")

    def test_cli_returns_one_for_an_incomplete_report(self) -> None:
        configuration = self.root / "etc"
        configuration.mkdir()
        (configuration / "large").write_bytes(b"12345")
        output = io.StringIO()

        with contextlib.redirect_stdout(output):
            status = inventory.run_cli(
                self.config(max_hash_bytes=4),
                geteuid=lambda: 0,
                generated_at=lambda: "now",
            )

        self.assertEqual(status, 1)
        self.assertEqual(json.loads(output.getvalue())["status"], "incomplete")

    def test_collection_does_not_change_fixture_tree(self) -> None:
        configuration = self.root / "etc"
        configuration.mkdir()
        file = configuration / "config"
        file.write_bytes(b"value")

        before = {
            path.relative_to(self.root).as_posix(): (
                path.lstat().st_mode,
                path.lstat().st_size,
                path.read_bytes() if path.is_file() else None,
            )
            for path in [self.root, configuration, file]
        }
        inventory.collect_inventory(self.config(), generated_at="now")
        after = {
            path.relative_to(self.root).as_posix(): (
                path.lstat().st_mode,
                path.lstat().st_size,
                path.read_bytes() if path.is_file() else None,
            )
            for path in [self.root, configuration, file]
        }

        self.assertEqual(after, before)

    def test_hardlinked_file_makes_inventory_incomplete(self) -> None:
        configuration = self.root / "etc"
        configuration.mkdir()
        source = configuration / "source"
        source.write_bytes(b"value")
        linked = configuration / "linked"
        try:
            os.link(source, linked)
        except OSError as error:
            self.skipTest(f"hardlinks unavailable: {error}")

        report = inventory.collect_inventory(self.config(), generated_at="now")

        self.assertEqual(report["status"], "incomplete")
        self.assertTrue(
            any(issue.startswith("hardlinked_file:") for issue in report["inventory_issues"])
        )

    @unittest.skipUnless(os.name == "posix", "POSIX special files")
    def test_fifo_makes_inventory_incomplete_without_reading_it(self) -> None:
        configuration = self.root / "etc"
        configuration.mkdir()
        os.mkfifo(configuration / "pipe")

        report = inventory.collect_inventory(self.config(), generated_at="now")

        self.assertEqual(report["status"], "incomplete")
        self.assertTrue(
            any(":fifo" in issue for issue in report["inventory_issues"])
        )

    @unittest.skipUnless(os.name == "posix", "POSIX lock semantics")
    def test_existing_operation_lock_is_held_without_creating_missing_lock(self) -> None:
        import fcntl

        existing = self.root / "existing.lock"
        existing.write_bytes(b"")
        missing = self.root / "missing.lock"
        config = self.config()
        config = inventory.InventoryConfig(
            targets=config.targets,
            activation_state_paths=config.activation_state_paths,
            activation_intent_paths=config.activation_intent_paths,
            legacy_run_paths=config.legacy_run_paths,
            max_entries=config.max_entries,
            max_hash_bytes=config.max_hash_bytes,
            operation_lock_paths=(existing, missing),
            required_target_names=config.required_target_names,
        )

        with inventory._held_operation_locks(config.operation_lock_paths) as checks:
            self.assertEqual(
                checks,
                [
                    {"path": str(existing), "exists": True, "status": "held_shared"},
                    {"path": str(missing), "exists": False, "status": "missing"},
                ],
            )
            descriptor = os.open(existing, os.O_RDONLY)
            try:
                with self.assertRaises(BlockingIOError):
                    fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
            finally:
                os.close(descriptor)

        self.assertFalse(missing.exists())

    def prepare_active_release(self, *, active_compose: bytes = b"services: {}\n") -> None:
        state = self.root / "state"
        release = state / RELEASE_ID
        release.mkdir(parents=True)
        state.chmod(0o700)
        release.chmod(0o700)
        compose = b"services: {}\n"
        environment = valid_environment().encode()
        (release / "docker-compose-prod.yml").write_bytes(compose)
        (release / ".env").write_bytes(environment)
        (release / ".env").chmod(0o600)
        (release / "manifest.sha256").write_text(
            f"{hashlib.sha256(environment).hexdigest()}  .env\n"
            f"{hashlib.sha256(compose).hexdigest()}  docker-compose-prod.yml\n",
            encoding="utf-8",
        )
        (release / "manifest.sha256").chmod(0o600)
        (state / "activation-state.json").write_text(
            json.dumps({"last_successful_run": 42, "history": [RELEASE_ID]}) + "\n",
            encoding="utf-8",
        )
        (state / "activation-state.json").chmod(0o600)
        configuration = self.root / "etc"
        configuration.mkdir()
        active = configuration / "app"
        active.mkdir(mode=0o755)
        (active / "docker-compose-prod.yml").write_bytes(active_compose)
        (active / ".env").write_bytes(environment)
        (active / ".env").chmod(0o600)

    def test_release_and_active_configuration_are_verified_semantically(self) -> None:
        self.prepare_active_release()

        original_metadata = inventory._metadata

        def root_owned_metadata(metadata: os.stat_result) -> dict[str, object]:
            result = original_metadata(metadata)
            result["uid"] = 0
            result["gid"] = 0
            return result

        with mock.patch.object(
            inventory, "_metadata", side_effect=root_owned_metadata
        ):
            report = inventory.collect_inventory(self.config(), generated_at="now")

        self.assertEqual(len(report["releases"]), 1)
        self.assertTrue(report["releases"][0]["valid"])
        self.assertEqual(
            report["active_release_verification"]["status"], "verified"
        )
        self.assertFalse(report["migration_blocked"])

    def test_unretained_release_does_not_block_inventory(self) -> None:
        self.prepare_active_release()
        abandoned_id = "b" * 40
        abandoned = self.root / "state" / abandoned_id
        abandoned.mkdir(mode=0o777)
        abandoned.chmod(0o777)
        (abandoned / "partial").write_text("abandoned\n", encoding="utf-8")
        original_metadata = inventory._metadata

        def root_owned_metadata(metadata: os.stat_result) -> dict[str, object]:
            result = original_metadata(metadata)
            result["uid"] = 0
            result["gid"] = 0
            return result

        with mock.patch.object(
            inventory,
            "_metadata",
            side_effect=root_owned_metadata,
        ):
            report = inventory.collect_inventory(self.config(), generated_at="now")

        releases = {release["release"]: release for release in report["releases"]}
        self.assertTrue(releases[RELEASE_ID]["valid"])
        self.assertFalse(releases[abandoned_id]["valid"])
        self.assertNotIn("invalid_release", report["blockers"])
        self.assertNotIn("incompatible_tool_metadata", report["blockers"])
        self.assertFalse(report["migration_blocked"])

        activation_state = self.root / "state" / "activation-state.json"
        activation_state.write_text(
            json.dumps(
                {
                    "last_successful_run": 42,
                    "history": [RELEASE_ID, abandoned_id],
                }
            )
            + "\n",
            encoding="utf-8",
        )
        with mock.patch.object(
            inventory,
            "_metadata",
            side_effect=root_owned_metadata,
        ):
            retained_report = inventory.collect_inventory(
                self.config(),
                generated_at="now",
            )

        self.assertIn("invalid_release", retained_report["blockers"])
        self.assertIn("incompatible_tool_metadata", retained_report["blockers"])

    def test_consolidated_audit_log_is_discovered_under_configuration(self) -> None:
        self.prepare_active_release()
        audit = self.root / "etc" / "deploy.log"
        audit.write_text(
            "2026-08-30T00:00:00+00:00 RESULT deploy status=PASS\n",
            encoding="utf-8",
        )

        config = self.with_targets(
            self.config(),
            inventory.InventoryTarget(
                "legacy_audit_log", self.root / "missing-legacy-audit.log"
            ),
        )
        report = inventory.collect_inventory(config, generated_at="now")

        self.assertEqual(
            [summary["path"] for summary in report["audit_logs"]], [str(audit)]
        )
        self.assertNotIn("missing_audit_log", report["blockers"])

    def test_active_configuration_mismatch_blocks_migration(self) -> None:
        self.prepare_active_release(active_compose=b"services: {changed: {}}\n")

        report = inventory.collect_inventory(self.config(), generated_at="now")

        self.assertEqual(
            report["active_release_verification"]["status"], "mismatch"
        )
        self.assertTrue(report["migration_blocked"])
        self.assertIn("active_release_unverified", report["blockers"])

    def test_matching_active_configuration_outside_canonical_app_blocks(self) -> None:
        self.prepare_active_release()
        configuration = self.root / "etc"
        app = configuration / "app"
        for name in (".env", "docker-compose-prod.yml"):
            (app / name).replace(configuration / name)
        app.rmdir()

        report = inventory.collect_inventory(self.config(), generated_at="now")

        self.assertIn("active_configuration_not_canonical", report["blockers"])
        self.assertIn("incompatible_tool_metadata", report["blockers"])

    def test_legacy_run_still_requires_bootstrap_configuration_at_canonical_app(
        self,
    ) -> None:
        state = self.root / "state"
        state.mkdir(mode=0o700)
        legacy = state / "last-successful-run"
        legacy.write_bytes(b"7\n")
        legacy.chmod(0o600)
        (self.root / "etc").mkdir()

        report = inventory.collect_inventory(self.config(), generated_at="now")

        self.assertIn("incompatible_tool_metadata", report["blockers"])

    def test_security_and_log_summaries_never_echo_sensitive_material(self) -> None:
        authorized_keys = self.root / "authorized_keys"
        key_data = base64.b64encode(b"public-key-material").decode()
        authorized_keys.write_text(
            'restrict,command="sudo -n /usr/local/sbin/commonex-deploy forced" '
            f"ssh-ed25519 {key_data} authorized-secret-comment-secret\n",
            encoding="utf-8",
        )
        sudo_policy = self.root / "sudo-policy"
        sudo_policy.write_text(inventory.EXPECTED_SUDO_POLICY_TEXT, encoding="utf-8")
        logrotate = self.root / "logrotate"
        logrotate.write_text(
            inventory.EXPECTED_LOGROTATE_POLICY_TEXT,
            encoding="utf-8",
        )
        audit = self.root / "audit.log"
        audit.write_text(
            "2026-08-30T00:00:00+00:00 RESULT deploy status=PASS\n"
            "unrecognized audit-secret\n",
            encoding="utf-8",
        )
        config = self.with_targets(
            self.config(),
            inventory.InventoryTarget("deploy_authorized_keys", authorized_keys),
            inventory.InventoryTarget("sudo_policy", sudo_policy),
            inventory.InventoryTarget("commonex_logrotate_policy", logrotate),
            inventory.InventoryTarget("canonical_audit_log", audit),
        )

        report = inventory.collect_inventory(
            config, generated_at="now", sudo_runner=effective_sudo_runner()
        )
        serialized = inventory.serialize_report(report)

        self.assertTrue(report["security_policies"]["authorized_keys"]["valid"])
        self.assertTrue(report["security_policies"]["sudo"]["valid"])
        self.assertTrue(report["security_policies"]["effective_sudo"]["valid"])
        self.assertTrue(report["logrotate_policy"]["valid"])
        self.assertEqual(report["audit_logs"][0]["unrecognized_line_count"], 1)
        for secret in [
            key_data,
            "authorized-secret",
            "comment-secret",
            "audit-secret",
        ]:
            self.assertNotIn(secret, serialized)

    def test_authorized_key_capability_overrides_and_option_decoys_are_rejected(
        self,
    ) -> None:
        authorized_keys = self.root / "authorized_keys"
        key_data = base64.b64encode(b"public-key-material").decode()
        unsafe_options = [
            "agent-forwarding",
            "port-forwarding",
            "pty",
            "user-rc",
            "X11-forwarding",
            'tunnel="0"',
            'environment="BASH_ENV=/tmp/unsafe"',
            "cert-authority",
            'environment="X=ssh-ed25519 QUJD",port-forwarding',
        ]

        for unsafe_option in unsafe_options:
            with self.subTest(option=unsafe_option):
                authorized_keys.write_text(
                    f"restrict,{unsafe_option},"
                    'command="sudo -n /usr/local/sbin/commonex-deploy forced" '
                    f"ssh-ed25519 {key_data}\n",
                    encoding="utf-8",
                )

                summary = inventory._authorized_keys_summary(authorized_keys)

                self.assertFalse(summary["valid"])

    def test_unrestricted_key_and_extra_sudo_grant_block_without_leaking_command(self) -> None:
        authorized_keys = self.root / "authorized_keys"
        key_data = base64.b64encode(b"public-key-material").decode()
        unauthorized_command = "sudo -n /bin/echo command-secret"
        authorized_keys.write_text(
            f'command="{unauthorized_command}" ssh-ed25519 {key_data}\n',
            encoding="utf-8",
        )
        sudo_policy = self.root / "sudo-policy"
        sudo_policy.write_text(
            "Defaults:commonex-deploy env_reset\n"
            f"{inventory.EXPECTED_SECURE_PATH}\n"
            'Defaults:commonex-deploy env_keep = "SSH_ORIGINAL_COMMAND"\n'
            "commonex-deploy ALL=(root) NOPASSWD: "
            "/usr/local/sbin/commonex-deploy forced\n"
            "commonex-deploy ALL=(root) NOPASSWD: /bin/echo sudo-secret\n",
            encoding="utf-8",
        )
        config = self.with_targets(
            self.config(),
            inventory.InventoryTarget("deploy_authorized_keys", authorized_keys),
            inventory.InventoryTarget("sudo_policy", sudo_policy),
        )

        report = inventory.collect_inventory(
            config, generated_at="now", sudo_runner=effective_sudo_runner()
        )
        serialized = inventory.serialize_report(report)

        self.assertIn("invalid_security_policy", report["blockers"])
        self.assertNotIn("command-secret", serialized)
        self.assertNotIn("sudo-secret", serialized)

    def test_sudo_policy_rejects_alias_grants_and_include_directives(self) -> None:
        sudo_policy = self.root / "sudo-policy"
        canonical = inventory.EXPECTED_SUDO_POLICY_TEXT
        unsafe_suffixes = (
            "User_Alias CE = commonex-deploy\nCE ALL=(ALL) NOPASSWD: ALL\n",
            "ALL ALL=(ALL) NOPASSWD: ALL\n",
            "#include /tmp/attacker-controlled-sudoers\n",
            "#includedir /tmp/attacker-controlled-sudoers\n",
            "@include /tmp/attacker-controlled-sudoers\n",
            "@includedir /tmp/attacker-controlled-sudoers\n",
            "Defaults:commonex-deploy !env_reset\n",
        )

        for unsafe_suffix in unsafe_suffixes:
            with self.subTest(suffix=unsafe_suffix.splitlines()[0]):
                sudo_policy.write_text(canonical + unsafe_suffix, encoding="utf-8")

                summary = inventory._sudo_policy_summary(sudo_policy)

                self.assertFalse(summary["valid"])

    def test_effective_sudo_policy_accepts_only_the_forced_command(self) -> None:
        calls = []

        def runner(command: object, **kwargs: object) -> object:
            calls.append((command, kwargs))
            return inventory.subprocess.CompletedProcess(
                command,
                0,
                stdout=sudo_list_output(inventory.EXPECTED_EFFECTIVE_SUDO_GRANT),
                stderr=b"",
            )

        summary = inventory._effective_sudo_policy_summary(runner)

        self.assertTrue(summary["valid"])
        self.assertEqual(summary["grant_count"], 1)
        self.assertEqual(calls[0][0], inventory.SUDO_LIST_COMMAND)
        self.assertEqual(calls[0][1]["env"], inventory.SUDO_LIST_ENVIRONMENT)
        self.assertEqual(calls[0][1]["timeout"], 10)
        self.assertFalse(calls[0][1]["check"])
        self.assertEqual(calls[0][1]["cwd"], "/")
        self.assertEqual(calls[0][1]["stderr"], inventory.subprocess.DEVNULL)

    def test_effective_sudo_policy_rejects_overridden_security_defaults(self) -> None:
        unsafe_defaults = (
            CANONICAL_EFFECTIVE_DEFAULTS + ("!env_reset",),
            CANONICAL_EFFECTIVE_DEFAULTS + ("setenv",),
            CANONICAL_EFFECTIVE_DEFAULTS + ("secure_path=/tmp",),
            CANONICAL_EFFECTIVE_DEFAULTS + ("env_keep+=PYTHONPATH",),
            CANONICAL_EFFECTIVE_DEFAULTS + ("env_keep-=SSH_ORIGINAL_COMMAND",),
            CANONICAL_EFFECTIVE_DEFAULTS + ("env_file=/tmp/attacker",),
            CANONICAL_EFFECTIVE_DEFAULTS
            + ("restricted_env_file=/tmp/attacker",),
            CANONICAL_EFFECTIVE_DEFAULTS + ("env_check+=PYTHONPATH",),
        )

        for defaults in unsafe_defaults:
            with self.subTest(default=defaults[-1]):
                summary = inventory._effective_sudo_policy_summary(
                    effective_sudo_runner(defaults=defaults)
                )

                self.assertFalse(summary["valid"])

    def test_effective_sudo_policy_rejects_any_other_grant_shape(self) -> None:
        invalid_grant_sets = (
            (
                inventory.EXPECTED_EFFECTIVE_SUDO_GRANT,
                inventory.EXPECTED_EFFECTIVE_SUDO_GRANT,
            ),
            ("(root) PASSWD: " + inventory.EXPECTED_SUDO_COMMAND,),
            ("(ALL) NOPASSWD: ALL",),
            ("(deploy) NOPASSWD: " + inventory.EXPECTED_SUDO_COMMAND,),
            ("(root) NOPASSWD: !/bin/su",),
        )

        for grants in invalid_grant_sets:
            with self.subTest(grants=grants):
                summary = inventory._effective_sudo_policy_summary(
                    effective_sudo_runner(*grants)
                )

                self.assertFalse(summary["valid"])

    def test_effective_sudo_policy_rejects_external_grants_without_leaking(self) -> None:
        sudo_policy = self.root / "sudo-policy"
        sudo_policy.write_text(inventory.EXPECTED_SUDO_POLICY_TEXT, encoding="utf-8")
        external_grant = "(ALL) NOPASSWD: /bin/echo external-sudo-secret"
        config = self.with_targets(
            self.config(), inventory.InventoryTarget("sudo_policy", sudo_policy)
        )

        report = inventory.collect_inventory(
            config,
            generated_at="now",
            sudo_runner=effective_sudo_runner(
                inventory.EXPECTED_EFFECTIVE_SUDO_GRANT, external_grant
            ),
        )
        serialized = inventory.serialize_report(report)

        self.assertTrue(report["security_policies"]["sudo"]["valid"])
        self.assertFalse(report["security_policies"]["effective_sudo"]["valid"])
        self.assertEqual(
            report["security_policies"]["effective_sudo"]["grant_count"], 2
        )
        self.assertIn("invalid_security_policy", report["blockers"])
        self.assertNotIn("external-sudo-secret", serialized)
        self.assertNotIn("sudo diagnostic secret", serialized)

    def test_effective_sudo_policy_fails_closed_on_query_or_output_errors(self) -> None:
        failed = inventory._effective_sudo_policy_summary(
            effective_sudo_runner(returncode=1)
        )
        malformed = inventory._effective_sudo_policy_summary(
            lambda command, **_kwargs: inventory.subprocess.CompletedProcess(
                command,
                0,
                stdout=b"unparseable sudo output\n",
                stderr=b"sudo parse secret",
            )
        )
        invalid_utf8 = inventory._effective_sudo_policy_summary(
            lambda command, **_kwargs: inventory.subprocess.CompletedProcess(
                command, 0, stdout=b"\xff", stderr=b""
            )
        )
        oversized = inventory._effective_sudo_policy_summary(
            lambda command, **_kwargs: inventory.subprocess.CompletedProcess(
                command,
                0,
                stdout=b"x" * (inventory.MAX_SUDO_LIST_BYTES + 1),
                stderr=b"",
            )
        )

        self.assertFalse(failed["valid"])
        self.assertFalse(failed["query_succeeded"])
        self.assertFalse(malformed["valid"])
        self.assertFalse(malformed["query_succeeded"])
        self.assertFalse(invalid_utf8["valid"])
        self.assertFalse(oversized["valid"])

    def test_logrotate_policy_rejects_extra_directives_and_scripts(self) -> None:
        logrotate = self.root / "logrotate"
        unsafe_directives = (
            "copytruncate",
            "olddir /tmp/attacker-controlled",
            "postrotate",
            "include /tmp/attacker-controlled",
            "weekly",
            "rotate 999",
        )

        for directive in unsafe_directives:
            with self.subTest(directive=directive):
                policy = inventory.EXPECTED_LOGROTATE_POLICY_TEXT.replace(
                    "}\n",
                    "    " + directive + "\n}\n",
                )
                logrotate.write_text(policy, encoding="utf-8")

                summary = inventory._logrotate_summary(logrotate)

                self.assertFalse(summary["valid"])

    def test_logrotate_script_block_is_rejected_without_leaking_payload(self) -> None:
        logrotate = self.root / "logrotate"
        policy = inventory.EXPECTED_LOGROTATE_POLICY_TEXT.replace(
            "}\n",
            "    postrotate\n"
            "        /bin/echo logrotate-script-secret\n"
            "    endscript\n"
            "}\n",
        )
        logrotate.write_text(policy, encoding="utf-8")
        config = self.with_targets(
            self.config(),
            inventory.InventoryTarget("commonex_logrotate_policy", logrotate),
        )

        report = inventory.collect_inventory(config, generated_at="now")
        serialized = inventory.serialize_report(report)

        self.assertIn("invalid_logrotate_policy", report["blockers"])
        self.assertNotIn("logrotate-script-secret", serialized)

    @unittest.skipUnless(os.name == "posix", "POSIX permission semantics")
    def test_policy_files_require_documented_root_only_modes(self) -> None:
        sudo_policy = self.root / "sudo-policy"
        logrotate = self.root / "logrotate"
        sudo_policy.write_text(inventory.EXPECTED_SUDO_POLICY_TEXT, encoding="utf-8")
        logrotate.write_text(
            inventory.EXPECTED_LOGROTATE_POLICY_TEXT,
            encoding="utf-8",
        )
        config = self.with_targets(
            self.config(),
            inventory.InventoryTarget("sudo_policy", sudo_policy),
            inventory.InventoryTarget("commonex_logrotate_policy", logrotate),
        )

        for path, unsafe_mode in ((sudo_policy, 0o640), (logrotate, 0o664)):
            with self.subTest(path=path.name, mode=oct(unsafe_mode)):
                sudo_policy.chmod(0o440)
                logrotate.chmod(0o644)
                path.chmod(unsafe_mode)

                report = inventory.collect_inventory(
                    config,
                    generated_at="now",
                    sudo_runner=effective_sudo_runner(),
                )

                self.assertIn("incompatible_tool_metadata", report["blockers"])

    @unittest.skipUnless(os.name == "posix", "POSIX ownership semantics")
    def test_policy_files_require_root_ownership(self) -> None:
        sudo_policy = self.root / "sudo-policy"
        logrotate = self.root / "logrotate"
        sudo_policy.write_text(inventory.EXPECTED_SUDO_POLICY_TEXT, encoding="utf-8")
        logrotate.write_text(
            inventory.EXPECTED_LOGROTATE_POLICY_TEXT,
            encoding="utf-8",
        )
        sudo_policy.chmod(0o440)
        logrotate.chmod(0o644)
        policy_paths = {sudo_policy, logrotate}
        real_lstat = Path.lstat

        def non_root_policy_owner(path: Path) -> os.stat_result:
            metadata = real_lstat(path)
            if path not in policy_paths:
                return metadata
            values = list(metadata)
            values[4] = 1000
            return os.stat_result(values)

        config = self.with_targets(
            self.config(),
            inventory.InventoryTarget("sudo_policy", sudo_policy),
            inventory.InventoryTarget("commonex_logrotate_policy", logrotate),
        )
        with mock.patch.object(Path, "lstat", non_root_policy_owner):
            report = inventory.collect_inventory(
                config, generated_at="now", sudo_runner=effective_sudo_runner()
            )

        self.assertIn("incompatible_tool_metadata", report["blockers"])

    def test_exact_policies_allow_only_an_optional_final_newline(self) -> None:
        sudo_policy = self.root / "sudo-policy"
        logrotate = self.root / "logrotate"

        for final_newline in (True, False):
            with self.subTest(final_newline=final_newline):
                sudo_policy.write_text(
                    inventory.EXPECTED_SUDO_POLICY_TEXT[
                        : None if final_newline else -1
                    ],
                    encoding="utf-8",
                )
                logrotate.write_text(
                    inventory.EXPECTED_LOGROTATE_POLICY_TEXT[
                        : None if final_newline else -1
                    ],
                    encoding="utf-8",
                )

                self.assertTrue(inventory._sudo_policy_summary(sudo_policy)["valid"])
                self.assertTrue(inventory._logrotate_summary(logrotate)["valid"])

    def test_missing_or_invalid_required_policy_prevents_complete_status(self) -> None:
        missing_keys = self.root / "missing-authorized-keys"
        missing_sudo = self.root / "missing-sudo"
        invalid_logrotate = self.root / "logrotate"
        invalid_logrotate.write_text(
            "/var/log/commonex/deploy.log {\n"
            "  weekly\n"
            "  rotate 12\n"
            "  create 0666 commonex-deploy commonex-deploy\n"
            "}\n",
            encoding="utf-8",
        )
        config = self.with_targets(
            self.config(),
            inventory.InventoryTarget("deploy_authorized_keys", missing_keys),
            inventory.InventoryTarget("sudo_policy", missing_sudo),
            inventory.InventoryTarget("commonex_logrotate_policy", invalid_logrotate),
        )

        report = inventory.collect_inventory(
            config, generated_at="now", sudo_runner=effective_sudo_runner()
        )

        self.assertNotEqual(report["status"], "complete")
        self.assertIn("invalid_logrotate_policy", report["blockers"])
        self.assertIn(
            "missing_security_policy:authorized_keys", report["inventory_issues"]
        )
        self.assertIn("missing_security_policy:sudo_policy", report["inventory_issues"])

    def test_missing_new_logrotate_policy_does_not_block_legacy_inventory(self) -> None:
        missing_logrotate = self.root / "not-installed-yet"
        config = self.with_targets(
            self.config(),
            inventory.InventoryTarget(
                "commonex_logrotate_policy", missing_logrotate
            ),
        )

        report = inventory.collect_inventory(config, generated_at="now")

        self.assertFalse(report["logrotate_policy"]["exists"])
        self.assertNotIn("missing_logrotate_policy", report["blockers"])

    def test_malformed_audit_timestamp_does_not_leak(self) -> None:
        audit = self.root / "audit.log"
        audit.write_text(
            "2026-08-30Ttimestamp-secret ACTION deploy status=PASS\n",
            encoding="utf-8",
        )
        config = self.with_targets(
            self.config(), inventory.InventoryTarget("canonical_audit_log", audit)
        )

        report = inventory.collect_inventory(config, generated_at="now")
        serialized = inventory.serialize_report(report)

        self.assertEqual(report["audit_logs"][0]["recognized_line_count"], 0)
        self.assertNotIn("timestamp-secret", serialized)

    def test_multiple_legacy_run_files_block_migration(self) -> None:
        state = self.root / "state"
        state.mkdir()
        first = state / "last-successful-run"
        second = self.root / "other-last-successful-run"
        first.write_text("41\n", encoding="ascii")
        second.write_text("42\n", encoding="ascii")
        config = self.config()
        config = inventory.InventoryConfig(
            targets=config.targets,
            activation_state_paths=config.activation_state_paths,
            activation_intent_paths=config.activation_intent_paths,
            legacy_run_paths=(first, second),
            max_entries=config.max_entries,
            max_hash_bytes=config.max_hash_bytes,
        )

        report = inventory.collect_inventory(config, generated_at="now")

        self.assertIn("multiple_legacy_runs", report["blockers"])


if __name__ == "__main__":
    unittest.main()
