# Backend Test Quality Design

## Objective

Raise the correctness guarantees of the NestJS backend by closing the test gaps found in the 2026-09-12 assessment:
no application-level tests, no gRPC tests, dead and misleading mocks in use-case tests, test-only code in production
classes, no schema-drift guard, and no coverage signal in CI. The existing table-driven use-case tests and the
repository tests with SQL snapshots stay and are strengthened.

## Scope

- `backend/` only. Android, web, and infra are untouched except the `backend-checks` CI job.
- No behaviour change for HTTP clients, with one approved exception: `POST /v2/user/event/:eventId` and
  `POST /v2/user/event/:eventId/expenses` return `200` instead of `201`, matching their documented `@ApiResponse`;
  both clients accept any 2xx. The gRPC surface changes because it is not used by any client yet and is
  currently broken in ways the new tests expose.
- Test names are English and start with a verb in third person ("returns", "rejects", "saves").

## Test Tiers

| Tier | Location | Runner | Needs Postgres | Purpose |
|---|---|---|---|---|
| Unit | `src/**/__tests__/*.test.ts`, `*.spec.ts` next to source | `npm test` | no | value objects, pure services, filters, guards, controllers with mocked use cases |
| DB-backed | `src/usecases/**/__tests__`, `src/frameworks/**/__tests__` | `npm test` | yes | use cases and repositories against a real Postgres |
| E2E | `test/**/*.e2e-spec.ts` | `npm run test:e2e` | yes | full Nest application over Fastify `inject()` and a real gRPC client |

Unit and DB-backed tests share one jest project because they already do today and the split would only move files.
E2E tests get their own jest config in `test/jest-e2e.json` because they boot the whole application, need a
different setup, and must be runnable as a separate CI step.

## Decisions

### Test support module

`src/test-support/` holds everything tests share: `db.ts` (create a `RelationalDataService` from the app config,
truncate all tables), `relational-state.ts` (the existing `TestCase` helpers moved from
`src/usecases/__tests__/test-helpers.ts`), and `apply-state-changes.ts`. It is reachable as `#test-support/*` from
both jest projects. `RelationalDataService.flush()` is removed from production code; the domain abstraction no longer
exposes a truncate operation.

### Application factory

`src/app.factory.ts` exports `configureHttpApp(app)` (validation pipe, exception filters, Swagger, CORS) and
`createGrpcOptions({url, protoPath})`. `src/main.ts` keeps OpenTelemetry and the metrics plugin and calls the
factory, so production and tests configure the application through the same code. The shared validation pipe options
live in `src/api/validation-pipe.ts`.

### SQL snapshots stay

The `toMatchSnapshot()` assertions on `queryDetails` are the record of what TypeORM generates and are the intended
guard for library upgrades. They are kept as they are. `jest --ci` is enabled so a snapshot can never be written or
updated implicitly; updating requires an explicit `-u` run whose diff is reviewed. The `test_schema_N` regex in
`BaseRepository` is removed because the per-worker schema scheme it served no longer exists. Behavioural assertions
are added next to the snapshots where the SQL shape encodes a rule (upsert, ordering, supported-currency filter, lock
conflicts, transaction rollback).

### Mocking model for use-case tests

Use-case tests are integration tests over a real Postgres. They stop stubbing `EventService` and
`IdempotencySharedUseCase`; every expected outcome is produced by database state. `CurrencyRateService` remains the
only stubbed collaborator because it calls an external API. Idempotency is exercised for real: one replay test and
one hash-mismatch test per idempotent use case.

### gRPC

- Request parameters become concrete DTO classes built with `IntersectionType` from `@nestjs/swagger`, so the
  validation pipe has a metatype to validate against.
- The gRPC controller gets a controller-scoped `ValidationPipe` and two controller-scoped RPC exception filters that
  translate `BadRequestException` and the ten domain errors into gRPC status codes, `details`, and an `error-code`
  metadata entry carrying the `B40xx` code. `inheritAppConfig` is not used because the HTTP filters call
  `httpAdapter.reply` and cannot serve an RPC context.
- `src/expenses.proto` fixes: `UserResponse.eventId` becomes `string`, `SplitInfo.amount` and
  `SplitInfo.exchangedAmount` become `double`, `CreateEventShareTokenResponse.expiresAt` becomes `string`.
- Proto loader options are set explicitly on the server (`enums: String`, `defaults: false`, `arrays: true`,
  `objects: true`, `oneofs: true`, `longs: String`, `keepCase: false`) so enum values arrive as the domain strings and
  unset optional fields arrive as `undefined`.
- Expense responses map `Date` fields to ISO strings before serialization.

### Status code mapping for gRPC

| Domain error | HTTP | gRPC status |
|---|---|---|
| `EventNotFoundError`, `CurrencyNotFoundError`, `CurrencyRateNotFoundError` | 404 | `NOT_FOUND` (5) |
| `EventDeletedError` | 410 | `FAILED_PRECONDITION` (9) |
| `InvalidPinCodeError` | 403 | `PERMISSION_DENIED` (7) |
| `InvalidTokenError`, `TokenExpiredError` | 401 | `UNAUTHENTICATED` (16) |
| `InconsistentExchangedAmountError` | 400 | `INVALID_ARGUMENT` (3) |
| `EventOperationConflictError` | 409 | `ABORTED` (10) |
| `IdempotencyHashMismatchError` | 422 | `FAILED_PRECONDITION` (9) |
| validation (`BadRequestException`) | 400 | `INVALID_ARGUMENT` (3) |

### Schema drift guard

A DB-backed spec asserts that `showMigrations()` reports nothing pending and that
`driver.createSchemaBuilder().log()` yields zero `upQueries`. If it ever fails, the executor stops and reports; the
fix is a reviewed migration, never an automatic one.

### Coverage

`npm run test:cov` runs in CI instead of `npm test`. `collectCoverageFrom` excludes tests, test support, `main.ts`,
`otel.ts`, and barrel files. A global `coverageThreshold` is set to the measured value minus five points per metric
and is raised as coverage grows.

### Jest configuration

Both jest projects use `restoreMocks: true` and `clearMocks: true`, `--ci`, and `--runInBand`. The `testRegex` for
the `src` project is `\.(test|spec)\.ts$`; the `test` project matches `\.e2e-spec\.ts$`.

## Out of Scope

- Retiring the `[result, queryDetails]` tuple from the repository abstraction.
- Parallel test execution with per-worker schemas.
- Property-based or mutation testing.
- Enforcing the `User-Agent` contract from `docs/network-contracts.md` in the backend.
