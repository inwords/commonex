# CommonEx production deployment

`commonex_deploy.py` is the repository source for the extensionless `commonex-deploy` host command. It accepts a bounded gzip archive on standard input, validates its exact file list, hashes, and immutable image references, renders Docker Compose in a sanitized environment, installs files atomically, records a configuration rollback copy, and reconciles Compose. Mutating operations are serialized with a host lock. The workflow supplies its monotonically increasing GitHub run number; the host rejects a deploy or rollback whose run number is older than or equal to the last successful activation.

## Host bootstrap

Complete this bootstrap and preflight before enabling the workflow. Preserve the currently working Compose file, `.env`, and host-managed Grafana content; do not generate replacements during wrapper installation.

The merged wrapper **must be installed and validated before approving the first immutable deploy**. The ordinary deploy job asks the host for `current-images`: before the first immutable activation the host responds with the expected bootstrap message, and later it supplies the active immutable references. Keep a recoverable copy of the previous wrapper while installing the merged version:

```bash
install -d -o root -g root -m 0755 /etc/commonex/app
install -d -o root -g root -m 0700 /var/lib/commonex
install -d -o root -g root -m 0700 /var/lib/commonex/rollback
install -d -o root -g root -m 0755 /var/log/commonex
install -d -o root -g root -m 0755 /run/commonex

test -f /etc/commonex/app/docker-compose-prod.yml
test -f /etc/commonex/app/.env
test -d /etc/commonex/app/grafana/provisioning
test -d /etc/commonex/app/grafana/dashboards
chown root:root /etc/commonex/app/docker-compose-prod.yml /etc/commonex/app/.env
chmod 0644 /etc/commonex/app/docker-compose-prod.yml
chmod 0600 /etc/commonex/app/.env

python3 infra/deploy/install_commonex_deploy.py install \
  --bundle <prepared-deployment-tool-bundle> \
  --tool-git-sha <repository-git-sha>
python3 infra/deploy/install_commonex_deploy.py install \
  --bundle <prepared-deployment-tool-bundle> \
  --tool-git-sha <repository-git-sha> \
  --apply

install -o root -g root -m 0644 \
  infra/deploy/commonex-deploy.logrotate \
  /etc/logrotate.d/commonex-deploy
logrotate --debug /etc/logrotate.d/commonex-deploy

cd /etc/commonex/app
docker compose --env-file .env -f docker-compose-prod.yml config --quiet
docker compose up --help | grep -F -- '--remove-orphans'
```

Install the restricted SSH key and sudo policy below, then test that the forced command rejects an invalid command. Also test `ssh commonex-production "current-images"`: on a host with no activation history it must fail with `commonex-deploy: no immutable activation history exists; bootstrap required`; after the first activation it must print four image-reference lines. Before printing, it verifies that the active Compose and environment files exactly match the release recorded at the front of activation history. This output is not secret-bearing, but it remains limited to the forced command scope.

The installer stages the tool under `/opt/commonex/deploy/versions/<repository-git-sha>`, verifies the complete immutable version, retains the previous stable entrypoint and `current` target, then switches them atomically. If the tool itself must be rolled back before the first immutable deploy, stop pending workflow approvals and use the rollback directory printed by the installer. Test the restored forced command and Compose render before enabling deployments again. Do not use a tool rollback as a substitute for a release rollback after immutable activations have begun.

The script intentionally uses `/usr/bin/python3`; verify that it is Python 3.9 or newer with `/usr/bin/python3 --version`.

## Immutable release contract

Every release archive contains only `docker-compose-prod.yml` and `.env`. After staging, the root-only host directory also contains the wrapper-generated `manifest.sha256`. The release `.env` must contain these four image variables, each as the repository name followed by a digest in the exact `repository@sha256:<64-lowercase-hex>` form:

- `COMMONEX_BACKEND_IMAGE=ruggedbl/commonex-nest-backend@sha256:<64-lowercase-hex>`
- `COMMONEX_FRONTEND_IMAGE=ruggedbl/commonex-next-web@sha256:<64-lowercase-hex>`
- `COMMONEX_OTEL_COLLECTOR_IMAGE=ruggedbl/opentelemetry-collector-custom@sha256:<64-lowercase-hex>`
- `COMMONEX_NGINX_IMAGE=ruggedbl/nginx-http3@sha256:<64-lowercase-hex>`

