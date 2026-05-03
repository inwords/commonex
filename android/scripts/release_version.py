from __future__ import annotations

import argparse
import datetime as dt
import re
import sys
from pathlib import Path


ANDROID_VERSION_CODE_RE = re.compile(r"^(\s*versionCode\s*=\s*)(\d+)(\s*)$", re.MULTILINE)
ANDROID_VERSION_NAME_RE = re.compile(r'^(\s*versionName\s*=\s*")([^"]+)(".*)$', re.MULTILINE)
ANDROID_APPLICATION_ID_RE = re.compile(r'^(\s*applicationId\s*=\s*")([^"]+)(".*)$', re.MULTILINE)
IOS_MARKETING_VERSION_RE = re.compile(r"(\bMARKETING_VERSION\s*=\s*)([^;]+)(;)")
IOS_PROJECT_VERSION_RE = re.compile(r"(\bCURRENT_PROJECT_VERSION\s*=\s*)(\d+)(;)")
PREP_BRANCH_RE = re.compile(r"^release/prep/(\d{4})-(\d{2})-(\d+)/(\d+)$")


def fail(message: str) -> None:
    raise SystemExit(message)


def parse_release_date(value: str) -> dt.date:
    try:
        return dt.datetime.strptime(value, "%Y-%m-%d").date()
    except ValueError as exc:
        raise SystemExit(f"Invalid date '{value}'. Expected YYYY-MM-DD.") from exc


def parse_positive_int(name: str, value: str) -> int:
    try:
        parsed = int(value)
    except ValueError as exc:
        raise SystemExit(f"Invalid {name} '{value}'. Expected an integer.") from exc
    if parsed < 1:
        raise SystemExit(f"Invalid {name} '{value}'. Expected a positive integer.")
    return parsed


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError as exc:
        raise SystemExit(f"File not found: {path}") from exc


def write_text(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8", newline="\n")


def parse_current_version_code(android_build_file: Path) -> int:
    content = read_text(android_build_file)
    match = ANDROID_VERSION_CODE_RE.search(content)
    if match is None:
        fail(f"Could not find versionCode in {android_build_file}")
    return int(match.group(2))


def parse_application_id(android_build_file: Path) -> str:
    content = read_text(android_build_file)
    match = ANDROID_APPLICATION_ID_RE.search(content)
    if match is None:
        fail(f"Could not find applicationId in {android_build_file}")
    return match.group(2)


def compute_metadata(release_date: dt.date, release_n: int, patch: int, current_version_code: int) -> dict[str, str]:
    version_name = f"{release_date.year:04d}.{release_date.month:02d}.{release_n}"
    version_code = str(current_version_code + 1)
    branch_name = f"release/prep/{release_date.year:04d}-{release_date.month:02d}-{release_n}/{patch}"
    tag_name = f"release/{release_date.year:04d}-{release_date.month:02d}-{release_n}/{patch}"
    pr_title = f"Release {version_name}/{patch}"
    return {
        "version_name": version_name,
        "version_code": version_code,
        "branch_name": branch_name,
        "tag_name": tag_name,
        "pr_title": pr_title,
    }


def write_github_output(path: Path, values: dict[str, str]) -> None:
    with path.open("a", encoding="utf-8", newline="\n") as handle:
        for key, value in values.items():
            handle.write(f"{key}={value}\n")


def metadata_command(args: argparse.Namespace) -> int:
    release_date = parse_release_date(args.date)
    release_n = parse_positive_int("release_n", args.release_n)
    patch = parse_positive_int("patch", args.patch)
    android_build_file = Path(args.android_build_file)
    current_version_code = parse_current_version_code(android_build_file)
    metadata = compute_metadata(release_date, release_n, patch, current_version_code)
    if args.github_output:
        write_github_output(Path(args.github_output), metadata)
    else:
        for key, value in metadata.items():
            print(f"{key}={value}")
    return 0


def bump_command(args: argparse.Namespace) -> int:
    version_code = parse_positive_int("version_code", args.version_code)
    version_name = args.version_name
    android_build_file = Path(args.android_build_file)
    ios_project_file = Path(args.ios_project_file)

    android_content = read_text(android_build_file)
    android_content, version_code_count = ANDROID_VERSION_CODE_RE.subn(
        rf"\g<1>{version_code}\g<3>",
        android_content,
        count=1,
    )
    android_content, version_name_count = ANDROID_VERSION_NAME_RE.subn(
        rf'\g<1>{version_name}\g<3>',
        android_content,
        count=1,
    )
    if version_code_count != 1 or version_name_count != 1:
        fail(f"Failed to update Android version fields in {android_build_file}")
    write_text(android_build_file, android_content)

    ios_content = read_text(ios_project_file)
    ios_content, marketing_count = IOS_MARKETING_VERSION_RE.subn(
        rf"\g<1>{version_name}\g<3>",
        ios_content,
    )
    ios_content, project_version_count = IOS_PROJECT_VERSION_RE.subn(
        rf"\g<1>{version_code}\g<3>",
        ios_content,
    )
    if marketing_count < 1 or project_version_count < 1:
        fail(f"Failed to update iOS version fields in {ios_project_file}")
    write_text(ios_project_file, ios_content)
    return 0


def tag_from_branch_command(args: argparse.Namespace) -> int:
    branch = args.branch
    match = PREP_BRANCH_RE.fullmatch(branch)
    if match is None:
        fail(
            "Invalid release branch "
            f"'{branch}'. Expected release/prep/YYYY-MM-N/P."
        )
    year, month, release_n, patch = match.groups()
    values = {
        "tag_name": f"release/{year}-{month}-{release_n}/{patch}",
        "version_name": f"{year}.{month}.{release_n}",
        "patch": patch,
    }
    if args.github_output:
        write_github_output(Path(args.github_output), values)
    else:
        for key, value in values.items():
            print(f"{key}={value}")
    return 0


def package_name_command(args: argparse.Namespace) -> int:
    package_name = parse_application_id(Path(args.android_build_file))
    values = {
        "package_name": package_name,
    }
    if args.github_output:
        write_github_output(Path(args.github_output), values)
    else:
        print(package_name)
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Compute and apply CommonEx mobile release metadata."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    metadata_parser = subparsers.add_parser("metadata", help="Compute release metadata.")
    metadata_parser.add_argument("--date", required=True)
    metadata_parser.add_argument("--release-n", required=True)
    metadata_parser.add_argument("--patch", required=True)
    metadata_parser.add_argument("--android-build-file", required=True)
    metadata_parser.add_argument("--github-output")
    metadata_parser.set_defaults(func=metadata_command)

    bump_parser = subparsers.add_parser("bump", help="Apply version bump to Android and iOS files.")
    bump_parser.add_argument("--android-build-file", required=True)
    bump_parser.add_argument("--ios-project-file", required=True)
    bump_parser.add_argument("--version-name", required=True)
    bump_parser.add_argument("--version-code", required=True)
    bump_parser.set_defaults(func=bump_command)

    tag_parser = subparsers.add_parser("tag-from-branch", help="Convert a release prep branch to a release tag.")
    tag_parser.add_argument("--branch", required=True)
    tag_parser.add_argument("--github-output")
    tag_parser.set_defaults(func=tag_from_branch_command)

    package_name_parser = subparsers.add_parser("package-name", help="Extract Android applicationId.")
    package_name_parser.add_argument("--android-build-file", required=True)
    package_name_parser.add_argument("--github-output")
    package_name_parser.set_defaults(func=package_name_command)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
