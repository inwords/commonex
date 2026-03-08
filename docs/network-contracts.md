# Network Contracts

This document captures repo-wide HTTP and transport-level contracts that should stay consistent across clients and services.

It is intentionally focused on communication rules and operational behavior, not DTO field-by-field specs. Keep DTO schemas in code and transport-layer DTO files.

## Topology

CommonEx currently uses three transport surfaces:

- Browser HTTP traffic enters through Nginx on `commonex.ru` and is proxied from `/api/` to the backend HTTP service.
- Mobile KMM clients call the backend HTTP API directly through the shared network layer using `https://dev-api.commonex.ru` as the current host configuration.
- gRPC traffic is exposed separately on `grpc.commonex.ru` and is not used by the web client or the shared mobile KMM stores described here.

Relevant implementation points:

- HTTP ingress and proxying: `infra/nginx/nginx-prod.conf`
- Backend HTTP bootstrap: `backend/src/main.ts`
- Shared mobile host configuration: `android/shared/core/network/src/commonMain/kotlin/com/inwords/expenses/core/network/NetworkComponent.kt`
- Web browser base URL selection: `web/src/6-shared/api/http-client.ts`

## Base URL And Routing Rules

Use these routing rules when changing clients or backend endpoints:

- Backend HTTP controllers are mounted without an `/api` prefix in Nest controllers, but production ingress exposes them under `/api/` through Nginx.
- Web client requests should stay relative to `/api` in production and use `http://localhost:3001` only for local browser development.
- Shared mobile KMM clients build absolute URLs from `HostConfig` and append path segments explicitly.

Current host behavior:

- Mobile shared KMM host: `https://dev-api.commonex.ru`
- Browser local development host: `http://localhost:3001`
- Browser production base path: `/api`
- Backend HTTP listen port: `3001`

Do not hardcode full endpoint URLs in feature stores when `HostConfig` or the web `HttpClient` base logic can supply the host portion.

## API Versioning

HTTP API versioning is path-based.

Current observed pattern:

- `/api/user/...` for legacy or v1-style routes such as event creation, event deletion, and base currency list access
- `/api/v2/user/...` for event reads and mutations that use request bodies and support share-token based event access
- `/api/v3/user/...` for currencies-with-rates access used by the web client

Representative routes:

- Create event: `/api/user/event`
- Delete event: `/api/user/event/:eventId`
- Get event info via token or pin code: `/api/v2/user/event/:eventId`
- Add users to event: `/api/v2/user/event/:eventId/users`
- Get event expenses: `/api/v2/user/event/:eventId/expenses`
- Create expense: `/api/v2/user/event/:eventId/expense`
- Create event share token: `/api/v2/user/event/:eventId/share-token`
- Get currencies with rates: `/api/v3/user/currencies/all`

When changing routes:

- preserve version segments instead of silently moving behavior between versions
- document cross-client impact before changing shared endpoints
- prefer adding a new versioned route over changing request semantics in place when an existing client contract would break

## Access And Authentication Model

The public user-facing HTTP API is currently access-code based, not session-token based.

Current contract:

- There is no general `Authorization: Bearer ...` application-auth layer in the user-facing clients covered here.
- Event-scoped access is granted by request data:
    - `pinCode` for event-protected operations
    - `token` for share-token based event reads where supported
- For V2 event-info reads, exactly one of `pinCode` or `token` must be provided.

Operational consequences:

- Treat `pinCode` and share `token` as request credentials, even though they are body/query fields rather than headers.
- Do not add app-auth assumptions to mobile or web clients without documenting a new repo-wide contract first.
- If a route currently validates event access with `pinCode`, keep that behavior aligned across clients before introducing alternate auth paths.

Special-case header:

- `x-devtools-secret` exists for backend devtools endpoints only. It is not part of the normal mobile/web user API contract.

## Request Shape Conventions

These conventions are stable enough to document even though DTO details belong elsewhere:

- JSON is the default payload format for HTTP requests and responses.
- Web client requests always send `Content-Type: application/json`.
- Shared mobile KMM stores set JSON content type explicitly on mutating requests and rely on Ktor content negotiation for JSON decoding.
- V2 event operations commonly use `POST` with a JSON body even for read-style operations such as event info and expense retrieval.
- URL path construction in shared mobile code should use structured path segments rather than raw string concatenation.

Examples of body-level access patterns:

- V2 event info: body contains either `pinCode` or `token`
- V2 expenses read: body contains `pinCode`
- V2 expense creation: body contains domain payload plus `pinCode`
- Share-token creation: body contains `pinCode`

## Response And Error Envelope

Backend business and validation errors are intentionally normalized into a compact JSON envelope.

