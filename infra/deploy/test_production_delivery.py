from io import BytesIO, StringIO
from pathlib import Path
import tarfile
from tempfile import TemporaryDirectory
from typing import List, Optional, Sequence, Tuple
import unittest
from unittest.mock import patch

from infra.deploy import production_delivery


SHA = "a" * 40
CURRENT_IMAGES = "".join(
    "{}=example/{}@sha256:{}\n".format(key, key.lower(), "1" * 64)
    for key in (
        "COMMONEX_BACKEND_IMAGE",
        "COMMONEX_FRONTEND_IMAGE",
        "COMMONEX_NGINX_IMAGE",
        "COMMONEX_OTEL_COLLECTOR_IMAGE",
    )
)
EXPECTED_IMAGES = "".join(reversed(CURRENT_IMAGES.splitlines(keepends=True)))


class ScriptedClient:
    def __init__(self, results: Sequence[production_delivery.ForcedCommandResult]):
        self.results = list(results)
        self.calls: List[Tuple[Tuple[str, ...], Optional[bytes]]] = []

    def run(
        self,
        command: Sequence[str],
        *,
        stdin: Optional[bytes] = None,
    ) -> production_delivery.ForcedCommandResult:
        self.calls.append((tuple(command), stdin))
        if not self.results:
            raise AssertionError("unexpected forced command")
        return self.results.pop(0)


