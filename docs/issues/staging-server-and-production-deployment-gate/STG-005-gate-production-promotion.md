---
id: STG-005
title: Require passing staging evidence and manual approval for exact-image promotion
labels: [ready-for-agent]
blocked_by: [STG-004]
phase: 2
---

## Outcome

Production promotion requires the full passing staging gate and explicit manual approval for the exact candidate being promoted. Production uses the same immutable custom-image digests exercised on staging.

## Context

[Parent spec](../staging-server-and-production-deployment-gate.md). Releases come from `main`; staging deployment and validation happen automatically. STG-004 establishes the upgrade/application-rollback/redeployment rehearsal, including public service checks, the unchanged Android suite, and persistent-data checks. Manual production approval remains the operator's decision after that evidence passes.

The existing delivery vocabulary and safety mechanisms continue to apply: Release, Activation, Active Release, Retained Release, validated bundles, replay protection, intent recovery, and activation transaction locks. No additional local-test/CI coordination is requested.

## Scope

Connect the completed staging result to the production promotion entry point. Bind staging evidence and approval to a concrete release identity: Git SHA, immutable custom-image digests, and the associated release bundle. Display enough evidence for the approver to identify the candidate, baseline, rehearsal outcome, Android report, migration execution or already-applied limitation, and data-check results.

Reject promotion without a complete passing result for that exact candidate. A bootstrap-only result or partial rehearsal cannot satisfy the gate. Preserve failing diagnostics and make failed required checks visible.

Resolve images once and promote those tested digests without rebuilding or substituting staging-specific images. Keep environment-specific runtime configuration distinct from image identity and preserve release-bundle validation guarantees. Carry the approved identity through production activation so approval of one candidate cannot authorize another candidate or a changed digest. Candidate changes require their own passing result and approval.

Keep the existing production post-activation public-service verification and manual rollback path. Staging success does not replace verification of the actual production activation. Ordinary rollback preserves the migrated database.

## Acceptance criteria

- Applicable `main` releases automatically reach staging deployment and the full rehearsal; production still requires an explicit manual approval after passing evidence exists.
- Missing, failed, incomplete, bootstrap-only, or identity-mismatched staging results prevent production promotion.
- Approval and production activation reference the exact same candidate and immutable custom-image digests that passed staging. A later build, mutable-tag change, or different candidate cannot inherit approval.
- Production promotion performs no custom-image rebuild and records the activated release identity and verification outcome.
- Existing post-activation verification, manual rollback, and activation safety checks remain functional. Production failures retain actionable diagnostics.

## Validation

Use focused existing workflow/deployment-wrapper coverage to prove rejection of absent or failing evidence, candidate/digest substitution, and approval mismatch; prove acceptance of a matching passing candidate after approval. Verify promotion consumes the tested digests without rebuild. Exercise the authorized promotion path and retain production post-activation evidence; verify manual rollback behavior without changing database policy.

## Non-goals

Automatic production approval; branch/PR previews; new Android tests or retry rules; database downgrade or snapshot restoration; developer/CI coordination; production schema reconstruction or zero-downtime gates.
