# Infrastructure Troubleshooting

## Container Issues

- **Container won't start**: Check logs:
  `docker compose -f infra/docker-compose-prod.yml logs [service-name]`
- **Container keeps restarting**: Check exit codes and logs
- **Port conflicts**: Verify ports aren't already in use: `netstat -tuln` or `lsof -i :PORT`

## Network Issues

- **Services can't communicate**: Verify Docker network exists: `docker network ls`
- **Connection refused**: Check service is running and listening on correct port
- **Host tests cannot reach DB**: `docker-compose-prod.yml` keeps DB on an internal network; temporarily publish
  `5432:5432` only for local test runs.
- **Nginx Docker DNS re-resolution**: Compose-backed upstreams use Docker's `127.0.0.11` resolver, refresh names every
  10 seconds, and time out DNS queries after 5 seconds. Verify service names match `infra/docker-compose-prod.yml`
  and every `server ... resolve` entry remains inside an upstream with a shared `zone`. Inspect the effective runtime
  configuration with `docker compose -f infra/docker-compose-prod.yml exec nginx nginx -T` and check Nginx error logs
  for resolver failures.

## Logging Issues

- **No logs appearing**: Check log driver configuration
- **View Nginx error logs**:
  `docker compose -f infra/docker-compose-prod.yml exec nginx cat /var/log/nginx/error.log`

## OpenTelemetry Issues

- **No traces appearing**: Check collector configuration and service connectivity
- **Collector not receiving data**: Verify service instrumentation and collector endpoints
- **Review collector logs**: `docker compose -f infra/docker-compose-prod.yml logs otel-collector`
- **OTel metrics export 503 / VictoriaMetrics `storage is in read-only mode`**: VictoriaMetrics stopped accepting writes because free disk on the volume for `victoriametrics_data` fell below `-storage.minFreeDiskSpaceBytes` (default 10MB). Free host/volume space (`df -h`, `docker system df`), then restart `victoriametrics`. Collector retries are expected until writes succeed again.
- **Delta metrics with VictoriaMetrics**: If a source emits delta temporality, add `deltatocumulativeprocessor` in the
  collector metrics pipeline before `otlp_http/victoriametrics`.
- **Metrics appear in Grafana after ~1 minute**:
    - check `span_metrics` flush cadence (`metrics_flush_interval`) in `infra/otel-collector/otel-collector-config.yaml`
    - check VictoriaMetrics query-side delay (`-search.latencyOffset`) in `infra/docker-compose-prod.yml`
    - check panel/query window (`$__rate_interval`, step/min-interval), especially for `rate(...)` queries
- **Collector rejects telemetry under memory pressure**: `memory_limiter` refusals are retryable, but telemetry can be lost
  if senders exhaust their retries. Check Collector memory/refusal metrics and downstream exporter health.
- **Collector health**: Probe `http://127.0.0.1:13133/` from inside the Collector container. The endpoint uses component
  status reporting, so its result depends on components emitting status; an exporter retry alone does not necessarily make
  it unhealthy.

## Resource Issues

- **Disk space**: Clean up unused images: `docker system prune -a`
- **Memory**: Monitor with `docker stats` and adjust limits if needed
