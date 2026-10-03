# Official Nginx Docker startup scripts

These files are copied unchanged from [nginx/docker-nginx](https://github.com/nginx/docker-nginx)
at commit [`ef5a25a6314e652a2dcfbce6074bef729f4d9639`](https://github.com/nginx/docker-nginx/tree/ef5a25a6314e652a2dcfbce6074bef729f4d9639).
Their redistribution terms are retained in [LICENSE](LICENSE), which is also included in the image.

| File | Upstream path | SHA256 |
| --- | --- | --- |
| `docker-entrypoint.sh` | `entrypoint/docker-entrypoint.sh` | `3372fd90162da30d09f62c245e9c6bfd620fb0bb5b48ab54b16e804c2b9e8aae` |
| `20-envsubst-on-templates.sh` | `entrypoint/20-envsubst-on-templates.sh` | `00d6d14424342d6a3439f4d7af190bfc33b96f40221946b174a6c7385ee3b98c` |
| `LICENSE` | `LICENSE` | `5e01e80542c4ee1c1640501bea5bd55e1e2174034343b45e228465c300ce5158` |

The upstream entrypoint discovers ordered hooks. CommonEx supplies only hostname
validation, a private output directory, the variable allowlist, optional API include
selection, and the final configuration validation. The official `20-envsubst` hook
renders all templates. Other official-image hooks are not needed by this custom build.

The official entrypoint runs hooks for any `nginx` command, including `nginx -V`.
For version inspection without startup configuration, use
`docker run --rm --entrypoint nginx <image> -V`.

To update, select and review an upstream commit, copy these three files byte-for-byte,
update the commit and checksums here, and run the Nginx environment tests and image build.
