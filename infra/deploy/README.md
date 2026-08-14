# CommonEx production deployment

`commonex_deploy.py` is the repository source for the extensionless `commonex-deploy` host command. It accepts a bounded gzip archive on standard input, validates its exact file list, hashes, and immutable image references, renders Docker Compose in a sanitized environment, installs files atomically, records a configuration rollback copy, and reconciles Compose. Mutating operations are serialized with a host lock. The workflow supplies its monotonically increasing GitHub run number; the host rejects a deploy or rollback whose run number is older than or equal to the last successful activation.

## Host bootstrap

Complete this bootstrap and preflight before enabling the workflow. Preserve the currently working Compose file, `.env`, and host-managed Grafana content; do not generate replacements during wrapper installation.

The merged wrapper **must be installed and validated before approving the first immutable deploy**. The workflow always asks the host for `current-images`: before the first immutable activation the host responds with the expected bootstrap message, and later it supplies the active immutable references used for partial-build inheritance. Keep a recoverable copy of the previous wrapper while installing the merged version:

```bash
install -d -o root -g root -m 0755 /etc/commonex/app
install -d -o root -g root -m 0755 /etc/commonex/rollback
install -d -o root -g root -m 0700 /var/lib/commonex-releases

test -f /etc/commonex/app/docker-compose-prod.yml
test -f /etc/commonex/app/.env
test -d /etc/commonex/app/grafana/provisioning
test -d /etc/commonex/app/grafana/dashboards
chown root:root /etc/commonex/app/docker-compose-prod.yml /etc/commonex/app/.env
chmod 0644 /etc/commonex/app/docker-compose-prod.yml
chmod 0600 /etc/commonex/app/.env

test ! -e /usr/local/sbin/commonex-deploy || \
  cp -a /usr/local/sbin/commonex-deploy /usr/local/sbin/commonex-deploy.previous
install -o root -g root -m 0755 infra/deploy/commonex_deploy.py /usr/local/sbin/commonex-deploy

cd /etc/commonex/app
docker compose --env-file .env -f docker-compose-prod.yml config --quiet
```

Install the restricted SSH key and sudo policy below, then test that the forced command rejects an invalid command. Also test `ssh commonex-production "current-images"`: on a host with no activation history it must fail with `commonex-deploy: no immutable activation history exists; bootstrap required`; after the first activation it must print four image-reference lines. This output is not secret-bearing, but it remains limited to the forced command scope.

If the merged wrapper itself must be rolled back before the first immutable deploy, stop pending workflow approvals, restore `/usr/local/sbin/commonex-deploy.previous` to `/usr/local/sbin/commonex-deploy` with `install -o root -g root -m 0755`, test the restored wrapper and Compose render, then investigate before enabling deployments again. Do not use this wrapper rollback as a substitute for a release rollback after immutable activations have begun.

The script intentionally uses `/usr/bin/python3`; verify that it is Python 3.9 or newer with `/usr/bin/python3 --version`.

## Immutable release contract

Every staged release contains only `docker-compose-prod.yml` and `.env`. Its `.env` must contain these four image variables, each as the repository name followed by a digest in the exact `repository@sha256:<64-lowercase-hex>` form:

- `COMMONEX_BACKEND_IMAGE=ruggedbl/commonex-nest-backend@sha256:<64-lowercase-hex>`
- `COMMONEX_FRONTEND_IMAGE=ruggedbl/commonex-next-web@sha256:<64-lowercase-hex>`
- `COMMONEX_OTEL_COLLECTOR_IMAGE=ruggedbl/opentelemetry-collector-custom@sha256:<64-lowercase-hex>`
- `COMMONEX_NGINX_IMAGE=ruggedbl/nginx-http3@sha256:<64-lowercase-hex>`

For an ordinary deploy, the workflow resolves the SHA tag for each changed service to a digest. It inherits an unchanged service's digest reference from `current-images`, so a partial build does not change unrelated service images. Only the explicit first-deployment bootstrap consults `latest` for services that were not built in that run; once an immutable activation exists, later deploys do not use `latest` for inheritance. The Git SHA tags are retained as registry anchors for their image blobs. This process does not delete remote image tags, including tags older than the three retained host releases.

## Host paths and restricted command

