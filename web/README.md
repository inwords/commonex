# CommonEx Web

CommonEx Web is the browser client for the CommonEx expense-sharing platform.

## Runtime Model

- Framework shell: Next.js 16
- UI/state stack: Material UI + MobX + react-hook-form
- Routing model: client-side SPA routing via `BrowserRouter` inside `src/app/page.tsx`
- Production artifact: static export (`next.config.mjs` sets `output: "export"`)
- Production serving: Nginx serves the exported `build/` directory in the web container

## Commands

Run commands from `web/`.

```bash
npm install
npm run dev
npm run build
npm run lint
```

`npm run lint` uses the ESLint CLI through `eslint.config.mjs`, keeping Next core-web-vitals rules via `eslint-config-next`.

There is currently no checked-in `npm run test` script in this project.

## Deployment Notes

- The Docker image builds the static export and copies `build/` into Nginx.
- SPA route fallback in production comes from `web/nginx.conf` (`try_files $uri /index.html =404`).
- The current Next build warns that `rewrites` do not apply with `output: export`, so do not rely on Next rewrites for production routing behavior.

## More Detail

For engineering workflow, structure, validation, and troubleshooting, see `web/AGENTS.md`.
For cross-project product and transport references, start with `../docs/README.md`.

## Expense editing (backend PR #344)

Editing is disabled by default. Enable it at **build time** with
`NEXT_PUBLIC_EXPENSE_EDITING_ENABLED=true npm run build`, or for development with
`NEXT_PUBLIC_EXPENSE_EDITING_ENABLED=true npm run dev`. For Docker pass
`--build-arg NEXT_PUBLIC_EXPENSE_EDITING_ENABLED=true`. Only the exact value `true` enables it.
Unset the variable or set it to `false` and rebuild to disable it; changing the
Nginx container environment cannot change an already exported app. This public
switch controls rollout, not authorization.

Deploy the backend versioned-editing contract from PR #344 before enabling the
flag. Enabled clients create new trips with `version: 2`; older mobile clients
cannot access these trips. Existing trips are not upgraded. Editing is shown in
expense details only for version-2 trips with versioned expense responses.
With the flag disabled, event creation and expense read/create routes retain
their existing behavior.

The client keeps v2 reads and creation, and saves a revision using
`POST /v4/user/event/:eventId/expense/:id/revisions` with the original
`expectedVersion`, `createdAt` and one idempotency key across automatic retries.
A conflict keeps the draft until the user explicitly loads the latest version.
Manual shares and saved exchange amounts are preserved on description-only edits;
automatic conversion uses the expense date. Revision history, date editing and
refund editing are outside this feature.
