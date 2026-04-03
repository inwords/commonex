# OpenTelemetry Runtime

- Bootstrap file: `src/otel.ts`
- Fastify server tracing: `@fastify/otel` instrumentation is created in `src/otel.ts` and its plugin is manually
  registered in `src/main.ts` before `NestFactory.create(...)`
- Enabled auto-instrumentations are allowlisted to:
  `@opentelemetry/instrumentation-grpc`,
  `@opentelemetry/instrumentation-pg`,
  `@opentelemetry/instrumentation-nestjs-core`,
  `@opentelemetry/instrumentation-runtime-node`
- HTTP server metrics are produced by `src/frameworks/observability/fastify-http-metrics.plugin.ts`, registered in
  `src/main.ts` before Nest app creation.
- `http.server.request.duration` histogram boundaries are configured via `NodeSDK` views in `src/otel.ts`
  (meter `commonex-backend.fastify-http`).
- Metrics export interval: `5000` ms (`PeriodicExportingMetricReader`)
- Graceful SDK shutdown hooks are registered for `SIGTERM` and `SIGINT` via `process.once(...)`
