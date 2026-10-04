# Persistent staging deployment

Staging runs the complete `infra/docker-compose-prod.yml` topology on a dedicated
host. It has its own database, credentials, observability volumes,
release history, and activation identifiers. Production remains a separate host.
Use the existing [Release/Activation lifecycle](../CONTEXT.md) and
[deployment safety/recovery procedures](README.md).

## Access and network

Keep administrative SSH endpoints and identities in the private operator inventory.
Use the approved administrative access method for host setup; keep it out of CI.

The application deployment account is `commonex-deploy`, with the same restricted
authorized-key command and sudo policy documented in [the main runbook](README.md#host-paths-and-restricted-command).
Use a separate staging deployment key, never the production key. Store it in the
GitHub `staging` environment with staging-specific server and application secrets.
Manual activation identifiers must strictly increase. Automated validation uses
exactly the observed `last_successful_run + 1`, preserving replay protection and
rejecting an intervening activation without introducing a test lock.

Pin the SSH host key using an already trusted administrative connection or the
cloud console. Store the verified key in private operator/CI configuration.

Reverify after a host replacement. Never disable host-key checking. Configure a
dedicated operator/CI SSH directory using:

```sh
SERVER_IP="$STAGING_SERVER_ADDRESS" DEPLOY_SSH_KEY="$(cat <staging-deployment-private-key>)" \
  python3 infra/deploy/configure_ssh.py application \
  --environment staging --known-hosts <verified-staging-known-hosts>
```

The helper writes the calling account's `~/.ssh` identity/config; use a dedicated
account or isolated home, preserving existing operator SSH configuration.

DNS A records for all three names point to the staging host's public IPv4 address.
HTTPS records are
`1 . alpn="h3,h2"` for web/Grafana and `1 . alpn="h2"` for gRPC, with TTL 300.
The host firewall permits TCP 80/443 and UDP 443, plus administrative SSH. Also
verify cloud ingress. AAAA records require a reachable assigned public IPv6
address; none was configured on this host during bootstrap. Do not publish a
production address or an IPv4-mapped address as staging IPv6.

## Host bootstrap and environment

Use the canonical paths on this dedicated host:

- `/opt/commonex/deploy`: versioned deployment tooling.
- `/etc/commonex/app`: active Compose, root-only `.env`, and host-managed datasource provisioning.
- `/etc/commonex/ssl/certbot/{live,archive}/commonex.ru`: staging copy of the wildcard certificate.
- `/etc/commonex/www`: nginx's mounted files and Certbot webroot.
- `/var/lib/commonex`: immutable releases, activation state/intent, and configuration backups.
- `/var/log/commonex/deploy.log`: root-only deployment audit.
- `/run/commonex/deploy.lock`: existing activation/certificate lock.

As root, create the directories and install the existing wrapper using
[the versioned installer](README.md#host-bootstrap). Prepare its bundle from the
reviewed checkout: copy `commonex_deploy.py` and the complete `commonex_host/`
package into a root-owned `0755` bundle directory, files `0644`. Keep the installer
outside that bundle. Install its logrotate policy. On a fresh host, create active
Compose and environment files before the first activation; there is no previous
deployment to migrate or production state to import.

```sh
install -d -m 0755 /etc/commonex/app/grafana/provisioning/datasources \
  /etc/commonex/www/certbot /var/log/commonex /run/commonex
install -d -m 0700 /var/lib/commonex/rollback
chmod 0700 /var/lib/commonex
install -m 0644 infra/docker-compose-prod.yml /etc/commonex/app/docker-compose-prod.yml
python3 infra/deploy/install_commonex_deploy.py install \
  --bundle <root-owned-tool-bundle> --tool-git-sha <tool-source-sha>
python3 infra/deploy/install_commonex_deploy.py install \
  --bundle <root-owned-tool-bundle> --tool-git-sha <tool-source-sha> --apply
install -m 0644 infra/deploy/commonex-deploy.logrotate /etc/logrotate.d/commonex-deploy
```

Create regular `0644` files `/etc/commonex/www/assetlinks.json` containing `[]`
and `apple-app-site-association` containing
`{"applinks":{"apps":[],"details":[]}}`. These satisfy existing mounts; real
mobile app-link association is outside this ticket.

Prepare private staging environment inputs with the following values. Generate
independent database, devtools, and Grafana secrets. Do not commit them or reuse
production database/Grafana credentials. The exchange-rate API ID may be supplied
by the operator from the existing account.

| Input | Staging value |
| --- | --- |
| `POSTGRES_HOST`, `POSTGRES_PORT` | `db`, `5432` |
| `POSTGRES_USER_NAME`, `POSTGRES_DATABASE` | Host-private staging database identifiers |
| `POSTGRES_SCHEMA` | `public` |
| `POSTGRES_PASSWORD`, `DEVTOOLS_SECRET` | Independent generated secrets |
| `OPEN_EXCHANGE_RATES_API_ID` | Operator-supplied usable API credential |
| `GF_SECURITY_ADMIN_USER`, `GF_SECURITY_ADMIN_PASSWORD` | Host-private administrator name and independent generated password |
| `COMMONEX_WEB_HOSTS`, `COMMONEX_API_HOST` | Both `staging.commonex.ru` |
| `COMMONEX_GRPC_HOST` | `staging-grpc.commonex.ru` |
| `COMMONEX_GRAFANA_HOST` | `staging-gf.commonex.ru` |
| `GF_SERVER_ROOT_URL` | `https://staging-gf.commonex.ru/` |

The four `COMMONEX_*_IMAGE` keys in the active `.env` must be immutable references
from the [image catalog](release-images.json), built from `main`. Resolve all four
SHA tags for the first deployment, never `latest`; preserve the same digests for
later promotion. Keep operator `environment.inputs` root-only and **without** image
keys: the delivery tool appends them to build the validated two-file archive.
No additional release file, staging-specific image build, or bundle-validation
exception is needed. The shared Nginx image owns hostname templates and the
pinned official Docker Nginx entrypoint and envsubst renderer (see
[upstream provenance](../nginx/upstream/README.md)). CommonEx hooks validate
hostnames and publish `/tmp/commonex-nginx.conf`. It includes the separate API
server only when API and web have different hostnames. Restricted substitution
preserves Nginx request variables; production hostnames remain the defaults.
Build this updated shared image from merged `main` before deploying this Compose
revision, then use the same immutable digest in both environments. Older images
do not support these environment inputs without their previous startup wrapper.
The standard `/etc/nginx/nginx.conf` points to the generated configuration, so
plain `nginx -t` and explicit `nginx -t -c /tmp/commonex-nginx.conf` validate the
same active configuration;
reload signals the master, which rereads its startup configuration.

For an empty database, initialize one backend before starting both replicas:

```sh
cd /etc/commonex/app
docker compose --env-file .env -f docker-compose-prod.yml config --quiet
docker compose --env-file .env -f docker-compose-prod.yml up -d --pull always \
  --wait --wait-timeout 180 db nest-backend-green
```

The backend runs migrations and seeds supported currencies automatically. Serial
initialization prevents concurrent first-install migration/seeding attempts.
Never repeat bootstrap by deleting volumes or importing production data.

## Wildcard certificate and renewals

Production remains the issuer/renewal owner. After a successful renewal check,
its service starts a separate push to staging. An hourly persistent timer retries
failed delivery, including after reboot. Setup, protected configuration, monitoring,
and recovery are documented in the [certificate propagation runbook](../certificates/staging-sync.md).

Only `cert.pem`, `chain.pem`, `fullchain.pem`, and `privkey.pem` are transferred.
The dedicated SSH key accepts only the fixed certificate receiver; DNS credentials,
ACME accounts, and renewal configuration stay on production. Neither host's
administrative SSH key is used by automation.

Installation uses the activation lock and refuses pending activation intents.
It validates trust, wildcard coverage, matching key, and chain; retains versioned
archive files, replaces live links, tests the rendered nginx configuration, and
reloads nginx. Installation errors attempt to restore prior links. The private key is root-owned, group
`1001`, mode `0640`, readable by the existing nginx image; other certificate files
are `0644`. Bind directories stay in place. No additional certificate is issued.

## Deployment and application initialization

From the operator checkout, with the restricted `commonex-staging` SSH alias:

```sh
python3 infra/deploy/production_delivery.py --environment staging deploy \
  <main-release-sha> <increasing-activation-number> \
  '["backend","frontend","nginx","otel-collector"]' \
  infra/docker-compose-prod.yml <private-environment-inputs-without-image-keys>
python3 infra/deploy/verify_public_services.py --environment staging
```

Delivery verification requires an HTTP/2-capable `curl`. To run the standalone
public checker on a host with that client from a shell with input redirection:

```sh
ssh <private-staging-admin-alias> \
  'python3 - --environment staging' < infra/deploy/verify_public_services.py
```

The existing stage/validate/deploy lifecycle preserves immutable references,
activation intent recovery, replay protection, and rollback history. A repeat
activation of an already staged release uses the forced command directly:

```sh
ssh commonex-staging 'deploy <already-staged-main-release-sha> <next-activation-number>'
ssh commonex-staging current-images
python3 infra/deploy/verify_public_services.py --environment staging
```

Do not restage that SHA: staging an existing immutable release is deliberately
rejected. The activation uses a strictly larger identifier and preserves volumes. Ordinary
rollback follows the existing manual procedure and does not downgrade the DB.

Initialize today's exchange rates through
`POST /api/devtools/currency-rate/fetch?date=<current-UTC-date>` with the staging
`x-devtools-secret` loaded from private configuration. Do not place it in command
history or logs. Startup seeds currencies but does not fetch daily rates; without
this step the currencies API is unavailable until the midnight cron succeeds.
Verify `GET /api/v3/user/currencies/all` succeeds. Android fixtures belong to STG-002.

## Verification and persistence

Run the dependency-free suite on Linux with `umask 022`:

```sh
python3 -m unittest discover -s infra/deploy -p 'test_*.py'
python3 -m unittest infra.certificates.tests.test_staging_sync
```

Run certificate installer tests as root in isolated temporary paths. Windows
cannot validate all Linux ownership/symlink behavior.

Verify nine running services and every defined health check. Public verification
checks trusted HTTPS web/API/Grafana and the backend's expected gRPC
`UNIMPLEMENTED` response through HTTP/2. Additionally verify HTTP/3 over UDP using
an HTTP/3-capable client, the Grafana health API, and all three datasource queries.
Generate API/web traffic and confirm nginx/backend traces appear in VictoriaTraces
and metric samples in VictoriaMetrics. Query through Grafana as well as storage;
container startup alone is insufficient.

Create a marked event and expense through the public API; read them remotely,
record IDs, then repeat activation and reread the same IDs and split amounts.
Record the custom-image digest set, active release, activation numbers, migration
history, volume names, Grafana datasource identities, and telemetry samples before
and after. Preserve evidence outside secret-bearing logs. Compose project `app`
uses `app_postgres_data`, `app_victoriametrics_data`, `app_victoriatraces_data`, and
`app_grafana_data`; keep the active Compose directory/project identity stable.
Docker stores their contents under its volume data root (inspect with
`docker volume inspect`). Never use `down --volumes` during normal operation.

## Automatic main validation

Applicable `main` pushes in `.github/workflows/main.yml` build all four custom
images and run `staging-deploy`, `staging-android`, and `staging-result`. PRs and
the manual production rollback entry point do not deploy staging. Android CI
still runs independently against the persistent server; it uses the same reusable
`android-ui-tests.yml` workflow, emulator, Marathon assertions, retries, and device
tests as staging validation.

Before enabling this pipeline:

1. Install the reviewed deployment tool using the existing versioned installer.
   The host must support `ssh commonex-staging release-status`; older versions
   reject this command. Verify its SHA, activation number, four immutable image
   references, and all declared services. It checks active files against the
   retained release, refuses unresolved activation intents, and inspects custom
   containers' actual image references. Defined healthchecks must be healthy;
   other services must be running. No environment values are returned.
2. Create a GitHub `staging` environment allowing `main`, without required manual
   approval or a wait timer. Staging activation and final read-only verification
   both use this environment. Keep production's environment protections in place.
3. Configure the following **staging-only** environment secrets. The `STAGING_`
   prefix prevents fallback to existing production secret names:

   - `STAGING_SERVER_IP`, `STAGING_SSH_DEPLOY_PRIVATE_KEY`: the staging IPv4 address
     and dedicated restricted application deployment key.
   - `STAGING_SSH_KNOWN_HOSTS`: an operator-verified known-hosts line using the
     alias `commonex-staging`, key type, and public host key. No runtime key scan
     or host-key bypass is used.
   - `STAGING_POSTGRES_PORT`, `STAGING_POSTGRES_USER_NAME`,
     `STAGING_POSTGRES_PASSWORD`, `STAGING_POSTGRES_DATABASE`,
     `STAGING_POSTGRES_HOST`, `STAGING_POSTGRES_SCHEMA`: the existing private
     staging database configuration. Do not replace these credentials on a
     persistent initialized database.
   - `STAGING_OPEN_EXCHANGE_RATES_API_ID`, `STAGING_DEVTOOLS_SECRET`,
     `STAGING_GF_SECURITY_ADMIN_USER`, `STAGING_GF_SECURITY_ADMIN_PASSWORD`:
     the existing staging runtime inputs.

   The Android job receives the existing optional repository `SENTRY_AUTH_TOKEN`.
   Public hostnames are fixed staging runtime inputs; no staging-specific image
   is built. The already provisioned Android event fixture needs no recurring
   setup. Verify it remains usable without resetting accumulated data.

The delivery adapter resolves the candidate SHA tags once, preserves the observed
baseline before replacement, stages the existing validated two-file release,
and activates it through the same restricted commands and Compose health wait.
It then checks public web/API/Grafana/gRPC and the actual running service status
before starting Android tests built from that exact candidate SHA.

The final job reads the Active Release again. A changed SHA, activation number,
or image digest fails validation, including an intervening rollback and
reactivation of the same candidate. Failed or missing Android execution/source
evidence also fails. There is no staging workflow concurrency group, reservation,
or test lock; the existing host activation lock and replay checks remain the
only activation coordination. Concurrent attempts may fail and require a new
candidate after inspecting diagnostics. Existing immutable releases are not
restaged by a rerun.

Every run retains candidate-specific `staging-activation-*`, `staging-android-*`,
and `staging-validation-*` artifacts, named with SHA, workflow run ID, and attempt.
The final artifact combines `activation.json`, source identity, and Android
reports. The JSON records baseline/candidate identities, image digests, activation
number, service/public/Android checks, observed final release, and bounded safe
diagnostics. The job summary shows the result and digests. SSH keys, environment
files, archives, and certificate material are excluded from artifacts.

A passing STG-003 report is only initial staging validation:
`rehearsal.status=not_run` and `promotion_eligible=false`. STG-004 adds upgrade and
rollback data checks; STG-005 connects complete evidence to production approval.
Until those tickets land, production delivery still follows its existing separate
job and does not depend on staging validation.

Validate the orchestration with the existing Linux deployment suite and a workflow
syntax checker, then exercise an eligible `main` candidate through the actual
workflow. Inspect exact digests, source identity, service/public evidence, and all
retained Android reports. Exercise a required-check failure and confirm the final
result fails with diagnostics. Repeat activation and verify marked API records
and persistent volumes survive. Live CI evidence is required to close STG-003;
local regression checks alone do not satisfy its acceptance criteria.
