---
name: update-backend-dependencies
description: Use when updating npm dependencies, toolchain versions, or container base images in the CommonEx backend project.
---

# Update Backend Dependencies

Follow `.agents/skills/update-dependency-batch` for the mandatory four-phase workflow.

## Version Sources In Repo

Read versions from:
- `backend/package.json`
- `backend/package-lock.json`
- `backend/Dockerfile`

Use exact versions only. Do not introduce `^` or `~`.

## Where To Check On The Internet

Use primary sources:
- `npm outdated` for a first pass on installed vs wanted vs latest
- `npm view <package> version` when you need the exact current registry latest
- the package's official changelog, GitHub releases page, or framework release notes
- the base image release page or tag listing for `node:24-alpine3.23`

Prefer official NestJS, Fastify, OpenTelemetry, TypeORM, TypeScript, ESLint, Jest, and Node release notes when relevant.

## Smallest Valid Update Bundles

Default bundle boundaries:
- **Nest stack**: `@nestjs/*` packages, `@nestjs/swagger`, `@nestjs/terminus`, `@nestjs/typeorm`, and `@nestjs/testing` when the same upstream release line expects them together
- **Fastify stack**: `fastify`, `@fastify/*`, and `fastify-plugin`
- **OpenTelemetry stack**: `@opentelemetry/*` packages and any recursively referenced instrumentation packages
- **TypeScript and lint stack**: `typescript`, `@typescript-eslint/*`, `eslint`, `@eslint/*`, `globals`, `eslint-config-prettier`, `eslint-plugin-prettier` when peer constraints require it
- **Test stack**: `jest`, `ts-jest`, `@types/jest`, `supertest`, and related type packages when peer versions are coupled
- **Node image stack**: `backend/Dockerfile` base image updates

Single-package updates are fine when there is no peer or framework coupling.

## Proposal

Follow `.agents/skills/update-dependency-batch` (one `## N — …` section per bundle: **Summary**, then full numbered release notes). **Stop** after the proposal; do not edit manifests until the user selects bundles.

## Apply (after user selection)

When the user selects bundles in a follow-up message:
- update `package.json`, regenerate `package-lock.json`, and keep pinned exact versions
- refactor deprecated APIs exposed by the chosen upgrades
- update Docker base images when selected and reconcile runtime assumptions if Node or Alpine changed
- ask before making uncertain behavior changes in request handling, persistence, or observability

## Validation

Follow project's validation instructions.

If a selected bundle changes migrations, transport behavior, or runtime bootstrap, call that out explicitly in the report.
