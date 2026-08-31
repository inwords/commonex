---
status: accepted
---

# Use a namespaced production host layout

CommonEx production delivery will separate files by lifecycle while keeping every path visibly CommonEx-owned: `/usr/local/sbin/commonex-deploy` is the stable administrative entry point, versioned executable modules live under `/opt/commonex/deploy`, configuration lives under `/etc/commonex`, persistent release and activation state under `/var/lib/commonex`, audit logs under `/var/log/commonex`, and the boot-scoped lock under `/run/commonex`. We rejected placing executable code and mutable state together under `/etc/commonex` because configuration restore, state retention, audit rotation, runtime cleanup, permissions, and rollback have different operational lifecycles; production will move to the canonical layout through an approval-gated, verified, recoverable one-time migration rather than permanent dual-path behavior.

The repository implementation and production adoption completed on 2026-08-31 at deployment-tool revision `874375e69882589a1bcd67f64167ca9d388d1ede` after the complete read-only inventory, reviewed dry-run migration plan, and post-migration verification in `infra/deploy/HOST_LAYOUT_MIGRATION.md` passed. The legacy state and audit inputs remain retained for rollback.
