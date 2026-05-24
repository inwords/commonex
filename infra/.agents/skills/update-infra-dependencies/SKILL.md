---
name: update-infra-dependencies
description: Use when updating Docker image tags, custom infrastructure build inputs, or observability stack versions in the CommonEx infra project.
---

# Update Infra Dependencies

Follow `.agents/skills/update-dependency-batch` for the mandatory four-phase workflow.

## Version Sources In Repo

Read versions from:
- `infra/docker-compose-prod.yml`
- `infra/nginx/Dockerfile`
- `infra/otel-collector/Dockerfile`
- `infra/otel-collector/manifest.yaml`
- `infra/nginx/nginx-prod.conf`
- `infra/otel-collector/otel-collector-config.yaml`

## Where To Check On The Internet

Use official or primary sources:
- Docker Hub, GHCR, or the image publisher's official tag listings for images pinned in `docker-compose-prod.yml`
- NGINX official changelog for `NGINX_VERSION`
- OpenSSL release notes for `OPENSSL_VERSION`
- `nginxinc/nginx-otel` releases for `NGINX_OTEL_VERSION`
- OpenTelemetry Collector official releases for `OTEL_VERSION`
- VictoriaMetrics, Grafana, PostgreSQL, and Certbot official releases or image release notes

For custom-built images, verify both the base image and the source-built component versions.

## Smallest Valid Update Bundles

Default bundle boundaries:
- **Nginx source-build stack**: `NGINX_VERSION`, `OPENSSL_VERSION`, `NGINX_OTEL_VERSION`, and any required config changes driven by upstream behavior
- **OTel Collector stack**: `OTEL_VERSION` and any manifest or config changes required by the selected collector release
- **VictoriaMetrics metrics stack**: `victoriametrics/victoria-metrics` and `victoriametrics/victoria-traces` when a coordinated upgrade is recommended upstream
- **Grafana stack**: `grafana/grafana` plus provisioning adjustments if release notes require them
- **Database image stack**: `postgres` image updates
- **Certbot stack**: `certbot/certbot` updates

Independent image bumps are acceptable when upstream compatibility does not couple them.

## Proposal

Follow `.agents/skills/update-dependency-batch` (one `## N — …` section per bundle: **Summary**, then full numbered release notes). **Stop** after the proposal; do not edit manifests until the user selects bundles.

## Apply (after user selection)

When the user selects bundles in a follow-up message:
- update the exact image tags or Dockerfile args
- adjust configs when upstream defaults or directives changed
- call out any operational migration steps instead of hiding them in code changes
- ask before making uncertain topology or rollout behavior changes

## Validation

Follow project's validation instructions.

If an image tag update was selected for a service without a custom Dockerfile, config validation is the minimum gate unless the user asks for a live pull or deploy check.
