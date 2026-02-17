# CommonEx Backend

Backend service for the CommonEx expense sharing platform.

It exposes:

- REST API (NestJS + Fastify)
- gRPC API

## Overview

- Framework: NestJS v11 (Fastify adapter, h2c)
- ORM/DB: TypeORM + PostgreSQL
- Validation: `class-validator` + `class-transformer`
- Observability: OpenTelemetry
- Language: TypeScript

## Requirements

- Node.js
- npm
- PostgreSQL

## Quick Start

```bash
cd backend
npm install
cp example.env .env
```

Set required values in `.env`, then:

```bash
npm run db:migrate
npm run start:dev
```

## Required Environment Variables

Core database and app values:

- `POSTGRES_HOST`
- `POSTGRES_PORT`
- `POSTGRES_USER_NAME`
- `POSTGRES_PASSWORD`
- `POSTGRES_DATABASE`
- `POSTGRES_SCHEMA`
- `OPEN_EXCHANGE_RATES_API_ID`
- `DEVTOOLS_SECRET`

Use `example.env` as the template.

## Running the Service

```bash
npm run start
npm run start:dev
npm run start:debug
npm run start:prod
```

## API Utilities

- Swagger UI: `/swagger/api`
- Health endpoint: `/health`
- gRPC listener: `0.0.0.0:5000`

Note: with Fastify, Swagger static assets require runtime dependency `@fastify/static` (pinned in `dependencies`).

## Scripts

```bash
# quality
npm run lint
npm run build

# tests
npm run test
npm run test:watch
npm run test:cov

# db
npm run db:migrate
npm run db:migrate:new
npm run db:migrate:empty
npm run db:migrate:docker_prod
npm run db:drop
```

`npm run test:e2e` currently points to `./test/jest-e2e.json`, which is missing in this repository.

## Database and Migrations

Typical schema-change workflow:

1. Update TypeORM entities/repositories in `src/frameworks/relational-data-service/`.
2. Generate migration:
   ```bash
   npm run db:migrate:new
   ```
3. Review migration under `migrations/default/`.
4. Apply migrations:
   ```bash
   npm run db:migrate
   ```

## Testing

Default test run:

```bash
npm run test
```

Local DB-backed run (PowerShell, PostgreSQL exposed on `5432`):

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

## Project Structure

- `src/main.ts`: app bootstrap (Fastify + Swagger + gRPC)
- `src/api/http`: REST controllers, DTOs, filters, guards
- `src/api/grpc`: gRPC controllers/contracts
- `src/usecases`: application use cases
- `src/domain`: entities, value objects, errors, abstractions
- `src/frameworks`: infrastructure adapters (DB/services)
- `migrations/default`: DB migrations

## Development Rules (Short)

- Keep domain layer framework-agnostic.
- Keep TypeORM and transport details in framework/API layers.
- Keep HTTP filters/guards adapter-agnostic (avoid direct Fastify/Express response typing in shared logic).
- Pin exact dependency versions (`dependencies` and `devDependencies`).

## Deployment Notes

- Production container runs DB migrations before app start (`db:migrate:docker_prod`).
- Exposed ports:
    - HTTP: `3001`
    - gRPC: `5000`

## Troubleshooting

- Startup fails during Swagger setup:
    - confirm `@fastify/static` is present in `dependencies`.
- Env validation fails:
    - verify required `.env` keys exist and are non-empty.
- Database connection issues:
    - verify Postgres availability/credentials, then run `npm run db:migrate`.

## Pre-PR Checklist

```bash
npm run lint
npm run test
npm run build
```
