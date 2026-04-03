# Agent Instructions for CommonEx Web

For non-trivial work and when to search upstream docs, follow root [AGENTS.md](../AGENTS.md) (workflow lifecycle and freshness policy).
Cross-project reference docs: [../docs/domain.md](../docs/domain.md) and [../docs/network-contracts.md](../docs/network-contracts.md).

## Project Overview

CommonEx web is a **Next.js** browser client for the expense-sharing platform. Uses Material UI for components,
MobX for state management, and follows strict feature-sliced architecture. Current runtime: client-rendered SPA with
static export served by Nginx.

## Technology Stack

- **Framework**: Next.js v16 with App Router shell
- **UI Library**: Material UI v7
- **State Management**: MobX
- **Forms**: react-hook-form with MUI integration
- **Routing**: client-side `BrowserRouter`
- **Production**: static export to `build/`, served by Nginx

**Freshness note:** Next.js v16 and Material UI v7 are newer than typical training data. Always verify API usage, configuration, and migration patterns against current upstream docs when implementing.

## Architecture

Feature-Sliced Design with strict folder structure:

- **`2-pages`**: Page components (routes)
- **`3-widgets`**: Composite UI components
- **`4-features`**: Business logic components
- **`5-entities`**: Business entities with stores/services
- **`6-shared`**: Shared utilities and types

## Prerequisites

- **Node.js and npm** (versions: see `package.json` engines or lockfile).

## Essential Commands

**Always run from `web/` directory.**

```bash
npm run dev        # Development server
npm run build      # Production build
npm run lint       # ESLint flat config (includes Next core-web-vitals)
```

No automated test runner is currently wired through `package.json`.

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
- Keep state management in `5-entities/` layer

### Styling

- Use MUI theme for consistent styling
- Custom components should extend MUI components

## Coding Standards

- Use MUI components as the base
- Integrate forms with react-hook-form
- Strictly follow feature-sliced design folder structure
- Use absolute imports from `src/`

## Common Tasks

### Adding a New Entity

1. Create entity folder in `src/5-entities/{entity-name}/`
2. Add types/interfaces
3. Add API service methods
4. Create MobX store for state management
5. Build UI components in `4-features/` or `3-widgets/`

## Deployment

- Production uses a Docker image that builds the app and serves `build/` from Nginx.
- Static-export SPA, not `next start` server.
- Client-side route fallback: `web/nginx.conf` with `try_files`.
- `next.config.mjs` rewrites are not applied with `output: export`; production routing comes from Nginx.

## Validation Steps

```bash
npm run lint
npm run build
```

For troubleshooting and PowerShell workarounds, see [`docs/troubleshooting.md`](docs/troubleshooting.md).
