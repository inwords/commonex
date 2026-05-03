from __future__ import annotations

import argparse
import base64
import datetime as dt
import http.client
import json
import os
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path
from typing import Any


API_BASE = "https://public-api.rustore.ru"
PUBLISH_TYPE = "MANUAL"
REQUEST_TIMEOUT_SECONDS = 60
UPLOAD_TIMEOUT_SECONDS = 300


class StageError(RuntimeError):
    def __init__(self, stage: str, message: str) -> None:
        super().__init__(f"{stage}: {message}")
        self.stage = stage
        self.message = message


def unwrap_api_response(stage: str, payload: dict[str, Any]) -> Any:
    code = payload.get("code")
    if code != "OK":
        message = payload.get("message") or "RuStore API returned a non-OK response."
        raise StageError(stage, str(message))
    return payload.get("body")


def request_json(
    *,
    stage: str,
    method: str,
    url: str,
    token: str | None = None,
    body: dict[str, Any] | None = None,
) -> Any:
    headers = {
        "Accept": "application/json",
    }
    data = None
    if token is not None:
        headers["Public-Token"] = token
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode("utf-8")

    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        details = exc.read().decode("utf-8", errors="replace")
        raise StageError(stage, f"HTTP {exc.code}: {details}") from exc
    except urllib.error.URLError as exc:
        raise StageError(stage, f"Network error: {exc.reason}") from exc
    except json.JSONDecodeError as exc:
        raise StageError(stage, f"Invalid JSON response: {exc}") from exc

    if not isinstance(payload, dict):
        raise StageError(stage, "Invalid RuStore API response payload.")
    return unwrap_api_response(stage, payload)


def private_key_debug_info(private_key: str, key_path: Path) -> str:
    lines = private_key.splitlines()
    first_line = lines[0] if lines else "<empty>"
    last_line = lines[-1] if lines else "<empty>"
    return (
        f"path={key_path}; "
        f"chars={len(private_key)}; "
        f"lines={len(lines)}; "
        f"contains_literal_backslash_n={'\\\\n' in private_key}; "
        f"contains_cr={'\\r' in private_key}; "
        f"starts_with_begin={first_line.startswith('-----BEGIN ')}; "
        f"ends_with_end={last_line.startswith('-----END ')}; "
        f"first_line={first_line!r}; "
        f"last_line={last_line!r}"
    )


def sign_with_openssl(key_id: str, private_key: str, timestamp: str) -> str:
    message = f"{key_id}{timestamp}".encode("utf-8")
    with tempfile.TemporaryDirectory(prefix="rustore-auth-") as temp_dir:
        temp_path = Path(temp_dir)
        key_path = temp_path / "key.pem"
        message_path = temp_path / "message.txt"
        key_path.write_text(private_key, encoding="utf-8", newline="\n")
        message_path.write_bytes(message)

        try:
            result = subprocess.run(
                [
                    "openssl",
                    "dgst",
                    "-sha512",
                    "-sign",
                    str(key_path),
                    "-binary",
                    str(message_path),
                ],
                check=True,
                capture_output=True,
            )
        except FileNotFoundError as exc:
            raise StageError("authenticate", "OpenSSL is required to sign the RuStore auth payload.") from exc
        except subprocess.CalledProcessError as exc:
            stderr = exc.stderr.decode("utf-8", errors="replace")
            diagnostics = private_key_debug_info(private_key, key_path)
            raise StageError(
                "authenticate",
                f"OpenSSL signing failed: {stderr} | key_debug: {diagnostics}",
            ) from exc

    return base64.b64encode(result.stdout).decode("ascii")


def authenticate(key_id: str, private_key: str) -> str:
    normalized_key_id = key_id.strip()
    timestamp = dt.datetime.now(dt.timezone.utc).astimezone().isoformat(timespec="milliseconds")
    signature = sign_with_openssl(normalized_key_id, private_key, timestamp)
    body = {
        "keyId": normalized_key_id,
        "timestamp": timestamp,
        "signature": signature,
    }
    try:
        result = request_json(
            stage="authenticate",
            method="POST",
            url=f"{API_BASE}/public/auth",
            body=body,
        )
    except StageError as exc:
        if "Signature encode error" in exc.message:
            raise StageError(
                "authenticate",
                f"{exc.message} (debug: key_id={normalized_key_id!r}, key_id_len={len(normalized_key_id)}, "
                f"timestamp={timestamp!r}, signature_len={len(signature)})",
            ) from exc
        raise
    if not isinstance(result, dict) or not isinstance(result.get("jwe"), str):
        raise StageError("authenticate", "RuStore auth response did not include a token.")
    return result["jwe"]


def list_versions(token: str, package_name: str) -> list[dict[str, Any]]:
    url = (
        f"{API_BASE}/public/v1/application/{urllib.parse.quote(package_name, safe='')}/version"
        "?filterTestingType=ALL&page=0&size=100"
    )
    result = request_json(
        stage="list-versions",
        method="GET",
        url=url,
        token=token,
    )
    if not isinstance(result, dict):
        raise StageError("list-versions", "RuStore returned an invalid versions payload.")
    content = result.get("content", [])
    if not isinstance(content, list):
        raise StageError("list-versions", "RuStore returned an invalid versions list.")
    return [item for item in content if isinstance(item, dict)]


