"""Validated access to the repository-owned production image catalog."""

from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
import re


CATALOG_PATH = Path(__file__).with_name("release-images.json")
CATALOG_SIZE = 4
CATALOG_FIELDS = frozenset(
    {
        "service",
        "environment_key",
        "repository",
        "workflow_build_identity",
    }
)
SERVICE_PATTERN = re.compile(r"^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$")
ENVIRONMENT_KEY_PATTERN = re.compile(r"^[A-Z][A-Z0-9_]*_IMAGE$")
REPOSITORY_PATTERN = re.compile(
    r"^[a-z0-9]+(?:[._-][a-z0-9]+)*/[a-z0-9]+(?:[._-][a-z0-9]+)*$"
)
BUILD_IDENTITY_PATTERN = re.compile(
    r"^\./[a-z0-9]+(?:[._-][a-z0-9]+)*(?:/[a-z0-9]+(?:[._-][a-z0-9]+)*)*$"
)
JsonObject = dict[str, object]


@dataclass(frozen=True)
class ReleaseImage:
    """One production image and its workflow build identity."""

    service: str
    environment_key: str
    repository: str
    workflow_build_identity: str


def _unique_json_object(pairs: list[tuple[str, object]]) -> JsonObject:
    result: JsonObject = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("release image catalog contains a duplicate key")
        result[key] = value
    return result


def load_release_image_catalog(
    path: Path = CATALOG_PATH,
) -> tuple[ReleaseImage, ...]:
    """Load and strictly validate the closed four-image production catalog."""

    try:
        raw_catalog = json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=_unique_json_object,
        )
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValueError("release image catalog is invalid") from error

    if not isinstance(raw_catalog, list) or len(raw_catalog) != CATALOG_SIZE:
        raise ValueError("release image catalog must contain exactly four images")

    images: list[ReleaseImage] = []
    for raw_image in raw_catalog:
        if not isinstance(raw_image, dict) or set(raw_image) != CATALOG_FIELDS:
            raise ValueError("release image catalog entry has invalid fields")
        if any(not isinstance(value, str) for value in raw_image.values()):
            raise ValueError("release image catalog entry has invalid values")

        image = ReleaseImage(**raw_image)
        if (
            SERVICE_PATTERN.fullmatch(image.service) is None
            or ENVIRONMENT_KEY_PATTERN.fullmatch(image.environment_key) is None
            or REPOSITORY_PATTERN.fullmatch(image.repository) is None
            or BUILD_IDENTITY_PATTERN.fullmatch(image.workflow_build_identity) is None
        ):
            raise ValueError("release image catalog entry has invalid values")
        images.append(image)

    if [image.service for image in images] != sorted(
        image.service for image in images
    ):
        raise ValueError("release image catalog entries are not ordered by service")

    for attribute in (
        "service",
        "environment_key",
        "repository",
        "workflow_build_identity",
    ):
        values = [getattr(image, attribute) for image in images]
        if len(values) != len(set(values)):
            raise ValueError(f"release image catalog has duplicate {attribute}")

    return tuple(images)
