# AGENTS.md

## Project Overview

This is a React 19 + TypeScript + Vite 6 frontend application for DataSophon (DDH). It uses Ant Design 6, ProComponents, Tailwind CSS 4, and pnpm as the package manager.

## Build/Lint/Test Commands

### Development
```bash
pnpm dev          # Start dev server (port 5180)
pnpm build        # Production build
pnpm preview      # Preview production build
pnpm deploy       # Deploy application
```

### Linting
```bash
pnpm lint         # Run ESLint
```

### Testing
```bash
pnpm test              # Run all tests in watch mode
pnpm test run          # Run all tests once
pnpm test:coverage     # Run tests with coverage report
```

### Running Single Tests
```bash
# Run specific test file
pnpm test -- src/components/Common/CommonTable/index.test.tsx

# Run test by name pattern
pnpm test -- -t "should render ProTable"

# Run tests in specific directory
pnpm test -- src/components/ComponentName
```

## Code Style Guidelines

### Imports
- Use ES6 imports with named exports preferred
- Use `type` keyword for TypeScript type imports: `import { type ModalFuncProps } from "antd"`
- Import lodash functions from `lodash-es`
- Use absolute imports with `@` alias (mapped to `./src`): `import { showMsg } from "@/utils/util"`

### TypeScript
- Strict mode is **disabled** (`strict: false` in tsconfig)
- `noUnusedLocals` and `noUnusedParameters` are enabled
- Use `verbatimModuleSyntax` - prefer type-only imports when appropriate
- Avoid `any` type when possible; use proper typing

### Naming Conventions
- **Components**: PascalCase (`CommonTable`, `FormModal`)
- **Functions/Variables**: camelCase (`invokeGenOptionCol`, `showComfirmModal`)
- **Files**: camelCase for utilities, PascalCase for components
- **Constants**: UPPER_SNAKE_CASE for true constants
- **Boolean variables**: prefix with `is`, `has`, `should` (`isDisabled`, `hasError`)

### React Components
- Use functional components with hooks
- Export components as `export default ComponentName`
- Use `React.createElement` for dynamic element creation in tests
- Prefer `useCallback` and `useMemo` for performance optimization

### Error Handling
- Use `showMsgAfferRequest` for API response messages
- Use `showComfirmModal` for confirmation dialogs
- Handle async operations with try/catch when necessary

### Testing
- Test files: `*.test.tsx` or `*.test.ts` alongside source files
- Use Vitest with `@testing-library/react`
- Mock dependencies at top level with `vi.mock()`
- Use `vi.fn()` for function mocks
- Clean up with `afterEach(() => cleanup())`
- Globals enabled: `describe`, `it`, `expect`, `vi` available without imports
- Setup file: `src/config/setupTests.ts` runs before all tests
- Coverage reports: HTML, JSON, and text formats in `coverage/` directory

### Styling
- Use Tailwind CSS classes for styling
- Use Ant Design components and theme tokens
- CSS variables enabled via ConfigProvider

### Code Formatting
- No explicit Prettier config - follow existing code style
- Use semicolons at end of statements
- Use double quotes for strings
- 4-space indentation in most files
- Trailing commas in multiline structures

## Project Structure

```
src/
├── api/           # API services and interceptors
├── components/    # Reusable components
├── config/        # Configuration files
├── constants/     # Constants and enums
├── context/       # React contexts
├── hooks/         # Custom hooks
├── pages/         # Page components
├── routes/        # Routing configuration
├── styles/        # Global styles
├── utils/         # Utility functions
└── App.tsx        # Root component
```

## Important Notes

- Package manager: `pnpm@10.11.0`
- Node.js target: ES2020
- JSX runtime: react-jsx (automatic)
- Test framework: **Vitest 4.1.2**
- Test environment: **jsdom**
- Coverage provider: **v8**
- Test libraries: `@testing-library/react` 16.3.2, `@testing-library/dom` 10.4.1, `@testing-library/jest-dom` 6.9.1

## Common Patterns

### Async Component Loading
```typescript
const showModal = asyncHook(() => import('./Modal/api'));
const api = await showModal();
api.default({ config });
```

### API Error Handling
```typescript
const res = await apiCall();
showMsgAfferRequest(res);
```

### Table Column Generation
```typescript
const columns = invokeGenOptionCol(list, config);
// Use as ProTable column render function
```