Current HTTP error shape:

- `statusCode`
- `code`
- `message`

Observed sources:

- domain/business errors: `backend/src/api/http/filters/business-error.filter.ts`
- validation errors: `backend/src/api/http/filters/validation-exception.filter.ts`

Client expectations:

- Web `HttpClient` parses that envelope and raises an `ApiError` using `statusCode`, `code`, and `message`.
- Shared mobile code can parse backend `code` values from client-error responses when route-specific mapping needs it.
- Do not replace this envelope with ad hoc per-endpoint error payloads unless all consuming clients are updated together.

Known route-specific mapping examples:

- V2 token-based event reads distinguish invalid token vs expired token by backend error `code`.
- Validation failures are exposed as `400` plus `VALIDATION_ERROR` in the normalized error envelope.

## Retry, Redirect, And Failure Semantics

Shared mobile KMM HTTP behavior is centralized in the shared network module and should stay coherent across Android and iOS.

Current Ktor client rules:

- `expectSuccess = true`: non-2xx responses surface as exceptions and enter centralized error mapping
- `followRedirects = false`: redirect responses are not automatically followed
- `ContentNegotiation` uses Kotlinx Serialization JSON with `ignoreUnknownKeys = true`

Retry policy:

- Automatic retries are limited to HTTP `409 Conflict`
- Only idempotent methods are retried automatically: `GET`, `HEAD`, `PUT`, `DELETE`, `OPTIONS`
- Retry budget: 2 retries after the initial attempt
- Delay policy: exponential backoff with base `200ms`, max `2000ms`, randomization `100ms`, respecting `Retry-After` when present
- `POST` requests are not retried automatically by default, even when they return `409`

Shared mobile failure mapping:

- HTTP `409` client errors map to `IoResult.Error.Retry`
- redirect responses map to `IoResult.Error.Retry`
- server errors map to `IoResult.Error.Retry`
- transport `IOException` maps to `IoResult.Error.Retry`
- parse errors map to `IoResult.Error.Failure`
- other non-409 client errors map to `IoResult.Error.Failure`

Implications for future changes:

- Do not assume `POST` mutations are automatically retried; design idempotency and sync behavior explicitly.
- If you change retry-worthy statuses or method rules, update the shared Ktor config and the contract doc together.
- If a backend route starts depending on redirect semantics, mobile clients will currently treat redirects as retry/failure conditions rather than following them.

Validation reference:

- `android/shared/core/network/src/commonTest/kotlin/com/inwords/expenses/core/network/RequestRetryTest.kt`

## User-Agent

The canonical `User-Agent` format for mobile HTTP clients is:

`CommonEx/<versionCode> (<platform>/<buildType>)`

Field meanings:

- `versionCode`: application build/version code passed into the shared network layer
- `platform`: client platform identifier
- `buildType`: short build-type marker

Current allowed values:

- `platform`: `Android`, `iOS`
- `buildType`: `r` for production/release builds, `d` for non-production/debug builds

Examples:

- `CommonEx/123 (Android/r)`
- `CommonEx/123 (Android/d)`
- `CommonEx/456 (iOS/r)`

Source of truth:

- Implementation: `android/shared/core/network/src/commonMain/kotlin/com/inwords/expenses/core/network/UserAgent.kt`
- Format validation: `android/shared/core/network/src/commonTest/kotlin/com/inwords/expenses/core/network/UserAgentTest.kt`

Notes:

- The shared KMM network layer builds this value centrally so Android and iOS stay aligned.
- If additional clients start sending the same logical contract, update this document first and then align implementations.

## Ingress And Protocol Notes

Production Nginx is configured for:

- HTTPS and HTTP/2
- HTTP/3/QUIC advertisement on public hosts
- `/api/` proxying to backend HTTP service
- separate `grpc.commonex.ru` gRPC ingress
- permissive backend CORS (`origin: '*'`) at the Nest layer

Do not assume the browser talks directly to `dev-api.commonex.ru` in production. The documented browser contract is same-origin `/api` through the gateway.

## Change Checklist For Agents

When changing network communication behavior, check these before editing:

1. Is the change transport-level and shared across clients? If yes, update this document.
2. Does the change affect route versioning or access method (`pinCode`, `token`, devtools header)? If yes, call out cross-client impact first.
3. Does the change affect retryability, redirect handling, error envelope shape, or `User-Agent`? If yes, update shared client code and docs together.
4. Does the change belong to transport behavior rather than DTO schema? Keep it here; keep DTO field details out of this file.
5. Is the behavior mobile-only, web-only, or backend-only? Document the repo-wide rule here only if at least one other layer depends on it or needs to know about it.
