# Agent Instructions for CommonEx Infrastructure

For non-trivial work and when to search upstream docs, follow root [AGENTS.md](../../AGENTS.md) (workflow lifecycle and freshness policy).

## Project Overview

CommonEx infrastructure uses Docker and Docker Compose for containerization, Nginx as a reverse
proxy with OpenTelemetry support, and OpenTelemetry Collector for observability. The setup supports
blue-green deployment for backend services.

## Technology Stack

- **Containerization**: Docker
- **Reverse Proxy**: Nginx (with OpenTelemetry module)
- **Observability**: OpenTelemetry Collector
- **Orchestration**: Docker Compose for the documented production stack in `infra/docker-compose-prod.yml`

## Architecture

- Multi-container setup with Docker Compose
- Nginx as reverse proxy and gateway
- OpenTelemetry for distributed tracing and metrics
- Blue-green deployment support for backend services

## Components

### Nginx

- Custom build with OpenTelemetry module (`ngx_otel_module`)
- HTTP1.1, HTTP/2 and HTTP/3 (QUIC) support for clients and h2c (HTTP/2 cleartext) to upstream backends
- Brotli compression
- SSL/TLS termination with certificate compression (`ssl_certificate_compression on`)
- Upstream load balancing for backend services
- Security headers defined at `http` level and inherited via `add_header_inherit merge`

### OpenTelemetry Collector

- Receives traces and metrics from services
- Exports to configured backends
- Configured via `otel-collector-config.yaml`

### Docker Compose

- **Production**: `docker-compose-prod.yml` - production deployment with blue-green backend and an internal-only
  PostgreSQL network (DB port is not published to the host by default)

## Prerequisites

- **Docker and Docker Compose** (versions: see `docker-compose-prod.yml` or Dockerfiles; do not duplicate in docs).
- **Git** for version control

## Environment Setup

### Docker Installation

Ensure Docker and Docker Compose are installed and running:

```bash
# Check Docker
docker --version

# Check Docker Compose
docker compose version

# Verify Docker daemon is running
docker ps
```

### Configuration

- Environment variables configured on the server
- Docker network setup for service communication
- Volume mounts for persistent data (if needed)

## Essential Commands

**Run commands from repository root or `infra/` directory as specified.**

### Starting Services

```bash
# Production (from repo root)
docker compose -f infra/docker-compose-prod.yml up -d --pull always

# Production (with build)
docker compose -f infra/docker-compose-prod.yml up -d --build

# View logs while starting
docker compose -f infra/docker-compose-prod.yml up
```

### Stopping Services

```bash
# Stop services (from repo root)
docker compose -f infra/docker-compose-prod.yml down

# Stop and remove volumes
docker compose -f infra/docker-compose-prod.yml down -v
```

### Viewing Logs

```bash
# All services
docker compose -f infra/docker-compose-prod.yml logs -f

# Specific service
docker compose -f infra/docker-compose-prod.yml logs -f nginx
docker compose -f infra/docker-compose-prod.yml logs -f nest-backend-green
docker compose -f infra/docker-compose-prod.yml logs -f nest-backend-blue
docker compose -f infra/docker-compose-prod.yml logs -f otel-collector

# Last 100 lines
docker compose -f infra/docker-compose-prod.yml logs --tail=100
```

### Service Management

```bash
# Restart a specific service
docker compose -f infra/docker-compose-prod.yml restart nginx

# Scale services (if applicable)
docker compose -f infra/docker-compose-prod.yml up -d --scale nest-backend-green=1 --scale nest-backend-blue=1

# Check service status
docker compose -f infra/docker-compose-prod.yml ps
```

## Development Workflow

### Production Deployment

1. Use `docker-compose-prod.yml` for production
2. Configure environment variables on the server
3. Deploy via CI/CD pipeline or manually:
   ```bash
   docker compose -f infra/docker-compose-prod.yml up -d --pull always
   ```

### Local Development

1. Use `infra/docker-compose-prod.yml` for parity checks when needed.
2. For host-based backend tests, temporarily publish DB port `5432:5432` in the `db` service and use host DB env values
   in `backend/` tests.
