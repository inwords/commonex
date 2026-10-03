# Automatic staging certificate propagation

Production's existing certificate renewal service starts an independent transfer
service after each successful renewal check. An hourly persistent timer retries
when staging is unavailable. Transfer failures do not alter production renewal
state. An invalid candidate is rejected before changing staging; installation
errors attempt to restore prior live links. Inspect both services when checking
renewal completion.

[staging_sync.py](staging_sync.py) reads the four certificate files under the
production renewal lock, then sends a bounded archive over pinned SSH. The
staging receiver validates trust, wildcard coverage, certificate/key and chain
consistency, serializes with application activation, installs versioned files,
and validates/reloads nginx. Installation errors attempt to restore prior live links.

Server addresses, usernames, host pins, and SSH identities belong in private host
configuration. Generate a dedicated transfer key on production; transfer only its
public half to staging. Keep administrative and application deployment keys separate.
This mechanism does not issue certificates or transfer DNS/ACME credentials.

## Install reviewed tooling

On both hosts, install the same reviewed `staging_sync.py` as root-owned `0644`
under `/opt/commonex/staging-certificate-sync/versions/<source-content-sha256>/`.
Keep the prior version and select the reviewed directory with an atomic `current`
symlink. All parent directories must be root-owned and protected from other users'
writes. Use `/usr/bin/python3` (Python 3.9 or newer).

On staging, create a dedicated SSH receiver account using the private operator
inventory's account name. Install
[the forced receiver](staging-sync/commonex-staging-certificate-receive) as root-owned
`0755` at `/usr/local/sbin/commonex-staging-certificate-receive`.

Give that account exactly this sudo command (substitute its private account name):

```text
<receiver-user> ALL=(root) NOPASSWD: /usr/bin/python3 -B /opt/commonex/staging-certificate-sync/current/staging_sync.py --install
```

Validate the policy with `visudo -cf` before installation. Use a root-owned home,
`.ssh` directory and authorized-key file, readable by SSH but not writable by the
receiver. Authorize only the new public key with:

```text
restrict,from="<production-egress-address>",command="/usr/local/sbin/commonex-staging-certificate-receive" <transfer-public-key>
```

The wrapper accepts only the exact `install` command and no arguments. Verify
arbitrary commands, shell access, forwarding, and general sudo are denied.
Preserve existing administrative/deployment access and validate SSH configuration
before reloading it if account allowlists require an update.

## Configure the production sender

Create `/etc/commonex/certificates/staging.json` as root-owned `0600` with exactly
these fields, substituting private host configuration:

```json
{
  "target": "<receiver-user>@<staging-address>",
  "identity": "/etc/commonex/certificates/staging-sync/identity",
  "known_hosts": "/etc/commonex/certificates/staging-sync/known_hosts",
  "source_directory": "/etc/commonex/ssl/certbot/live/commonex.ru"
}
```

The identity must be a root-owned `0600` regular file. Obtain the staging host key
through a trusted administrative connection or cloud console and store a matching
address entry in `known_hosts`; it must be a root-owned regular file without
group/world write permission. These paths must be absolute. Keep their parent
directories protected. The sender disables ambient SSH configuration and global
host pins, and requires the specified verified pin.

Install the new production units as root-owned `0644`:

```sh
install -m 0644 infra/certificates/systemd/commonex-staging-certificate-sync.service \
  infra/certificates/systemd/commonex-staging-certificate-sync.timer /etc/systemd/system/
install -d -m 0755 /etc/systemd/system/commonex-certificate-renew.service.d
install -m 0644 infra/certificates/systemd/commonex-certificate-renew.service.d/staging-sync.conf \
  /etc/systemd/system/commonex-certificate-renew.service.d/staging-sync.conf
systemd-analyze verify /etc/systemd/system/commonex-staging-certificate-sync.service \
  /etc/systemd/system/commonex-staging-certificate-sync.timer
systemctl daemon-reload
systemctl start commonex-staging-certificate-sync.service
systemctl enable --now commonex-staging-certificate-sync.timer
```

The drop-in adds an asynchronous `ExecStartPost` dispatch to the existing renewal
service. Dispatch errors are ignored by the renewal service; inspect the transfer
service separately. This preserves the renewal runner, existing delivery guards, and certificate
release archive format. Protect this host-managed drop-in during future maintenance.

## Verify and recover

Check `systemctl status commonex-staging-certificate-sync.service` and
`journalctl -u commonex-staging-certificate-sync.service`. Confirm the hourly timer
is enabled and the renewal service shows the extra `ExecStartPost`. Compare
staging's served serial/expiry with production across all three staging domains.
Certificate/private-key contents and captured SSH diagnostics are never logged.

If delivery fails, check staging availability, pinned host key, receiver policy,
certificate validity, and any pending application activation. Repair the cause
and start the transfer service again; the timer also retries. A lost SSH
acknowledgment can report failure after successful installation, so verify the
served certificate before assuming staging still has its previous certificate. An application
intent requires the existing activation recovery procedure, not deleting the
intent to bypass the guard. Watch served expiry if failure persists.

To pause, stop/disable the transfer timer and remove only `staging-sync.conf`,
then reload systemd. Production's renewal service keeps its other drop-ins and
its existing timer. Roll back transfer tooling by selecting the retained version;
do not restore old certificate private keys as a tooling rollback.

## Validation

Run on Linux as root in isolated temporary test paths:

```sh
python3 -m unittest infra.certificates.tests.test_staging_sync
```
