from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from infra.deploy import staging_delivery as staging
from infra.deploy.production_delivery import BOOTSTRAP_DIAGNOSTIC, ForcedCommandResult
from infra.deploy.release_image_catalog import load_release_image_catalog


A = "a" * 40
B = "b" * 40
IMAGES = {image.environment_key: image.repository + "@sha256:" + "1" * 64
          for image in load_release_image_catalog()}
REFERENCES = "".join(key + "=" + value + "\n" for key, value in sorted(IMAGES.items()))


def status(sha=B, activation=101, healthy=True):
    return {"release_sha": sha, "activation_number": activation, "images": IMAGES,
            "healthy": healthy,
            "services": [{"service": "nginx", "state": "running", "health": "healthy"}]}


class Client:
    def __init__(self, snapshots, failures=None):
        self.snapshots = iter(snapshots)
        self.failures = failures or {}
        self.commands = []
        self.archives = []

    def run(self, command, *, stdin=None):
        self.commands.append(tuple(command))
        if stdin is not None:
            self.archives.append(stdin)
        if command[0] in self.failures:
            return self.failures[command[0]]
        if command[0] == "release-status":
            snapshot = next(self.snapshots)
            if snapshot is None:
                return ForcedCommandResult(1, stderr=BOOTSTRAP_DIAGNOSTIC)
            return ForcedCommandResult(0, json.dumps(snapshot).encode())
        if command[0] == "current-images":
            return ForcedCommandResult(0, REFERENCES.encode())
        return ForcedCommandResult(0)


class StagingDeliveryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.compose = self.root / "compose.yml"
        self.compose.write_text("services: {}\n")
        self.environment = self.root / "inputs"
        self.environment.write_text("POSTGRES_PASSWORD=private-value\n")

    def activate(self, client, public=0, run=101):
        report = staging.new_report(B)
        result = staging.activate_candidate(
            report, run, self.compose, self.environment, client,
            image_resolver=lambda *_: REFERENCES,
            public_verifier=lambda: public,
        )
        return result, report

    def test_candidate_activation_records_exact_identity(self):
        client = Client([status(A, 100), status()])
        result, report = self.activate(client)
        self.assertEqual(0, result)
        self.assertEqual(status(A, 100), report["baseline"])
        self.assertEqual("passed", report["status"])
        self.assertEqual(IMAGES, report["expected_images"])
        self.assertFalse(report["promotion_eligible"])
        self.assertEqual({"status": "not_run"}, report["rehearsal"])
        self.assertNotIn("private-value", json.dumps(report))
        self.assertEqual(("release-status",), client.commands[0])
        self.assertEqual(("stage", B), client.commands[2])
        self.assertIn(("deploy", B, "101"), client.commands)

    def test_allocates_above_private_operator_activation_counter(self):
        client = Client([status(A, 9000), status(B, 9001)])
        result, report = self.activate(client, run=1)
        self.assertEqual(0, result)
        self.assertEqual(9001, report["activation_number"])
        self.assertIn(("deploy", B, "9001"), client.commands)

    def test_bootstrap_records_no_baseline_and_no_rehearsal(self):
        _, report = self.activate(Client([None, status(activation=1)]))
        self.assertIsNone(report["baseline"])
        self.assertFalse(report["promotion_eligible"])

    def test_github_run_ahead_still_uses_exactly_next_host_counter(self):
        client = Client([status(A, 100), status()])
        result, report = self.activate(client, run=999999)
        self.assertEqual(0, result)
        self.assertEqual(101, report["activation_number"])
        self.assertEqual(999999, report["workflow_run_id"])

    def test_intervening_activation_is_rejected_and_retains_diagnostics(self):
        report_path = self.root / "report.json"
        client = Client([status(A, 100), status(A, 102)], {
            "deploy": ForcedCommandResult(1, stderr=b"commonex-deploy: stale run number\n")})
        activate = staging.activate_candidate
        with patch.object(staging.delivery, "SshForcedCommandClient", return_value=client), \
                patch.object(staging, "activate_candidate", wraps=lambda report, run, compose, environment, actual_client:
                    activate(report, run, compose, environment, actual_client,
                             image_resolver=lambda *_: REFERENCES,
                             public_verifier=lambda: 0)), redirect_stdout(StringIO()):
            result = staging.main([
                "--report", str(report_path), "--candidate", B,
                "deploy", "999999",
                str(self.compose), str(self.environment),
            ])
        self.assertEqual(1, result)
        report = json.loads(report_path.read_text())
        self.assertEqual(101, report["activation_number"])
        self.assertIn("stale run number", "\n".join(report["diagnostics"]))

    def test_public_or_service_health_failure_fails_activation(self):
        for public, healthy in ((1, True), (0, False)):
            with self.subTest(public=public):
                result, report = self.activate(Client([status(A, 100), status(healthy=healthy)]), public)
                self.assertEqual(1, result)
                self.assertEqual("failed", report["status"])
                self.assertTrue(report["checks"]["activation"])
                self.assertTrue(report["checks"]["release_identity"])
                self.assertEqual(public == 0, report["checks"]["public_services"])
                self.assertEqual(healthy, report["checks"]["service_health"])

    def test_activation_failure_is_not_a_pass(self):
        client = Client([status(A, 100), status(A, 100)], {
            "deploy": ForcedCommandResult(3, stderr=b"commonex-deploy: pending intent\n")})
        with redirect_stdout(StringIO()):
            result, report = self.activate(client)
        self.assertEqual(1, result)
        self.assertFalse(report["checks"]["activation"])
        self.assertFalse(report["checks"]["release_identity"])
        self.assertEqual(status(A, 100), report["after_activation"])
        self.assertEqual("failed", report["status"])

    def test_committed_audit_failure_cannot_pass_despite_observed_candidate(self):
        client = Client([status(A, 100), status()], {
            "deploy": ForcedCommandResult(2, stderr=b"commonex-deploy: final audit failed\n")})
        with redirect_stdout(StringIO()), redirect_stderr(StringIO()):
            result, report = self.activate(client)
        self.assertEqual(1, result)
        self.assertTrue(all(report["checks"].values()))
        self.assertEqual("failed", report["status"])
        self.assertFalse(report["promotion_eligible"])

    def test_activation_identity_rejects_different_sha_digest_or_intervening_activation(self):
        for snapshot in (status(A), status(activation=102),
                         dict(status(), images={key: value.replace("1" * 64, "2" * 64)
                                                for key, value in IMAGES.items()})):
            with self.subTest(snapshot=snapshot):
                result, report = self.activate(Client([status(A, 100), snapshot]))
                self.assertEqual(1, result)
                self.assertEqual("failed", report["status"])
                self.assertFalse(report["checks"]["release_identity"])

    def test_cli_writes_failed_report_without_private_exception_text(self):
        report_path = self.root / "report.json"
        with patch.object(staging, "activate_candidate", side_effect=ValueError("private-value")), \
                redirect_stdout(StringIO()):
            result = staging.main([
                "--report", str(report_path), "--candidate", B,
                "deploy", "101",
                str(self.compose), str(self.environment),
            ])
        self.assertEqual(1, result)
        text = report_path.read_text()
        self.assertIn("ValueError", text)
        self.assertNotIn("private-value", text)
        self.assertEqual("failed", json.loads(text)["status"])


if __name__ == "__main__":
    unittest.main()
