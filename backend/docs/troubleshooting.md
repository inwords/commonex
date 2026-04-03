# Backend Troubleshooting

## PowerShell-Specific Notes

### Local DB-backed runs (PowerShell, when PostgreSQL is on `5432`)

```powershell
$env:POSTGRES_HOST='127.0.0.1'
$env:POSTGRES_PORT='5432'
$env:POSTGRES_USER_NAME='postgres'
$env:POSTGRES_PASSWORD='postgres'
$env:POSTGRES_DATABASE='postgres'
$env:POSTGRES_SCHEMA='public'
$env:OPEN_EXCHANGE_RATES_API_ID='test'
$env:DEVTOOLS_SECRET='test-secret'
npm run db:migrate
npm run test
```

### Temporary Docker DB-backed runs (PowerShell, no local PostgreSQL)

```powershell
docker run --rm -d --name commonex-backend-test-db `
  -e POSTGRES_PASSWORD='postgres' `
  -e POSTGRES_USER='postgres' `
  -e POSTGRES_DB='postgres' `
  -p 55432:5432 postgres:16-alpine

$env:POSTGRES_HOST='127.0.0.1'
$env:POSTGRES_PORT='55432'
$env:POSTGRES_USER_NAME='postgres'
$env:POSTGRES_PASSWORD='postgres'
$env:POSTGRES_DATABASE='postgres'
$env:POSTGRES_SCHEMA='public'
$env:OPEN_EXCHANGE_RATES_API_ID='test'
$env:DEVTOOLS_SECRET='test-secret'
node node_modules/ts-node/dist/bin.js --transpile-only scripts/migrate.ts
node node_modules/jest/bin/jest.js --runInBand
docker stop commonex-backend-test-db
```

### PowerShell Script-Policy Issues

`npm run ...` can fail because package scripts resolve to `*.ps1` shims. Use CMD or direct binary paths:
- `.\node_modules\.bin\nest.cmd build`
- `.\node_modules\.bin\jest.cmd --runInBand`
- `node node_modules/@nestjs/cli/bin/nest.js build`

## Common Issues

- **Missing `@fastify/static`**: app can fail during Swagger setup on Fastify.
- **Missing HTTP/Fastify spans after migration**: ensure `fastifyOtelInstrumentation.plugin()` is registered in
  `src/main.ts` before Nest app creation and `fastifyOtelInstrumentation` is included in NodeSDK instrumentations in
  `src/otel.ts` (`@opentelemetry/instrumentation-http` is intentionally disabled).
- **Missing HTTP request metrics**: ensure `fastifyHttpMetricsPlugin` is registered in `src/main.ts` and `src/otel.ts`
  still defines the `http.server.request.duration` view for meter `commonex-backend.fastify-http`.
- **Grafana query note**:
  `sum by(http.route, http.request.method, http.response.status_code) (rate(http.server.request.duration_count[$__rate_interval]))`
  is valid for request-rate panels; timeout/client-abort points can have missing `http.response.status_code`.
- **Env parsing errors on startup**: verify required `.env` keys are present and non-empty.
- **DB connection failures**: verify PostgreSQL availability and credentials, then run `npm run db:migrate`.
- **Frequent PG reconnects**: check `src/config.ts` and
  `src/frameworks/relational-data-service/postgres/config.ts` for pool/connection settings.
- **Query timeout layering**: backend config sets client-side `query_timeout` slightly above server-side
  `statement_timeout` to avoid client timeout racing before PostgreSQL statement timeout.
