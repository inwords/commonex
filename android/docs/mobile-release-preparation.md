# Mobile release preparation

This flow prepares a CommonEx mobile release branch and PR while preserving the existing semantics from [`android/.agents/skills/prepare-mobile-release/SKILL.md`](../.agents/skills/prepare-mobile-release/SKILL.md).

## Files

- `.github/workflows/mobile-release-prepare.yml`
- `.github/workflows/mobile-release-tag.yml`
- `.github/workflows/mobile-sentry-release.yml`
- `android/scripts/release_version.py`

## Branch, tag, and commit conventions

- Release branch: `release/prep/YYYY-MM-N/P`
- Release tag: `release/YYYY-MM-N/P`
- Pull request title: `Release YYYY.MM.N/P`
- Version bump commit: `Bump version to VERSION_NAME`
- Baseline/startup profile commit: `Update baseline and startup profiles for version VERSION_NAME`

Workflow-dispatch inputs:

- `release_version`: `YYYY-MM-N`
- `patch`: positive integer, default `1`

Derived values:

- `VERSION_NAME = YYYY.MM.N`
- `VERSION_CODE = current Android versionCode + 1`

The version bump is mobile-wide:

- Android `versionName` and `versionCode` are updated in `android/app/build.gradle.kts`
- iOS `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` are updated in `android/iosApp/iosApp.xcodeproj/project.pbxproj`

## Prepare workflow

Workflow: `.github/workflows/mobile-release-prepare.yml`

Trigger: manual `workflow_dispatch`

Behavior:

1. Computes release metadata from the inputs and current Android version code.
2. Fails fast if the target release branch or release tag already exists on `origin`.
3. Creates the release branch from `main`.
4. Bumps Android and iOS versions together.
5. Creates commit `Bump version to VERSION_NAME`.
6. Runs `./android/gradlew -p android --quiet :app:generateBaselineProfile`.
7. Creates commit `Update baseline and startup profiles for version VERSION_NAME`.
8. Pushes the release branch and opens a PR to `main` using a GitHub App installation token so downstream `pull_request` workflows still run.

## Tag workflow

Workflow: `.github/workflows/mobile-release-tag.yml`

Trigger: merged pull requests into `main` whose head branch matches `release/prep/**`

Behavior:

1. Validates the merged branch name.
2. Computes the final tag `release/YYYY-MM-N/P`.
3. Fails explicitly if the tag already exists on `origin`.
4. Tags the merge commit using a GitHub App installation token.
5. Pushes the tag.

## Downstream automations

Pushing a release tag fans out into independent workflows:

- `.github/workflows/mobile-sentry-release.yml` creates the Sentry release, associates commits, and records a `production` deploy.
- `.github/workflows/android-rustore-publish.yml` handles RuStore bundle publishing.

## Validation notes

- The mobile release workflows intentionally do not modify `.github/workflows/android.yml`.
- Duplicate protection runs before any release branch push and before any release tag creation.
- The prepare flow depends on the Android SDK, baseline profile generation prerequisites, and GitHub CLI availability on the runner.
- The prepare and tag workflows require the GitHub App credentials `RELEASE_BOT_APP_CLIENT_ID` and `RELEASE_BOT_APP_PRIVATE_KEY`.
