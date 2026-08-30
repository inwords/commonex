import contextlib
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

from infra.deploy import resolve_release_images as resolver


GIT_SHA = "0123456789abcdef0123456789abcdef01234567"
REPOSITORIES = {
    "COMMONEX_BACKEND_IMAGE": "ruggedbl/commonex-nest-backend",
    "COMMONEX_FRONTEND_IMAGE": "ruggedbl/commonex-next-web",
    "COMMONEX_NGINX_IMAGE": "ruggedbl/nginx-http3",
    "COMMONEX_OTEL_COLLECTOR_IMAGE": "ruggedbl/opentelemetry-collector-custom",
}
DIGESTS = {
    "ruggedbl/commonex-nest-backend": "sha256:" + "a" * 64,
    "ruggedbl/commonex-next-web": "sha256:" + "b" * 64,
    "ruggedbl/nginx-http3": "sha256:" + "c" * 64,
    "ruggedbl/opentelemetry-collector-custom": "sha256:" + "d" * 64,
}


def current_images() -> str:
    return "".join(
        f"{key}={repository}@{DIGESTS[repository]}\n"
        for key, repository in REPOSITORIES.items()
    )


class ResolveReleaseImagesTest(unittest.TestCase):
    def test_changed_services_resolve_sha_tags_and_unchanged_refs_are_reused(self) -> None:
        inspected_tags = []

        def inspect(tag: str) -> str:
            inspected_tags.append(tag)
            repository, separator, version = tag.rpartition(":")
            self.assertEqual(separator, ":")
            self.assertEqual(version, GIT_SHA)
            return DIGESTS[repository]

        output = resolver.resolve_release_images(
            '["backend", "nginx"]', GIT_SHA, current_images(), inspect
        )

        self.assertEqual(
            output,
            "COMMONEX_BACKEND_IMAGE="
            f"ruggedbl/commonex-nest-backend@{DIGESTS['ruggedbl/commonex-nest-backend']}\n"
            "COMMONEX_FRONTEND_IMAGE="
            f"ruggedbl/commonex-next-web@{DIGESTS['ruggedbl/commonex-next-web']}\n"
            "COMMONEX_NGINX_IMAGE="
            f"ruggedbl/nginx-http3@{DIGESTS['ruggedbl/nginx-http3']}\n"
            "COMMONEX_OTEL_COLLECTOR_IMAGE="
            "ruggedbl/opentelemetry-collector-custom@"
            f"{DIGESTS['ruggedbl/opentelemetry-collector-custom']}\n",
        )
        self.assertEqual(
            inspected_tags,
            [
                f"ruggedbl/commonex-nest-backend:{GIT_SHA}",
                f"ruggedbl/nginx-http3:{GIT_SHA}",
            ],
        )

    def test_bootstrap_requires_every_service_and_resolves_only_sha_tags(self) -> None:
        inspected_tags = []

        def inspect(tag: str) -> str:
            inspected_tags.append(tag)
            repository = tag.rsplit(":", 1)[0]
            return DIGESTS[repository]

        resolver.resolve_release_images(
            '["backend", "frontend", "otel-collector", "nginx"]',
            GIT_SHA,
            None,
            inspect,
        )

        self.assertEqual(
            inspected_tags,
            [
                f"ruggedbl/commonex-nest-backend:{GIT_SHA}",
                f"ruggedbl/commonex-next-web:{GIT_SHA}",
                f"ruggedbl/nginx-http3:{GIT_SHA}",
                f"ruggedbl/opentelemetry-collector-custom:{GIT_SHA}",
            ],
        )

    def test_bootstrap_rejects_partial_service_set_before_inspection(self) -> None:
        with self.assertRaisesRegex(
            ValueError, "bootstrap requires all services"
        ):
            resolver.resolve_release_images(
                '["frontend"]',
                GIT_SHA,
                None,
                lambda _tag: self.fail("image inspection must not run"),
            )

    def test_malformed_changed_service_json_fails_before_inspection(self) -> None:
        invalid_values = [
            "not-json",
            "{}",
            '"backend"',
            '["backend", 1]',
            '["backend", "backend"]',
        ]

        for value in invalid_values:
            with self.subTest(value=value), self.assertRaises(ValueError):
                resolver.resolve_release_images(
                    value,
                    GIT_SHA,
                    current_images(),
                    lambda _tag: self.fail("image inspection must not run"),
                )

    def test_unknown_changed_service_fails_before_inspection(self) -> None:
        with self.assertRaisesRegex(ValueError, "changed services are invalid"):
            resolver.resolve_release_images(
                '["database"]',
                GIT_SHA,
                current_images(),
                lambda _tag: self.fail("image inspection must not run"),
            )

    def test_missing_current_key_fails_outside_bootstrap(self) -> None:
        incomplete = current_images().replace(
            "COMMONEX_NGINX_IMAGE="
            f"ruggedbl/nginx-http3@{DIGESTS['ruggedbl/nginx-http3']}\n",
            "",
        )

        with self.assertRaisesRegex(ValueError, "current images are invalid"):
            resolver.resolve_release_images(
                '["backend"]',
                GIT_SHA,
                incomplete,
                lambda _tag: self.fail("image inspection must not run"),
            )

    def test_current_images_reject_unknown_keys_repositories_and_digests(self) -> None:
        valid = current_images()
        invalid_values = [
            valid + "SECRET=value\n",
            valid.replace("ruggedbl/nginx-http3", "attacker/nginx"),
            valid.replace("sha256:" + "a" * 64, "sha256:" + "A" * 64),
            valid.replace("sha256:" + "b" * 64, "sha512:" + "b" * 64),
            valid.replace("\nCOMMONEX_FRONTEND_IMAGE", "\n\nCOMMONEX_FRONTEND_IMAGE"),
        ]

        for value in invalid_values:
            with self.subTest(value=value), self.assertRaisesRegex(
                ValueError, "current images are invalid"
            ):
                resolver.resolve_release_images(
                    "[]",
                    GIT_SHA,
                    value,
                    lambda _tag: self.fail("image inspection must not run"),
                )

    def test_output_is_exactly_the_four_allowlisted_assignments(self) -> None:
        output = resolver.resolve_release_images(
            "[]", GIT_SHA, current_images(), lambda _tag: self.fail("unused")
        )

        self.assertEqual(output.count("\n"), 4)
        self.assertEqual(
            output.splitlines(),
            [
                "COMMONEX_BACKEND_IMAGE="
                f"ruggedbl/commonex-nest-backend@{DIGESTS['ruggedbl/commonex-nest-backend']}",
                "COMMONEX_FRONTEND_IMAGE="
                f"ruggedbl/commonex-next-web@{DIGESTS['ruggedbl/commonex-next-web']}",
                "COMMONEX_NGINX_IMAGE="
                f"ruggedbl/nginx-http3@{DIGESTS['ruggedbl/nginx-http3']}",
                "COMMONEX_OTEL_COLLECTOR_IMAGE="
                "ruggedbl/opentelemetry-collector-custom@"
                f"{DIGESTS['ruggedbl/opentelemetry-collector-custom']}",
            ],
        )

    def test_manifest_inspection_uses_buildx_json_and_validates_digest(self) -> None:
        calls = []

        def run(command, **kwargs):
            calls.append((command, kwargs))
            return subprocess.CompletedProcess(
                command,
                0,
                stdout=json.dumps(
                    {
                        "mediaType": "application/vnd.oci.image.manifest.v1+json",
                        "digest": "sha256:" + "e" * 64,
                        "size": 1234,
                    }
                )
                + "\n",
                stderr="",
            )

        digest = resolver.resolve_manifest_digest("repository/image:tag", run)

        self.assertEqual(digest, "sha256:" + "e" * 64)
        self.assertEqual(
            calls,
            [
                (
                    [
                        "docker",
                        "buildx",
                        "imagetools",
                        "inspect",
                        "repository/image:tag",
                        "--format",
                        "{{json .Manifest}}",
                    ],
                    {
                        "check": True,
                        "capture_output": True,
                        "text": True,
                        "encoding": "utf-8",
                    },
                )
            ],
        )

    def test_manifest_inspection_rejects_malformed_json_or_digest(self) -> None:
        invalid_outputs = [
            "not-json",
            "[]",
            '{"digest":"sha256:' + "A" * 64 + '"}',
            '{"digest":"sha512:' + "a" * 64 + '"}',
            '{"mediaType":"example"}',
        ]

        for output in invalid_outputs:
            with self.subTest(output=output), self.assertRaisesRegex(
                ValueError, "manifest response is invalid"
            ):
                resolver.resolve_manifest_digest(
                    "repository/image:tag",
                    lambda command, **_kwargs: subprocess.CompletedProcess(
                        command, 0, stdout=output, stderr="sensitive"
                    ),
                )

    def test_registry_failure_is_not_retried_with_latest(self) -> None:
        inspected_tags = []

        def inspect(tag: str) -> str:
            inspected_tags.append(tag)
            raise subprocess.CalledProcessError(1, ["docker", "buildx"])

        with self.assertRaises(subprocess.CalledProcessError):
            resolver.resolve_release_images(
                '["backend"]', GIT_SHA, current_images(), inspect
            )

        self.assertEqual(
            inspected_tags,
            [f"ruggedbl/commonex-nest-backend:{GIT_SHA}"],
        )
        self.assertNotIn("latest", inspected_tags)

    def test_git_sha_is_strictly_validated_before_inspection(self) -> None:
        for value in ["a" * 39, "A" * 40, "g" * 40, GIT_SHA + "1"]:
            with self.subTest(value=value), self.assertRaisesRegex(
                ValueError, "Git SHA is invalid"
            ):
                resolver.resolve_release_images(
                    '["backend"]',
                    value,
                    current_images(),
                    lambda _tag: self.fail("image inspection must not run"),
                )

    def test_cli_accepts_optional_current_images_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            current_path = Path(directory) / "current-images.env"
            current_path.write_text(current_images(), encoding="utf-8")
            output = io.StringIO()

            with contextlib.redirect_stdout(output):
                resolver.main(
                    ["[]", GIT_SHA, str(current_path)],
                    lambda _tag: self.fail("image inspection must not run"),
                )

        self.assertEqual(output.getvalue(), current_images())

    def test_cli_failure_does_not_echo_inputs_or_inspection_errors(self) -> None:
        stderr = io.StringIO()

        with contextlib.redirect_stderr(stderr):
            status = resolver.run_cli(
                ['["database-secret"]', GIT_SHA],
                lambda _tag: "registry-secret",
            )

        self.assertEqual(status, 1)
        self.assertEqual(
            stderr.getvalue(),
            "resolve-release-images: unable to resolve release images\n",
        )
        self.assertNotIn("database-secret", stderr.getvalue())
        self.assertNotIn("registry-secret", stderr.getvalue())

    def test_cli_reports_catalog_failure_with_existing_generic_diagnostic(self) -> None:
        stderr = io.StringIO()

        with mock.patch.object(
            resolver,
            "load_release_image_catalog",
            side_effect=ValueError("catalog-secret"),
        ), contextlib.redirect_stderr(stderr):
            status = resolver.run_cli(
                ['["backend", "frontend", "nginx", "otel-collector"]', GIT_SHA],
                lambda _tag: self.fail("image inspection must not run"),
            )

        self.assertEqual(status, 1)
        self.assertEqual(
            stderr.getvalue(),
            "resolve-release-images: unable to resolve release images\n",
        )
        self.assertNotIn("catalog-secret", stderr.getvalue())


if __name__ == "__main__":
    unittest.main()