Every push to `main` builds and SHA-tags all four custom images, with each service serialized across workflow runs. A surviving run is therefore self-contained even if an earlier run fails or is superseded while waiting. Pull-request image builds remain change-scoped and are not pushed. The deploy job resolves all four current Git SHA tags to digests; first-deployment bootstrap fails closed unless all four services were built, and it never consults `latest`. The Git SHA tags are retained as registry anchors for their image blobs. This process does not delete remote image tags, including tags older than the three retained host releases.

## Host paths and restricted command

- Source: `infra/deploy/commonex_deploy.py` and `infra/deploy/commonex_host/`
- Installed executable: `/usr/local/sbin/commonex-deploy`, owned by `root:root`, mode `0755`
- Versioned tool: `/opt/commonex/deploy/versions/<repository-git-sha>/`, selected by `/opt/commonex/deploy/current`
- Releases: `/var/lib/commonex`, owned by `root:root`, mode `0700`
- Activation state: `/var/lib/commonex/activation-state.json`, owned by `root:root`, mode `0600`; it records the replay-protection `last_successful_run` and a most-recently-used history of up to three distinct release SHAs.
- Activation intent: `/var/lib/commonex/activation-intent.json`, owned by `root:root`, mode `0600`; it identifies the previous and candidate releases plus the configuration backup while an activation is in progress. Its presence blocks further wrapper commands until manual reconciliation.
- Legacy last successful workflow run: `/var/lib/commonex/last-successful-run`, owned by `root:root`, mode `0600`; it is read only when no activation-state file exists.
- Deployment log: `/var/log/commonex/deploy.log`, owned by `root:root`, mode `0600`, rotated by `/etc/logrotate.d/commonex-deploy`
- Per-activation configuration rollback: `/var/lib/commonex/rollback/deploy-<sha>-<timestamp>`
- Operation lock: `/run/commonex/deploy.lock`; the boot-scoped parent is owned by `root:root`, mode `0755`
- Active configuration: `/etc/commonex/app/docker-compose-prod.yml` (`0644`) and `/etc/commonex/app/.env` (`0600`), both owned by `root:root`

The activation history and eligible rollback targets are capped at three distinct SHAs. Cleanup attempts to remove staged directories not represented by that history, but extra directories can remain if cleanup fails; inspect `/var/log/commonex/deploy.log` for `RESULT cleanup status=FAILED`. A staged release that is not in the history is not a rollback target; operators may only roll back to one of the listed retained SHAs.

The `commonex-deploy` user is not a member of the Docker group. Its authorized key must use:

```text
restrict,command="sudo -n /usr/local/sbin/commonex-deploy forced" <dedicated-deploy-public-key>
```

The only sudo policy is:

```text
Defaults:commonex-deploy env_reset
Defaults:commonex-deploy secure_path="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
Defaults:commonex-deploy env_keep = "SSH_ORIGINAL_COMMAND"
commonex-deploy ALL=(root) NOPASSWD: /usr/local/sbin/commonex-deploy forced
```

Install that policy in `/etc/sudoers.d/commonex-deploy` with mode `0440`, then run `visudo -cf /etc/sudoers.d/commonex-deploy`. The forced command accepts only:

- `stage <40-character-sha>`
- `validate <40-character-sha>`
- `deploy <40-character-sha> <positive-github-run-number>`
- `rollback <40-character-sha> <positive-github-run-number>`
- `current-images` (no arguments; read-only image-reference output)

Grafana dashboards and datasource provisioning remain host-managed and are outside this release contract.

## Validation

The production host currently runs Python 3.9, so the wrapper must remain compatible with that version. Run its dependency-free regression suite with:

```bash
python3 -m unittest discover -s infra/deploy -p 'test_*.py'
```

