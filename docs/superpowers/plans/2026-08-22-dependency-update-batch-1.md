# Dependency Update Batch 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to execute this plan task-by-task.

**Goal:** Apply and validate the approved first dependency-update batch, then publish a reviewed draft PR.

**Architecture:** Keep dependency-family updates atomic within each project and isolate project commits. Prefer manifest/lockfile-only changes; any required production migration or warning fix must be minimal, test-driven, and behaviour-preserving.

**Tech Stack:** Gradle/Kotlin Multiplatform/Android, NestJS/Fastify/TypeScript/Jest/PostgreSQL, Next.js/React/TypeScript/ESLint, GitHub CLI.

**Spec:** `docs/superpowers/specs/2026-08-22-dependency-update-batch-1-design.md`

## Global Constraints

- Work only in `C:\Development\HybridProjects\expenses-dependency-update-batch-1` on `codex/dependency-update-batch-1`.
- Preserve `android/gradlew.bat` as an unstaged checkout/EOL artifact; stage files explicitly and never use `git add .`.
- Use `apply_patch` for hand edits. Do not edit generated build output, `.env`, secrets, or ignored local configuration.
- Use exact dependency versions. Respect the caps: `@fastify/static` 9.3.0, MUI 7.3.11, MobX 6.16.1.
- Adopt a new API only to replace deprecation, remove an introduced warning, or improve an existing path without product behaviour change.
- Treat every new warning or test failure with systematic debugging. Any production-code fix follows red-green-refactor.
- Record concise CommonEx-relevant release-note implications and authoritative links for the draft PR.

### Task 1: Android dependency families

**Files:**
- Modify: `android/gradle/shared.versions.toml`
- Modify only if required by migration/warnings: Android Gradle build files or affected source/tests

**Steps:**

1. Confirm the selected Android catalog keys and usages; check the official migration/release notes linked in the catalog/proposal where an API or plugin ID changes.
2. Apply bundles 4, 6, and 8-19 with these targets: Baseline Profile 1.5.0-rc01; Core 1.19.0; UiAutomator 2.4.0; Gradle Versions 0.61.0 with plugin ID `io.github.ben-manes.versions`; Okio 3.18.1; Wire 6.4.6; AppMetrica 8.5.1; Ktor 3.5.2; MockK 1.14.11; Sentry KMP 0.27.0; Sentry Android Gradle 6.19.0; AtomicFU 0.33.0; immutable collections 0.5.1; JUnit 6.1.3.
3. Keep every catalog family aligned and regenerate no checked-in build output.
4. Run `./gradlew --quiet testHostTest` from `android` (PowerShell equivalent on Windows). Run focused additional tasks if a changed plugin has a dedicated validation path.
5. Inspect output for warnings relative to baseline, fix all new warnings, and rerun affected validation.
6. Review the diff, stage only Android task files, and commit as `build(android): update dependency batch 1`.

### Task 2: Backend dependency families

**Files:**
- Modify: `backend/package.json`
- Modify: `backend/package-lock.json`
- Modify only if required by migration/warnings: affected backend source/config/tests

**Steps:**

1. Apply bundles 21, 22, 24, 26, and 28-47 with these exact targets:
   - `@fastify/static` 9.3.0; `fastify` 5.12.1; `@grpc/grpc-js` 1.14.4; `@grpc/proto-loader` 0.8.1.
   - `@nestjs/cli` 11.0.24; `@nestjs/config` 4.0.4; `@nestjs/schedule` 6.1.3; `@nestjs/swagger` 11.4.7; `@nestjs/typeorm` 11.0.3.
   - `@opentelemetry/api` 1.9.1; OTLP metric/trace exporters, instrumentation, and SDK Node 0.221.0; resources 2.10.0; auto-instrumentations-node 0.79.0.
   - `@types/lodash` 4.17.25; `@types/node` 26.2.0; `@types/supertest` 7.2.1.
   - TypeScript ESLint plugin/parser 8.67.0; `@eslint/eslintrc` 3.3.6; ESLint 10.9.0; eslint-plugin-prettier 5.5.6; globals 17.11.0.
   - Jest 30.4.2; ts-jest 29.4.12; ts-loader 9.6.2; Axios 1.19.0; dotenv 17.4.2; pg 8.23.0; Prettier 3.9.6; Zod 4.4.3.
   Keep OpenTelemetry, TypeScript ESLint, and Jest families coordinated.
2. Refresh the lockfile with the repository package manager using CMD as npm's script shell on Windows; do not accept unrelated major upgrades.
3. Inspect peer/deprecation/install warnings. Fix every warning newly introduced by this batch at its cause; distinguish documented baseline warnings.
4. Run lint and build. Start a uniquely named disposable PostgreSQL 16 container on port 55432, run migrations, then run all Jest tests in-band with the documented test environment; stop the exact container afterward.
5. If production code must change, first add or identify a failing focused test, then make the minimal fix and rerun the full backend checks.
6. Review the diff, stage only backend task files, and commit as `build(backend): update dependency batch 1`.

### Task 3: Web dependency families

**Files:**
- Modify: `web/package.json`
- Modify: `web/package-lock.json`
- Modify only if required by migration/warnings: affected web source/config

**Steps:**

1. Apply bundles 48-51, 54, 56-57, and 59 with these exact targets: `@mui/material` and `@mui/icons-material` 7.3.11; Next.js and eslint-config-next 16.3.2; React and React DOM 19.2.8; MobX 6.16.1 while retaining mobx-react-lite 4.1.1; react-router-dom 7.18.2; `@types/react` 19.2.18; `@types/react-dom` 19.2.4; `@types/node` 26.2.0; Prettier 3.9.6.
2. Refresh the lockfile using CMD as npm's script shell on Windows.
3. Run ESLint and the production Next.js build. Compare output with the documented baseline static-export rewrite warnings and fix every new warning.
4. If production code must change, add a focused regression test only where an existing runner supports it; otherwise use the narrowest build/type/lint proof and document the limitation.
5. Review the diff, stage only web task files, and commit as `build(web): update dependency batch 1`.

### Task 4: Integrated verification, release-note summary, and draft PR

**Files:**
- Modify as needed: `docs/superpowers/specs/2026-08-22-dependency-update-batch-1-design.md`
- Modify as needed: `docs/superpowers/plans/2026-08-22-dependency-update-batch-1.md`
- Create only if useful for durable project context: a concise batch result note under `docs/`

**Steps:**

1. Verify the final manifest versions and user caps, confirm no unselected bundle was deliberately added, and inspect all commits/diffs from `origin/main`.
2. Rerun Android host tests, backend lint/tests/build with disposable PostgreSQL, and web lint/build using fresh command output.
3. Run dependency/outdated checks where practical to prove capped packages remain intentionally behind only their disallowed majors; inspect install/build output for new warnings.
4. Produce a concise, project-relevant release-note summary and draft PR body in the task report with authoritative links, including migrations/features actually adopted and known baseline warnings that remain.
5. Confirm the branch diff/status is ready for the controller's final whole-branch review, except for the known unstaged `android/gradlew.bat` EOL artifact. Do not push or create the PR from this task.
6. After Task 4 review, the controller performs the required final whole-branch review, addresses findings, then pushes `codex/dependency-update-batch-1` and opens a draft PR whose title/body prominently identify **dependency update batch 1**.
