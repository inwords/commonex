# Certificate monitoring

## State

- The host runner publishes renewal results, certificate expiry, and public TLS probe metrics to VictoriaMetrics.
- The independent monitor timer runs hourly; renewal checks also publish metrics.
- Grafana uses the existing `victoriametrics-metrics-datasource` (UID `bf05veuz39r0ge`).
- Six certificate rules in **CommonEx operations / commonex-certificates** evaluate on the host. All use the `commonex-certificates-ui-only` mute interval; outbound notifications are deferred.
- Grafana configuration is host-managed. Certificate delivery does not install, reload, back up, or restore it. Grafana state versioning is a separate follow-up.

## Operations

Inspect the rules in Grafana for renewal failures, expiry, failed TLS probes, certificate mismatches, and stale monitoring. Healthy rules have state Normal and health `ok`.

The existing alert provisioning file is `/etc/commonex/app/grafana/provisioning/alerting/certificates.json` on LLHost. Preserve it and the datasource during certificate updates. The running Grafana instance is the source of truth for rules, thresholds, routing, and datasource settings.

For metric publication or renewal failures, use the [certificate recovery runbook](../README.md#failure-recovery-and-rollback). Metric names and publication behavior are defined in [renew.py](../renew.py).

## Deferred work

- Version and deploy Grafana state through a separately designed workflow.
- Configure outbound notifications and verify firing and resolved delivery.
- Add monitoring outside LLHost for complete host outages.
