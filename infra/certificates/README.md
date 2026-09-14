# CommonEx certificate renewal

## Production state

| Component | State / source of truth |
| --- | --- |
| Host | LLHost; CentOS Stream 9, Python 3.9; Compose project `app`. |
| DNS | Yandex Cloud zone `dnsbsk3d569ohf8eurns` for `commonex.ru.`. |
| Certificate | `/etc/commonex/ssl/certbot/live/commonex.ru/`; covers `commonex.ru` and `*.commonex.ru`. Older lineages are outside this runner's scope. |
| Renewal | Enabled; twice daily with jitter. Certbot decides whether issuance is due. |
| Monitoring | Enabled; hourly with jitter. [Grafana rules](monitoring/README.md) are UI-only; outbound notifications are deferred. |
| Installed code | `/opt/commonex/certificates/current`; image digest in `/etc/commonex/certificates/renewal.json`. |
| Credentials | Root-owned `0600` files in `/etc/commonex/certificates/`: `yandex-key.json`, `dns.json`, `renewal.json`. |
| Delivery | [Dedicated CI workflow and bootstrap](delivery/README.md). |
| Recovery backup | Root-only Certbot and Grafana backup: `/var/lib/commonex/certificates/adoption-edbbe90d/`. |

## Runtime contract

Certbot owns ACME issuance and certificate storage. The Python DNS hooks use the
Yandex Cloud API, PyJWT for PS256 signing, and dnspython for authoritative queries.
Challenge updates preserve unrelated TXT values; cleanup removes only the current
challenge. DNS aliases or delegation for `_acme-challenge` are unsupported.

Renewal uses a dedicated lock. Nginx validation, reload, and public TLS verification
use `/run/commonex/deploy.lock`; an unfinished application activation blocks reload.
The runner selects only `--cert-name commonex.ru` and suppresses other Certbot hooks.
TLS probes validate trust, hostname, and the served certificate on `commonex.ru`,
`www`, `dev-api`, `gf`, and `grpc`.

Use the [delivery workflow](delivery/README.md) for updates and rollback. The steps
below are for initial installation or manual recovery.

## 1. Create the Yandex service account

Use an authenticated `yc` CLI with permission to create service accounts and grant
zone access. Substitute the IDs below; keep authorized keys out of chat, commits,
and CI logs.

```sh
yc dns zone list
yc iam service-account create --name commonex-cert-renewal
yc dns zone add-access-binding <DNS_ZONE_ID> \
  --role dns.editor \
  --service-account-id <SERVICE_ACCOUNT_ID>
umask 077
yc iam key create --service-account-id <SERVICE_ACCOUNT_ID> \
  --output commonex-cert-renewal-key.json
```

Grant `dns.editor` on the specific zone. This role can manage every record in that
zone; the hook additionally checks the zone name and identifier allowlist.

Securely transfer the authorized-key JSON to the LLHost server, install it as
`/etc/commonex/certificates/yandex-key.json`, owned by root with mode `0600`, and
remove temporary transfer copies after verifying installation. The hook exchanges the
authorized key for a short-lived IAM token on demand; interactive IAM tokens are
unsuitable for unattended renewal.

## 2. Prepare the reviewed version

From the reviewed, committed repository revision on a build machine:

```sh
revision=$(git rev-parse HEAD)
docker build --target runtime -f infra/certificates/Dockerfile \
  -t commonex-certificates:$revision .
docker save -o commonex-certificates.tar commonex-certificates:$revision
```

Transfer the image archive and the `infra/certificates` source directory to the
server. The runtime image contains code and dependencies only. It receives DNS
credentials through read-only file mounts at execution time and never receives
the Docker socket. On the server, as root:

```sh
docker load -i commonex-certificates.tar
docker image inspect --format '{{.Id}}' commonex-certificates:<REVISION>
install -d -o root -g root -m 0700 /etc/commonex/certificates
install -d -o root -g root -m 0700 /var/lib/commonex/certificates
install -d -o root -g root -m 0755 /opt/commonex/certificates/versions/<REVISION>
```

Copy `renew.py`, `host_runtime.py`, and `systemd/` from the reviewed package into that versioned
directory, root-owned and not writable by other users. Copy the example JSON
files to `/etc/commonex/certificates/dns.json` and `renewal.json` with mode `0600`.
Set `dns.json`'s `zone_id` to the real zone ID. Keep its `service_account_key`
value `/run/secrets/yandex-key.json`: this is the container path. Set
`renewal.json`'s `image` to the **local immutable `sha256:...` image ID** printed
above, not its mutable tag. The runner also accepts a registry digest reference.
Keep this image available; do not prune it merely because no container is running.

Retain the prior `/opt/commonex/certificates/current` target on upgrades and switch
the symlink atomically while both certificate timers/services are stopped. Use
`/opt/commonex/certificates/versions/<REVISION>/renew.py` directly for the initial
rehearsal, before enabling a stable entrypoint or timers.

