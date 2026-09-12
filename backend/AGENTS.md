# Agent Instructions for CommonEx Backend

For non-trivial work and when to search upstream docs, follow root [AGENTS.md](../AGENTS.md) (workflow lifecycle and freshness policy).
Cross-project reference docs: [../docs/domain.md](../docs/domain.md) and [../docs/network-contracts.md](../docs/network-contracts.md).

## Project Overview

CommonEx backend is a NestJS service that provides REST and gRPC APIs for the expense sharing platform.

## Technology Stack

- Framework: NestJS v11 with Fastify HTTP adapter (HTTP/2 cleartext, h2c)
- Swagger static assets: pinned `@fastify/static` runtime dependency (see `package.json` for the current version)
- Database: PostgreSQL with TypeORM
- APIs: REST and gRPC
- Observability: OpenTelemetry (`@fastify/otel` + allowlisted Node auto-instrumentations). See [`docs/otel-runtime.md`](docs/otel-runtime.md) for details.
- Linting: ESLint 10 flat config (`eslint.config.js`) running typescript-eslint `strict-type-checked` and
  `stylistic-type-checked`; the few scoped exceptions (domain errors, DTO spreads in controllers, jest matchers) are
  documented inline in the config
- Formatting: Prettier 3 (`.prettierrc`, width 120, imports sorted by `@trivago/prettier-plugin-sort-imports`). `npm run format`
  applies it, `npm run format:check` verifies it in CI; lint does not run Prettier as an ESLint rule. `migrations/` is excluded.

**Freshness note:** NestJS v11, ESLint 10 flat config, and TypeORM APIs may be newer than training data. Verify against current upstream docs when implementing.

## Architecture

Clean architecture with layered boundaries:

- API layer (`src/api/http`, `src/api/grpc`): controllers, DTOs, transport concerns
- Use cases (`src/usecases`): business orchestration
- Domain (`src/domain`): entities, value objects, error models, abstractions
- Frameworks (`src/frameworks`): persistence, external services, infrastructure adapters

Flow direction: API -> use cases -> domain abstractions -> frameworks implementations.

## Prerequisites

- Node.js and npm (versions: see `package.json` engines or lockfile; do not duplicate in docs).
- PostgreSQL

## Environment Setup

1. Install dependencies:
   ```bash
   cd backend
   npm install
   ```
2. Create env file:
   ```bash
   cp example.env .env
   ```
3. Set required values in `.env`:
    - `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_USER_NAME`, `POSTGRES_PASSWORD`, `POSTGRES_DATABASE`,
      `POSTGRES_SCHEMA`
    - `OPEN_EXCHANGE_RATES_API_ID`
    - `DEVTOOLS_SECRET`
    - optional `OTEL_SERVICE_NAME` (defaults to `commonex-backend` in `src/config.ts`)
    - PostgreSQL pool and timeout/keepalive defaults are defined in `src/config.ts` and applied in
      `src/frameworks/relational-data-service/postgres/config.ts`

## Essential Commands

Always run commands from `backend/`.

```bash
npm run start:dev
npm run build
npm run typecheck
npm run lint
npm run lint:check
npm run format
npm run format:check
npm run test
npm run test:cov
npm run db:migrate
npm run db:migrate:new
```

## Runtime Endpoints

- Swagger UI: `/swagger/api`
- Health endpoint: `/health`
- gRPC listener: `0.0.0.0:5000`

## Development Workflow

1. Implement domain/use-case changes first, then transport and framework adapters.
2. For database schema changes:
    - update framework entities
    - run `npm run db:migrate:new`
    - review migration in `migrations/default/`
    - apply via `npm run db:migrate`
3. Keep API and domain contracts aligned; do not leak transport/persistence details into domain models.

## Coding Standards

- Keep the domain layer free of framework-specific code.
- Keep TypeORM decorators and persistence logic in the `frameworks/` layer.
- Use `class-validator` for API DTO validation.
- When using TypeORM `getRawOne` / `getRawMany`, prefer precise raw result types that match the current `pg` parser behavior; avoid defensive unions such as `Date | string` unless that code path can actually return both.
- Use SQL casts in raw projections only when they materially improve the returned JS type, for example `COUNT(...)::integer` to avoid `bigint` string results.
- Backend lint source of truth is `eslint.config.js`; do not add or rely on legacy `.eslintrc.*` files.
- Run `npm run format` before submitting; CI fails on unformatted `src/` or `scripts/` files. Import order is enforced by the
  Prettier plugin (builtins, third party, `#packages`, `#domain`, `#usecases`, `#frameworks`, `#api`, relative); side-effect
  imports such as `import './otel'` in `src/main.ts` keep their position.
- Backend line-length enforcement is `160` characters via ESLint `max-len`.
- Keep HTTP guards/filters adapter-agnostic: avoid direct `fastify`/`express` request-response types; prefer
  `HttpAdapterHost`/`AbstractHttpAdapter`.
- When a user-facing flow needs currencies or currency-version metadata subject to support gating,
  inject `SupportedCurrencyServiceAbstract` instead of reading raw repositories in the use case.
- For user-facing conditional GET routes, keep one shared version/validator shape for ETag generation and add tests that
  prove the `304` path emits the same validator as the `200` path for the same DB state.
- Keep changes minimal and focused on root causes.

## Common Tasks

- Add a use case: create in `src/usecases/`, wire in `usecases.layer.ts`, add tests beside use case.
- Add a REST endpoint: add DTO/controller under `src/api/http`, map DTO -> use case input.
- Add a migration: update TypeORM layer, generate via `npm run db:migrate:new`, verify and apply.

## Testing

```bash
npm run test        # unit and DB-backed tests under src/
npm run test:e2e    # application tests under test/ (HTTP via Fastify inject, gRPC via a real client)
npm run test:cov
```

Application tests boot `AppModule` through `src/app.factory.ts`, the same code `src/main.ts` uses, so pipes, filters and
Swagger are configured identically in tests and production. Shared test helpers live in `src/test-support/`.

Tests need a live PostgreSQL. `docker-compose.test.yml` starts one that reuses the production `db` service definition
from `infra/docker-compose-prod.yml` and reads credentials from `.env` (`cp example.env .env`):

```bash
docker compose -f docker-compose.test.yml up --wait db
npm run db:migrate
npm run test
docker compose -f docker-compose.test.yml down -v
```

For PowerShell-specific notes, see [`docs/troubleshooting.md`](docs/troubleshooting.md).

CI (`.github/workflows/main.yml`, job `backend-checks`) runs the same sequence plus `typecheck`, `lint:check` and
`format:check` on every pull request or push that touches `backend/` or `infra/` (the test database extends the production compose file);
production deploys wait for it.

## Deployment

- Backend container runs migrations before app start (`db:migrate:docker_prod` then `start:prod`).
- HTTP service on `3001`; gRPC on `5000`.
- Health endpoint: `/health`.

## Dependency Version Policy

- Pin exact versions in `dependencies` and `devDependencies`.
- Do not use version range prefixes such as `^` or `~`.

## Validation Steps

Before submitting backend changes:

```bash
npm run typecheck
npm run lint
npm run format:check
npm run test
npm run build
```

`typecheck` is the only step that type-checks test files: `nest build` excludes them and ts-jest runs in transpile-only mode
because `isolatedModules` is enabled, so a type error in a test does not fail `npm run test`. `lint` and `lint:check` cover
`src/`, `scripts/` and `migrations/`.

For troubleshooting, see [`docs/troubleshooting.md`](docs/troubleshooting.md).
