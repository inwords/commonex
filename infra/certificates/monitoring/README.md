# Certificate monitoring

## State

- The host runner publishes renewal results, certificate expiry, and public TLS probe metrics to VictoriaMetrics.
- The independent monitor timer runs hourly; renewal checks also publish metrics.
- Grafana uses the existing `victoriametrics-metrics-datasource` (UID `bf05veuz39r0ge`).
- Six certificate rules in **CommonEx operations / commonex-certificates** evaluate on the host. All use the `commonex-certificates-ui-only` mute interval; outbound notifications are deferred.
- Certificate delivery does not install, reload, back up, or restore Grafana.

## Operations

Inspect the rules in Grafana for renewal failures, expiry, failed TLS probes, certificate mismatches, and stale monitoring. Healthy rules have state Normal and health `ok`.

Certificate alert rules and the `commonex-certificates-ui-only` mute timing are
DB-owned in `grafana_data`.

For metric publication or renewal failures, use the [certificate recovery runbook](../README.md#failure-recovery-and-rollback). Metric names and publication behavior are defined in [renew.py](../renew.py).

## Deferred work

- Configure outbound notifications and verify firing and resolved delivery.
- Add monitoring outside LLHost for complete host outages.