## 3. Rehearse before enabling

First retain a root-only backup of the existing Certbot configuration, account,
and certificate storage outside `/etc/commonex/ssl/certbot`. Preserve private-key
permissions. Confirm the configured zone ID is the authoritative `commonex.ru.`
zone and no DNS reconciliation marker is present.

```sh
/usr/bin/python3 -B /opt/commonex/certificates/versions/<REVISION>/renew.py rehearse
```

Rehearsal uses the staging CA (`certbot renew --dry-run`) and real DNS challenges.
Certbot may delay startup by several minutes. The live certificate and production
renewal-success timestamp are preserved. Success requires DNS cleanup, nginx
validation/reload, and trusted TLS with the installed certificate on all five hosts.

Verify unrelated TXT data survives. Challenge presentation must succeed on every
authoritative nameserver; API acceptance alone is insufficient.

## 4. Install monitoring and timers

The runner publishes metrics to VictoriaMetrics. Existing [Grafana alerts](monitoring/README.md)
are managed on the host; certificate installation preserves their configuration.

After a successful rehearsal, select the reviewed version as
`/opt/commonex/certificates/current` and install the four unit files:

```sh
install -o root -g root -m 0644 \
  /opt/commonex/certificates/current/systemd/commonex-certificate-*.service \
  /opt/commonex/certificates/current/systemd/commonex-certificate-*.timer \
  /etc/systemd/system/
systemd-analyze verify /etc/systemd/system/commonex-certificate-*.service \
  /etc/systemd/system/commonex-certificate-*.timer
systemctl daemon-reload
systemctl start commonex-certificate-renew.service
systemctl start commonex-certificate-monitor.service
systemctl enable --now commonex-certificate-renew.timer commonex-certificate-monitor.timer
systemctl list-timers 'commonex-certificate-*'
```

Completion: both services succeed and both timers are enabled. A check with no
renewal due still verifies the served certificate and records success; forced
issuance is unnecessary.

## Failure recovery and rollback

- Inspect `journalctl -u commonex-certificate-renew -u commonex-certificate-monitor`.
  Errors deliberately omit API bodies, JWTs, private keys, and captured Certbot
  output. Certbot logs in its disposable container are transient.
- An uncertain DNS write or cleanup failure creates the root-only
  `/etc/commonex/ssl/certbot/.commonex-dns-reconciliation-required` marker. It
  blocks later scheduled renewals. Inspect the named Yandex operation/record,
  establish that no write remains pending, and remove only the marker's exact
  challenge value if present, preserving all other values. Remove the marker
  only after reconciliation, then rerun the rehearsal. A delayed operation must
  not be mistaken for a completed cleanup.
- Nginx validation failure never triggers a reload. A reload/served-certificate
  mismatch leaves renewal marked failed; the next successful check retries
  reconciliation even when a new certificate is not due.
- An application activation intent requires application recovery. Follow the
  [deployment failure runbook](../deploy/README.md#failure-handling-and-audit-outcomes)
  rather than deleting it to allow a certificate reload.
- A killed systemd renewal invokes cleanup under the renewal lock to stop its
  disposable container and leaves a reconciliation marker. For an interrupted
  process the exact token may be unavailable; inspect outstanding ACME TXT
  values before resuming. SIGKILL cannot guarantee Certbot cleanup ran.
- To pause automation, stop both timers and the renewal service. Existing TLS
  certificates remain installed. To roll back code, select the retained tool
  version and its immutable image configuration, rehearse, then resume. Do not
  restore old certificate private keys automatically as part of a code rollback.
- Before revoking a Yandex key, install and rehearse its replacement. Keep DNS
  credentials outside application release archives and outside Grafana.

## Local validation

```sh
docker build --target test -f infra/certificates/Dockerfile -t commonex-certificates:test .
docker run --rm commonex-certificates:test discover -s infra/certificates/tests -p 'test_*.py'
docker run --rm commonex-certificates:test discover -s infra/certificates/delivery -p 'test_*.py'
docker run --rm commonex-certificates:test discover -s infra/deploy -p 'test_*.py'
```

Unit tests use fake API/DNS responses and isolated files. They do not issue
certificates or modify Yandex. Live Yandex permissions, TXT presentation, propagation,
and staging issuance remain mandatory adoption checks.

## Official contracts

- [Yandex authorized-key authentication](https://yandex.cloud/en/docs/iam/operations/iam-token/create-for-sa)
- [Zone-scoped permissions](https://yandex.cloud/en/docs/dns/operations/zone-access)
- [Strict record-set update](https://yandex.cloud/en/docs/dns/api-ref/DnsZone/updateRecordSets)
- [Asynchronous operation status](https://yandex.cloud/en/docs/dns/api-ref/Operation/get)
- [Certbot DNS hooks and renewal](https://eff-certbot.readthedocs.io/en/stable/using.html)
- [dnspython authoritative queries](https://dnspython.readthedocs.io/en/stable/query.html)
