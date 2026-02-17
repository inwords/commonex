# Agent Instructions for CommonEx Backend

## Project Overview

CommonEx backend is a NestJS service that provides REST and gRPC APIs for the expense sharing platform.

## Technology Stack

- Framework: NestJS v11 with Fastify HTTP adapter (HTTP/2 cleartext, h2c)
- Swagger static assets: `@fastify/static` runtime dependency (pinned; currently `9.0.0`)
- Database: PostgreSQL with TypeORM
- APIs: REST and gRPC
- Observability: OpenTelemetry
- Linting: ESLint 9 flat config (`eslint.config.js`)
- Testing: Jest 30 with ts-jest
- Language: TypeScript 5.9

## Architecture

Clean architecture with layered boundaries:

- API layer (`src/api/http`, `src/api/grpc`): controllers, DTOs, transport concerns
- Use cases (`src/usecases`): business orchestration
- Domain (`src/domain`): entities, value objects, error models, abstractions
- Frameworks (`src/frameworks`): persistence, external services, infrastructure adapters

Flow direction: API -> use cases -> domain abstractions -> frameworks implementations.

## Key File Locations

- Main entry: `src/main.ts`
- App module: `src/app.module.ts`
- Domain entities: `src/domain/entities/`
- Value objects: `src/domain/value-objects/`
- Use cases: `src/usecases/`
- API controllers: `src/api/http/`, `src/api/grpc/`
- TypeORM entities and repositories: `src/frameworks/relational-data-service/`
- Migrations: `migrations/default/`

## Prerequisites

- Node.js
- npm
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

## Essential Commands

Always run commands from `backend/`.

```bash
npm run start
npm run start:dev
npm run start:debug
npm run start:prod

npm run build
npm run lint

npm run test
npm run test:watch
npm run test:cov

npm run db:migrate
npm run db:migrate:new
npm run db:migrate:empty
npm run db:migrate:docker_prod
npm run db:drop
```

`npm run test:e2e` points to `./test/jest-e2e.json`; this config file is currently missing in this repository.

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
- Keep HTTP guards/filters adapter-agnostic: avoid direct `fastify`/`express` request-response types; prefer
  `HttpAdapterHost`/`AbstractHttpAdapter` and generic request header typing.
- Keep changes minimal and focused on root causes.

## Common Tasks

- Add a use case:
    - create/update `src/usecases/...`
    - wire dependencies in `usecases.layer.ts` / module providers
    - add/update tests beside the use case
- Add a REST endpoint:
    - add DTO/controller under `src/api/http`
    - map DTO -> use case input
    - keep transport-specific types out of use-case/domain signatures
- Add a migration:
    - update TypeORM layer
    - generate migration via `npm run db:migrate:new`
    - verify and apply migration

## Testing

- Unit/integration tests:
  ```bash
  npm run test
  npm run test:cov
  ```
- Local DB-backed runs (PowerShell, when PostgreSQL is on `5432`):
  ```powershell
  $env:POSTGRES_HOST='127.0.0.1'
  $env:POSTGRES_PORT='5432'
  $env:POSTGRES_USER_NAME='postgres'
  $env:POSTGRES_PASSWORD='postgres'
  $env:POSTGRES_DATABASE='postgres'
  $env:POSTGRES_SCHEMA='public'
  $env:DEVTOOLS_SECRET='test-secret'
  npm run db:migrate
  npm run test
  ```

## Deployment

- Backend container runs migrations before app start (`db:migrate:docker_prod` then `start:prod`).
- HTTP service runs on `3001`; gRPC service runs on `5000`.
- Health endpoint: `/health`.

## Troubleshooting

- Missing `@fastify/static`: app can fail during Swagger setup on Fastify.
- Env parsing errors on startup: verify required `.env` keys are present and non-empty.
- DB connection failures: verify PostgreSQL availability and credentials, then run `npm run db:migrate`.
- PowerShell script-policy issues on local machine: invoke local binaries through `node` (for example, Nest CLI path)
  when needed.

## Dependency Version Policy

- Pin exact versions in `dependencies` and `devDependencies`.
- Do not use version range prefixes such as `^` or `~`.

## Validation Steps

Before submitting backend changes, run:

```bash
npm run lint
npm run test
npm run build
```
