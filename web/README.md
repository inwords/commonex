# CommonEx Web

CommonEx Web is the browser client for the CommonEx expense-sharing platform.

## Runtime Model

- Framework shell: Next.js 14
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

There is currently no checked-in `npm run test` script in this project.

## Deployment Notes

- The Docker image builds the static export and copies `build/` into Nginx.
- SPA route fallback in production comes from `web/nginx.conf` (`try_files $uri /index.html =404`).
- The current Next build warns that `rewrites` do not apply with `output: export`, so do not rely on Next rewrites for production routing behavior.

## More Detail

For engineering workflow, structure, validation, and troubleshooting, see `web/AGENTS.md`.
For cross-project product and transport references, start with `../docs/README.md`.
