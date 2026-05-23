---
name: update-web-dependencies
description: Use when updating npm dependencies, UI library versions, framework versions, or container runtime images in the CommonEx web project.
---

# Update Web Dependencies

## Version Sources In Repo

Read versions from:
- `web/package.json`
- `web/package-lock.json`
- `web/Dockerfile`
- `web/nginx.conf`
- `web/next.config.mjs`

## Where To Check On The Internet

Use primary sources:
- `npm outdated` and `npm view <package> version`
- official release notes for Next.js, React, Material UI, Emotion, react-hook-form, react-router-dom, TypeScript, ESLint, and related tooling
- the base image release page or tag listing for `node:24-alpine3.23`
- the image release page or tag listing for `nginxinc/nginx-unprivileged:1.29.5-alpine3.23`

For framework-major updates, also read official migration guides.

## Smallest Valid Update Bundles

Default bundle boundaries:
- **Next stack**: `next` and `eslint-config-next`
- **React stack**: `react`, `react-dom`, `@types/react`, `@types/react-dom`
- **MUI stack**: `@mui/material` and `@mui/icons-material`
- **Emotion stack**: `@emotion/react` and `@emotion/styled`
- **Forms stack**: `react-hook-form` and `react-hook-form-mui`
- **Lint and formatting stack**: `eslint`, `eslint-config-prettier`, `prettier`, and sort-import tooling when peer constraints require it
- **Runtime image stack**: `web/Dockerfile` base images

Other packages may be proposed individually when there is no coupling.

## Proposal Rules

Before editing, present numbered bundles with:
- current and target versions
- affected manifest entries or image tags
- bundle reason
- concise impact summary
- source links

If an upstream note only says another dependency was bumped, follow the chain recursively until the impact is understandable.

## Apply Rules

When the user selects bundles:
- update `package.json` and regenerate `package-lock.json`
- keep the existing architecture and feature-sliced layout intact
- apply migration or deprecation-driven refactors in touched code
- if a selected image update changes nginx behavior or build output assumptions, update config instead of leaving hidden drift

## Validation

Run from `web/`:

```bash
npm run lint
npm run build
```

If a selected update changes App Router, export behavior, or route serving assumptions, mention the operational impact explicitly.
