from pathlib import Path
import re
import unittest


WORKFLOW = (
    Path(__file__).resolve().parents[2] / ".github" / "workflows" / "main.yml"
)


class MainWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_main_push_builds_all_services_for_each_surviving_run(self) -> None:
        self.assertIn(
            'if [[ "$GITHUB_EVENT_NAME" == "push" && '
            '"$GITHUB_REF" == "refs/heads/main" ]]; then',
            self.workflow,
        )
        self.assertIn(
            "changed_services=\"$(jq -c '[.[].service]' "
            'infra/deploy/release-images.json)"',
            self.workflow,
        )
        self.assertIn(
            "changed_services='${{ steps.filter.outputs.changes }}'",
            self.workflow,
        )

    def test_infra_filter_combines_positive_paths_before_applying_exclusion(self) -> None:
        self.assertIn("predicate-quantifier: every", self.workflow)
        infra_rules = self.workflow.split("            infra:\n", maxsplit=1)[1].split(
            "\n\n", maxsplit=1
        )[0]
        patterns = [line.strip().removeprefix("- ").strip("'\"")
                    for line in infra_rules.splitlines()]
        # With `every`, disjoint positive globs exclude every changed file.
        self.assertEqual(1, sum(not pattern.startswith("!") for pattern in patterns))
        self.assertIn("!infra/grafana/sync/**", patterns)

    def test_each_service_build_is_serialized_across_workflow_runs(self) -> None:
        self.assertIn(
            "group: commonex-image-${{ matrix.service.name }}-${{ github.ref }}",
            self.workflow,
        )
        self.assertIn("cancel-in-progress: false", self.workflow)
        self.assertNotIn("queue:", self.workflow)

    def test_build_matrix_uses_the_canonical_release_image_catalog(self) -> None:
        self.assertIn(
            "infra/deploy/release-images.json > services.json",
            self.workflow,
        )
        self.assertIn("name: .service", self.workflow)
        self.assertIn("context: .workflow_build_identity", self.workflow)
        self.assertIn("image_env: .environment_key", self.workflow)
        self.assertIn("repository: .repository", self.workflow)
        self.assertNotIn('"ruggedbl/commonex-nest-backend"', self.workflow)

    def test_deploy_delegates_imperative_protocol_to_orchestrator(self) -> None:
        deploy_job = self.workflow.split("\n  rollback:", maxsplit=1)[0]

        self.assertIn(
            "python3 infra/deploy/production_delivery.py deploy",
            deploy_job,
        )
        self.assertIn(
            "CHANGED_SERVICES: ${{ needs.containers_matrix_prep.outputs.changed-services }}",
            deploy_job,
        )
        self.assertNotIn('ssh commonex-production "stage ', deploy_job)
        self.assertNotIn('ssh commonex-production "validate ', deploy_job)
        self.assertNotIn('ssh commonex-production "deploy ', deploy_job)
        self.assertNotIn('ssh commonex-production "current-images"', deploy_job)
        self.assertNotIn("infra/deploy/verify_public_services.py", deploy_job)
        self.assertNotIn("tar -C release", deploy_job)

    def test_rollback_delegates_imperative_protocol_to_orchestrator(self) -> None:
        rollback_job = self.workflow.split("\n  rollback:", maxsplit=1)[1]

        self.assertIn(
            "python3 infra/deploy/production_delivery.py rollback",
            rollback_job,
        )
        self.assertNotIn('ssh commonex-production "rollback ', rollback_job)
        self.assertNotIn('ssh commonex-production "current-images"', rollback_job)
        self.assertNotIn("infra/deploy/verify_public_services.py", rollback_job)

    def test_github_keeps_production_controls_visible(self) -> None:
        self.assertIn("permissions:\n  contents: read", self.workflow)
        self.assertIn("on:\n  workflow_dispatch:", self.workflow)
        self.assertIn("  push:\n    branches:\n      - main", self.workflow)
        self.assertIn("  pull_request:\n    branches:\n      - main", self.workflow)

        deploy_job = self.workflow.split("\n  deploy:", maxsplit=1)[1].split(
            "\n  rollback:", maxsplit=1
        )[0]
        rollback_job = self.workflow.split("\n  rollback:", maxsplit=1)[1]
        for job in (deploy_job, rollback_job):
            self.assertIn("environment: production", job)
            self.assertIn("group: commonex-production", job)
            self.assertIn("cancel-in-progress: false", job)
            self.assertIn("runs-on: ubuntu-latest", job)

        self.assertIn("github.event_name != 'workflow_dispatch'", deploy_job)
        self.assertIn("github.ref == 'refs/heads/main'", deploy_job)
        self.assertIn("github.event_name == 'workflow_dispatch'", rollback_job)
        self.assertIn("github.ref == 'refs/heads/main'", rollback_job)

    def test_staging_android_guards_deployment_success_despite_skipped_ancestors(self) -> None:
        job = re.search(r"(?ms)^  staging-android:[ \t]*\n(.*?)(?=^  \S|\Z)", self.workflow)
        self.assertIsNotNone(job, "staging-android job is missing")
        needs = re.search(r"(?ms)^    needs:[ \t]*(.*?)(?=^    \S|\Z)", job.group(1))
        self.assertIsNotNone(needs, "staging-android dependency is missing")
        self.assertRegex(needs.group(1), r"(?<![\w-])staging-deploy(?![\w-])")
        condition = re.search(r"(?ms)^    if:[ \t]*(.*?)(?=^    \S|\Z)", job.group(1))
        self.assertIsNotNone(condition, "staging-android needs an explicit condition")
        condition = condition.group(1)
        deployment_result = (
            r"needs\s*(?:\.\s*staging-deploy|\[\s*['\"]staging-deploy['\"]\s*\])"
            r"\s*(?:\.\s*result|\[\s*['\"]result['\"]\s*\])"
        )
        success = r"['\"]success['\"]"
        self.assertRegex(
            condition,
            rf"(?:{deployment_result}\s*==\s*{success}|"
            rf"{success}\s*==\s*{deployment_result})",
        )
        # Require the non-cancelled override; GitHub scheduling still needs a real run.
        self.assertRegex(condition, r"!\s*cancelled\s*\(\s*\)")

    def test_workflow_keeps_secret_materialization_outside_command_arguments(
        self,
    ) -> None:
        deploy_job = self.workflow.split("\n  rollback:", maxsplit=1)[0]
        orchestrator_command = deploy_job.split(
            "python3 infra/deploy/production_delivery.py deploy", maxsplit=1
        )[1]

        for environment in ("staging", "production"):
            self.assertIn(
                "python3 infra/deploy/write_runtime_environment.py "
                f"--environment {environment} release/.env",
                deploy_job,
            )
        self.assertNotIn("cat >> release/.env", deploy_job)
        self.assertNotIn("python3 - <<'PY'", deploy_job)
        self.assertIn("${{ secrets.POSTGRES_PASSWORD }}", deploy_job)
        self.assertNotIn("${{ secrets.", orchestrator_command)


if __name__ == "__main__":
    unittest.main()
