# Sentry release automation

This flow creates a finalized Sentry release, associates commits, and records a `production` deploy whenever a mobile release tag is pushed.

## Files

- `.github/workflows/mobile-sentry-release.yml`
- `android/scripts/sentry_release.py`

## Trigger

Workflow: `.github/workflows/mobile-sentry-release.yml`

Trigger: tag push matching `release/**`

This workflow is intentionally decoupled from store publishing. The release tag is treated as the canonical release event for Sentry.

## Behavior

1. Checks out the tagged revision with full git history and tags.
2. Installs `sentry-cli`.
3. Reads `applicationId`, `versionName`, and `versionCode` from `android/app/build.gradle.kts`.
4. Computes the Sentry release version as `package-name@version-name+version-code`.
5. Finds the previous release tag matching `release/*`.
6. Creates and finalizes the Sentry release.
7. Associates commits using the repository mapping and the previous/current release SHAs.
8. Creates a Sentry deploy in environment `production`.

Example release name:

- `ru.commonex@2026.04.1+8`

## Commit association

For non-initial releases, the workflow sends commits as:

- `<github-owner>/<github-repo>@<previous_release_sha>..<current_release_sha>`

For the first release with no previous `release/*` tag, the workflow falls back to:

- `<github-owner>/<github-repo>@<current_release_sha>`

## Required secret

- `SENTRY_AUTH_TOKEN`

The token should have at least:

- `project:releases`
- `org:read`

`org:ci` is also appropriate for CI-managed release workflows.
The workflow exposes this secret as an environment variable for `sentry-cli`; it is not passed on the command line.

## Notes

- The workflow uses `production` as the Sentry deploy environment by design.
- Store publication timing does not affect Sentry release/deploy creation.
- The repository mapping used for commit association is `${{ github.repository }}` at runtime and must match the repository configured in Sentry.

## References

- [Sentry CLI release management](https://docs.sentry.io/cli/releases/?promo_name=hp-banner)
- [Sentry API: Create a deploy](https://docs.sentry.io/api/releases/create-a-deploy/)
- [Sentry API permissions and scopes](https://docs.sentry.io/api/permissions/)
