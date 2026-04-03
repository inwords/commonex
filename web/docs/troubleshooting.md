# Web Troubleshooting

## Build Issues

- **Next.js cache issues**: Clear `.next` directory
    - Bash: `rm -rf .next`
    - PowerShell: `Remove-Item -Recurse -Force .next`
- **TypeScript errors**: Check `tsconfig.json` configuration
- **Module resolution**: Verify import paths use `src/` prefix

## PowerShell Workarounds

- **Dependency install blocked**: If `npm install` or `npm ci` fails because PowerShell tries a `*.ps1` shim:
    - `$env:npm_config_script_shell='cmd.exe'; npm.cmd install`
    - `$env:npm_config_script_shell='cmd.exe'; npm.cmd ci`
- **Script execution blocked**: If `npm run lint` or `npm run build` fails with execution policy errors, use CMD shims:
    - `.\node_modules\.bin\eslint.cmd .`
    - `.\node_modules\.bin\next.cmd build`
    - `.\node_modules\.bin\next.cmd dev`

## Runtime Issues

- **API endpoint errors**: Check the browser base-URL selection in `src/6-shared/api/http-client.ts` and the
  gateway/container routing for `/api`
- **HTTP client contract**: The shared browser `HttpClient` in `src/6-shared/api/http-client.ts` uses `http://localhost:3001` on localhost and `/api` otherwise, always sends `Content-Type: application/json`, and normalizes backend error envelopes into `ApiError`.
- **MobX store not updating**: Verify store is properly initialized and observable
- **Hydration errors**: Check for server/client mismatch in rendering
- **Direct SPA route refresh fails in production**: Verify the request is reaching the web container Nginx fallback
  (`try_files $uri /index.html =404`), not relying on Next rewrites

## Common Errors

- **"Module not found"**: Check import paths and file structure
- **"Cannot read property"**: Verify MobX store initialization
- **"Build fails"**: Clear `.next` cache and rebuild
- **"rewrites will not automatically work with output: export"**: Expected with the current `next.config.mjs`;
  production route fallback must come from Nginx, not Next rewrites
