# Agent Instructions for CommonEx Web

For non-trivial work and when to search upstream docs, follow root [AGENTS.md](../AGENTS.md) (workflow lifecycle and freshness policy).
Cross-project reference docs: [../docs/domain.md](../docs/domain.md) and [../docs/network-contracts.md](../docs/network-contracts.md).

## Project Overview

CommonEx web is a **Next.js** browser client for the expense-sharing platform. It uses Material UI for components,
MobX for state management, and follows a strict feature-sliced architecture. The current runtime model is a
client-rendered SPA: `src/app/page.tsx` mounts `BrowserRouter`, and production builds use static export served by Nginx.

## Technology Stack

- **Framework**: Next.js v14 with App Router shell
- **UI Library**: Material UI v5
- **State Management**: MobX
- **Forms**: react-hook-form with MUI integration
- **Language**: TypeScript
- **Routing runtime**: client-side `BrowserRouter`
- **Production output**: static export to `build/`
- **Production serving**: Nginx in the web container

## Architecture

Feature-driven with strict folder structure (Feature-Sliced Design):

- **`2-pages`**: Page components (routes)
- **`3-widgets`**: Composite UI components
- **`4-features`**: Business logic components
- **`5-entities`**: Business entities with stores/services
- **`6-shared`**: Shared utilities and types

### Key File Locations

- **App entry**: `src/app/`
- **SPA router entry**: `src/app/page.tsx`
- **Pages**: `src/2-pages/`
- **Widgets**: `src/3-widgets/`
- **Features**: `src/4-features/`
- **Entities**: `src/5-entities/`
- **Shared utilities**: `src/6-shared/`
- **Configuration**: `next.config.mjs`, `tsconfig.json`
- **Container runtime**: `Dockerfile`, `nginx.conf`

## Prerequisites

- **Node.js and npm** (versions: see `package.json` engines or lockfile; do not duplicate in docs).
- **Git** for version control

## Environment Setup

### Installation

```bash
cd web
npm install
```

### Environment Variables

- The current browser HTTP client derives its base URL from `window.location`:
  `http://localhost:3001` on localhost, `/api` otherwise.
- No repo-checked web-specific environment contract is currently wired into the browser client.

## Essential Commands

**Always run commands from the `web/` directory.**

### Development

```bash
# Start development server
npm run dev
```

### Building

```bash
# Build for production
npm run build
```

### Code Quality

```bash
# Run linter
npm run lint
```

### Testing

- There is currently no checked-in `npm run test` or `npm run test:watch` script in `package.json`.
- Treat build + lint as the available local validation baseline unless test tooling is added.

## Development Workflow

### Component Creation

1. Follow the feature-sliced structure strictly
2. Create entities first if introducing new business concepts (`5-entities/`)
3. Build features on top of entities (`4-features/`)
4. Compose widgets from features (`3-widgets/`)
5. Connect to pages (`2-pages/`)

### State Management

- Use MobX stores for complex state
- Services handle API calls
- Stores are injected via context or direct imports
- Keep state management in `5-entities/` layer

### Styling

- Use MUI theme for consistent styling
- Custom components should extend MUI components
- Use CSS modules for component-specific styles
- Follow Material Design guidelines

## Coding Standards

- **Components**: Use MUI components as the base
- **Custom components**: Follow MUI styling patterns
- **Forms**: Integrate with react-hook-form
- **Folder structure**: Strictly follow feature-sliced design
- **Imports**: Use absolute imports from `src/`

## Common Tasks

### Adding a New Entity

1. Create entity folder in `src/5-entities/{entity-name}/`
2. Add types/interfaces in entity folder
3. Add API service methods
4. Create MobX store for state management
5. Build UI components in `4-features/` or `3-widgets/`

### Example: Creating a Feature Component

```typescript
// src/5-entities/example/types.ts
export interface Example {
  id: string;
  name: string;
}

// src/5-entities/example/store.ts
import {makeAutoObservable} from 'mobx';

export class ExampleStore {
  examples: Example[] = [];

  constructor() {
    makeAutoObservable(this);
  }
}

// src/4-features/example/ExampleFeature.tsx
export const ExampleFeature = () => {
  // Feature component using ExampleStore
};
```

## Testing

- No automated test runner is currently wired through `package.json`.
- If test tooling is introduced, document the exact scripts and commands here instead of generic framework defaults.

## Deployment

- Production uses a Docker image that builds the app and serves the exported `build/` directory from Nginx.
- The app is currently deployed as a static-export SPA, not as a `next start` server.
- Client-side route fallback in the web container is handled by `web/nginx.conf` with `try_files`.
- `next.config.mjs` currently defines `rewrites`, but Next warns during build that rewrites are not applied with
  `output: export`; do not treat those rewrites as production routing behavior.

## Validation Steps

Before submitting changes, run these validation steps:

```bash
# 1. Run linter
npm run lint

# 2. Build for production
npm run build
```

### Quick Validation (for small changes)

```bash
# Fast validation for minor changes
npm run lint
```

## Troubleshooting

### Build Issues

- **Next.js cache issues**: Clear `.next` directory
    - Bash: `rm -rf .next`
    - PowerShell: `Remove-Item -Recurse -Force .next`
- **TypeScript errors**: Check `tsconfig.json` configuration
- **Module resolution**: Verify import paths use `src/` prefix
- **PowerShell script execution blocked**: If `npm run lint` or `npm run build` fails with `node_modules\.bin\next.ps1`
  execution policy errors, run Next via CMD shim:
    - `.\node_modules\.bin\next.cmd lint`
    - `.\node_modules\.bin\next.cmd build`
    - The same issue can affect `npm run dev`

### Runtime Issues

- **API endpoint errors**: Check the browser base-URL selection in `src/6-shared/api/http-client.ts` and the
  gateway/container routing for `/api`
- **HTTP client contract**: The shared browser `HttpClient` in `src/6-shared/api/http-client.ts` uses `http://localhost:3001` on localhost and `/api` otherwise, always sends `Content-Type: application/json`, and normalizes backend error envelopes into `ApiError`.
- **MobX store not updating**: Verify store is properly initialized and observable
- **MUI theme issues**: Review theme configuration in app setup
- **Hydration errors**: Check for server/client mismatch in rendering
- **Direct SPA route refresh fails in production**: Verify the request is reaching the web container Nginx fallback
  (`try_files $uri /index.html =404`), not relying on Next rewrites

### Common Errors

- **"Module not found"**: Check import paths and file structure
- **"Cannot read property"**: Verify MobX store initialization
- **"MUI theme error"**: Ensure theme provider wraps app correctly
- **"Build fails"**: Clear `.next` cache and rebuild
- **"rewrites will not automatically work with output: export"**: Expected with the current `next.config.mjs`;
  production route fallback must come from Nginx, not Next rewrites
- **"Port already in use"**: Change port or kill process using port 3000

### Development Server Issues

- **Hot reload not working**: Restart dev server
- **Slow builds**: Check for large dependencies or inefficient imports
- **Memory issues**: Increase Node.js memory limit if needed