- Source: `infra/deploy/commonex_deploy.py`
- Installed executable: `/usr/local/sbin/commonex-deploy`, owned by `root:root`, mode `0755`
- Releases: `/var/lib/commonex-releases`, owned by `root:root`, mode `0700`
- Activation state: `/var/lib/commonex-releases/activation-state.json`, owned by `root:root`, mode `0600`; it records the replay-protection `last_successful_run` and a most-recently-used history of up to three distinct release SHAs.
- Legacy last successful workflow run: `/var/lib/commonex-releases/last-successful-run`, owned by `root:root`, mode `0600`; it is read only when no activation-state file exists.
- Deployment log: `/var/log/commonex-deploy.log`, owned by `root:root`, mode `0600`
- Per-activation configuration rollback: `/etc/commonex/rollback/deploy-<sha>-<timestamp>`
- Active configuration: `/etc/commonex/app/docker-compose-prod.yml` (`0644`) and `/etc/commonex/app/.env` (`0600`), both owned by `root:root`

After an activation, the MRU history and cleanup retain up to three distinct staged release directories represented by that history (three once enough releases have been activated). A staged release that is not in that history is not a rollback target; operators may only roll back to one of the listed retained SHAs.

The `commonex-deploy` user is not a member of the Docker group. Its authorized key must use:

```text
restrict,command="sudo -n /usr/local/sbin/commonex-deploy forced" <dedicated-deploy-public-key>
```

The only sudo policy is:

```text
Defaults:commonex-deploy env_reset
Defaults:commonex-deploy secure_path="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
Defaults:commonex-deploy env_keep += "SSH_ORIGINAL_COMMAND"
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

Before enabling deployments, confirm the restricted SSH key reaches only the forced command, invalid and extra-argument commands are rejected, the deployment log and activation state remain root-only, and `docker compose --env-file .env -f docker-compose-prod.yml config --quiet` succeeds from `/etc/commonex/app`.

## Manual release rollback

Rollback is manual-only. It is never initiated automatically by the wrapper or workflow. To activate a retained release:

1. Inspect `/var/lib/commonex-releases/activation-state.json` as root and choose one of its listed retained 40-character SHAs. Do not select a directory or SHA outside that history.
2. In GitHub Actions, run the existing workflow from the `main` ref with the `release_sha` input set to that SHA. The workflow validates the lowercase 40-character form, checks out `main`, and sends only `rollback <release_sha> <GITHUB_RUN_NUMBER>` through the restricted SSH command.
3. Obtain the `production` environment approval. The rollback shares the `commonex-production` concurrency group with deploys, so it waits for any in-progress production activation rather than running concurrently.
4. Inspect the workflow result and the matching `/var/log/commonex-deploy.log` audit records. A successful rollback writes `RESULT rollback target=<sha> ... status=PASS` and moves that SHA to the front of the MRU history.
5. On the host, run `docker compose --env-file .env -f docker-compose-prod.yml ps` in `/etc/commonex/app`, then verify service health before resuming deployments.

During activation the wrapper pulls the staged digest-pinned images, saves the current allowlisted configuration, installs the selected release, reconciles Compose with `up -d --pull always --wait --wait-timeout 120`, then records the new run number and history. A normal failure before commit restores the previous configuration and does not advance the activation history or successful-run state. Because Compose may already have changed containers, still perform the checks below.

### Failure handling and audit outcomes

- **Exit 0 — success.** Confirm the `status=PASS` audit result, `docker compose ... ps`, and the relevant service health checks.
- **Exit 1 — ordinary failure.** Stop further deployments. Inspect the matching audit event for `configuration_restored=PASS`, `NOT_NEEDED`, or `FAILED`. For `PASS`, validate and reconcile the restored definition with `docker compose --env-file .env -f docker-compose-prod.yml config --quiet` and `docker compose --env-file .env -f docker-compose-prod.yml up -d`, then check `ps` and service health. For `FAILED`, restore the allowlisted files from the rollback directory recorded in the audit event before validating and reconciling. The run/history state is not advanced by this path.
- **Exit 3 — ambiguous activation commit.** Treat the active release and activation-state result as unknown: the wrapper could not durably confirm restoration of the prior activation state after a state-write problem. Do not retry or start another deploy/rollback. Stop approvals, preserve the audit log and state file, inspect the active configuration and running containers, and resolve the state manually before any further activation.
- **Exit 2 — post-commit audit failure.** The activation already committed: configuration, runtime reconciliation, and activation state have completed, but writing the final success audit record failed. Do not assume a rollback occurred and do not retry blindly. Inspect the active `.env`, `activation-state.json`, Compose `ps`, and service health; repair the audit-log condition and record the operator decision before resuming deployments.

All activation attempts are logged, including rejected non-increasing run numbers and rollback targets that are not retained. The audit log is the operational record; it must not be made writable by the deployment account.
