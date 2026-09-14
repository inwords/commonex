# Grafana Git Sync

Grafana at `https://gf.commonex.ru/` manages dashboards and folders through Git
Sync. The connection `repository-1e0ed32` tracks the `main` branch of
`inwords/commonex`, scoped to `infra/grafana/sync`, and presents it as the
`inwords/commonex` folder in organization 1.

## Ownership

- `sync/`: dashboard resources and folder metadata owned by Git Sync. Save from
  Grafana to commit directly to `main`, or edit the resources in Git. Preserve
  dashboard `metadata.name` values (the dashboard UIDs) to keep existing URLs.
- `provisioning/datasources/`: host-managed PostgreSQL provisioning, mounted
  read-only at `/etc/grafana/provisioning/datasources`. Preserve `postgres-ds`.
- `grafana_data`: persistent Grafana database, GitHub App connection, credentials,
  manually configured data sources, and other instance state. Certificate alerts
  are DB-owned; see [certificate monitoring](../certificates/monitoring/README.md).

The connection authenticates with a GitHub App. Keep its private key outside Git.
Git Sync registers a repository webhook using the public Grafana root URL; no
webhook URL override is required. The connection also polls every 60 seconds.
Direct commits use the `write` workflow; `branch` permits branch-based saves.

Changes limited to `infra/grafana/sync/**` are excluded from the application
workflow's push/PR triggers and infra change filter. Mixed application and
dashboard changes still trigger normal application validation and deployment.
Dashboard resources are fetched by Grafana and are not copied onto the host by
the [application deployment](../deploy/README.md).

Git Sync covers dashboards and folders only. Data sources, alert rules, and
library panels stay outside Git Sync.

## Migration and recovery

Before changing ownership, back up the live Grafana database, export the live
dashboards, and preserve the host provisioning files. Live dashboards can contain
edits absent from the old checked-in JSON. Keep backups outside active
provisioning paths, root-owned and inaccessible to other users.

Inspect ownership using the dashboard resource API's
`metadata.annotations["grafana.app/managedBy"]`. In Grafana 13.2.1, the legacy
dashboard API can report `provisioned: false` for a classic file-managed resource.

Retire classic file provisioning before transferring dashboard ownership. Do
not simply remove an active provider or its dashboard files: classic provisioning
can delete the database dashboard. For Grafana 13.2.1:

1. Set `disableDeletion: true` in the existing provider.
2. Reload dashboard provisioning while the source JSON still exists.
3. Archive the JSON outside the provider's scanned directory.
4. Wait for reconciliation and verify the dashboard still exists, its contents
   are unchanged, and its classic management annotations have been cleared.
5. Archive the old provider outside the provisioning directory.

Complete this sequence before activating the Compose change that removes the
legacy mounts. Removing the provider first can trigger orphan cleanup.

Scope migration to explicitly selected dashboard UIDs. Do not use full-instance
migration for this setup; it includes resources beyond the dashboards being
moved. Verify the same UIDs, panel definitions, data-source references, and
working URLs after migration, along with unchanged alert rules and data sources.

Archive the obsolete host dashboard provider outside
`/etc/commonex/app/grafana/provisioning`, and archive its source JSON outside
`/etc/commonex/app/grafana/dashboards`. Old retained application releases can
remount these paths during rollback; they must not reactivate the old provider.
Keep the directories empty if an older Compose release still expects them.

Revert a Git commit to restore a synced dashboard definition. An application
release rollback does not roll dashboards back. Database recovery is a separate
operation: stop Grafana, restore a verified backup with its original ownership,
and reconcile Git Sync configuration and the repository state before resuming
writes. Never restore legacy provisioning alongside Git Sync for the same UID.

## Verification

Confirm the connection is healthy, the latest pull succeeded, and both expected
dashboards appear in the provisioned folder. Verify one save through Grafana
produces a Git commit and one Git change is reflected in Grafana. Check that
dashboard-only commits do not start an application workflow run. Preserve all
existing data sources and certificate alert rules throughout the cutover.

References: [Git Sync setup](https://grafana.com/docs/grafana/latest/as-code/observability-as-code/git-sync/git-sync-setup/),
[migration guidance](https://grafana.com/docs/grafana/latest/as-code/observability-as-code/git-sync/export-resources/).