class ProductionDeliveryTest(unittest.TestCase):
    def test_public_verifier_does_not_reparse_orchestrator_arguments(self) -> None:
        with patch.object(
            production_delivery,
            "verify_public_services",
            return_value=0,
        ) as verify:
            result = production_delivery._default_public_verifier()

        self.assertEqual(result, 0)
        verify.assert_called_once_with(())

    def test_ssh_adapter_uses_exact_forced_command_and_binary_stdin(self) -> None:
        completed = __import__("subprocess").CompletedProcess(
            [], 0, stdout=b"out", stderr=b"err"
        )
        with patch.object(
            production_delivery.subprocess,
            "run",
            return_value=completed,
        ) as run:
            result = production_delivery.SshForcedCommandClient().run(
                ("deploy", SHA, "7"), stdin=b"archive"
            )

        run.assert_called_once_with(
            ["ssh", "commonex-production", "deploy {} 7".format(SHA)],
            input=b"archive",
            stdout=production_delivery.subprocess.PIPE,
            stderr=production_delivery.subprocess.PIPE,
            check=False,
        )
        self.assertEqual(
            result,
            production_delivery.ForcedCommandResult(0, b"out", b"err"),
        )

    def test_archive_contains_only_allowlisted_members_with_exact_modes(self) -> None:
        archive = production_delivery.build_release_archive(b"compose\n", b"SECRET=x\n")

        with tarfile.open(fileobj=BytesIO(archive), mode="r:gz") as bundle:
            members = bundle.getmembers()
            self.assertEqual(
                [(member.name, member.mode) for member in members],
                [("docker-compose-prod.yml", 0o644), (".env", 0o600)],
            )
            self.assertEqual(bundle.extractfile(members[0]).read(), b"compose\n")
            self.assertEqual(bundle.extractfile(members[1]).read(), b"SECRET=x\n")

    def test_deploy_bootstrap_runs_exact_protocol_and_appends_images(self) -> None:
        client = ScriptedClient(
            [
                production_delivery.ForcedCommandResult(
                    1, stderr=production_delivery.BOOTSTRAP_DIAGNOSTIC
                ),
                production_delivery.ForcedCommandResult(0),
                production_delivery.ForcedCommandResult(0),
                production_delivery.ForcedCommandResult(0),
                production_delivery.ForcedCommandResult(
                    0, stdout=EXPECTED_IMAGES.encode("utf-8")
                ),
            ]
        )
        resolver_calls = []
        verifier_calls = []

        def resolve(changed: str, sha: str, current: Optional[str]) -> str:
            resolver_calls.append((changed, sha, current))
            return EXPECTED_IMAGES

        with TemporaryDirectory() as directory:
            compose = Path(directory) / "compose.yml"
            environment = Path(directory) / "secrets.env"
            compose.write_bytes(b"services: {}\n")
            environment.write_bytes(b"SECRET=not-printed")
            result = production_delivery.deploy_release(
                SHA,
                42,
                '["backend","frontend","nginx","otel-collector"]',
                compose,
                environment,
                client,
                image_resolver=resolve,
                public_verifier=lambda: verifier_calls.append("verify") or 0,
            )

        self.assertEqual(result, 0)
        self.assertEqual(
            [call[0] for call in client.calls],
            [
                ("current-images",),
                ("stage", SHA),
                ("validate", SHA),
                ("deploy", SHA, "42"),
                ("current-images",),
            ],
        )
        self.assertEqual(
            resolver_calls,
            [('["backend","frontend","nginx","otel-collector"]', SHA, None)],
        )
        self.assertEqual(verifier_calls, ["verify"])
        archive = client.calls[1][1]
        self.assertIsNotNone(archive)
        with tarfile.open(fileobj=BytesIO(archive), mode="r:gz") as bundle:
            environment_contents = bundle.extractfile(".env").read()
        self.assertEqual(
            environment_contents,
            b"SECRET=not-printed\n" + EXPECTED_IMAGES.encode("utf-8"),
        )

    def test_deploy_uses_current_images_for_resolution(self) -> None:
        client = ScriptedClient(
            [
                production_delivery.ForcedCommandResult(
                    0, stdout=CURRENT_IMAGES.encode("utf-8")
                ),
                production_delivery.ForcedCommandResult(0),
                production_delivery.ForcedCommandResult(0),
                production_delivery.ForcedCommandResult(0),
                production_delivery.ForcedCommandResult(
                    0, stdout=EXPECTED_IMAGES.encode("utf-8")
                ),
            ]
        )
        resolver_calls = []

        with TemporaryDirectory() as directory:
            compose = Path(directory) / "compose.yml"
            environment = Path(directory) / "secrets.env"
            compose.write_bytes(b"compose")
            environment.write_bytes(b"secret")
            result = production_delivery.deploy_release(
                SHA,
                1,
                "[]",
                compose,
                environment,
                client,
                image_resolver=lambda changed, sha, current: (
                    resolver_calls.append((changed, sha, current)) or EXPECTED_IMAGES
                ),
                public_verifier=lambda: 0,
            )

        self.assertEqual(result, 0)
        self.assertEqual(resolver_calls, [("[]", SHA, CURRENT_IMAGES)])

    def test_deploy_does_not_retry_or_verify_after_mutation_failure(self) -> None:
        client = ScriptedClient(
            [
                production_delivery.ForcedCommandResult(
                    0, stdout=CURRENT_IMAGES.encode("utf-8")
                ),
                production_delivery.ForcedCommandResult(0),
                production_delivery.ForcedCommandResult(0),
                production_delivery.ForcedCommandResult(
                    3,
                    stderr=(
                        b"commonex-deploy: unresolved activation intent; "
                        b"manually reconcile the host\n"
                    ),
                ),
            ]
        )
        verification_calls = []
        error = StringIO()

        with TemporaryDirectory() as directory:
            compose = Path(directory) / "compose.yml"
            environment = Path(directory) / "secrets.env"
            compose.write_bytes(b"compose")
            environment.write_bytes(b"secret")
            result = production_delivery.deploy_release(
                SHA,
                2,
                "[]",
                compose,
                environment,
                client,
                image_resolver=lambda *_args: EXPECTED_IMAGES,
                public_verifier=lambda: verification_calls.append(True) or 0,
                stderr=error,
            )

        self.assertEqual(result, 3)
        self.assertEqual(len(client.calls), 4)
        self.assertEqual(verification_calls, [])
        self.assertIn("unresolved activation intent", error.getvalue())
        self.assertIn("do not retry", error.getvalue())
        self.assertNotIn("secret", error.getvalue().lower())

    def test_deploy_exit_two_verifies_before_preserving_failure(self) -> None:
        events = []

        class EventClient(ScriptedClient):
            def run(self, command, *, stdin=None):
                events.append(" ".join(command))
                return super().run(command, stdin=stdin)

        client = EventClient(
            [
                production_delivery.ForcedCommandResult(
                    0, stdout=CURRENT_IMAGES.encode("utf-8")
                ),
                production_delivery.ForcedCommandResult(0),
                production_delivery.ForcedCommandResult(0),
                production_delivery.ForcedCommandResult(2),
                production_delivery.ForcedCommandResult(
                    0, stdout=EXPECTED_IMAGES.encode("utf-8")
                ),
            ]
        )
        error = StringIO()

        with TemporaryDirectory() as directory:
            compose = Path(directory) / "compose.yml"
            environment = Path(directory) / "secrets.env"
            compose.write_bytes(b"compose")
            environment.write_bytes(b"secret")
            result = production_delivery.deploy_release(
                SHA,
                3,
                "[]",
                compose,
                environment,
                client,
                image_resolver=lambda *_args: EXPECTED_IMAGES,
                public_verifier=lambda: events.append("public-health") or 0,
                stderr=error,
            )

        self.assertEqual(result, 2)
        self.assertEqual(events[-2:], ["current-images", "public-health"])
        self.assertIn("final audit record failed", error.getvalue())

    def test_deploy_runs_public_verification_even_when_images_mismatch(self) -> None:
        client = ScriptedClient(
            [
                production_delivery.ForcedCommandResult(
                    0, stdout=CURRENT_IMAGES.encode("utf-8")
                ),
                production_delivery.ForcedCommandResult(0),
                production_delivery.ForcedCommandResult(0),
                production_delivery.ForcedCommandResult(0),
                production_delivery.ForcedCommandResult(0, stdout=b"wrong\n"),
            ]
        )
        verification_calls = []

        with TemporaryDirectory() as directory:
            compose = Path(directory) / "compose.yml"
            environment = Path(directory) / "secrets.env"
            compose.write_bytes(b"compose")
            environment.write_bytes(b"secret")
            result = production_delivery.deploy_release(
                SHA,
                4,
                "[]",
                compose,
                environment,
                client,
                image_resolver=lambda *_args: EXPECTED_IMAGES,
                public_verifier=lambda: verification_calls.append(True) or 0,
                stderr=StringIO(),
            )

        self.assertEqual(result, 1)
        self.assertEqual(verification_calls, [True])

    def test_deploy_preserves_exit_two_after_failed_postcommit_check(self) -> None:
        client = ScriptedClient(
            [
                production_delivery.ForcedCommandResult(
                    0, stdout=CURRENT_IMAGES.encode("utf-8")
                ),
                production_delivery.ForcedCommandResult(0),
                production_delivery.ForcedCommandResult(0),
                production_delivery.ForcedCommandResult(2),
                production_delivery.ForcedCommandResult(0, stdout=b"wrong\n"),
            ]
        )
        error = StringIO()

        with TemporaryDirectory() as directory:
            compose = Path(directory) / "compose.yml"
            environment = Path(directory) / "secrets.env"
            compose.write_bytes(b"compose")
            environment.write_bytes(b"secret")
            result = production_delivery.deploy_release(
                SHA,
                5,
                "[]",
                compose,
                environment,
                client,
                image_resolver=lambda *_args: EXPECTED_IMAGES,
                public_verifier=lambda: 1,
                stderr=error,
            )

        self.assertEqual(result, 2)
        self.assertIn("active image references", error.getvalue())
        self.assertIn("final audit record failed", error.getvalue())

    def test_rollback_runs_exact_protocol_and_preserves_exit_two(self) -> None:
        events = []

        class EventClient(ScriptedClient):
            def run(self, command, *, stdin=None):
                events.append(" ".join(command))
                return super().run(command, stdin=stdin)

        client = EventClient(
            [
                production_delivery.ForcedCommandResult(2),
                production_delivery.ForcedCommandResult(0, stdout=b"images\n"),
            ]
        )
        error = StringIO()

        result = production_delivery.rollback_release(
            SHA,
            9,
            client,
            public_verifier=lambda: events.append("public-health") or 0,
            stderr=error,
        )

        self.assertEqual(result, 2)
        self.assertEqual(
            events,
            ["rollback {} 9".format(SHA), "current-images", "public-health"],
        )
        self.assertIn("final audit record failed", error.getvalue())

    def test_rollback_does_not_retry_ambiguous_activation(self) -> None:
        client = ScriptedClient(
            [
                production_delivery.ForcedCommandResult(
                    3,
                    stderr=b"commonex-deploy: manually reconcile activation state\n",
                )
            ]
        )
        verification_calls = []
        error = StringIO()

        result = production_delivery.rollback_release(
            SHA,
            10,
            client,
            public_verifier=lambda: verification_calls.append(True) or 0,
            stderr=error,
        )

        self.assertEqual(result, 3)
        self.assertEqual(len(client.calls), 1)
        self.assertEqual(verification_calls, [])
        self.assertIn("manually reconcile activation state", error.getvalue())
        self.assertIn("do not retry", error.getvalue())

    def test_host_diagnostics_are_bounded_sanitized_and_redacted(self) -> None:
        forged_partial_line = (
            b"ignored-noise" * production_delivery.MAX_HOST_DIAGNOSTIC_BYTES
            + b"commonex-deploy: forged partial line"
        )
        result = production_delivery.ForcedCommandResult(
            3,
            stderr=(
                forged_partial_line
                + b"\ncommonex-deploy: manually reconcile\n"
                + b"commonex-deploy: token=diagnostic-secret\n"
                + b"commonex-deploy: \x1b[31m::error:: injected\rnext-line\n"
            ),
        )
        error = StringIO()

        returned = production_delivery._command_failure("deploy", result, error)
        diagnostic = error.getvalue()

        self.assertEqual(returned, 3)
        self.assertIn("manually reconcile", diagnostic)
        self.assertIn("[redacted]", diagnostic)
        self.assertIn("earlier host diagnostic omitted", diagnostic)
        self.assertIn("do not retry", diagnostic)
        self.assertNotIn("forged partial line", diagnostic)
        self.assertNotIn("diagnostic-secret", diagnostic)
        self.assertNotIn("\x1b", diagnostic)
        self.assertFalse(any(line.startswith("::") for line in diagnostic.splitlines()))

    def test_initial_current_images_failure_forwards_safe_host_diagnostic(self) -> None:
        client = ScriptedClient(
            [
                production_delivery.ForcedCommandResult(
                    1,
                    stderr=b"commonex-deploy: unresolved activation intent\n",
                )
            ]
        )
        error = StringIO()

        with TemporaryDirectory() as directory:
            compose = Path(directory) / "compose.yml"
            environment = Path(directory) / "secrets.env"
            result = production_delivery.deploy_release(
                SHA,
                1,
                "[]",
                compose,
                environment,
                client,
                image_resolver=lambda *_args: EXPECTED_IMAGES,
                public_verifier=lambda: 0,
                stderr=error,
            )

        self.assertEqual(result, 1)
        self.assertIn("unresolved activation intent", error.getvalue())

    def test_invalid_release_is_rejected_before_contacting_host(self) -> None:
        client = ScriptedClient([])

        with self.assertRaisesRegex(ValueError, "lowercase 40-character Git SHA"):
            production_delivery.rollback_release(
                "BAD",
                1,
                client,
                public_verifier=lambda: 0,
            )
        self.assertEqual(client.calls, [])

    def test_invalid_run_number_is_rejected_before_deploy_side_effects(self) -> None:
        client = ScriptedClient([])
        resolver_calls = []

        with patch.object(Path, "read_bytes") as read_bytes:
            with self.assertRaisesRegex(ValueError, r"\[1-9\]\[0-9\]"):
                production_delivery.deploy_release(
                    SHA,
                    10**20,
                    "[]",
                    Path("compose.yml"),
                    Path("environment.env"),
                    client,
                    image_resolver=lambda *_args: resolver_calls.append(True) or "",
                    public_verifier=lambda: 0,
                )

        self.assertEqual(client.calls, [])
        self.assertEqual(resolver_calls, [])
        read_bytes.assert_not_called()

    def test_run_number_validation_matches_host_contract(self) -> None:
        self.assertEqual(production_delivery._positive_run_number("1"), 1)
        self.assertEqual(
            production_delivery._positive_run_number("9" * 20),
            int("9" * 20),
        )
        for value in ("0", "01", "+1", " 1", "1 ", "1" * 21):
            with self.subTest(value=value):
                with self.assertRaises(production_delivery.argparse.ArgumentTypeError):
                    production_delivery._positive_run_number(value)

        with self.assertRaises(ValueError):
            production_delivery._validate_run_number(True)

    def test_cli_preserves_invalid_rollback_release_diagnostic(self) -> None:
        error = StringIO()
        result = production_delivery.main(
            ["rollback", "BAD", "1"],
            client=ScriptedClient([]),
            stderr=error,
        )

        self.assertEqual(result, 1)
        self.assertEqual(
            error.getvalue(),
            "release_sha must be a lowercase 40-character Git SHA\n",
        )


if __name__ == "__main__":
    unittest.main()