3. Remove temporary DB port publishing after tests to keep production topology unchanged.
4. Quick local Postgres option for host tests (without editing compose):
   ```bash
   docker run --name commonex-postgres-test -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=postgres -p 5432:5432 -d postgres:17-alpine3.23
   # cleanup
   docker rm -f commonex-postgres-test
   ```

## Configuration

### Nginx Configuration

- **Production config**: `infra/nginx/nginx-prod.conf`
- **Custom Dockerfile**: `infra/nginx/Dockerfile`
- Includes OpenTelemetry module configuration
- Upstream configuration for backend services (blue-green)

### SSL and Certificates

- **Wildcard certificate** for `*.commonex.ru` (Certbot, single mount at `/etc/nginx/ssl/live/commonex.ru`)
- **Certificate renewal** uses DNS-01 challenge (no HTTP ACME challenge location required)
- **Main domain** (`commonex.ru`, `www.commonex.ru`): HTTP port 80 and HTTPS port 443
- **API and gRPC subdomains** (`dev-api.commonex.ru`, `grpc.commonex.ru`): HTTPS-only; no plain HTTP access expected
- **HTTP/3 bootstrap**: production also uses a DNS HTTPS/SVCB record to advertise HTTP/3-capable endpoints before the first `Alt-Svc` response. This is external DNS configuration, not repo-managed; keep infra docs and DNS config aligned when transport behavior changes.
- **Alt-Svc fallback**: nginx still advertises `Alt-Svc: h3=":443"; ma=86400` on HTTPS responses, so clients can discover HTTP/3 support even without DNS bootstrapping.
- **QUIC listener requirements**: nginx listens on `443` for both TCP and UDP, and Compose publishes both `443:443/tcp` and `443:443/udp`. If UDP publishing is removed, HTTP/3 negotiation stops working even if nginx config still enables `http3 on`.
- **QUIC transport settings**: nginx enables `quic_retry on` and `quic_gso on` in the production gateway config.
- **TLS protocol policy**: nginx is currently TLS 1.3-only (`ssl_protocols TLSv1.3`) and has `ssl_early_data on` enabled.
- **TLS certificate compression** enabled (`ssl_certificate_compression on`) — reduces TLS handshake size.
  The nginx image must be built with OpenSSL options `enable-brotli` and `enable-zstd`, and build deps
  `brotli-dev` and `zstd-dev`; otherwise nginx logs `SSL_CTX_compress_certs()` warnings.
- **HTTP/2 to upstream (h2c)**: gateway nginx proxies to backends via `proxy_http_version 2`. Backend (NestJS) uses the
  **Fastify** adapter with `http2: true` (h2c) in `main.ts`; web container (static export) uses embedded nginx with
  `http2 on`. Backend Docker health checks use Node’s `http2` client (h2c prior knowledge).
