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


if __name__ == "__main__":
    unittest.main()
