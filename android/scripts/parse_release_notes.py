from __future__ import annotations

import argparse
import sys
from pathlib import Path


def fail(message: str) -> int:
    print(message, file=sys.stderr)
    return 1


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate and extract release notes from a plain-text file.")
    parser.add_argument("--file", required=True)
    parser.add_argument("--max-length", required=True, type=int)
    args = parser.parse_args()

    path = Path(args.file)
    try:
        content = path.read_text(encoding="utf-8")
    except FileNotFoundError:
        return fail(f"Release notes file not found: {path}")

    text = content.strip()
    if not text:
        return fail(f"Release notes are empty in {path}.")

    if len(text) > args.max_length:
        return fail(f"Release notes exceed {args.max_length} characters in {path}.")

    print(text, end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())
