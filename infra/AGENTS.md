# Agent Instructions for CommonEx Infrastructure

For non-trivial work and when to search upstream docs, follow root [AGENTS.md](../AGENTS.md) (workflow lifecycle and freshness policy).

## Project Overview

CommonEx infrastructure uses Docker Compose for containerization, Nginx as reverse proxy with OpenTelemetry support,
and OpenTelemetry Collector for observability. Supports blue-green deployment for backend services.

**Compose shorthand:** All commands below assume `docker compose -f infra/docker-compose-prod.yml` as the compose prefix. Run from repo root.

## Components

### Nginx

- Custom build with OpenTelemetry module (`ngx_otel_module`)
- HTTP1.1, HTTP/2 and HTTP/3 (QUIC) support for clients; h2c to upstream backends
- Brotli compression
- SSL/TLS termination with certificate compression (`ssl_certificate_compression on`)
- Docker DNS re-resolution for replaceable Compose service containers
- Latency-aware `least_time header inflight` balancing for blue-green backend traffic
- A 100-request-header limit, validated during the image build
- Build-time TLS 1.3 proof using the `X25519MLKEM768` post-quantum hybrid group
- Security headers defined at `http` level and inherited via `add_header_inherit merge`

### OpenTelemetry Collector

- Receives traces and metrics from services
- Configured via `otel-collector-config.yaml`
- Exports to VictoriaMetrics via OTLP HTTP (`/opentelemetry/v1/metrics`) with cumulative temporality
- Runs `memory_limiter` before `batch` with `GOMEMLIMIT=150MiB` under the 200 MB container limit
- Excludes `collector.instance.id` from span metrics because the deployment has one Collector instance
- Uses the legacy-semantics health endpoint locally at `127.0.0.1:13133` for its Compose health check
- `deltatocumulative` is not currently configured; add it only if an upstream source starts emitting delta metrics

### Docker Compose

- **Production**: `docker-compose-prod.yml` - production deployment with blue-green backend and an internal-only
  PostgreSQL network (DB port not published to host by default)

## Essential Commands

```bash
# Start (from repo root)
docker compose -f infra/docker-compose-prod.yml up -d --pull always

# Start with build
docker compose -f infra/docker-compose-prod.yml up -d --build

# Stop
docker compose -f infra/docker-compose-prod.yml down

# Rebuild specific service
docker compose -f infra/docker-compose-prod.yml build nginx
docker compose -f infra/docker-compose-prod.yml up -d nginx

# Logs
docker compose -f infra/docker-compose-prod.yml logs -f [service-name]

# Status
docker compose -f infra/docker-compose-prod.yml ps
```

## Local Development

1. Use `infra/docker-compose-prod.yml` for parity checks when needed.
2. For host-based backend tests, temporarily publish DB port `5432:5432` in the `db` service.
3. Remove temporary DB port publishing after tests to keep production topology unchanged.
4. Quick local Postgres option:
   ```bash
   docker run --name commonex-postgres-test -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=postgres -p 5432:5432 -d postgres:17-alpine3.24
   docker rm -f commonex-postgres-test  # cleanup
   ```

## Configuration

### Nginx

- **Production config**: `infra/nginx/nginx-prod.conf`
- **Dockerfile**: `infra/nginx/Dockerfile`
- Upstream configuration for backend services (blue-green)

### SSL and Certificates

- **Wildcard certificate** for `*.commonex.ru` (Certbot, DNS-01 challenge)
- **Main domain** (`commonex.ru`, `www.commonex.ru`): HTTP 80 and HTTPS 443
- **API, Grafana, and gRPC subdomains** (`dev-api.commonex.ru`, `gf.commonex.ru`, `grpc.commonex.ru`): HTTPS-only
- **HTTP/3 bootstrap**: production uses DNS HTTPS/SVCB record to advertise HTTP/3 before first `Alt-Svc` response
- **Alt-Svc fallback**: nginx advertises `Alt-Svc: h3=":443"; ma=86400` on HTTPS responses
- **QUIC requirements**: nginx listens on `443` TCP+UDP; Compose publishes both. Removing UDP breaks HTTP/3.
- **QUIC transport**: `quic_retry on` and `quic_gso on` enabled
- **TLS**: 1.3-only (`ssl_protocols TLSv1.3`), `ssl_early_data on`
- **TLS certificate compression**: requires OpenSSL with `enable-brotli` and `enable-zstd`
- **h2c to upstream**: `proxy_http_version 2`; backend uses Fastify `http2: true`; web container uses embedded nginx `http2 on`
- **Grafana** (`gf.commonex.ru`): dedicated HTTPS `server` block; `GF_SERVER_ROOT_URL=https://gf.commonex.ru/`; nginx proxies to `grafana:3000` over HTTP
- **gRPC transport**: `grpc.commonex.ru` terminated by dedicated server block with `grpc_pass`; not an `/api` route
- **Mobile app links**: `/.well-known/apple-app-site-association` and `/.well-known/assetlinks.json` are served from `/etc/commonex/www/` on the host (see `infra/www/`). Copy `infra/www/apple-app-site-association` to the server before deploy
- **ECH**: not yet enabled; requires OpenSSL 4.0

### OpenTelemetry

- **Config**: `infra/otel-collector/otel-collector-config.yaml`
- **Dockerfile**: `infra/otel-collector/Dockerfile`
- **Manifest**: `infra/otel-collector/manifest.yaml`

## Deployment

### Health Checks

- Nginx monitors upstream health
- Backend services expose `/health` endpoints
- The OpenTelemetry Collector health check probes `http://127.0.0.1:13133/` inside its container

### Blue-Green Deployment

- Backend services support blue-green deployment
- Nginx configured for upstream load balancing
- Zero-downtime deployments possible

## Validation Steps

```bash
docker compose -f infra/docker-compose-prod.yml config
docker compose -f infra/docker-compose-prod.yml ps
docker build --pull --tag commonex-nginx:validation infra/nginx
```

For troubleshooting, see [`docs/troubleshooting.md`](docs/troubleshooting.md).
