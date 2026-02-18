# Agent Instructions for CommonEx Web

## Project Overview

CommonEx web is a **Next.js** application providing the web interface for the expense sharing platform. It uses Material
UI for components, MobX for state management, and follows a strict feature-sliced architecture.

## Technology Stack

- **Framework**: Next.js v14 with App Router
- **UI Library**: Material UI v5
- **State Management**: MobX
- **Forms**: react-hook-form with MUI integration
- **Language**: TypeScript

## Architecture

Feature-driven with strict folder structure (Feature-Sliced Design):

- **`2-pages`**: Page components (routes)
- **`3-widgets`**: Composite UI components
- **`4-features`**: Business logic components
- **`5-entities`**: Business entities with stores/services
- **`6-shared`**: Shared utilities and types

### Key File Locations

- **App entry**: `src/app/`
- **Pages**: `src/2-pages/`
- **Widgets**: `src/3-widgets/`
- **Features**: `src/4-features/`
- **Entities**: `src/5-entities/`
- **Shared utilities**: `src/6-shared/`
- **Configuration**: `next.config.mjs`, `tsconfig.json`

## Prerequisites

- **Node.js 20+** (check with `node --version`)
- **npm 10+** (comes with Node.js)
- **Git** for version control

## Environment Setup

### Installation

```bash
cd web
npm install
```

### Environment Variables

- Check for `.env.local` or `.env` file
- Configure API endpoint URLs if needed
- See project documentation for required variables

## Essential Commands

**Always run commands from the `web/` directory.**

### Development

```bash
# Start development server
npm run dev

# Start production server (after build)
npm run start
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

```bash
# Run tests (if configured)
npm run test

# Run tests in watch mode (if configured)
npm run test:watch
```

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

- **Unit tests**: Jest for utility functions and services
- **Component tests**: React Testing Library
- Test files should be co-located with source files

## Deployment

- Static export or server-side rendering
- Docker image for deployment
- Nginx serving static files
- Build output in `.next/` directory

## Validation Steps

Before submitting changes, run these validation steps:

```bash
# 1. Run linter
npm run lint

# 2. Build for production
npm run build

# 3. Run tests (if configured)
npm run test
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

### Runtime Issues

- **API endpoint errors**: Check API endpoint URLs in environment variables
- **MobX store not updating**: Verify store is properly initialized and observable
- **MUI theme issues**: Review theme configuration in app setup
- **Hydration errors**: Check for server/client mismatch in rendering

### Common Errors

- **"Module not found"**: Check import paths and file structure
- **"Cannot read property"**: Verify MobX store initialization
- **"MUI theme error"**: Ensure theme provider wraps app correctly
- **"Build fails"**: Clear `.next` cache and rebuild
- **"Port already in use"**: Change port or kill process using port 3000

### Development Server Issues

- **Hot reload not working**: Restart dev server
- **Slow builds**: Check for large dependencies or inefficient imports
- **Memory issues**: Increase Node.js memory limit if needed
