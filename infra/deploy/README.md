# CommonEx production deployment

`commonex_deploy.py` is the repository source for the extensionless `commonex-deploy` host command. It accepts a bounded gzip archive on standard input, validates its exact file list and hashes, renders Docker Compose in a sanitized environment, installs files atomically, records a rollback copy, and reconciles Compose. Mutating operations are serialized with a host lock. The workflow also supplies its monotonically increasing GitHub run number; the host rejects a deployment older than or equal to the last successful run.

## Host bootstrap

Complete this bootstrap and preflight before enabling the workflow. Preserve the currently working Compose file, `.env`, and host-managed Grafana content; do not generate replacements during wrapper installation.

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

install -o root -g root -m 0755 infra/deploy/commonex_deploy.py /usr/local/sbin/commonex-deploy

cd /etc/commonex/app
docker compose --env-file .env -f docker-compose-prod.yml config --quiet
```

The script intentionally uses `/usr/bin/python3`; verify that it is Python 3.9 or newer with `/usr/bin/python3 --version`.

## Host paths

- Source: `infra/deploy/commonex_deploy.py`
- Installed executable: `/usr/local/sbin/commonex-deploy`, owned by `root:root`, mode `0755`
- Releases: `/var/lib/commonex-releases`, owned by `root:root`, mode `0700`
- Last successful workflow run: `/var/lib/commonex-releases/last-successful-run`, created atomically as `root:root`, mode `0600`
- Deployment log: `/var/log/commonex-deploy.log`, owned by `root:root`, mode `0600`
- Per-release rollback: `/etc/commonex/rollback/deploy-<sha>-<timestamp>`
- Active configuration: `/etc/commonex/app/docker-compose-prod.yml` (`0644`) and `/etc/commonex/app/.env` (`0600`), both owned by `root:root`

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

Install that policy in `/etc/sudoers.d/commonex-deploy` with mode `0440`, then run `visudo -cf /etc/sudoers.d/commonex-deploy`. The forced command accepts only `stage <40-character-sha>`, `validate <40-character-sha>`, and `deploy <40-character-sha> <positive-github-run-number>`.

A release archive contains only `docker-compose-prod.yml` and `.env`. Grafana dashboards and datasource provisioning remain host-managed and are outside this release contract.

## Validation

The production host currently runs Python 3.9, so the wrapper must remain compatible with that version. Run its dependency-free regression suite with:

```bash
python3 -m unittest discover -s infra/deploy -p 'test_*.py'
```

Before enabling deployments, confirm the restricted SSH key reaches only the forced command, a deliberately invalid command is rejected, the deployment log remains root-only, and `docker compose ... config --quiet` succeeds from `/etc/commonex/app`.

## Rollback

If file installation or Compose reconciliation fails, the wrapper first restores the previous configuration files automatically. Compose may already have changed some running containers, so an operator must still:

1. Stop further deployments.
2. Confirm the `configuration_restored` result in `/var/log/commonex-deploy.log`. If restoration failed, restore the allowlisted files from the rollback directory named in the same event.
3. Validate with `docker compose --env-file .env -f docker-compose-prod.yml config --quiet`.
4. Reconcile the restored definition with `docker compose --env-file .env -f docker-compose-prod.yml up -d`.
5. Verify database, both backend replicas, web, Nginx, OTel, VictoriaMetrics, VictoriaTraces, and Grafana.

The workflow still uses mutable application image tags; the rollback directory protects configuration but is not an immutable image rollback mechanism.
