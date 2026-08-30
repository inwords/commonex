import json
from pathlib import Path
import tempfile
import unittest

from infra.deploy import commonex_deploy
from infra.deploy import release_image_catalog as catalog


EXPECTED_IMAGES = (
    catalog.ReleaseImage(
        service="backend",
        environment_key="COMMONEX_BACKEND_IMAGE",
        repository="ruggedbl/commonex-nest-backend",
        workflow_build_identity="./backend",
    ),
    catalog.ReleaseImage(
        service="frontend",
        environment_key="COMMONEX_FRONTEND_IMAGE",
        repository="ruggedbl/commonex-next-web",
        workflow_build_identity="./web",
    ),
    catalog.ReleaseImage(
        service="nginx",
        environment_key="COMMONEX_NGINX_IMAGE",
        repository="ruggedbl/nginx-http3",
        workflow_build_identity="./infra/nginx",
    ),
    catalog.ReleaseImage(
        service="otel-collector",
        environment_key="COMMONEX_OTEL_COLLECTOR_IMAGE",
        repository="ruggedbl/opentelemetry-collector-custom",
        workflow_build_identity="./infra/otel-collector",
    ),
)


def serialized(images=EXPECTED_IMAGES) -> str:
    return json.dumps(
        [
            {
                "service": image.service,
                "environment_key": image.environment_key,
                "repository": image.repository,
                "workflow_build_identity": image.workflow_build_identity,
            }
            for image in images
        ]
    )


class ReleaseImageCatalogTest(unittest.TestCase):
    def load_text(self, value: str):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "release-images.json"
            path.write_text(value, encoding="utf-8")
            return catalog.load_release_image_catalog(path)

    def test_repository_catalog_is_the_exact_ordered_four_image_set(self) -> None:
        self.assertEqual(catalog.load_release_image_catalog(), EXPECTED_IMAGES)

    def test_catalog_matches_standalone_host_allowlist(self) -> None:
        catalog_allowlist = {
            image.environment_key: image.repository
            for image in catalog.load_release_image_catalog()
        }

        self.assertEqual(
            catalog_allowlist,
            commonex_deploy.IMMUTABLE_IMAGE_REPOSITORIES,
        )

    def test_catalog_rejects_wrong_size_or_entry_shape(self) -> None:
        missing_entry = serialized(EXPECTED_IMAGES[:-1])
        extra_field = json.loads(serialized())
        extra_field[0]["dockerfile"] = "Dockerfile"
        missing_field = json.loads(serialized())
        del missing_field[0]["repository"]

        for value in (
            "{}",
            missing_entry,
            json.dumps(extra_field),
            json.dumps(missing_field),
        ):
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.load_text(value)

    def test_catalog_rejects_missing_or_malformed_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            missing = Path(directory) / "missing.json"
            with self.assertRaisesRegex(ValueError, "catalog is invalid"):
                catalog.load_release_image_catalog(missing)

        with self.assertRaisesRegex(ValueError, "catalog is invalid"):
            self.load_text("not-json")

    def test_catalog_rejects_duplicate_json_keys_and_identifiers(self) -> None:
        duplicate_json_key = serialized().replace(
            '"service": "backend"',
            '"service": "backend", "service": "backend-copy"',
            1,
        )

        for attribute in (
            "service",
            "environment_key",
            "repository",
            "workflow_build_identity",
        ):
            images = [
                catalog.ReleaseImage(
                    **{
                        **image.__dict__,
                        attribute: getattr(EXPECTED_IMAGES[0], attribute),
                    }
                )
                if index == 1
                else image
                for index, image in enumerate(EXPECTED_IMAGES)
            ]
            with self.subTest(attribute=attribute), self.assertRaises(ValueError):
                self.load_text(serialized(images))

        with self.assertRaises(ValueError):
            self.load_text(duplicate_json_key)

    def test_catalog_rejects_invalid_values_and_noncanonical_order(self) -> None:
        invalid_values = (
            ("service", "Backend"),
            ("environment_key", "COMMONEX_BACKEND"),
            ("repository", "ruggedbl/commonex-nest-backend:latest"),
            ("workflow_build_identity", "../backend"),
        )

        for attribute, value in invalid_values:
            images = list(EXPECTED_IMAGES)
            images[0] = catalog.ReleaseImage(
                **{**images[0].__dict__, attribute: value}
            )
            with self.subTest(attribute=attribute), self.assertRaises(ValueError):
                self.load_text(serialized(images))

        with self.assertRaisesRegex(ValueError, "not ordered by service"):
            self.load_text(serialized(reversed(EXPECTED_IMAGES)))


if __name__ == "__main__":
    unittest.main()
