# CommonEx host-layout migration runbook

This runbook moves the production host to the namespaced layout without adding
fallback reads from legacy locations. The application continues serving during
the file copy, but production approvals must remain paused until every
post-migration check passes.

## Safety boundary

- Run the inventory and migration only from a trusted root session on the host.
- Keep the inventory report root-readable because it contains host metadata.
- Stop immediately if the inventory is incomplete, an Activation Intent exists,
  the Active Release is not verified, or old and new state both exist.
- A missing canonical logrotate policy is expected on an unmigrated host and is
  recorded without blocking inventory. An existing invalid policy does block;
  install the repository policy during the post-migration gate before approvals.
- Do not remove old paths. The migration leaves them as rollback inputs.
- `migrate_commonex_host.py` and `install_commonex_deploy.py` are dry-run by
  default. Mutation requires the explicit `--apply` option.
- Roll back the layout only before an application activation changes canonical
  state. Append-only audit growth from validation is accepted and preserved.

## Inputs

Prepare these root-owned inputs:

1. A complete JSON report from `inventory_commonex_host.py`.
2. A deployment-tool bundle containing `commonex_deploy.py` and any supporting
   `commonex_host` package files.
3. The lowercase 40-character repository Git SHA that produced that bundle.
4. The reviewed legacy state, audit, and lock paths from the inventory.

The normal legacy split layout uses:

```text
/var/lib/commonex-releases
/var/log/commonex-deploy.log
/run/lock/commonex-deploy.lock
```

For the observed consolidated legacy layout, pass the inventoried paths
explicitly. A state root of `/etc/commonex` activates selective migration: only
activation documents, retained SHA releases, and `rollback/` are copied to
`/var/lib/commonex`; active configuration and other `/etc/commonex` content stay
in place.

## Plan and apply

First produce and review a dry-run plan:

```bash
sudo python3 infra/deploy/migrate_commonex_host.py \
  --source-state-root /etc/commonex \
  --source-audit /etc/commonex/deploy.log \
  --source-lock /etc/commonex/deploy.lock \
  migrate \
  --inventory /root/commonex-host-inventory.json \
  --tool-bundle /root/commonex-deploy-bundle \
  --tool-git-sha <repository-git-sha>
```

Use the exact paths reported by production; the `/etc/commonex` examples above
are not assumptions. The plan re-hashes live inputs and fails if they changed
since inventory.

After production approvals are paused and the plan is approved, repeat the same
command with `--apply` after the `migrate` arguments. The executor:

1. acquires both the legacy lock and `/run/commonex/deploy.lock`, preventing old
   and new tool versions from overlapping during the authority switch;
2. revalidates the complete inventory snapshot;
3. copies and fsyncs state and audit data into staging paths;
4. verifies copied content and metadata;
5. stages and verifies `/opt/commonex/deploy/versions/<tool-git-sha>`;
6. promotes canonical data while the old command is still authoritative;
7. atomically switches `current` and the stable launcher;
8. writes `/var/lib/commonex/host-layout-migration.json`.

If a failure occurs after data promotion, canonical copies are moved to a
root-only failure quarantine and the previous tool selector is restored. Legacy
inputs are never deleted.

## Post-migration gate

Before re-enabling approvals, verify from the host that:

- `current` resolves to the intended repository Git SHA;
- the stable forced command still rejects unapproved input with the documented
  exit meaning;
- `current-images` matches the Active Release;
- production Compose renders successfully;
- runtime health checks pass;
- an audit append keeps `/var/log/commonex/deploy.log` root-owned at mode `0600`;
- the root-controlled rotation policy recognizes that canonical audit path.

Do not perform an application activation merely to test the host-layout change.

## Rollback rehearsal

The migration receipt names the installer's rollback directory. Plan a rollback
without changing the host:

```bash
sudo python3 infra/deploy/migrate_commonex_host.py \
  --source-state-root /etc/commonex \
  --source-audit /etc/commonex/deploy.log \
  --source-lock /etc/commonex/deploy.lock \
  rollback \
  --receipt /var/lib/commonex/host-layout-migration.json
```

Add `--apply` only during an approved rollback or rehearsal. Rollback restores
the previous tool authority first, then moves canonical copies into
`/var/lib/commonex-migration-rollbacks/<migration-id>-<timestamp>/`. It does not
delete the newly installed tool version or either legacy input.

The standalone installer has the same dry-run/apply model when a tool-only
upgrade or rollback is needed:

```bash
sudo python3 infra/deploy/install_commonex_deploy.py install \
  --bundle /root/commonex-deploy-bundle \
  --tool-git-sha <repository-git-sha>
```

For a tool-only rollback, review the dry-run and then add `--apply`:

```bash
sudo python3 infra/deploy/install_commonex_deploy.py rollback \
  --rollback-directory /opt/commonex/deploy/rollbacks/<installer-rollback-id>
```
