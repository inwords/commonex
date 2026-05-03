# RuStore publishing

This flow publishes a release tag to RuStore after the mobile release PR has been merged and tagged.

## Files

- `.github/workflows/android-rustore-publish.yml`
- `android/scripts/parse_release_notes.py`
- `android/scripts/rustore_publish.py`
- `android/release-notes/ru-RU.txt`

## Publish workflow

Workflow: `.github/workflows/android-rustore-publish.yml`

Triggers:

- tag push matching `release/**`

Behavior:

1. Resolves and validates the release tag.
2. Checks out the tagged revision.
3. Builds a signed `:app:bundleRelease`.
4. Validates `android/release-notes/ru-RU.txt`.
5. Extracts the Russian notes and stages them together with the AAB under one artifact root.
6. Waits at the protected `rustore-production` environment gate.
7. Derives package name from tagged `android/app/build.gradle.kts`.
8. Authenticates with the RuStore API.
9. Fails if a RuStore draft already exists for the app.
10. Creates a new draft with `publishType=MANUAL`.
11. Uploads the AAB and submits the draft for moderation.
12. Uploads the staged AAB, parsed notes, and publish log artifacts with 90-day retention.

## Secrets and environment

Repository or organization secrets:

- `KEYSTORE_JKS_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`
- `RUSTORE_KEY_ID`
- `RUSTORE_PRIVATE_KEY`
- `SENTRY_AUTH_TOKEN`

Protected environment:

- `rustore-production`

The environment is used as an approval gate before the RuStore API call. The package name is not stored as a secret; it is derived from the Android `applicationId`.
`RUSTORE_PRIVATE_KEY` must be provided as a single-line base64-encoded private key string from RuStore Console (non-PEM format).

## Release notes

File:

- `android/release-notes/ru-RU.txt`

Format:

- plain UTF-8 text
- human-readable line breaks are preserved directly in the file
- the file contains the Russian notes sent to RuStore

Validation performed by `android/scripts/parse_release_notes.py`:

- file must exist
- file must be non-empty after trimming
- text must not exceed the configured max length

Current publish behavior:

- The workflow publishes `ru-RU.txt` to RuStore.
- `en-US.txt` can exist for other channels, but it is not part of the RuStore publish flow.

## Script behavior

`android/scripts/rustore_publish.py` is stdlib-only and uses the current RuStore Public API flow:

1. Generate a JWE auth token with `POST /public/auth`
2. Query existing versions
3. Fail if an existing `DRAFT` version is present
4. Create a draft if no draft exists
5. Upload the `.aab`
6. Submit the draft for moderation

The workflow passes release notes to the publisher via a text file argument so multiline notes are preserved exactly as written in `android/release-notes/ru-RU.txt`.
The workflow passes the RuStore private key to the publisher via the `RUSTORE_PRIVATE_KEY` environment variable instead of placing the key directly on the process command line.

Important behavior:

- Draft strategy is `FAIL_IF_EXISTS`
- `ubuntu-latest` must provide `openssl` for RSA signing of the auth payload
- RuStore API JSON requests use an explicit short client-side timeout, and the AAB upload uses a longer 5-minute timeout, so CI fails instead of hanging indefinitely on network stalls
- if any existing draft is present, the script fails and leaves that draft untouched
- after moderation, publication remains manual in RuStore
- failures exit non-zero with explicit stage names such as `authenticate`, `create-draft`, `upload-aab`, or `submit`

## Manual fallback

If the API publish step fails after the environment approval gate:

1. Download the AAB artifact from the workflow run.
2. Open RuStore Console.
3. Upload or reuse the release draft manually.
4. Copy the notes from `android/release-notes/ru-RU.txt`.
5. Submit the version through the Console.

If the workflow succeeds, the version is still configured for manual publication in RuStore. Publish it from RuStore after moderation when you are ready.

## References

- [RuStore API: Creating a draft release](https://www.rustore.ru/help/en/work-with-rustore-api/api-upload-publication-app/create-draft-version)
- [RuStore API: Getting application version status](https://www.rustore.ru/help/en/work-with-rustore-api/api-upload-publication-app/get-version-status)
- [RuStore API: Uploading an AAB file](https://www.rustore.ru/help/en/work-with-rustore-api/api-upload-publication-app/apk-file-upload/file-upload-aab)
- [RuStore API: Submitting a draft app release for review](https://www.rustore.ru/help/en/work-with-rustore-api/api-upload-publication-app/send-draft-app-for-moderation)
- [RuStore API: Authorization and workflow principles](https://www.rustore.ru/help/work-with-rustore-api/api-authorization-process)
