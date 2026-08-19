from pathlib import Path
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
            "MAIN_SERVICES: '[\"backend\",\"frontend\",\"otel-collector\",\"nginx\"]'",
            self.workflow,
        )
        self.assertIn(
            'if [[ "$GITHUB_EVENT_NAME" == "push" && '
            '"$GITHUB_REF" == "refs/heads/main" ]]; then',
            self.workflow,
        )
        self.assertIn('changed_services="$MAIN_SERVICES"', self.workflow)
        self.assertIn(
            "changed_services='${{ steps.filter.outputs.changes }}'",
            self.workflow,
        )

    def test_each_service_build_is_serialized_across_workflow_runs(self) -> None:
        self.assertIn(
            "group: commonex-image-${{ matrix.service.name }}-${{ github.ref }}",
            self.workflow,
        )
        self.assertIn("cancel-in-progress: false", self.workflow)
        self.assertNotIn("queue:", self.workflow)

    def test_deploy_confirms_active_images_after_activation(self) -> None:
        deploy_command = (
            'ssh commonex-production "deploy $GITHUB_SHA $GITHUB_RUN_NUMBER"'
        )
        verification_command = (
            'ssh commonex-production "current-images" > active-images.env'
        )

        self.assertIn(verification_command, self.workflow)
        self.assertLess(
            self.workflow.index(deploy_command),
            self.workflow.index(verification_command),
        )
        self.assertIn(
            "sort release-images.env > expected-images.sorted", self.workflow
        )
        self.assertIn("sort active-images.env > active-images.sorted", self.workflow)
        self.assertIn(
            "cmp -s expected-images.sorted active-images.sorted", self.workflow
        )

    def test_deploy_runs_public_service_verifier_after_activation(self) -> None:
        deploy_job = self.workflow.split("\n  rollback:", maxsplit=1)[0]
        deploy_command = (
            'ssh commonex-production "deploy $GITHUB_SHA $GITHUB_RUN_NUMBER"'
        )
        verification_command = "python3 infra/deploy/verify_public_services.py"

        self.assertIn(verification_command, deploy_job)
        self.assertIn(
            "if: always() && steps.deploy_activation.outcome == 'success'",
            deploy_job,
        )
        self.assertLess(
            deploy_job.index(deploy_command),
            deploy_job.index(verification_command),
        )

    def test_deploy_verifies_committed_exit_two_before_preserving_failure(self) -> None:
        deploy_job = self.workflow.split("\n  rollback:", maxsplit=1)[0]

        self.assertIn("id: deploy_activation", deploy_job)
        self.assertIn(
            'ssh commonex-production "deploy $GITHUB_SHA $GITHUB_RUN_NUMBER" '
            "|| activation_status=$?",
            deploy_job,
        )
        self.assertIn('echo "status=$activation_status" >> "$GITHUB_OUTPUT"', deploy_job)
        self.assertIn(
            'if [[ "$activation_status" -ne 0 && "$activation_status" -ne 2 ]]',
            deploy_job,
        )
        self.assertIn(
            "if: always() && steps.deploy_activation.outputs.status == '2'",
            deploy_job,
        )

    def test_rollback_verifies_active_images_and_public_service_health(self) -> None:
        rollback_job = self.workflow.split("\n  rollback:", maxsplit=1)[1]
        rollback_command = (
            'ssh commonex-production "rollback $RELEASE_SHA $GITHUB_RUN_NUMBER"'
        )
        verification_command = (
            'ssh commonex-production "current-images" > active-images.env'
        )

        self.assertIn(verification_command, rollback_job)
        self.assertLess(
            rollback_job.index(rollback_command),
            rollback_job.index(verification_command),
        )
        health_verification = "python3 infra/deploy/verify_public_services.py"
        self.assertIn(health_verification, rollback_job)
        self.assertIn(
            "if: always() && steps.rollback_activation.outcome == 'success'",
            rollback_job,
        )
        self.assertLess(
            rollback_job.index(verification_command),
            rollback_job.index(health_verification),
        )

    def test_rollback_verifies_committed_exit_two_before_preserving_failure(
        self,
    ) -> None:
        rollback_job = self.workflow.split("\n  rollback:", maxsplit=1)[1]

        self.assertIn("id: rollback_activation", rollback_job)
        self.assertIn(
            'ssh commonex-production "rollback $RELEASE_SHA $GITHUB_RUN_NUMBER" '
            "|| activation_status=$?",
            rollback_job,
        )
        self.assertIn(
            'echo "status=$activation_status" >> "$GITHUB_OUTPUT"', rollback_job
        )
        self.assertIn(
            'if [[ "$activation_status" -ne 0 && "$activation_status" -ne 2 ]]',
            rollback_job,
        )
        self.assertIn(
            "if: always() && steps.rollback_activation.outputs.status == '2'",
            rollback_job,
        )


if __name__ == "__main__":
    unittest.main()
