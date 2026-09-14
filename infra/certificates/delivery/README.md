# Certificate infrastructure delivery

## State

- LLHost has the fixed installer and dedicated `commonex-certificates-deploy` SSH account.
- GitHub's `production` environment contains `SSH_CERTIFICATES_DEPLOY_PRIVATE_KEY`.
- The first protected workflow deployment is pending; GitHub secret and registry wiring remain unverified end to end.
- Inspect the installed fingerprint, active release, and rollback history with the dedicated SSH command `status`. CI compares that fingerprint with the checked-out installer source before publishing.

The [Certificate infrastructure workflow](../../../.github/workflows/certificates.yml) defines triggers, inputs, secrets, and validation. Production deployments and rollbacks run from `main`, using the `production` environment and the `commonex-production` concurrency group shared with application deployment.

## Release contract

[contract.py](contract.py) is the archive allowlist and manifest schema shared by publisher and host. A release binds its Git revision to an immutable container digest. Credentials, Certbot storage, and operational state stay on the host.

Activation installs an immutable version under `/opt/commonex/certificates/versions`, selects `current`, and updates the image and systemd units. Acceptance requires a DNS rehearsal, production certificate check, and successful metric publication. Grafana state remains host-managed. Timers are disabled during activation; running services finish naturally. Prior enabled/active timer states are preserved, including paused timers.

Ordinary failure restores and verifies the previous runner, image, units, and timer states. The initial manual installation is a rollback target. Certificate keys and renewal state are never rolled back.

The first CI deployment requires a reviewed Git revision different from the manual installation's `status.current`. Manual and CI installations can use different image digests and file layouts; each revision identifies one immutable release, including its retained rollback configuration.

## Trust boundary

Certificate publishing is **root-equivalent authority**: releases contain root-executed Python and systemd units. Restrict the `production` environment to `main` and retain review protection. The dedicated deployment key is separate from application and root access.

The fixed installer owns archive validation, the SSH entrypoint, sudo policy, and recovery guards; release archives cannot replace them. Installer or shared-dependency changes require a reviewed bootstrap update. Routine runner changes do not require bootstrap. Application and certificate delivery reuse the activation transaction, durable storage, versioned installer, bounded archive reader, and CI SSH helper.

## Bootstrap or upgrade

1. For initial setup, create a dedicated Ed25519 key. Store its private half in `production` as `SSH_CERTIFICATES_DEPLOY_PRIVATE_KEY`; transfer only the public half to LLHost. Keep root bootstrap access separate.
2. Confirm `DOCKER_USERNAME`/`DOCKER_PASSWORD` can publish `ruggedbl/commonex-certificates`, and the host can pull it through public access or a read-only registry login. Delivery reuses `SERVER_IP` and [pinned host keys](../../deploy/known_hosts).
3. Prepare the fixed bundle from the reviewed checkout:

   ```sh
   python3 infra/certificates/delivery/bootstrap.py prepare \
     --output /tmp/commonex-certificate-delivery-bundle
   git rev-parse HEAD
   ```

   Transfer the bundle to `/root/commonex-certificate-delivery-bundle` and the public key to `/root/certificate-deploy.pub`. Bundle directories require `0755`, files `0644`, and all ancestors root ownership without write access for others.
4. On LLHost, inspect the plan, then apply the reviewed revision:

   ```sh
   python3 /root/commonex-certificate-delivery-bundle/bootstrap.py install \
     --bundle /root/commonex-certificate-delivery-bundle \
     --revision <REVIEWED_GIT_SHA> --public-key /root/certificate-deploy.pub
   python3 /root/commonex-certificate-delivery-bundle/bootstrap.py install \
     --bundle /root/commonex-certificate-delivery-bundle \
     --revision <REVIEWED_GIT_SHA> --public-key /root/certificate-deploy.pub --apply
   ```

   Bootstrap installs under `/opt/commonex/certificate-delivery/` and retains prior entrypoint/version backups. The account home is `/home/commonex-certificates-deploy`, where SSH can read its root-owned authorized key. When `AllowUsers` is configured, bootstrap adds a separate drop-in and validates preservation of existing access before reloading SSH.
5. Verify the dedicated key accepts `status` and rejects arbitrary commands. Allowed commands are `status`, `deploy <sha> <run-number>`, and `rollback <sha> <run-number>`; its sole sudo command is `/usr/local/sbin/commonex-certificates-deploy forced`. Interactive shell and SCP access are disabled.
6. Run the protected workflow from `main`. Completion requires successful preflight, image publication, host activation, and acceptance checks.

## Rollback and recovery

Dispatch the workflow from `main` with `rollback` and a `release_sha` from `status` history. Only the last three successfully activated distinct revisions are eligible. Retain the initial local image while the manual baseline remains eligible; release files and registry SHA tags are not pruned.

Run numbers are independent of application deployment. Successful or older numbers are rejected; use a new dispatch. A failed run can be retried after successful restoration. Staging is atomic; abandoned temporary staging directories can be removed while delivery is idle.

Exit code `2` means activation committed but its final audit write failed. The selected release remains active and the run number is consumed. Inspect `status` and repair audit storage before a new dispatch.

State, backups, and audit records live under `/var/lib/commonex/certificates/delivery/`. An `activation-intent.json` blocks subsequent delivery and both certificate services, including after reboot. The fixed systemd guards remain in place during rollback.

For a retained intent:

1. Keep timers disabled. Inspect the intent, activation state, backup `configuration.json`, current pointer, image configuration, and DNS reconciliation marker.
2. Establish whether the candidate committed, or restore the recorded backup.
3. Verify the runner and metric publication against the selected release.
4. Clear the intent only after reconciliation; restore the recorded timer states.

Root access can repair the fixed installer independently. For DNS and renewal recovery, use the [certificate runbook](../README.md).
