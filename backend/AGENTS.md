# Agent Instructions for CommonEx Backend

For non-trivial work and when to search upstream docs, follow root [AGENTS.md](../AGENTS.md) (workflow lifecycle and freshness policy).
Cross-project reference docs: [../docs/domain.md](../docs/domain.md) and [../docs/network-contracts.md](../docs/network-contracts.md).

## Project Overview

CommonEx backend is a NestJS service that provides REST and gRPC APIs for the expense sharing platform.

## Technology Stack

- Framework: NestJS v11 with Fastify HTTP adapter (HTTP/2 cleartext, h2c)
- Swagger static assets: `@fastify/static` runtime dependency (pinned; currently `9.0.0`)
- Database: PostgreSQL with TypeORM
- APIs: REST and gRPC
- Observability: OpenTelemetry (`@fastify/otel` + allowlisted Node auto-instrumentations). See [`docs/otel-runtime.md`](docs/otel-runtime.md) for details.
- Linting: ESLint 10 flat config (`eslint.config.js`) with `eslint-config-prettier` compatibility
- Formatting: Prettier 3.8 is explicit-only via `npm run format`; backend lint does not run Prettier as an ESLint rule

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
npm run lint
npm run format
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
- Keep backend formatting aligned with the repo `.editorconfig`; backend Prettier is reserved for explicit formatting runs.
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
npm run test
npm run test:cov
```

For PowerShell-specific test setups (local DB, Docker DB), see [`docs/troubleshooting.md`](docs/troubleshooting.md).

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
npm run lint
npm run test
npm run build
```

For troubleshooting, see [`docs/troubleshooting.md`](docs/troubleshooting.md).
