import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import uuid


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(os.environ.get("COMMONEX_COLLECTOR_TEST_IMAGE"),
                     "Set COMMONEX_COLLECTOR_TEST_IMAGE to a built custom Collector image")
class PostgreSQLFilesystemTests(unittest.TestCase):
    def test_named_volume_emits_filesystem_metrics_only_for_database_mount(self) -> None:
        source = (REPOSITORY_ROOT / "infra/otel-collector/otel-collector-config.yaml").read_text()
        receiver = source.split("  hostmetrics/postgresql_volume:\n", 1)[1].split("\nprocessors:", 1)[0]
        config = ("receivers:\n  hostmetrics/postgresql_volume:\n" +
                  receiver.replace("collection_interval: 30s", "collection_interval: 1s") +
                  "\nexporters:\n  debug:\n    verbosity: detailed\n"
                  "service:\n  pipelines:\n    metrics:\n"
                  "      receivers: [hostmetrics/postgresql_volume]\n"
                  "      exporters: [debug]\n")
        volumes = ["commonex-filesystem-test-" + uuid.uuid4().hex for _ in range(2)]
        created = []
        try:
            for volume in volumes:
                subprocess.run(["docker", "volume", "create", volume], check=True,
                               capture_output=True, text=True)
                created.append(volume)
            with tempfile.TemporaryDirectory(prefix="commonex-filesystem-test-") as directory:
                config_path = Path(directory) / "collector.yaml"
                config_path.write_text(config)
                result = subprocess.run([
                    "docker", "run", "--rm", "--pull=never", "--network=none",
                    "--mount", f"type=volume,src={volumes[0]},dst=/var/lib/postgresql/data,readonly",
                    "--mount", f"type=volume,src={volumes[1]},dst=/unrelated-volume,readonly",
                    "--mount", f"type=bind,src={config_path},dst=/test.yaml,readonly",
                    "--entrypoint", "/bin/sh", os.environ["COMMONEX_COLLECTOR_TEST_IMAGE"],
                    "-c", "/otelcol-custom --config=/test.yaml & collector_pid=$!; "
                    "sleep 5; kill -TERM $collector_pid; wait $collector_pid",
                ], capture_output=True, text=True, timeout=30)
                self.assertEqual(result.returncode, 0, result.stderr)
                output = result.stdout + result.stderr
                self.assertIn("Name: system.filesystem.usage", output)
                self.assertIn("Name: system.filesystem.inodes.usage", output)
                self.assertIn("mountpoint: Str(/var/lib/postgresql/data)", output)
                self.assertNotIn("mountpoint: Str(/unrelated-volume)", output)
        finally:
            for volume in created:
                subprocess.run(["docker", "volume", "rm", volume], check=True,
                               capture_output=True, text=True)


if __name__ == "__main__":
    unittest.main()
