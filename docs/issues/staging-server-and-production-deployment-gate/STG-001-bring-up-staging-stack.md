---
id: STG-001
title: Bring up the persistent full production stack on staging
labels: [ready-for-agent]
phase: 1
blocked_by: []
status: implemented
---

## Outcome

A developer can reach a persistent, initialized CommonEx staging environment running the full production Compose topology on a dedicated staging host. This ticket delivers the first usable deployment with manual operation; automatic release rehearsals follow in phase 2.

## Context

Follow the [accepted spec](../staging-server-and-production-deployment-gate.md). Obtain host access through private operator configuration. Verify Docker, Compose, administrative access, and the absence of an existing CommonEx deployment during preparation.

## Scope

- Extend existing delivery configuration and checks to target staging. Use the accepted namespaced host layout and Release/Activation lifecycle, preserving activation safety mechanisms and existing production behavior.
- Run the complete production Compose definition, including persistent PostgreSQL and observability storage. Initialize an empty database and application without importing production data. Preserve state on subsequent deployments.
- Configure web and HTTP API at `staging.commonex.ru`, gRPC at `staging-grpc.commonex.ru`, and Grafana at `staging-gf.commonex.ru`.
- Make reverse-proxy routes and Grafana's public URL environment-selectable while using the exact custom-image digests eligible for later production promotion. Do not create staging-specific application or nginx builds. Preserve release-bundle validation guarantees if its configuration format changes.
- Arrange delivery of the existing wildcard certificate and private key, including automatic renewal propagation, through an operator-controlled mechanism. Supply isolated staging credentials without committing secret values.
- Document repeatable bootstrap/deployment commands, environment inputs, persistent-state locations, certificate renewal delivery, and public verification in the relevant existing docs.

## Acceptance criteria

1. The supplied host runs every service in the production Compose topology; health checks pass where defined.
2. Trusted HTTPS serves the three agreed domains; public web, HTTP API, and gRPC checks succeed. Required TCP HTTPS and UDP HTTP/3 access is configured.
3. Grafana is reachable, telemetry ingestion works, and configured datasources can be queried.
4. A repeat deployment preserves database and observability data and uses recorded immutable image identities.
5. Staging configuration, state, and credentials are separate from production, with a documented administrative and future CI access model.

## Validation

Reuse public-service verification and deployment-wrapper regression coverage for environment selection. Record service health, public transport checks, telemetry/datasource evidence, deployed release identity, and persistence across a repeat deployment. Container startup alone is insufficient.

## Operational prerequisites

Verify DNS administration, certificate/key and renewal access, application/exchange-rate/Grafana credentials, registry access, SSH and administrative access, and required network ports. Report missing prerequisites without exposing secrets.

## Non-goals

Android endpoint selection and fixture provisioning belong to STG-002. This ticket adds no release gate, automatic production promotion, certificate issuance, database resets, branch environments, or new test/deployment coordination.

## Implementation and verification

The persistent stack is live. Setup, private configuration inputs, and ongoing
operation are documented in the [staging runbook](../../../infra/deploy/staging.md).

- All nine production-topology services run; all five defined health checks pass.
- Trusted TLS serves web/API, Grafana, and gRPC using the existing wildcard
  certificate. Public HTTP/2 and HTTP/3 checks passed.
- Exchange rates were initialized; the currencies API works. No production
  application data was imported.
- Grafana's PostgreSQL, VictoriaMetrics, and VictoriaTraces data sources were
  queried successfully. Backend and gateway traces were observed.
- Repeat activation preserved marked API data, historical metrics, and traces.
  It reused the same immutable main images and retained all persistent volumes.
- Restricted deployment access rejects arbitrary commands and retains activation
  replay protection. Credentials and access identities remain in private
  configuration. Production application configuration was preserved.
- Automatic renewal dispatch and its hourly retry are enabled. Two transfers
  succeeded; the dedicated SSH key rejects arbitrary commands and general sudo.
  Trusted staging endpoints serve the same certificate as production. Existing
  production renewal code, service guards, and certificate material were preserved.
- Linux validation: 176 deployment tests and 16 certificate propagation tests
  passed; the new systemd units validated successfully.

The shared Nginx image now owns hostname templates and its startup entrypoint.
The full image build passed its existing TLS/header feature proofs. Isolated
production and staging containers passed startup as the Nginx user, trusted
synthetic-certificate TLS, hostname selection, and standard validation/reload
checks. Request variables remained literal; invalid hostnames were rejected.
Build its new immutable `main` image before deploying the revised Compose file.
The running bootstrap remains on its previously verified image and activation.

Deployment identities, image digests, and detailed probe evidence are retained
in the private operator inventory. Android fixtures/tests and automatic release
gating belong to the following tickets.