Before enabling deployments, confirm the restricted SSH key reaches only the forced command, invalid and extra-argument commands are rejected, the deployment log and state files remain root-only, `docker compose --env-file .env -f docker-compose-prod.yml config --quiet` succeeds from `/etc/commonex/app`, and `docker compose up --help` lists `--remove-orphans`. The unit tests verify the exact Compose command used for successful activation and failure restoration; a live Docker daemon integration test is not part of this repository suite. Post-activation verification checks the public HTTP routes and sends an empty gRPC frame through `grpc.commonex.ru`; it requires the expected `UNIMPLEMENTED` response from the backend's standard health-method path, so the workflow runner must provide HTTP/2-capable `curl`.

## Manual release rollback

Rollback is manual-only. It is never initiated automatically by the wrapper or workflow. To activate a retained release:

1. Inspect `/var/lib/commonex/activation-state.json` as root and choose one of its listed retained 40-character SHAs. Do not select a directory or SHA outside that history.
2. In GitHub Actions, run the existing workflow from the `main` ref with the `release_sha` input set to that SHA. The workflow validates the lowercase 40-character form, checks out `main`, and sends only `rollback <release_sha> <GITHUB_RUN_NUMBER>` through the restricted SSH command.
3. Obtain the `production` environment approval. The rollback shares the `commonex-production` concurrency group with deploys, so it waits for any in-progress production activation rather than running concurrently.
4. Inspect the workflow result and the matching `/var/log/commonex/deploy.log` audit records. A successful rollback writes `RESULT rollback target=<sha> ... status=PASS` and moves that SHA to the front of the MRU history.
5. On the host, run `docker compose --env-file .env -f docker-compose-prod.yml ps` in `/etc/commonex/app`, then verify service health before resuming deployments.

During activation the wrapper pulls the staged digest-pinned images, saves the current allowlisted configuration, durably records its activation intent, installs the selected release, reconciles Compose with `up -d --pull always --remove-orphans --wait --wait-timeout 120`, records the new run number and history, and finally clears the intent. A normal failure before commit restores and reconciles the previous configuration, then clears the intent without advancing the activation history or successful-run state. Because Compose may already have changed containers, still perform the checks below.

### Failure handling and audit outcomes

- **Exit 0 — success.** Confirm the `status=PASS` audit result, `docker compose ... ps`, and the relevant service health checks.
- **Exit 1 — ordinary failure.** For a failed activation audit record containing `configuration_restored`, stop further deployments. For `PASS`, validate and reconcile the restored definition with `docker compose --env-file .env -f docker-compose-prod.yml config --quiet` and `docker compose --env-file .env -f docker-compose-prod.yml up -d --remove-orphans`, then check `ps`, service health, and that no activation-intent file remains. For `FAILED`, restore the allowlisted files from the rollback directory recorded in the audit event before validating and reconciling. The run/history state is not advanced by this path. Rejected stale-run, non-retained-target, and input-validation failures do not change configuration and may not contain `configuration_restored`; correct the command or input before trying again.
- **Exit 3 — ambiguous activation commit.** Treat the active release and activation-state result as unknown: a state commit/restoration or activation-intent cleanup could not be durably confirmed. Do not retry or start another wrapper command. Stop approvals; preserve the audit log, state, intent, active files, and backup named in the intent; inspect Compose and service health; decide whether the prior or candidate release is authoritative; make the active files, running containers, and activation state agree; then remove `activation-intent.json` as root and fsync or otherwise durably persist the release directory before resuming. Never remove an intent merely to unblock the workflow.
- **Exit 2 — post-commit audit failure.** The activation already committed: configuration, runtime reconciliation, and activation state have completed, but writing the final success audit record failed. The workflow still runs its active-configuration and public-health checks before preserving the failed result. Do not assume a rollback occurred and do not retry blindly. Inspect the active `.env`, `activation-state.json`, Compose `ps`, and service health; repair the audit-log condition and record the operator decision before resuming deployments.

The wrapper attempts to log activations, including rejected non-increasing run numbers and rollback targets that are not retained. If the audit write itself fails, exit 2 or the explicit audit-failure diagnostic is the only guarantee; preserve the available host and workflow evidence. The audit log must not be made writable by the deployment account.
