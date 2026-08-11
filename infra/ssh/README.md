# CommonEx SSH hardening

Install `60-commonex.conf` as `/etc/ssh/sshd_config.d/60-commonex.conf` only after both the root break-glass key and the restricted `commonex-deploy` key have succeeded in separate sessions.

Before reloading SSH:

1. Preserve the active root session.
2. Run `sshd -t`.
3. Inspect the effective settings with `sshd -T` and abort if they do not match the values below. OpenSSH uses the first obtained value, so an earlier main-file or drop-in directive can override `60-commonex.conf`.
4. Reload rather than restart `sshd`.
5. Open and validate new root and restricted-deployer sessions before closing the original session.

Required effective values:

```text
permitrootlogin without-password
passwordauthentication no
kbdinteractiveauthentication no
pubkeyauthentication yes
maxauthtries 3
allowusers root
allowusers commonex-deploy
```

Rollback by restoring the pre-change `sshd_config.d` snapshot, running `sshd -t`, and reloading `sshd` from the preserved root session.