- **gRPC transport split**: `grpc.commonex.ru` is terminated by a dedicated nginx server block that proxies with `grpc_pass grpc://keepalive-nest-backend-grpc`; do not treat it as just another `/api` route.
- **Encrypted Client Hello (ECH)**: not yet enabled; requires OpenSSL 4.0 (expected April 2026). When available,
  configure with `ssl_ech_file` directive per server block. ECH encrypts SNI in the TLS handshake for privacy.
  See [nginx ECH docs](https://nginx.org/en/docs/http/ngx_http_ssl_module.html#ssl_ech_file)

### OpenTelemetry Configuration

- **Config file**: `infra/otel-collector/otel-collector-config.yaml`
- **Dockerfile**: `infra/otel-collector/Dockerfile`
- **Manifest**: `infra/otel-collector/manifest.yaml`
- Metrics are exported to VictoriaMetrics via OTLP HTTP (`/opentelemetry/v1/metrics`) with cumulative temporality as
  the default expectation.
- `deltatocumulative` is not configured in the current collector pipeline; add it only if an upstream source starts
  emitting delta metrics.

### Docker Compose Files

- **Production**: `infra/docker-compose-prod.yml`

## Deployment

### Health Checks

- Services include health check configurations
- Nginx monitors upstream health
- Backend services expose `/health` endpoints

### Blue-Green Deployment

- Backend services support blue-green deployment
- Nginx configured for upstream load balancing
- Zero-downtime deployments possible

## Common Tasks

### Rebuilding Services

```bash
# Rebuild and restart all services
docker compose -f infra/docker-compose-prod.yml up -d --build

# Rebuild specific service
docker compose -f infra/docker-compose-prod.yml build nginx
docker compose -f infra/docker-compose-prod.yml up -d nginx
```

### Updating Configuration

1. Update configuration files
2. Rebuild affected services: `docker compose -f infra/docker-compose-prod.yml build [service]`
3. Restart services: `docker compose -f infra/docker-compose-prod.yml up -d [service]`

### Inspecting Services

```bash
# Execute command in running container
docker compose -f infra/docker-compose-prod.yml exec nginx sh
docker compose -f infra/docker-compose-prod.yml exec nest-backend-green sh
docker compose -f infra/docker-compose-prod.yml exec nest-backend-blue sh

# Check container resource usage
docker stats

# Inspect container configuration
docker compose -f infra/docker-compose-prod.yml config
```

## Validation Steps

Before deploying, verify:

```bash
# 1. Check Docker Compose configuration is valid
docker compose -f infra/docker-compose-prod.yml config

# 2. Check service status
docker compose -f infra/docker-compose-prod.yml ps

# 3. Check service health
docker compose -f infra/docker-compose-prod.yml ps --format json | jq '.[] | {name: .Name, status: .State}'

# 4. Verify network connectivity
docker compose -f infra/docker-compose-prod.yml exec nest-backend-green ping nginx
```

## Troubleshooting

### Container Issues

- **Container won't start**: Check logs:
  `docker compose -f infra/docker-compose-prod.yml logs [service-name]`
- **Container keeps restarting**: Check exit codes and logs
- **Port conflicts**: Verify ports aren't already in use: `netstat -tuln` or `lsof -i :PORT`
- **Out of memory**: Check Docker resource limits and system memory

### Network Issues

- **Services can't communicate**: Verify Docker network exists: `docker network ls`
- **Connection refused**: Check service is running and listening on correct port
- **Host tests cannot reach DB**: `docker-compose-prod.yml` keeps DB on an internal network; temporarily publish
  `5432:5432` only for local test runs.
- **DNS resolution**: Verify service names match `infra/docker-compose-prod.yml`

### Logging Issues

- **No logs appearing**: Check log driver configuration
- **Logs too verbose**: Adjust log levels in service configuration
- **View Nginx error logs**:
  `docker compose -f infra/docker-compose-prod.yml exec nginx cat /var/log/nginx/error.log`

### OpenTelemetry Issues

- **No traces appearing**: Check collector configuration and service connectivity
- **Collector not receiving data**: Verify service instrumentation and collector endpoints
- **Review collector logs**: `docker compose -f infra/docker-compose-prod.yml logs otel-collector`
- **Delta metrics with VictoriaMetrics**: If a source is configured to emit delta temporality (for example via
  `OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE=delta`), add `deltatocumulativeprocessor` in the collector
  metrics pipeline before `otlphttp/victoriametrics`.
- **Metrics appear in Grafana after ~1 minute**:
    - check `spanmetrics` flush cadence (`metrics_flush_interval`) in `infra/otel-collector/otel-collector-config.yaml`
    - check VictoriaMetrics query-side delay (`-search.latencyOffset`) in `infra/docker-compose-prod.yml`
    - check panel/query window (`$__rate_interval`, step/min-interval), especially for `rate(...)` queries

### Common Errors

- **"Cannot connect to Docker daemon"**: Ensure Docker daemon is running
- **"Port already allocated"**: Change port mapping or stop conflicting service
- **"No such service"**: Verify the service name in `infra/docker-compose-prod.yml`
- **"Permission denied"**: Check Docker socket permissions or use `sudo` (not recommended)
- **"Network not found"**: Recreate network: `docker compose -f infra/docker-compose-prod.yml down`
  then `up`

### Resource Issues

- **Docker resource limits**: Check Docker Desktop settings or daemon configuration
- **Disk space**: Clean up unused images: `docker system prune -a`
- **Memory**: Monitor with `docker stats` and adjust limits if needed
