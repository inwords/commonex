# Backend Test Quality — Session Follow-Ups

Handoff from the 2026-09-12 backend test-quality session. Everything planned is merged; this file lists only what
was deliberately left out, ranked. Each item is independent and small enough for one PR.

- Plan: [2026-09-12-backend-test-quality.md](2026-09-12-backend-test-quality.md)
- Design: [../specs/2026-09-12-backend-test-quality-design.md](../specs/2026-09-12-backend-test-quality-design.md)

## What shipped

Thirteen plan tasks across seven PRs, all merged: #289 test-support module and `flush()` removal, #290 app factory
and HTTP end-to-end tests, #291 gRPC contract tests and transport fixes, #292 use-case tests driven by database
state, #293 repository behaviour tests and schema-drift guard, #294 unit tests for the domain and framework layers,
#296 coverage thresholds in CI.

Real defects the new tests exposed and fixed: gRPC ran without validation and surfaced every domain error as
`UNKNOWN`; the gRPC `metadata` argument was `undefined`, so idempotency keys never arrived; `int32` proto fields
truncated ULIDs and fractional amounts; replayed idempotent responses crashed the ISO mapper; failed results were
cached and replayed as plain objects, producing 500s on retry; `DeleteEvent` existed in the controller but not in
the proto; two v2 read endpoints answered `201` against their documented `200`.

Coverage baseline measured on CI, with thresholds set five points below each:

| Metric | Measured | Threshold |
|---|---|---|
| Statements | 70.74% | 65 |
| Branches | 41.18% | 36 |
| Functions | 80.13% | 75 |
| Lines | 70.64% | 65 |

## Follow-ups

### 1. gRPC end-to-end covers 5 of 11 RPCs

`backend/test/grpc/user.e2e-spec.ts` exercises `CreateEvent`, `GetEventInfo`, `DeleteEvent`, `CreateExpenseV2` and
`CreateEventShareTokenV2`. Untested: `AddUsersToEvent`, `AddUsersToEventV2`, `GetAllEventExpenses`,
`GetAllEventExpensesV2`, `CreateExpense` (V1), `GetEventInfoV2`.

Start with `AddUsersToEventV2`. It is the only idempotent write whose metadata path has no gRPC coverage, and that
path was silently broken until this session. The helpers already exist: `callUnary` and `expectGrpcError` in
`backend/test/support/grpc-client.ts`, and the `createEvent` fixture in `backend/test/support/fixtures.ts`.

### 2. `toExpenseGrpcResponse` spreads the entity

`backend/src/api/grpc/user/dto/expense-grpc-response.dto.ts` spreads `...expense` and converts only `createdAt` and
`updatedAt`. A future `Date` field on `IExpense` would ship a `Date` into a proto `string` field, silently, at
runtime. That is the exact failure this session already fixed once for `expiresAt`. Listing the fields explicitly
costs about eight lines and removes the trap.

### 3. Idempotent replays return values whose types lie

`backend/src/usecases/shared/idempotency.usecase.ts` returns `existing.response as TResult`. The response survived a
JSONB round trip, so `Date` fields come back as ISO strings while the type still says `Date`. The gRPC boundary
absorbs this through `toIsoString`, and HTTP serialises both forms identically, so nothing is broken today. A
general fix needs either a reviver at the boundary or per-use-case rehydration; the honest interim step is to type
the stored response as its serialised shape rather than as `TResult`.

### 4. Seventeen dead mock-reset calls

`jest.clearAllMocks()` and `jest.restoreAllMocks()` remain in the use-case tests even though the jest config sets
`restoreMocks` and `clearMocks`. They are no-ops that imply the config does not exist. Mechanical sweep.

### 5. Russian comments in seven production files

Six value objects under `backend/src/domain/value-objects/` plus
`backend/src/usecases/users/v2/save-event-expense-v2.usecase.ts`. The plan scoped translation to test names only.
One `docs(backend)` commit finishes it.

### 6. Branch coverage floor is thin and global-only

41.18% measured against a 36 threshold, enforced only in aggregate, so a new untested module can land while the
total holds. Once the number grows, add per-directory thresholds for `src/usecases` and `src/api` in the jest
`coverageThreshold` block.

### 7. Test harness ergonomics

Both in `backend/test/support/test-app.ts`. `close()` calls `app.close()` and relies on
`FrameworksLayer.onApplicationShutdown` to destroy the DataSource, which is correct but invisible; one comment
stops someone "fixing" a perceived leak. `getFreePort()` binds port 0, closes, then reuses the number, which is a
time-of-check/time-of-use race that is only safe because end-to-end tests run with `--runInBand`; note that before
anyone parallelises the suite.

### 8. Smaller items

- `backend/src/packages/__tests__/date-utils.test.ts` restores fake timers inside the test rather than in
  `afterEach`. Safe today only because it is the last test in the file.
- `backend/src/frameworks/currency-rate-service/__tests__/currency-rate-service.test.ts:29` is named "retries failed
  requests up to three times" but asserts three total calls, meaning two retries. Rename to "retries until the
  request succeeds".
- `backend/src/frameworks/__tests__/frameworks.layer.test.ts` compares a JavaScript `.sort()` against Postgres
  `ORDER BY code`. The two agree for uppercase ASCII but are collation-dependent in principle.

## Standing decision to revisit

The proto scalar fixes in #291 changed wire types in place (`UserResponse.eventId` to `string`, `SplitInfo` amounts
to `double`, share-token `expiresAt` to `string`) on the explicit basis that no gRPC client is deployed and the old
types were non-functional. If a gRPC client ships, any further type correction must use new field numbers instead.

## Notes for whoever picks this up

- The development machine used in this session has no PostgreSQL and no Docker, so DB-backed and end-to-end suites
  run only in GitHub Actions (`backend-checks`). Locally you get typecheck, lint, format, build and the DB-free jest
  subset.
- New SQL snapshot entries can be derived offline by building TypeORM metadata without connecting, then writing the
  entries by hand. CI runs jest with `--ci`, which prints a mismatch diff but never writes a snapshot.
- ESLint `no-restricted-imports` matches string `patterns` gitignore-style, where a leading `#` is a comment. The
  `#test-support/*` guard therefore uses the `regex` form.
