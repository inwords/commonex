# Dependency Update Batch 1 Design

## Objective

Apply the user-selected dependency bundles from the 2026-08-22 dependency proposal in an isolated worktree, preserve explicit major-version caps, adopt only low-risk project-relevant improvements, eliminate warnings introduced by the batch, and publish the result as a draft pull request.

## Scope

- Android bundles: 4, 6, 8-19.
- Backend bundles: 21 (capped at `@fastify/static` 9.3.0), 22, 24, 26, 28-47.
- Web bundles: 48 (MUI capped at 7.3.11), 49-51 (MobX capped at 6.16.1), 54, 56-57, 59.
- No infrastructure or CI/container dependency bundles are included.
- This is explicitly **dependency update batch 1** in the branch, commits, documentation, and draft PR.

## Implementation Strategy

The projects are updated and validated independently, in this order: Android, backend, then web. Each project gets a focused implementation commit after review. Manifest and lockfile changes are configuration updates; if an upgrade requires a production-code migration or warning fix, that change is test-driven and kept in the same project commit.

New dependency features are adopted only when they replace a deprecated API, remove an upgrade warning, or improve an existing code path without changing product behaviour. Broad refactors and unrelated vulnerability remediation are out of scope.

## Version and Atomicity Constraints

- Android catalog aliases that represent one dependency family remain aligned, including Wire plugin/runtime, Ktor modules, Sentry KMP runtime/plugin, and JUnit artifacts.
- The Gradle Versions plugin changes from `com.github.ben-manes.versions` to `io.github.ben-manes.versions` while moving to 0.61.0.
- Backend OpenTelemetry core and auto-instrumentation packages move as one coordinated set.
- Backend TypeScript ESLint parser/plugin and Jest/ts-jest pairs move together.
- `@fastify/static` must stop at 9.3.0; MUI must stop at 7.3.11; MobX must stop at 6.16.1.
- No selected package may cross a user-disallowed major-version boundary through transitive manifest editing.

## Validation and Warning Policy

The clean baseline is recorded before edits. Android host tests, backend lint/tests/build, and web lint/build are rerun after their respective updates, followed by integrated verification. Backend tests use the documented disposable PostgreSQL container. New compiler, linter, build, or runtime warnings are investigated and fixed at their cause. Known baseline warnings are reported separately and are not silently expanded into unrelated work.

## Release Notes and Delivery

The draft PR summarizes only release-note changes relevant to CommonEx and links to authoritative upstream notes; it does not reproduce full third-party release text. Delivery uses separate Android, backend, and web commits, plus the setup/documentation commit, on `codex/dependency-update-batch-1` based on the latest `origin/main`.
