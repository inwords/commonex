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
Manual activation identifiers must strictly increase; see
[automatic main validation](#automatic-main-validation) for CI's counter policy.

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
images and run `staging-deploy` and `staging-android`. PRs and the manual
production rollback entry point do not deploy staging. Independent Android CI
also uses the [shared Android runner](../../android/marathon/README.md#reports)
against the persistent server.

Before enabling this pipeline:

1. Install the reviewed deployment tool using the [versioned installer](README.md#host-bootstrap)
   and verify `ssh commonex-staging release-status` follows the
   [release-status contract](README.md#release-status). Older versions reject it.
2. Create a GitHub `staging` environment allowing `main`, without required manual
   approval or a wait timer. Staging deployment uses this environment. Keep
   production's environment protections in place.
3. Configure the following **staging-only** environment secrets. The `STAGING_`
   prefix prevents fallback to existing production secret names:

   - `STAGING_SERVER_IP`, `STAGING_SSH_DEPLOY_PRIVATE_KEY`: the staging IPv4 address
     and dedicated restricted application deployment key.
   - `STAGING_SSH_KNOWN_HOSTS`: the [verified host key](#access-and-network),
     recorded with alias `commonex-staging`, key type, and public host key.
   - For `POSTGRES_*`, `OPEN_EXCHANGE_RATES_API_ID`, `DEVTOOLS_SECRET`, and
     `GF_SECURITY_ADMIN_*` inputs in the [bootstrap table](#host-bootstrap-and-environment),
     prefix each key with `STAGING_` (for example, `STAGING_POSTGRES_PASSWORD`).
     Use the existing staging values; do not replace persistent database credentials.

   The Android job receives the existing optional repository `SENTRY_AUTH_TOKEN`.
   Runtime hostnames come from the [bootstrap table](#host-bootstrap-and-environment).
   Keep the provisioned Android fixture and accumulated data; no recurring setup
   is needed.

The delivery adapter resolves candidate SHA tags once, records the baseline, and
uses its activation number plus one (or one for an empty host). It follows the
[shared delivery lifecycle](#deployment-and-application-initialization), preserving
activation locks, recovery, and replay protection. There is no staging workflow
concurrency group, reservation, or test lock. An intervening activation can fail
the attempt; inspect diagnostics before choosing a new candidate. Reruns do not
restage an existing immutable release.

`staging-deploy` records `status=passed` only after service/public checks and
verification of the activated release and image digests. Its success starts
`staging-android`, which builds from the candidate SHA, verifies the checkout,
and runs the unchanged suite and retry rules. The jobs report separate checks;
an Android failure does not rewrite the deployment report. There is no combined
report or release recheck after Android finishes.

Each producer retains its own candidate-specific `staging-activation-*` or
`staging-android-*` artifact, named with SHA, workflow run ID, and attempt. The
deployment artifact contains `activation.json`: baseline/candidate identities,
image digests, activation number, service/public checks, observed activated
release, and bounded safe diagnostics. Its job summary shows the deployment
result and digests. The Android artifact contains `source-identity.json` with
candidate and checked-out SHAs, plus the existing Android reports and raw results.
SSH keys, environment files, archives, and certificate material are excluded.

Passing STG-003 checks supply only initial staging validation. The deployment
report retains `rehearsal.status=not_run` and `promotion_eligible=false`. STG-004
adds upgrade and rollback data checks; STG-005 connects complete evidence to
production approval.
Until those tickets land, production delivery still follows its existing separate
job and does not depend on staging validation.

For orchestration changes, run the deployment suite and workflow syntax checks.
CI acceptance and failure exercises are defined in
[STG-003](../../docs/issues/staging-server-and-production-deployment-gate/STG-003-automate-main-staging-validation.md#validation).
