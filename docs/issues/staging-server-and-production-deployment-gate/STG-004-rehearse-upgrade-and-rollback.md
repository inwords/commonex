---
id: STG-004
title: Rehearse staging upgrade and application rollback with persistent data
labels: [ready-for-agent]
blocked_by: [STG-003]
phase: 2
---

## Outcome

Each candidate from `main` receives a recorded A → B → A → B rehearsal, proving public-service behavior and data compatibility before production promotion.

## Context

[Parent spec](../staging-server-and-production-deployment-gate.md). A is the distinct previously deployed, retained staging release; B is the candidate identified by Git SHA and immutable custom-image digests. Staging data accumulates. Ordinary rollback preserves the migrated database. The existing Android suite and runner remain unchanged.

## Scope

Extend the existing delivery entry point and verification seams to perform the rehearsal:

Integrate this sequence into STG-003's pipeline rather than append it after a completed candidate deployment. Capture A and establish its test records before the first B activation, so pending migrations operate on baseline data.

1. Under A, create identifiable events, participants, and expenses through the backend; confirm remote persistence.
2. Activate B using the candidate's immutable release images and run pending migrations.
3. Check all Compose services, available health checks, public web/HTTP API/gRPC routes, Grafana, and telemetry ingestion/datasource access. Run the existing Android suite under its current pass/fail and retry rules. Explicitly create B-era backend records and verify remote reads before rollback.
4. Roll back application images and release configuration to retained A while retaining the migrated database. Verify A reads earlier data and representative B-era writes, then creates and reads new records successfully.
5. Reactivate B and verify records from every rehearsal phase remain usable through public backend reads and writes.

Keep deployment data checks outside the Android suite. Mobile assertions alone do not establish backend persistence. Preserve activation transaction locks, intent recovery, and replay protection; allocate strictly increasing activation identifiers for candidate activation, rollback, and reactivation. Add no developer/CI coordination or reservations.

## Acceptance criteria

- Every required check passes before a candidate receives a passing rehearsal result; failures preserve diagnostics and prevent promotion eligibility.
- Reports identify A, B, Git SHA, image digests, applied migration history, activation outcomes, Android report, and phase-specific data checks. Results spanning different candidate identities cannot pass as one release.
- First installation establishes a baseline but is reported as bootstrap, not a successful rehearsal; a distinct A-to-B transition is required.
- Repeated runs report migrations already applied and distinguish schema migration execution from compatibility checks against an already migrated database. Reconstructing production schema history is not an additional gate.
- Neither rollback nor verification reverses migrations, restores snapshots, or resets accumulated data.

## Validation

Exercise a distinct A/B pair on staging through the normal delivery entry point. Inspect public read/write evidence, Android results, migration history, and increasing activation identifiers. Cover required-check failure, identity mismatch, bootstrap-only execution, and repeated already-migrated execution with focused existing deployment-wrapper regression coverage.

## Non-goals

Android assertion or retry changes; Android execution on rollback A; new coordination; database downgrade/recovery tooling; production schema reconstruction; zero-downtime or benchmark gates.