def create_draft(token: str, package_name: str, whats_new: str) -> int:
    url = f"{API_BASE}/public/v1/application/{urllib.parse.quote(package_name, safe='')}/version"
    result = request_json(
        stage="create-draft",
        method="POST",
        url=url,
        token=token,
        body={
            "whatsNew": whats_new,
            "publishType": PUBLISH_TYPE,
        },
    )
    if not isinstance(result, int):
        raise StageError("create-draft", "RuStore did not return a numeric draft version ID.")
    return result


def ensure_draft(token: str, package_name: str, whats_new: str) -> tuple[int, str]:
    versions = list_versions(token, package_name)
    drafts = [version for version in versions if version.get("versionStatus") == "DRAFT"]
    if drafts:
        raise StageError(
            "draft-exists",
            "A RuStore draft already exists for this app. "
            "Delete or publish the existing draft before running the workflow again.",
        )
    return create_draft(token, package_name, whats_new), "created"


def upload_aab(token: str, package_name: str, version_id: int, aab_path: Path) -> None:
    if not aab_path.is_file():
        raise StageError("upload-aab", f"AAB file not found: {aab_path}")

    parsed = urllib.parse.urlsplit(
        f"{API_BASE}/public/v1/application/{urllib.parse.quote(package_name, safe='')}/version/{version_id}/aab"
    )
    boundary = f"----CommonExRuStoreBoundary{uuid.uuid4().hex}"
    filename = aab_path.name
    preamble = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        "Content-Type: application/octet-stream\r\n\r\n"
    ).encode("utf-8")
    epilogue = f"\r\n--{boundary}--\r\n".encode("utf-8")
    content_length = len(preamble) + aab_path.stat().st_size + len(epilogue)

    connection = http.client.HTTPSConnection(
        parsed.hostname,
        parsed.port or 443,
        timeout=UPLOAD_TIMEOUT_SECONDS,
    )
    try:
        connection.putrequest("POST", parsed.path)
        connection.putheader("Accept", "application/json")
        connection.putheader("Public-Token", token)
        connection.putheader("Content-Type", f"multipart/form-data; boundary={boundary}")
        connection.putheader("Content-Length", str(content_length))
        connection.endheaders()
        connection.send(preamble)
        with aab_path.open("rb") as handle:
            while True:
                chunk = handle.read(1024 * 1024)
                if not chunk:
                    break
                connection.send(chunk)
        connection.send(epilogue)
        response = connection.getresponse()
        payload = response.read().decode("utf-8", errors="replace")
    except OSError as exc:
        raise StageError("upload-aab", f"Network error: {exc}") from exc
    finally:
        connection.close()

    if response.status >= 400:
        raise StageError("upload-aab", f"HTTP {response.status}: {payload}")
    try:
        decoded = json.loads(payload)
    except json.JSONDecodeError as exc:
        raise StageError("upload-aab", f"Invalid JSON response: {payload}") from exc
    if not isinstance(decoded, dict):
        raise StageError("upload-aab", "RuStore returned an invalid upload response.")
    unwrap_api_response("upload-aab", decoded)


def set_publish_settings(token: str, package_name: str, version_id: int) -> None:
    url = (
        f"{API_BASE}/public/v1/application/{urllib.parse.quote(package_name, safe='')}"
        f"/version/{version_id}/publish-settings"
    )
    request_json(
        stage="publish-settings",
        method="POST",
        url=url,
        token=token,
        body={"publishType": PUBLISH_TYPE},
    )


def submit_for_moderation(token: str, package_name: str, version_id: int) -> None:
    url = (
        f"{API_BASE}/public/v1/application/{urllib.parse.quote(package_name, safe='')}"
        f"/version/{version_id}/commit?priorityUpdate=0"
    )
    request_json(
        stage="submit",
        method="POST",
        url=url,
        token=token,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Publish an Android release to RuStore.")
    parser.add_argument("--key-id", required=True)
    parser.add_argument("--package-name", required=True)
    parser.add_argument("--aab-path", required=True)
    whats_new_group = parser.add_mutually_exclusive_group(required=True)
    whats_new_group.add_argument("--whats-new")
    whats_new_group.add_argument("--whats-new-file")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    private_key = os.environ.get("RUSTORE_PRIVATE_KEY")
    if not private_key:
        print("RUSTORE_PRIVATE_KEY environment variable is required.", file=sys.stderr)
        return 1
    aab_path = Path(args.aab_path)
    whats_new = args.whats_new
    if args.whats_new_file is not None:
        whats_new_path = Path(args.whats_new_file)
        try:
            whats_new = whats_new_path.read_text(encoding="utf-8")
        except FileNotFoundError:
            print(f"whats-new: file not found: {whats_new_path}", file=sys.stderr)
            return 1

    try:
        token = authenticate(args.key_id, private_key)
        version_id, draft_action = ensure_draft(token, args.package_name, whats_new)
        upload_aab(token, args.package_name, version_id, aab_path)
        set_publish_settings(token, args.package_name, version_id)
        submit_for_moderation(token, args.package_name, version_id)
    except StageError as exc:
        print(f"{exc.stage}: {exc.message}", file=sys.stderr)
        return 1

    print(f"RuStore draft {draft_action}: versionId={version_id}")
    print(f"RuStore submission completed: versionId={version_id}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
