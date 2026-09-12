# Backend Test Quality Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add application-level HTTP and gRPC tests, fix the gRPC transport the new tests expose as broken, remove dead mocks and test-only code from production, guard against schema drift, and make coverage a CI signal for the NestJS backend.

**Architecture:** A `src/test-support/` module and an `src/app.factory.ts` bootstrap factory are introduced first so every later task builds on them. E2E tests live in `test/` with their own jest config and boot `AppModule` through the same factory production uses. Use-case tests keep their table-driven shape but stop stubbing pure collaborators. Repository SQL snapshots are kept as the TypeORM upgrade guard and gain behavioural assertions beside them.

**Tech Stack:** NestJS 11, Fastify 5, Jest 30 with ts-jest, TypeORM 0.3, PostgreSQL, `@grpc/grpc-js` + `@grpc/proto-loader`, class-validator, zod.

## Global Constraints

- Every command runs from `backend/`. There is no root `package.json`.
- Spec: `docs/superpowers/specs/2026-09-12-backend-test-quality-design.md`.
- A PostgreSQL that matches `example.env` must be running before any task. One-time setup:

  ```bash
  cp example.env .env
  docker compose -f docker-compose.test.yml up --wait db
  npm run db:migrate
  ```

  If `docker` is not installed, use Homebrew instead:

  ```bash
  brew install postgresql@17 && brew services start postgresql@17
  createuser -s postgres || true
  psql -d postgres -c "ALTER USER postgres PASSWORD 'postgres';"
  createdb -U postgres database || true
  cp example.env .env
  npm run db:migrate
  ```

  `POSTGRES_SCHEMA` must stay `public`; snapshots contain unqualified table names.
- Full verification for every task, in this order:

  ```bash
  npm run typecheck && npm run lint:check && npm run format:check && npm test && npm run test:e2e
  ```

  `npm run test:e2e` exists from Task 2 onward. Run `npm run format` before `format:check` after writing files.
- Never run jest with `-u` except in the step that creates snapshots for a brand-new spec. Never modify an existing `.snap` entry.
- Test names are English, start with a verb in third person, and describe the outcome ("returns 304 when the ETag matches").
- Commit messages follow the existing style: `test(backend): …`, `refactor(backend): …`, `fix(backend): …`, `ci(backend): …`, `docs(backend): …`. Do not add any `Co-Authored-By` trailer.
- Do not stage `.DS_Store`, `.github/.DS_Store`, `infra/.DS_Store`, or `android/.idea/yatool.xml`.
- New dependencies are pinned to exact versions, no `^` or `~`.
- Line width is 120 for Prettier; ESLint `max-len` is 160. Imports are sorted by Prettier; run `npm run format`.
- When a test fails because the real system disagrees with an assertion, fix the system or the fixture data, not the assertion, unless the assertion contradicts the spec. Report every such deviation in the task summary.

---

### Task 1: Test support module, remove `flush()` from production, jest hygiene

**Files:**
- Create: `src/test-support/db.ts`
- Move: `src/usecases/__tests__/test-helpers.ts` → `src/test-support/relational-state.ts`
- Move: `src/usecases/__tests__/utils-apply-changes-to-state.ts` → `src/test-support/apply-state-changes.ts`
- Modify: `src/frameworks/relational-data-service/postgres/relational-data-service.ts` (remove `flush`)
- Modify: `src/domain/abstracts/relational-data-service/relational-data-service.ts` (remove `flush`)
- Modify: `src/domain/abstracts/relational-data-service/types.ts` (remove `flush`)
- Modify: `src/frameworks/relational-data-service/postgres/repositories/base.repository.ts` (remove schema regex)
- Modify: `package.json` (jest config, scripts), `tsconfig.json` (paths, include), `.prettierrc` (import order), `eslint.config.js` (test globs)
- Modify: all 24 test files that call `relationalDataService.flush()` and all 19 files importing `test-helpers`

**Interfaces:**
- Produces: `truncateAllTables(dataSource: DataSource): Promise<void>` and `createTestRelationalDataService({showQueryDetails?}): RelationalDataService` in `#test-support/db`; `TestCase`, `RelationalState`, `RelationalStateChanges`, `prepareInitRelationalState`, `validateRelationalStateChanges`, `validateFinalRelationalState`, `useFakeTimers` in `#test-support/relational-state`. Every later task imports from these paths.

- [ ] **Step 1: Create `src/test-support/db.ts`**

```ts
import {DataSource} from 'typeorm';

import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

export const createTestRelationalDataService = ({
  showQueryDetails = false,
}: {showQueryDetails?: boolean} = {}): RelationalDataService =>
  new RelationalDataService({dbConfig: appDbConfig, showQueryDetails});

/** Empties every mapped table. Test-only; production code never truncates. */
export const truncateAllTables = async (dataSource: DataSource): Promise<void> => {
  const tableNames = dataSource.entityMetadatas.map((metadata) => metadata.tableName).join(', ');

  await dataSource.query(`TRUNCATE ${tableNames} RESTART IDENTITY CASCADE;`);
};
```

- [ ] **Step 2: Move the shared helpers**

```bash
git mv src/usecases/__tests__/test-helpers.ts src/test-support/relational-state.ts
git mv src/usecases/__tests__/utils-apply-changes-to-state.ts src/test-support/apply-state-changes.ts
```

In `src/test-support/relational-state.ts` change the relative import to `import {type StateChanges, applyChanges} from './apply-state-changes';` and change the Russian error text to:

```ts
throw new Error(`Final relational state mismatch for "${key}"\n\n${(error as Error).message}`, {cause: error});
```

- [ ] **Step 3: Wire the `#test-support` alias**

`tsconfig.json`: add `"#test-support/*": ["./src/test-support/*"]` to `paths` and change `"include"` to `["src", "scripts", "migrations", "test"]`.

`package.json` jest block: add `"#test-support/(.*)": "<rootDir>/test-support/$1"` to `moduleNameMapper`.

`.prettierrc`: add `"^#test-support/(.*)$"` to `importOrder` after `"^#api/(.*)$"`.

`eslint.config.js`: the test override `files` array becomes `['**/*.test.ts', '**/*.spec.ts', '**/__tests__/**/*.ts', 'test/**/*.ts', 'src/test-support/**/*.ts']`.

`package.json` scripts: `lint`, `lint:check`, `format`, `format:check` globs become `"{src,scripts,migrations,test}/**/*.ts"`.

- [ ] **Step 4: Rewrite the test imports and the `flush()` calls**

```bash
grep -rl "__tests__/test-helpers" src | xargs sed -i '' \
  -e "s|'\.\./\.\./\.\./__tests__/test-helpers'|'#test-support/relational-state'|" \
  -e "s|'\.\./\.\./__tests__/test-helpers'|'#test-support/relational-state'|" \
  -e "s|'#usecases/__tests__/test-helpers'|'#test-support/relational-state'|"
grep -rl "relationalDataService.flush()" src | xargs sed -i '' \
  -e "s|await relationalDataService.flush();|await truncateAllTables(relationalDataService.dataSource);|" \
  -e "s|let relationalDataService: RelationalDataServiceAbstract;|let relationalDataService: RelationalDataService;|"
```

Then in each of those files add `import {truncateAllTables} from '#test-support/db';` and delete the `RelationalDataServiceAbstract` import where `npm run typecheck` reports it unused. `src/usecases/health/__tests__/health-check.usecase.test.ts` and `src/usecases/users/v3/__tests__/*.usecase.test.ts` do not call `flush`; leave them.

- [ ] **Step 5: Remove `flush` from production**

Delete the `flush()` method from `RelationalDataService`, the `abstract flush` line from `RelationalDataServiceAbstract`, and `flush: () => unknown;` from `IRelationalDataService`.

Replace the body of `BaseRepository.getQueryDetails` with:

```ts
  public getQueryDetails<T extends object>(queryBuilder: QueryBuilder<T>): IQueryDetails {
    if (!this.showQueryDetails) {
      return {queryString: undefined, queryParameters: undefined};
    }

    return {
      queryString: queryBuilder.getQuery(),
      queryParameters: queryBuilder.getParameters(),
    };
  }
```

and delete the `removeSchemaName` property and both `FIXME` comments.

- [ ] **Step 6: Jest hygiene in `package.json`**

Replace the jest block:

```json
  "jest": {
    "moduleFileExtensions": ["js", "json", "ts"],
    "rootDir": "src",
    "testRegex": "\\.(test|spec)\\.ts$",
    "transform": {"^.+\\.(t|j)s$": "ts-jest"},
    "collectCoverageFrom": [
      "**/*.ts",
      "!**/__tests__/**",
      "!**/*.spec.ts",
      "!**/*.test.ts",
      "!**/index.ts",
      "!main.ts",
      "!otel.ts",
      "!test-support/**"
    ],
    "coverageReporters": ["text-summary", "lcov"],
    "restoreMocks": true,
    "clearMocks": true,
    "testTimeout": 20000,
    "coverageDirectory": "../coverage",
    "testEnvironment": "node",
    "moduleNameMapper": {
      "#api/(.*)": "<rootDir>/api/$1",
      "#domain/(.*)": "<rootDir>/domain/$1",
      "#usecases/(.*)": "<rootDir>/usecases/$1",
      "#frameworks/(.*)": "<rootDir>/frameworks/$1",
      "#packages/(.*)": "<rootDir>/packages/$1",
      "#test-support/(.*)": "<rootDir>/test-support/$1"
    }
  }
```

Scripts: `"test": "jest --runInBand --ci"`, `"test:watch": "jest --watch --runInBand"`, `"test:cov": "jest --coverage --runInBand --ci"`. Leave `test:e2e` for Task 2.

- [ ] **Step 7: Verify**

```bash
npm run format && npm run typecheck && npm run lint:check && npm run format:check && npm test
```

Expected: all 29 suites pass, `Snapshots: 39 passed, 39 total`, zero written or obsolete.

- [ ] **Step 8: Commit**

```bash
git add package.json tsconfig.json .prettierrc eslint.config.js src
git commit -m "refactor(backend): move test helpers to src/test-support and drop flush from production"
```

---

### Task 2: Application factory, e2e harness, smoke tests

**Files:**
- Create: `src/api/validation-pipe.ts`, `src/app.factory.ts`
- Modify: `src/main.ts`, `src/frameworks/frameworks.layer.ts` (export `initOrUpdateCurrencies`)
- Create: `test/jest-e2e.json`, `test/support/test-app.ts`, `test/app.e2e-spec.ts`
- Modify: `package.json` (`test:e2e` script), `.github/workflows/main.yml` (`backend-checks` job), `AGENTS.md`

**Interfaces:**
- Produces: `createValidationPipe(): ValidationPipe`; `configureHttpApp(app: NestFastifyApplication): void`; `createGrpcOptions({url, protoPath}): GrpcOptions`; `initOrUpdateCurrencies(rDataService)` exported; `createTestApp({configure?, grpc?}): Promise<TestApp>` with `TestApp = {app, rDataService, grpcUrl, reset, close}` and `PROTO_PATH` in `test/support/test-app.ts`.

- [ ] **Step 1: Create `src/api/validation-pipe.ts`**

```ts
import {ValidationPipe} from '@nestjs/common';

export const createValidationPipe = (): ValidationPipe =>
  new ValidationPipe({
    whitelist: true,
    forbidNonWhitelisted: true,
    transform: true,
    transformOptions: {
      enableImplicitConversion: true,
    },
  });
```

- [ ] **Step 2: Create `src/app.factory.ts`**

```ts
import {HttpAdapterHost} from '@nestjs/core';
import {GrpcOptions, Transport} from '@nestjs/microservices';
import {NestFastifyApplication} from '@nestjs/platform-fastify';
import {DocumentBuilder, SwaggerModule} from '@nestjs/swagger';

import {BusinessErrorFilter} from '#api/http/filters/business-error.filter';
import {ValidationExceptionFilter} from '#api/http/filters/validation-exception.filter';
import {createValidationPipe} from '#api/validation-pipe';

export const GRPC_PACKAGE = 'user';

export const configureHttpApp = (app: NestFastifyApplication): void => {
  const {httpAdapter} = app.get(HttpAdapterHost);

  app.useGlobalPipes(createValidationPipe());
  app.useGlobalFilters(new ValidationExceptionFilter(httpAdapter), new BusinessErrorFilter(httpAdapter));

  const config = new DocumentBuilder()
    .setTitle('Expenses Swagger')
    .setVersion('0.0.1')
    .addServer('/api', 'API Server')
    .addApiKey(
      {
        type: 'apiKey',
        name: 'x-devtools-secret',
        in: 'header',
        description: 'Devtools secret for accessing devtools endpoints',
      },
      'devtools-secret',
    )
    .build();
  const document = SwaggerModule.createDocument(app, config);

  SwaggerModule.setup('swagger/api', app, document);
  app.enableCors({origin: '*'});
};

export const createGrpcOptions = ({url, protoPath}: {url: string; protoPath: string}): GrpcOptions => ({
  transport: Transport.GRPC,
  options: {
    package: GRPC_PACKAGE,
    protoPath,
    url,
  },
});
```

- [ ] **Step 3: Rewrite `src/main.ts` to use the factory**

```ts
// The OpenTelemetry SDK starts on import and must patch Fastify and Nest before they are loaded.
// Side-effect imports are not reordered by the import sorter, so this stays first.
import './otel';

import {join} from 'path';

import {NestFactory} from '@nestjs/core';
import {FastifyAdapter, NestFastifyApplication} from '@nestjs/platform-fastify';

import {fastifyHttpMetricsPlugin} from '#frameworks/observability/fastify-http-metrics.plugin';

import {configureHttpApp, createGrpcOptions} from './app.factory';
import {AppModule} from './app.module';
import {fastifyOtelInstrumentation} from './otel';

async function bootstrap(): Promise<void> {
  const fastifyAdapter = new FastifyAdapter({http2: true});
  const fastifyInstance = fastifyAdapter.getInstance();

  await fastifyInstance.register(fastifyHttpMetricsPlugin, {
    applicationRoot: '/',
  });
  await fastifyInstance.register(fastifyOtelInstrumentation.plugin());

  const app = await NestFactory.create<NestFastifyApplication>(AppModule, fastifyAdapter);

  configureHttpApp(app);
  app.connectMicroservice(createGrpcOptions({url: '0.0.0.0:5000', protoPath: join(__dirname, '../expenses.proto')}));

  await app.startAllMicroservices();
  await app.listen(3001, '0.0.0.0');
}

bootstrap().catch((error: unknown) => {
  console.error('Failed to bootstrap the application', error);
  process.exit(1);
});
```

In `src/frameworks/frameworks.layer.ts` change `const initOrUpdateCurrencies = async (...)` to `export const initOrUpdateCurrencies = async (...)` and move it above `providers` so it is declared before use.

- [ ] **Step 4: Create `test/jest-e2e.json`**

```json
{
  "moduleFileExtensions": ["js", "json", "ts"],
  "rootDir": "..",
  "roots": ["<rootDir>/test"],
  "testRegex": "\\.e2e-spec\\.ts$",
  "transform": {"^.+\\.ts$": "ts-jest"},
  "testEnvironment": "node",
  "testTimeout": 30000,
  "restoreMocks": true,
  "clearMocks": true,
  "moduleNameMapper": {
    "#api/(.*)": "<rootDir>/src/api/$1",
    "#domain/(.*)": "<rootDir>/src/domain/$1",
    "#usecases/(.*)": "<rootDir>/src/usecases/$1",
    "#frameworks/(.*)": "<rootDir>/src/frameworks/$1",
    "#packages/(.*)": "<rootDir>/src/packages/$1",
    "#test-support/(.*)": "<rootDir>/src/test-support/$1"
  }
}
```

`package.json` script: `"test:e2e": "jest --config ./test/jest-e2e.json --runInBand --ci"`.

- [ ] **Step 5: Create `test/support/test-app.ts`**

```ts
import {createServer} from 'net';
import {join} from 'path';

import {FastifyAdapter, NestFastifyApplication} from '@nestjs/platform-fastify';
import {Test, TestingModuleBuilder} from '@nestjs/testing';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';

import {initOrUpdateCurrencies} from '#frameworks/frameworks.layer';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';

import {configureHttpApp, createGrpcOptions} from '../../src/app.factory';
import {AppModule} from '../../src/app.module';

export const PROTO_PATH = join(__dirname, '../../src/expenses.proto');

export interface TestApp {
  app: NestFastifyApplication;
  rDataService: RelationalDataService;
  grpcUrl: string | null;
  /** Truncates every table and re-seeds the supported currencies, like a fresh boot. */
  reset: () => Promise<void>;
  close: () => Promise<void>;
}

export const getFreePort = (): Promise<number> =>
  new Promise((resolve, reject) => {
    const server = createServer();

    server.unref();
    server.on('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();

      if (address == null || typeof address === 'string') {
        reject(new Error('Could not allocate a free port'));
        return;
      }

      server.close(() => {
        resolve(address.port);
      });
    });
  });

export const createTestApp = async ({
  configure = (builder): TestingModuleBuilder => builder,
  grpc = false,
}: {
  configure?: (builder: TestingModuleBuilder) => TestingModuleBuilder;
  grpc?: boolean;
} = {}): Promise<TestApp> => {
  const moduleRef = await configure(Test.createTestingModule({imports: [AppModule]})).compile();
  const app = moduleRef.createNestApplication<NestFastifyApplication>(new FastifyAdapter());

  configureHttpApp(app);

  let grpcUrl: string | null = null;

  if (grpc) {
    grpcUrl = `127.0.0.1:${await getFreePort()}`;
    app.connectMicroservice(createGrpcOptions({url: grpcUrl, protoPath: PROTO_PATH}));
  }

  await app.init();
  await app.getHttpAdapter().getInstance().ready();

  if (grpc) {
    await app.startAllMicroservices();
  }

  const rDataService = app.get<RelationalDataService>(RelationalDataServiceAbstract);

  return {
    app,
    rDataService,
    grpcUrl,
    reset: async (): Promise<void> => {
      await truncateAllTables(rDataService.dataSource);
      await initOrUpdateCurrencies(rDataService);
    },
    close: (): Promise<void> => app.close(),
  };
};
```

- [ ] **Step 6: Write `test/app.e2e-spec.ts`**

```ts
import {CURRENCIES_LIST} from '../src/constants';
import {UserV3Controller} from '#api/http/user/user-v3.controller';

import {TestApp, createTestApp} from './support/test-app';

describe('application bootstrap', () => {
  let testApp: TestApp;

  beforeAll(async () => {
    testApp = await createTestApp();
  });

  afterAll(async () => {
    await testApp.close();
  });

  it('resolves every controller from the dependency graph', () => {
    expect(testApp.app.get(UserV3Controller)).toBeInstanceOf(UserV3Controller);
  });

  it('reports the database as up on GET /health', async () => {
    const response = await testApp.app.inject({method: 'GET', url: '/health'});

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({
      status: 'ok',
      info: {database: {status: 'up'}},
      error: {},
      details: {database: {status: 'up'}},
    });
  });

  it('serves the generated OpenAPI document', async () => {
    const response = await testApp.app.inject({method: 'GET', url: '/swagger/api-json'});

    expect(response.statusCode).toBe(200);
    expect(response.json<{paths: Record<string, unknown>}>().paths).toHaveProperty('/user/event');
  });

  it('seeds every supported currency on startup', async () => {
    await testApp.reset();

    const [currencies] = await testApp.rDataService.currency.findAllSupported({orderBy: 'code'});

    expect(currencies.map((currency) => currency.code)).toEqual(CURRENCIES_LIST.map(({code}) => code).sort());
  });
});
```

- [ ] **Step 7: Run and verify**

```bash
npm run format && npm run test:e2e
```

Expected: `Tests: 4 passed`. Then the full verification command from Global Constraints.

- [ ] **Step 8: CI and docs**

In `.github/workflows/main.yml`, job `backend-checks`, add after the `Test` step:

```yaml
      - name: E2E test
        run: npm run test:e2e
```

In `AGENTS.md` (backend) section `## Testing`, replace the two-line command block at the top with:

````markdown
```bash
npm run test        # unit and DB-backed tests under src/
npm run test:e2e    # application tests under test/ (HTTP via Fastify inject, gRPC via a real client)
npm run test:cov
```

Application tests boot `AppModule` through `src/app.factory.ts`, the same code `src/main.ts` uses, so pipes, filters and
Swagger are configured identically in tests and production. Shared test helpers live in `src/test-support/`.
````

- [ ] **Step 9: Commit**

```bash
git add src test package.json .github/workflows/main.yml AGENTS.md
git commit -m "test(backend): add application factory and e2e harness with bootstrap smoke tests"
```

---

### Task 3: HTTP e2e coverage for user, validation, idempotency and devtools routes

**Files:**
- Create: `test/support/fixtures.ts`
- Create: `test/http/user-v1.e2e-spec.ts`, `test/http/user-v2.e2e-spec.ts`, `test/http/user-v3.e2e-spec.ts`, `test/http/validation.e2e-spec.ts`, `test/http/idempotency.e2e-spec.ts`, `test/http/devtools.e2e-spec.ts`

**Interfaces:**
- Consumes: `createTestApp`, `TestApp` from Task 2.
- Produces: `createEvent(app, input)`, `findCurrencyIdByCode(rDataService, code)`, `insertTodayRate(rDataService, rate)` in `test/support/fixtures.ts`, reused by Task 4.

- [ ] **Step 1: Create `test/support/fixtures.ts`**

```ts
import {NestFastifyApplication} from '@nestjs/platform-fastify';

import {getCurrentDateWithoutTimeUTC} from '#packages/date-utils';

import {CurrencyCode} from '#domain/entities/currency.entity';
import {CurrencyRateValueObject} from '#domain/value-objects/currency-rate.value-object';

import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {CreateEventResponseDto} from '#api/http/user/dto/create-event.dto';

export const findCurrencyIdByCode = async (rDataService: RelationalDataService, code: CurrencyCode): Promise<string> => {
  const [currencies] = await rDataService.currency.findAll({codes: [code]});
  const currency = currencies[0];

  if (currency === undefined) {
    throw new Error(`Currency ${code} is not seeded`);
  }

  return currency.id;
};

export const insertTodayRate = async (rDataService: RelationalDataService, rate: Record<string, number>): Promise<void> => {
  await rDataService.currencyRate.insert(new CurrencyRateValueObject({date: getCurrentDateWithoutTimeUTC(), rate}).value);
};

export const createEvent = async (
  app: NestFastifyApplication,
  input: {name?: string; currencyId: string; pinCode?: string; users?: {name: string}[]},
): Promise<CreateEventResponseDto> => {
  const response = await app.inject({
    method: 'POST',
    url: '/user/event',
    payload: {name: 'Trip', pinCode: '1234', users: [{name: 'Alice'}, {name: 'Bob'}], ...input},
  });

  if (response.statusCode !== 201) {
    throw new Error(`createEvent failed: ${response.statusCode} ${response.body}`);
  }

  return response.json<CreateEventResponseDto>();
};
```

- [ ] **Step 2: Write `test/http/user-v1.e2e-spec.ts`**

```ts
import {CurrencyCode} from '#domain/entities/currency.entity';
import {ExpenseType} from '#domain/entities/expense.entity';

import {createEvent, findCurrencyIdByCode} from '../support/fixtures';
import {TestApp, createTestApp} from '../support/test-app';

describe('HTTP /user (v1)', () => {
  let testApp: TestApp;
  let usdId: string;

  beforeAll(async () => {
    testApp = await createTestApp();
  });

  afterAll(async () => {
    await testApp.close();
  });

  beforeEach(async () => {
    await testApp.reset();
    usdId = await findCurrencyIdByCode(testApp.rDataService, CurrencyCode.USD);
  });

  it('creates an event with its users', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    expect(event).toMatchObject({name: 'Trip', currencyId: usdId, pinCode: '1234', deletedAt: null});
    expect(event.users.map((user) => user.name)).toEqual(['Alice', 'Bob']);
    expect(event.users.every((user) => user.eventId === event.id)).toBe(true);
  });

  it('rejects an event with an unknown currency with 404 B4004', async () => {
    const response = await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      payload: {name: 'Trip', currencyId: 'missing', pinCode: '1234', users: []},
    });

    expect(response.statusCode).toBe(404);
    expect(response.json()).toEqual({statusCode: 404, code: 'B4004', message: 'Currency not found'});
  });

  it('returns event info for the correct pin code', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const response = await testApp.app.inject({method: 'GET', url: `/user/event/${event.id}?pinCode=1234`});

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({id: event.id, name: 'Trip'});
  });

  it('rejects event info for a wrong pin code with 403 B4003', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const response = await testApp.app.inject({method: 'GET', url: `/user/event/${event.id}?pinCode=9999`});

    expect(response.statusCode).toBe(403);
    expect(response.json()).toEqual({statusCode: 403, code: 'B4003', message: 'Invalid pin code'});
  });

  it('returns 404 B4001 for an unknown event', async () => {
    const response = await testApp.app.inject({method: 'GET', url: '/user/event/missing?pinCode=1234'});

    expect(response.statusCode).toBe(404);
    expect(response.json()).toMatchObject({code: 'B4001'});
  });

  it('deletes an event and then reports it as gone with 410 B4002', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const deleteResponse = await testApp.app.inject({
      method: 'DELETE',
      url: `/user/event/${event.id}`,
      payload: {pinCode: '1234'},
    });
    const infoResponse = await testApp.app.inject({method: 'GET', url: `/user/event/${event.id}?pinCode=1234`});

    expect(deleteResponse.statusCode).toBe(200);
    expect(deleteResponse.json()).toMatchObject({id: event.id});
    expect(infoResponse.statusCode).toBe(410);
    expect(infoResponse.json()).toMatchObject({code: 'B4002'});
  });

  it('adds users to an event', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId, users: []});

    const response = await testApp.app.inject({
      method: 'POST',
      url: `/user/event/${event.id}/users`,
      payload: {pinCode: '1234', users: [{name: 'Carol'}]},
    });

    expect(response.statusCode).toBe(201);
    expect(response.json()).toEqual([expect.objectContaining({name: 'Carol', eventId: event.id})]);
  });

  it('creates an expense in the event currency and lists it', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const [alice, bob] = event.users;

    const createResponse = await testApp.app.inject({
      method: 'POST',
      url: `/user/event/${event.id}/expense`,
      payload: {
        description: 'Lunch',
        userWhoPaidId: alice?.id,
        currencyId: usdId,
        expenseType: ExpenseType.Expense,
        splitInformation: [
          {userId: alice?.id, amount: 40},
          {userId: bob?.id, amount: 60},
        ],
      },
    });
    const listResponse = await testApp.app.inject({method: 'GET', url: `/user/event/${event.id}/expenses`});

    expect(createResponse.statusCode).toBe(201);
    expect(createResponse.json()).toMatchObject({
      eventId: event.id,
      splitInformation: [
        {userId: alice?.id, amount: 40, exchangedAmount: 40},
        {userId: bob?.id, amount: 60, exchangedAmount: 60},
      ],
    });
    expect(listResponse.statusCode).toBe(200);
    expect(listResponse.json()).toHaveLength(1);
  });
});
```

- [ ] **Step 3: Write `test/http/user-v2.e2e-spec.ts`**

```ts
import {CurrencyCode} from '#domain/entities/currency.entity';
import {ExpenseType} from '#domain/entities/expense.entity';

import {createEvent, findCurrencyIdByCode, insertTodayRate} from '../support/fixtures';
import {TestApp, createTestApp} from '../support/test-app';

describe('HTTP /v2/user', () => {
  let testApp: TestApp;
  let usdId: string;
  let eurId: string;

  beforeAll(async () => {
    testApp = await createTestApp();
  });

  afterAll(async () => {
    await testApp.close();
  });

  beforeEach(async () => {
    await testApp.reset();
    usdId = await findCurrencyIdByCode(testApp.rDataService, CurrencyCode.USD);
    eurId = await findCurrencyIdByCode(testApp.rDataService, CurrencyCode.EUR);
  });

  it('returns event info by pin code via POST', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const response = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}`,
      payload: {pinCode: '1234'},
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({id: event.id});
  });

  it('issues a share token and accepts it instead of the pin code', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const tokenResponse = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}/share-token`,
      payload: {pinCode: '1234'},
    });
    const {token} = tokenResponse.json<{token: string; expiresAt: string}>();
    const infoResponse = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}`,
      payload: {token},
    });

    expect(tokenResponse.statusCode).toBe(201);
    expect(token).toMatch(/^[0-9a-f]{64}$/);
    expect(infoResponse.statusCode).toBe(200);
    expect(infoResponse.json()).toMatchObject({id: event.id});
  });

  it('rejects an unknown share token with 401 B4008', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const response = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}`,
      payload: {token: 'not-a-token'},
    });

    expect(response.statusCode).toBe(401);
    expect(response.json()).toEqual({statusCode: 401, code: 'B4008', message: 'Invalid token'});
  });

  it('converts an expense in another currency using the rate of the day', async () => {
    await insertTodayRate(testApp.rDataService, {USD: 1, EUR: 0.85});
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const [alice, bob] = event.users;

    const response = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}/expense`,
      payload: {
        pinCode: '1234',
        description: 'Dinner',
        userWhoPaidId: alice?.id,
        currencyId: eurId,
        expenseType: ExpenseType.Expense,
        splitInformation: [
          {userId: alice?.id, amount: 40},
          {userId: bob?.id, amount: 60},
        ],
      },
    });

    expect(response.statusCode).toBe(201);
    expect(response.json()).toMatchObject({
      isCustomRate: false,
      splitInformation: [
        {userId: alice?.id, amount: 40, exchangedAmount: 47.06},
        {userId: bob?.id, amount: 60, exchangedAmount: 70.59},
      ],
    });
  });

  it('rejects a foreign-currency expense with 404 B4005 when no rate exists for today', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const [alice] = event.users;

    const response = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}/expense`,
      payload: {
        pinCode: '1234',
        description: 'Dinner',
        userWhoPaidId: alice?.id,
        currencyId: eurId,
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: alice?.id, amount: 40}],
      },
    });

    expect(response.statusCode).toBe(404);
    expect(response.json()).toMatchObject({code: 'B4005'});
  });

  it('rejects a partially custom rate with 400 B4010', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const [alice, bob] = event.users;

    const response = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}/expense`,
      payload: {
        pinCode: '1234',
        description: 'Dinner',
        userWhoPaidId: alice?.id,
        currencyId: eurId,
        expenseType: ExpenseType.Expense,
        splitInformation: [
          {userId: alice?.id, amount: 40, exchangedAmount: 50},
          {userId: bob?.id, amount: 60},
        ],
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({code: 'B4010'});
  });

  it('lists expenses only with the correct pin code', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const ok = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}/expenses`,
      payload: {pinCode: '1234'},
    });
    const forbidden = await testApp.app.inject({
      method: 'POST',
      url: `/v2/user/event/${event.id}/expenses`,
      payload: {pinCode: '0000'},
    });

    expect(ok.statusCode).toBe(201);
    expect(ok.json()).toEqual([]);
    expect(forbidden.statusCode).toBe(403);
  });
});
```

Note: `POST /v2/user/event/:id/expenses` has no `@HttpCode`, so Nest returns 201 for a POST. Assert what the server does today; do not change the route.

- [ ] **Step 4: Write `test/http/user-v3.e2e-spec.ts`**

```ts
import {insertTodayRate} from '../support/fixtures';
import {TestApp, createTestApp} from '../support/test-app';

describe('HTTP /v3/user/currencies/all', () => {
  let testApp: TestApp;

  beforeAll(async () => {
    testApp = await createTestApp();
  });

  afterAll(async () => {
    await testApp.close();
  });

  beforeEach(async () => {
    await testApp.reset();
  });

  it('returns currencies with rates, a weak ETag and no-cache headers', async () => {
    await insertTodayRate(testApp.rDataService, {USD: 1, EUR: 0.92, XXX: 5});

    const response = await testApp.app.inject({method: 'GET', url: '/v3/user/currencies/all'});
    const body = response.json<{currencies: {code: string}[]; exchangeRate: Record<string, number>}>();

    expect(response.statusCode).toBe(200);
    expect(response.headers['cache-control']).toBe('private, no-cache');
    expect(response.headers.etag).toMatch(/^W\/"currencies-v3-[0-9a-f]{40}"$/);
    expect(body.currencies.map((currency) => currency.code)).toEqual(['AED', 'EUR', 'JPY', 'RUB', 'TRY', 'USD']);
    expect(body.exchangeRate).toEqual({USD: 1, EUR: 0.92});
  });

  it('returns 304 with the same ETag when If-None-Match matches', async () => {
    await insertTodayRate(testApp.rDataService, {USD: 1, EUR: 0.92});
    const first = await testApp.app.inject({method: 'GET', url: '/v3/user/currencies/all'});

    const second = await testApp.app.inject({
      method: 'GET',
      url: '/v3/user/currencies/all',
      headers: {'if-none-match': first.headers.etag as string},
    });

    expect(second.statusCode).toBe(304);
    expect(second.headers.etag).toBe(first.headers.etag);
    expect(second.body).toBe('');
  });

  it('returns 404 B4005 when there is no rate for today', async () => {
    const response = await testApp.app.inject({method: 'GET', url: '/v3/user/currencies/all'});

    expect(response.statusCode).toBe(404);
    expect(response.json()).toMatchObject({code: 'B4005'});
  });
});
```

- [ ] **Step 5: Write `test/http/validation.e2e-spec.ts`**

```ts
import {TestApp, createTestApp} from '../support/test-app';

describe('HTTP request validation', () => {
  let testApp: TestApp;

  beforeAll(async () => {
    testApp = await createTestApp();
  });

  afterAll(async () => {
    await testApp.close();
  });

  it('rejects unknown properties with 400 B4006', async () => {
    const response = await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      payload: {name: 'Trip', currencyId: 'x', pinCode: '1234', users: [], extra: true},
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toEqual({
      statusCode: 400,
      code: 'B4006',
      message: 'property extra should not exist',
    });
  });

  it('rejects a pin code that is not exactly four characters', async () => {
    const response = await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      payload: {name: 'Trip', currencyId: 'x', pinCode: '123', users: []},
    });

    expect(response.statusCode).toBe(400);
    expect(response.json<{message: string}>().message).toContain('pinCode');
  });

  it('joins several validation messages with a semicolon', async () => {
    const response = await testApp.app.inject({method: 'POST', url: '/user/event', payload: {}});

    expect(response.statusCode).toBe(400);
    expect(response.json<{message: string}>().message.split('; ').length).toBeGreaterThan(1);
  });

  it('requires either pinCode or token on v2 event info', async () => {
    const response = await testApp.app.inject({method: 'POST', url: '/v2/user/event/x', payload: {}});

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({code: 'B4006'});
  });
});
```

- [ ] **Step 6: Write `test/http/idempotency.e2e-spec.ts`**

```ts
import {CurrencyCode} from '#domain/entities/currency.entity';

import {findCurrencyIdByCode} from '../support/fixtures';
import {TestApp, createTestApp} from '../support/test-app';

describe('HTTP idempotency-key header', () => {
  let testApp: TestApp;
  let usdId: string;

  beforeAll(async () => {
    testApp = await createTestApp();
  });

  afterAll(async () => {
    await testApp.close();
  });

  beforeEach(async () => {
    await testApp.reset();
    usdId = await findCurrencyIdByCode(testApp.rDataService, CurrencyCode.USD);
  });

  const payload = (): object => ({name: 'Trip', currencyId: usdId, pinCode: '1234', users: [{name: 'Alice'}]});

  it('replays the first response and creates nothing on a repeated key', async () => {
    const first = await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      headers: {'idempotency-key': 'key-1'},
      payload: payload(),
    });
    const second = await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      headers: {'idempotency-key': 'key-1'},
      payload: payload(),
    });
    const [events] = await testApp.rDataService.event.findAll({limit: 10});

    expect(first.statusCode).toBe(201);
    expect(second.statusCode).toBe(201);
    expect(second.json()).toEqual(first.json());
    expect(events).toHaveLength(1);
  });

  it('rejects a reused key with a different body with 422 B4011', async () => {
    await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      headers: {'idempotency-key': 'key-2'},
      payload: payload(),
    });

    const response = await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      headers: {'idempotency-key': 'key-2'},
      payload: {...payload(), name: 'Other'},
    });

    expect(response.statusCode).toBe(422);
    expect(response.json()).toMatchObject({code: 'B4011'});
  });

  it('treats the same key on a different route as a different request', async () => {
    const create = await testApp.app.inject({
      method: 'POST',
      url: '/user/event',
      headers: {'idempotency-key': 'key-3'},
      payload: payload(),
    });
    const {id} = create.json<{id: string}>();

    const addUsers = await testApp.app.inject({
      method: 'POST',
      url: `/user/event/${id}/users`,
      headers: {'idempotency-key': 'key-3'},
      payload: {pinCode: '1234', users: [{name: 'Bob'}]},
    });

    expect(addUsers.statusCode).toBe(422);
  });
});
```

The third test documents current behaviour: the key is the primary key of `idempotency_keys`, so a reused key on another route hashes differently and is rejected. If the executor believes this should succeed instead, report it; do not change the assertion.

- [ ] **Step 7: Write `test/http/devtools.e2e-spec.ts`**

```ts
import {CurrencyRateServiceAbstract} from '#domain/abstracts/currency-rate-service/currency-rate-service';

import {env} from '../../src/config';
import {TestApp, createTestApp} from '../support/test-app';

describe('HTTP /devtools', () => {
  let testApp: TestApp;
  const getCurrencyRate = jest.fn<Promise<Record<string, number> | null>, [string]>();

  beforeAll(async () => {
    testApp = await createTestApp({
      configure: (builder) => builder.overrideProvider(CurrencyRateServiceAbstract).useValue({getCurrencyRate}),
    });
  });

  afterAll(async () => {
    await testApp.close();
  });

  beforeEach(async () => {
    await testApp.reset();
  });

  it('rejects requests without the secret header with 401', async () => {
    const response = await testApp.app.inject({method: 'GET', url: '/devtools/currency-rate?date=2026-01-06'});

    expect(response.statusCode).toBe(401);
  });

  it('rejects a wrong secret with 401', async () => {
    const response = await testApp.app.inject({
      method: 'GET',
      url: '/devtools/currency-rate?date=2026-01-06',
      headers: {'x-devtools-secret': `${env.DEVTOOLS_SECRET}-wrong`},
    });

    expect(response.statusCode).toBe(401);
  });

  it('returns null when no rate is stored for the date', async () => {
    const response = await testApp.app.inject({
      method: 'GET',
      url: '/devtools/currency-rate?date=2026-01-06',
      headers: {'x-devtools-secret': env.DEVTOOLS_SECRET},
    });

    expect(response.statusCode).toBe(200);
    expect(response.body).toBe('');
  });

  it('fetches, stores and returns the rate for a date', async () => {
    getCurrencyRate.mockResolvedValue({USD: 1, EUR: 0.9});

    const fetchResponse = await testApp.app.inject({
      method: 'POST',
      url: '/devtools/currency-rate/fetch?date=2026-01-06',
      headers: {'x-devtools-secret': env.DEVTOOLS_SECRET},
    });
    const [stored] = await testApp.rDataService.currencyRate.findByDate('2026-01-06');

    expect(getCurrencyRate).toHaveBeenCalledWith('2026-01-06');
    expect(fetchResponse.statusCode).toBe(200);
    expect(fetchResponse.json()).toMatchObject({date: '2026-01-06', rate: {USD: 1, EUR: 0.9}});
    expect(stored?.rate).toEqual({USD: 1, EUR: 0.9});
  });
});
```

If the "returns null" test shows Fastify serialises `null` as the string `null` with status 200, change that assertion to `expect(response.body).toBe('null')` and note it in the summary; both are acceptable server behaviour.

- [ ] **Step 8: Run and verify**

```bash
npm run format && npm run test:e2e
```

Expected: 7 suites, all passing. Then the full verification command.

- [ ] **Step 9: Commit**

```bash
git add test
git commit -m "test(backend): cover HTTP routes, validation, idempotency and devtools end to end"
```

---

### Task 4: gRPC end-to-end tests and transport fixes

**Files:**
- Create: `test/support/grpc-client.ts`, `test/grpc/user.e2e-spec.ts`
- Create: `src/api/grpc/user/dto/user-grpc-request.dto.ts`, `src/api/grpc/user/dto/user-grpc-response.dto.ts`
- Create: `src/api/grpc/filters/grpc-status.map.ts`, `src/api/grpc/filters/grpc-business-error.filter.ts`, `src/api/grpc/filters/grpc-validation-error.filter.ts`
- Modify: `src/api/grpc/user/user.controller.ts`, `src/app.factory.ts` (loader options), `src/expenses.proto`, `package.json` (add `rxjs`), `eslint.config.js` (`only-throw-error` unchanged; filters do not throw)

**Interfaces:**
- Consumes: `createTestApp({grpc: true})`, `PROTO_PATH`, fixtures from Task 3.
- Produces: `createUserServiceClient(url)` and `callUnary(client, method, request, metadata?)` in `test/support/grpc-client.ts`.

- [ ] **Step 1: Add `rxjs` as a direct dependency**

```bash
npm install --save-exact rxjs@7.8.2
```

It is already installed transitively at that version; this only records the direct import.

- [ ] **Step 2: Create `test/support/grpc-client.ts`**

```ts
import {Metadata, ServiceClientConstructor, ServiceError, credentials, loadPackageDefinition} from '@grpc/grpc-js';
import {loadSync} from '@grpc/proto-loader';

import {PROTO_PATH} from './test-app';

export type UserServiceMethod =
  | 'CreateEvent'
  | 'GetEventInfo'
  | 'DeleteEvent'
  | 'AddUsersToEvent'
  | 'GetAllEventExpenses'
  | 'CreateExpense'
  | 'GetEventInfoV2'
  | 'AddUsersToEventV2'
  | 'GetAllEventExpensesV2'
  | 'CreateExpenseV2'
  | 'CreateEventShareTokenV2';

type UnaryMethod = (
  request: object,
  metadata: Metadata,
  callback: (error: ServiceError | null, response: unknown) => void,
) => void;

export type UserServiceClient = Record<UserServiceMethod, UnaryMethod> & {close: () => void};

export const createUserServiceClient = (url: string): UserServiceClient => {
  const definition = loadSync(PROTO_PATH, {
    keepCase: false,
    longs: String,
    enums: String,
    defaults: false,
    arrays: true,
    objects: true,
    oneofs: true,
  });
  const proto = loadPackageDefinition(definition) as unknown as {user: {UserService: ServiceClientConstructor}};

  return new proto.user.UserService(url, credentials.createInsecure()) as unknown as UserServiceClient;
};

export const callUnary = <TResponse>(
  client: UserServiceClient,
  method: UserServiceMethod,
  request: object,
  metadata: Metadata = new Metadata(),
): Promise<TResponse> =>
  new Promise((resolve, reject) => {
    client[method](request, metadata, (error, response) => {
      if (error) {
        reject(error);
        return;
      }

      resolve(response as TResponse);
    });
  });

export const expectGrpcError = async (
  call: Promise<unknown>,
): Promise<{code: number; details: string; errorCode: string | undefined}> => {
  try {
    await call;
  } catch (error) {
    const serviceError = error as ServiceError;
    const errorCode = serviceError.metadata.get('error-code')[0];

    return {code: serviceError.code, details: serviceError.details, errorCode: typeof errorCode === 'string' ? errorCode : undefined};
  }

  throw new Error('Expected the gRPC call to fail');
};
```

- [ ] **Step 3: Write the failing `test/grpc/user.e2e-spec.ts`**

```ts
import {Metadata, status} from '@grpc/grpc-js';

import {CurrencyCode} from '#domain/entities/currency.entity';

import {createEvent, findCurrencyIdByCode} from '../support/fixtures';
import {UserServiceClient, callUnary, createUserServiceClient, expectGrpcError} from '../support/grpc-client';
import {TestApp, createTestApp} from '../support/test-app';

interface EventResponse {
  id: string;
  name: string;
  currencyId: string;
  pinCode: string;
  users: {id: string; name: string; eventId: string}[];
}

interface ExpenseResponse {
  id: string;
  expenseType: string;
  isCustomRate: boolean;
  createdAt: string;
  updatedAt: string;
  splitInformation: {userId: string; amount: number; exchangedAmount?: number}[];
}

describe('gRPC UserService', () => {
  let testApp: TestApp;
  let client: UserServiceClient;
  let usdId: string;

  beforeAll(async () => {
    testApp = await createTestApp({grpc: true});
    client = createUserServiceClient(testApp.grpcUrl as string);
  });

  afterAll(async () => {
    client.close();
    await testApp.close();
  });

  beforeEach(async () => {
    await testApp.reset();
    usdId = await findCurrencyIdByCode(testApp.rDataService, CurrencyCode.USD);
  });

  it('creates an event and returns its users with string event ids', async () => {
    const response = await callUnary<EventResponse>(client, 'CreateEvent', {
      name: 'Trip',
      currencyId: usdId,
      pinCode: '1234',
      users: [{name: 'Alice'}],
    });

    expect(response).toMatchObject({name: 'Trip', currencyId: usdId, pinCode: '1234'});
    expect(response.users).toEqual([expect.objectContaining({name: 'Alice', eventId: response.id})]);
  });

  it('maps a wrong pin code to PERMISSION_DENIED with the domain error code in metadata', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const error = await expectGrpcError(callUnary(client, 'GetEventInfo', {eventId: event.id, pinCode: '0000'}));

    expect(error).toEqual({code: status.PERMISSION_DENIED, details: 'Invalid pin code', errorCode: 'B4003'});
  });

  it('maps an unknown event to NOT_FOUND', async () => {
    const error = await expectGrpcError(callUnary(client, 'GetEventInfo', {eventId: 'missing', pinCode: '1234'}));

    expect(error).toEqual({code: status.NOT_FOUND, details: 'Event not found', errorCode: 'B4001'});
  });

  it('validates the request and maps violations to INVALID_ARGUMENT', async () => {
    const error = await expectGrpcError(
      callUnary(client, 'CreateEvent', {name: 'Trip', currencyId: usdId, pinCode: '12', users: []}),
    );

    expect(error.code).toBe(status.INVALID_ARGUMENT);
    expect(error.details).toContain('pinCode');
    expect(error.errorCode).toBe('B4006');
  });

  it('round-trips fractional amounts, enum strings and ISO timestamps on CreateExpenseV2', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});
    const [alice] = event.users;

    const response = await callUnary<ExpenseResponse>(client, 'CreateExpenseV2', {
      eventId: event.id,
      pinCode: '1234',
      description: 'Lunch',
      userWhoPaidId: alice?.id,
      currencyId: usdId,
      expenseType: 'expense',
      splitInformation: [{userId: alice?.id, amount: 40.5}],
    });
    const [stored] = await testApp.rDataService.expense.findByEventId(event.id);

    expect(response.expenseType).toBe('expense');
    expect(response.isCustomRate).toBe(false);
    expect(response.splitInformation).toEqual([{userId: alice?.id, amount: 40.5, exchangedAmount: 40.5}]);
    expect(response.createdAt).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
    expect(stored[0]?.expenseType).toBe('expense');
  });

  it('returns a share token with an ISO expiry on CreateEventShareTokenV2', async () => {
    const event = await createEvent(testApp.app, {currencyId: usdId});

    const response = await callUnary<{token: string; expiresAt: string}>(client, 'CreateEventShareTokenV2', {
      eventId: event.id,
      pinCode: '1234',
    });

    expect(response.token).toMatch(/^[0-9a-f]{64}$/);
    expect(new Date(response.expiresAt).toISOString()).toBe(response.expiresAt);
  });

  it('replays CreateEvent when the idempotency-key metadata repeats', async () => {
    const metadata = new Metadata();
    metadata.set('idempotency-key', 'grpc-key-1');
    const request = {name: 'Trip', currencyId: usdId, pinCode: '1234', users: [{name: 'Alice'}]};

    const first = await callUnary<EventResponse>(client, 'CreateEvent', request, metadata);
    const second = await callUnary<EventResponse>(client, 'CreateEvent', request, metadata);
    const [events] = await testApp.rDataService.event.findAll({limit: 10});

    expect(second.id).toBe(first.id);
    expect(events).toHaveLength(1);
  });
});
```

- [ ] **Step 4: Run the gRPC spec to record the failures**

```bash
npm run format && npx jest --config ./test/jest-e2e.json --runInBand --ci test/grpc
```

Expected: the error-mapping tests fail with `code: 2` (UNKNOWN), the validation test fails because no validation runs, `eventId` on users is not the event id, `amount` loses `.5`, `expenseType` is stored as `0`, and `expiresAt` is empty. Record which of these actually fail in the task summary.

- [ ] **Step 5: Fix the proto contract**

In `src/expenses.proto`: remove `import "google/protobuf/timestamp.proto";`; in `UserResponse` change `int32 eventId = 3;` to `string eventId = 3;`; in `SplitInfo` change both `int32` fields to `double`; in `CreateEventShareTokenResponse` change `google.protobuf.Timestamp expiresAt = 2;` to `string expiresAt = 2;`.

- [ ] **Step 6: Set explicit loader options in `src/app.factory.ts`**

```ts
export const createGrpcOptions = ({url, protoPath}: {url: string; protoPath: string}): GrpcOptions => ({
  transport: Transport.GRPC,
  options: {
    package: GRPC_PACKAGE,
    protoPath,
    url,
    loader: {
      keepCase: false,
      longs: String,
      enums: String,
      defaults: false,
      arrays: true,
      objects: true,
      oneofs: true,
    },
  },
});
```

- [ ] **Step 7: Create the gRPC request DTOs `src/api/grpc/user/dto/user-grpc-request.dto.ts`**

```ts
import {IntersectionType} from '@nestjs/swagger';

import {AddUsersToEventParamsDto, AddUsersToEventRequestDto} from '#api/http/user/dto/add-users-to-event.dto';
import {
  CreateEventShareTokenParamsDto,
  CreateEventShareTokenRequestDto,
} from '#api/http/user/dto/create-event-share-token.dto';
import {
  CreateExpenseParamsDto,
  CreateExpenseRequestV1Dto,
  CreateExpenseRequestV2Dto,
} from '#api/http/user/dto/create-expense.dto';
import {DeleteEventParamsDto, DeleteEventRequestDto} from '#api/http/user/dto/delete-event.dto';
import {GetEventExpensesParamsDto, GetEventExpensesRequestV2Dto} from '#api/http/user/dto/get-event-expenses.dto';
import {
  GetEventInfoParamsDto,
  GetEventInfoRequestV1Dto,
  GetEventInfoRequestV2Dto,
} from '#api/http/user/dto/get-event-info.dto';

// gRPC handlers receive one message, so params and body DTOs are merged. IntersectionType keeps the class-validator
// metadata of both sources, which a TypeScript intersection type would not (decorators see it as Object).
export class GetEventInfoGrpcRequestDto extends IntersectionType(GetEventInfoParamsDto, GetEventInfoRequestV1Dto) {}
export class GetEventInfoV2GrpcRequestDto extends IntersectionType(GetEventInfoParamsDto, GetEventInfoRequestV2Dto) {}
export class DeleteEventGrpcRequestDto extends IntersectionType(DeleteEventParamsDto, DeleteEventRequestDto) {}
export class AddUsersToEventGrpcRequestDto extends IntersectionType(AddUsersToEventParamsDto, AddUsersToEventRequestDto) {}
export class GetEventExpensesGrpcRequestDto extends GetEventExpensesParamsDto {}
export class GetEventExpensesV2GrpcRequestDto extends IntersectionType(
  GetEventExpensesParamsDto,
  GetEventExpensesRequestV2Dto,
) {}
export class CreateExpenseGrpcRequestDto extends IntersectionType(CreateExpenseParamsDto, CreateExpenseRequestV1Dto) {}
export class CreateExpenseV2GrpcRequestDto extends IntersectionType(CreateExpenseParamsDto, CreateExpenseRequestV2Dto) {}
export class CreateEventShareTokenGrpcRequestDto extends IntersectionType(
  CreateEventShareTokenParamsDto,
  CreateEventShareTokenRequestDto,
) {}
```

- [ ] **Step 8: Create the gRPC response DTOs `src/api/grpc/user/dto/user-grpc-response.dto.ts`**

```ts
import {IExpense} from '#domain/entities/expense.entity';

export interface ExpenseGrpcResponseDto extends Omit<IExpense, 'createdAt' | 'updatedAt'> {
  createdAt: string;
  updatedAt: string;
}

export interface ExpensesGrpcResponseDto {
  expenses: ExpenseGrpcResponseDto[];
}

export const toExpenseGrpcResponse = (expense: IExpense): ExpenseGrpcResponseDto => ({
  ...expense,
  createdAt: expense.createdAt.toISOString(),
  updatedAt: expense.updatedAt.toISOString(),
});
```

- [ ] **Step 9: Create the status map and the two RPC filters**

`src/api/grpc/filters/grpc-status.map.ts`:

```ts
import {status} from '@grpc/grpc-js';

import {ErrorCode} from '#domain/errors/error-codes.enum';

export const GRPC_STATUS_BY_ERROR_CODE: Record<ErrorCode, status> = {
  [ErrorCode.EVENT_NOT_FOUND]: status.NOT_FOUND,
  [ErrorCode.EVENT_ALREADY_DELETED]: status.FAILED_PRECONDITION,
  [ErrorCode.EVENT_INVALID_PIN]: status.PERMISSION_DENIED,
  [ErrorCode.EVENT_OPERATION_CONFLICT]: status.ABORTED,
  [ErrorCode.CURRENCY_NOT_FOUND]: status.NOT_FOUND,
  [ErrorCode.CURRENCY_RATE_NOT_FOUND]: status.NOT_FOUND,
  [ErrorCode.INCONSISTENT_EXCHANGED_AMOUNT]: status.INVALID_ARGUMENT,
  [ErrorCode.VALIDATION_ERROR]: status.INVALID_ARGUMENT,
  [ErrorCode.INTERNAL_ERROR]: status.INTERNAL,
  [ErrorCode.INVALID_TOKEN]: status.UNAUTHENTICATED,
  [ErrorCode.TOKEN_EXPIRED]: status.UNAUTHENTICATED,
  [ErrorCode.IDEMPOTENCY_HASH_MISMATCH]: status.FAILED_PRECONDITION,
};

export const ERROR_CODE_METADATA_KEY = 'error-code';
```

`src/api/grpc/filters/grpc-business-error.filter.ts`:

```ts
import {Metadata} from '@grpc/grpc-js';
import {Catch, RpcExceptionFilter} from '@nestjs/common';
import {Observable, throwError} from 'rxjs';

import {ErrorCode} from '#domain/errors/error-codes.enum';
import {
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
  EventDeletedError,
  EventNotFoundError,
  EventOperationConflictError,
  IdempotencyHashMismatchError,
  InconsistentExchangedAmountError,
  InvalidPinCodeError,
  InvalidTokenError,
  TokenExpiredError,
} from '#domain/errors/errors';

import {ERROR_CODE_METADATA_KEY, GRPC_STATUS_BY_ERROR_CODE} from './grpc-status.map';

interface BusinessError {
  code: ErrorCode;
  message: string;
}

@Catch(
  EventNotFoundError,
  EventDeletedError,
  EventOperationConflictError,
  InvalidPinCodeError,
  InvalidTokenError,
  TokenExpiredError,
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
  InconsistentExchangedAmountError,
  IdempotencyHashMismatchError,
)
export class GrpcBusinessErrorFilter implements RpcExceptionFilter<BusinessError> {
  catch(exception: BusinessError): Observable<never> {
    const metadata = new Metadata();
    metadata.set(ERROR_CODE_METADATA_KEY, exception.code);

    return throwError(() => ({
      code: GRPC_STATUS_BY_ERROR_CODE[exception.code],
      details: exception.message,
      metadata,
    }));
  }
}
```

`src/api/grpc/filters/grpc-validation-error.filter.ts`:

```ts
import {Metadata, status} from '@grpc/grpc-js';
import {BadRequestException, Catch, RpcExceptionFilter} from '@nestjs/common';
import {Observable, throwError} from 'rxjs';

import {ErrorCode} from '#domain/errors/error-codes.enum';

import {ERROR_CODE_METADATA_KEY} from './grpc-status.map';

@Catch(BadRequestException)
export class GrpcValidationErrorFilter implements RpcExceptionFilter<BadRequestException> {
  catch(exception: BadRequestException): Observable<never> {
    const {message} = exception.getResponse() as {message: unknown};
    const details = Array.isArray(message) ? message.join('; ') : String(message);
    const metadata = new Metadata();
    metadata.set(ERROR_CODE_METADATA_KEY, ErrorCode.VALIDATION_ERROR);

    return throwError(() => ({code: status.INVALID_ARGUMENT, details, metadata}));
  }
}
```

- [ ] **Step 10: Update `src/api/grpc/user/user.controller.ts`**

Add the imports and decorators:

```ts
import {Body, Controller, UseFilters, UsePipes} from '@nestjs/common';

import {createValidationPipe} from '#api/validation-pipe';

import {GrpcBusinessErrorFilter} from '../filters/grpc-business-error.filter';
import {GrpcValidationErrorFilter} from '../filters/grpc-validation-error.filter';
import {
  AddUsersToEventGrpcRequestDto,
  CreateEventShareTokenGrpcRequestDto,
  CreateExpenseGrpcRequestDto,
  CreateExpenseV2GrpcRequestDto,
  DeleteEventGrpcRequestDto,
  GetEventExpensesGrpcRequestDto,
  GetEventExpensesV2GrpcRequestDto,
  GetEventInfoGrpcRequestDto,
  GetEventInfoV2GrpcRequestDto,
} from './dto/user-grpc-request.dto';
import {ExpenseGrpcResponseDto, ExpensesGrpcResponseDto, toExpenseGrpcResponse} from './dto/user-grpc-response.dto';

@Controller()
@UsePipes(createValidationPipe())
@UseFilters(GrpcValidationErrorFilter, GrpcBusinessErrorFilter)
export class UserController {
```

Then replace every intersection-typed `@Body()` parameter with the matching class: `GetEventInfoGrpcRequestDto`, `DeleteEventGrpcRequestDto`, `AddUsersToEventGrpcRequestDto` (both versions), `GetEventExpensesGrpcRequestDto`, `CreateExpenseGrpcRequestDto`, `GetEventInfoV2GrpcRequestDto`, `GetEventExpensesV2GrpcRequestDto`, `CreateExpenseV2GrpcRequestDto`, `CreateEventShareTokenGrpcRequestDto`. `createEvent` keeps `CreateEventRequestDto`.

Change the four expense handlers' return types and bodies:

```ts
  async getAllEventExpenses(@Body() {eventId}: GetEventExpensesGrpcRequestDto): Promise<ExpensesGrpcResponseDto> {
    const result = await this.getEventExpensesUseCase.execute({eventId});

    if (isError(result)) {
      throw result.error;
    }

    return {expenses: result.value.map(toExpenseGrpcResponse)};
  }
```

`getAllEventExpensesV2` returns `{expenses: result.value.map(toExpenseGrpcResponse)}` the same way. `createExpense` and `createExpenseV2` return `Promise<ExpenseGrpcResponseDto>` with `return toExpenseGrpcResponse(result.value);`. Remove the now-unused HTTP DTO imports (`CreateExpenseResponseDto`, `GetEventExpensesResponseWithExpensesDto`, the params DTOs, and the request DTOs that moved into the gRPC DTO file). `npm run typecheck` lists them.

- [ ] **Step 11: Run the gRPC spec until green, then everything**

```bash
npm run format && npx jest --config ./test/jest-e2e.json --runInBand --ci test/grpc
```

Expected: 7 passed. Then the full verification command. `npm run build` must also pass because `nest build` copies the proto.

- [ ] **Step 12: Commit**

```bash
git add src test package.json package-lock.json
git commit -m "fix(backend): validate gRPC requests, map domain errors to gRPC statuses, fix proto scalar types"
```

---

### Task 5: Remove stubs from v1 use-case tests, exercise idempotency for real, English names

**Files:**
- Modify: `src/usecases/users/__tests__/delete-event.usecase.test.ts`, `get-event-expenses.usecase.test.ts`, `get-event-info.usecase.test.ts`, `save-event-expense.usecase.test.ts`, `save-event.usecase.test.ts`, `save-users-to-event.usecase.test.ts`

**Interfaces:**
- Consumes: `#test-support/relational-state`, `#test-support/db`.

- [ ] **Step 1: Remove every `EventService` stub**

In each of the six files: delete the `mockEventService` property from the test-case type and from every test case; delete the `jest.spyOn(eventService, …)` calls in the `it` body; keep constructing the real `EventService`. Run the file:

```bash
npx jest --runInBand --ci src/usecases/users/__tests__/<file>
```

For each failing case, make `initRelationalState` produce the expected outcome: an absent event for `EventNotFoundError`, an event with `deletedAt` set for `EventDeletedError`, an event whose `pinCode` differs from the input for `InvalidPinCodeError`. Never change `output`.

- [ ] **Step 2: Replace the idempotency stubs with real replay tests**

In `save-event.usecase.test.ts`, `save-users-to-event.usecase.test.ts` and `save-event-expense.usecase.test.ts`: delete the `mockIdempotencyUseCase` field and the `jest.spyOn(idempotencySharedUseCase, 'execute')` block, and delete the table case whose name mentions the cached idempotency response. Add two dedicated tests after the table loop. For `save-event.usecase.test.ts`:

```ts
  describe('idempotency', () => {
    const input = {
      event: {name: 'Trip', currencyId: 'currency-usd', pinCode: '1234'},
      users: [{name: 'Alice', createdAt: new Date('2023-01-01T00:00:00Z'), updatedAt: new Date('2023-01-01T00:00:00Z')}],
      idempotencyKey: 'key-1',
      url: '/user/event',
    };
    const currencies = [
      {
        id: 'currency-usd',
        code: CurrencyCode.USD,
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
      },
    ];

    it('replays the stored response and inserts nothing on a repeated key', async () => {
      await prepareInitRelationalState({rDataService: relationalDataService, initState: {currencies}});

      const first = await useCase.execute(input);
      const second = await useCase.execute(input);
      const [events] = await relationalDataService.event.findAll({limit: 10});
      const [keys] = await relationalDataService.idempotencyKey.findAll({limit: 10});

      expect(second).toEqual(JSON.parse(JSON.stringify(first)));
      expect(events).toHaveLength(1);
      expect(keys).toEqual([expect.objectContaining({key: 'key-1', url: '/user/event', statusCode: 200})]);
    });

    it('rejects a repeated key with a different body', async () => {
      await prepareInitRelationalState({rDataService: relationalDataService, initState: {currencies}});
      await useCase.execute(input);

      await expect(useCase.execute({...input, event: {...input.event, name: 'Other'}})).rejects.toBeInstanceOf(
        IdempotencyHashMismatchError,
      );
    });
  });
```

`JSON.parse(JSON.stringify(first))` is deliberate: the stored response is a JSONB round trip, so `Date` fields come back as ISO strings. Write the same two tests for `save-users-to-event` (input `{eventId, users, pinCode, idempotencyKey, url}` with an event seeded) and `save-event-expense` (input as in the existing success case plus `idempotencyKey` and `url`). Import `IdempotencyHashMismatchError` from `#domain/errors/errors`.

- [ ] **Step 3: Rename every test case to English**

Rewrite each `name:` string and each `it('…')` in the six files. Rules: English, third-person verb first, outcome plus condition. Examples of the mapping:

| Russian | English |
|---|---|
| `должен успешно сохранить расход когда валюта события и расхода одинаковая` | `saves the expense without conversion when the currencies match` |
| `должен вернуть ошибку когда события не существует` | `returns EventNotFoundError when the event does not exist` |
| `должен вернуть ошибку когда событие удалено` | `returns EventDeletedError when the event is deleted` |
| `должен вернуть ошибку когда pin код неверный` | `returns InvalidPinCodeError when the pin code is wrong` |
| `должен вернуть ошибку когда курс валюты не найден` | `returns CurrencyRateNotFoundError when no rate exists for the date` |

Translate all Russian comments in the same files too.

- [ ] **Step 4: Verify**

```bash
npm run format && npx jest --runInBand --ci src/usecases/users/__tests__ && grep -rn "[А-Яа-я]" src/usecases/users/__tests__ | wc -l
```

Expected: all pass, grep count `0`. Then the full verification command.

- [ ] **Step 5: Commit**

```bash
git add src/usecases/users/__tests__
git commit -m "test(backend): drive v1 use-case tests by database state and exercise idempotency for real"
```

---

### Task 6: Same treatment for v2 use-case tests

**Files:**
- Modify: `src/usecases/users/v2/__tests__/create-event-share-token-v2.usecase.test.ts`, `get-event-expenses-v2.usecase.test.ts`, `get-event-info-v2.usecase.test.ts`, `save-event-expense-v2.usecase.test.ts`, `save-users-to-event-v2.usecase.test.ts`

- [ ] **Step 1: Remove `EventService` stubs** exactly as in Task 5 Step 1. `save-event-expense-v2` and `get-event-info-v2` currently stub methods the use case does not call; after removal every case must still pass on database state alone.

- [ ] **Step 2: Real idempotency tests** for `save-event-expense-v2` and `save-users-to-event-v2` exactly as in Task 5 Step 2. For `save-event-expense-v2` seed one event and one USD currency and use the existing same-currency input plus `idempotencyKey: 'key-1'` and `url: '/v2/user/event/event-1/expense'`.

- [ ] **Step 3: Add the missing historical-rate case to `save-event-expense-v2.usecase.test.ts`**

Add a table case that seeds two rates and passes `createdAt`:

```ts
    {
      name: 'uses the rate of the createdAt date instead of today',
      initRelationalState: {
        events: [/* event-1 in USD as in the other cases */],
        currencies: [/* USD and EUR as in the conversion case */],
        currencyRates: [
          {date: '2026-01-01', rate: {USD: 1, EUR: 0.85}, createdAt: new Date('2026-01-01T00:00:00Z'), updatedAt: new Date('2026-01-01T00:00:00Z')},
          {date: '2025-12-15', rate: {USD: 1, EUR: 0.5}, createdAt: new Date('2025-12-15T00:00:00Z'), updatedAt: new Date('2025-12-15T00:00:00Z')},
        ],
      },
      input: {
        eventId: 'event-1',
        currencyId: 'currency-eur',
        description: 'Old dinner',
        userWhoPaidId: 'user-1',
        expenseType: ExpenseType.Expense,
        splitInformation: [{userId: 'user-1', amount: 10}],
        pinCode: '1234',
        createdAt: new Date('2025-12-15T18:00:00Z'),
        url: 'url',
      },
      output: success(
        expect.objectContaining({
          createdAt: new Date('2025-12-15T18:00:00Z'),
          splitInformation: [{userId: 'user-1', amount: 10, exchangedAmount: 20}],
        }),
      ),
      relationalStateChanges: {
        expenses: {inserted: [expect.objectContaining({splitInformation: [{userId: 'user-1', amount: 10, exchangedAmount: 20}]})]},
      },
    },
```

Expand the `/* … */` placeholders with the literal objects already used by the neighbouring cases in that file.

- [ ] **Step 4: English names** as in Task 5 Step 3.

- [ ] **Step 5: Verify**

```bash
npm run format && npx jest --runInBand --ci src/usecases/users/v2 && grep -rn "[А-Яа-я]" src/usecases/users/v2/__tests__ | wc -l
```

Expected: all pass, grep count `0`. Then the full verification command.

- [ ] **Step 6: Commit**

```bash
git add src/usecases/users/v2/__tests__
git commit -m "test(backend): drive v2 use-case tests by database state and cover historical rates"
```

---

### Task 7: English names in the remaining test files

**Files:**
- Modify: `src/usecases/cron/__tests__/fetch-daily-currency-rates.usecase.test.ts`, `src/usecases/devtools/__tests__/fetch-currency-rate.usecase.test.ts`, `src/usecases/devtools/__tests__/get-currency-rate.usecase.test.ts`, `src/usecases/health/__tests__/health-check.usecase.test.ts`, `src/usecases/shared/__tests__/fetch-and-save-currency-rate.usecase.test.ts`, `src/usecases/shared/__tests__/idempotency.usecase.test.ts`, `src/usecases/users/v3/__tests__/*.test.ts`, `src/frameworks/relational-data-service/postgres/repositories/__tests__/*.spec.ts` (comments only)

- [ ] **Step 1: Translate** every `name:`, `it('…')` and comment in those files following the Task 5 rules. Snapshot keys are built from `describe` and `it` names; the repository specs already have English names, so only translate their comments and never touch a `.snap` file.

- [ ] **Step 2: Verify**

```bash
npm run format && grep -rn "[А-Яа-я]" src test | wc -l && npm test
```

Expected: grep count `0`, all tests pass, `39 snapshots passed`, none obsolete.

- [ ] **Step 3: Commit**

```bash
git add src
git commit -m "test(backend): use English test names throughout"
```

---

### Task 8: Repository behaviour tests beside the SQL snapshots, idempotency-key repository spec

**Files:**
- Create: `src/frameworks/relational-data-service/postgres/repositories/__tests__/idempotency-key.repository.spec.ts`
- Modify: `currency.repository.spec.ts`, `currency-rate.repository.spec.ts`, `event.repository.spec.ts` in the same folder
- Modify: `AGENTS.md` (snapshot policy)

- [ ] **Step 1: Write `idempotency-key.repository.spec.ts`**

```ts
import {LessThan} from 'typeorm';

import {IIdempotencyKey} from '#domain/entities/idempotency-key.entity';

import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {createTestRelationalDataService, truncateAllTables} from '#test-support/db';

const buildKey = (overrides: Partial<IIdempotencyKey> = {}): IIdempotencyKey => ({
  key: 'key-1',
  url: '/user/event',
  requestHash: 'hash-1',
  response: {id: 'event-1'},
  statusCode: 200,
  expiresAt: new Date('2026-01-02T00:00:00Z'),
  createdAt: new Date('2026-01-01T00:00:00Z'),
  ...overrides,
});

describe('IdempotencyKeyRepository', () => {
  let relationalDataService: RelationalDataService;

  beforeAll(async () => {
    relationalDataService = createTestRelationalDataService({showQueryDetails: true});
    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
  });

  it('inserts a key and finds it by key', async () => {
    const record = buildKey();

    const [, insertDetails] = await relationalDataService.idempotencyKey.insert(record);
    const [found, findDetails] = await relationalDataService.idempotencyKey.findByKey('key-1');

    expect(found).toMatchObject(record);
    expect(insertDetails).toMatchSnapshot();
    expect(findDetails).toMatchSnapshot();
  });

  it('returns null for an unknown key', async () => {
    const [found] = await relationalDataService.idempotencyKey.findByKey('missing');

    expect(found).toBeNull();
  });

  it('lists keys up to the limit', async () => {
    for (const key of ['a', 'b', 'c']) {
      await relationalDataService.idempotencyKey.insert(buildKey({key}));
    }

    const [found, details] = await relationalDataService.idempotencyKey.findAll({limit: 2});

    expect(found).toHaveLength(2);
    expect(details).toMatchSnapshot();
  });

  it('deletes only the keys matching the criteria', async () => {
    await relationalDataService.idempotencyKey.insert(buildKey({key: 'expired', expiresAt: new Date('2025-12-31T00:00:00Z')}));
    await relationalDataService.idempotencyKey.insert(buildKey({key: 'valid', expiresAt: new Date('2026-12-31T00:00:00Z')}));

    await relationalDataService.idempotencyKey.delete({expiresAt: LessThan(new Date('2026-01-01T00:00:00Z'))});
    const [remaining] = await relationalDataService.idempotencyKey.findAll({limit: 10});

    expect(remaining.map((record) => record.key)).toEqual(['valid']);
  });
});
```

Create its snapshots once:

```bash
npx jest --runInBand -u src/frameworks/relational-data-service/postgres/repositories/__tests__/idempotency-key.repository.spec.ts
```

Expected: `3 snapshots written`. Open the new `.snap`, check it contains an `INSERT INTO "idempotency_keys"`, a `WHERE "idempotency_key"."key" = :key` and a `LIMIT 2`, then re-run with `--ci`.

- [ ] **Step 2: Add behaviour tests to `currency.repository.spec.ts`**

Inside the existing `describe('findAll')` add:

```ts
    it('orders by the requested column and direction', async () => {
      await relationalDataService.currency.insert([
        {id: 'c-usd', code: CurrencyCode.USD, createdAt: new Date('2023-01-01T00:00:00Z'), updatedAt: new Date('2023-01-01T00:00:00Z')},
        {id: 'c-eur', code: CurrencyCode.EUR, createdAt: new Date('2023-01-01T00:00:00Z'), updatedAt: new Date('2023-01-01T00:00:00Z')},
        {id: 'c-rub', code: CurrencyCode.RUB, createdAt: new Date('2023-01-01T00:00:00Z'), updatedAt: new Date('2023-01-01T00:00:00Z')},
      ]);

      const [descending] = await relationalDataService.currency.findAll({orderBy: 'code', orderDirection: 'DESC'});
      const [ascending] = await relationalDataService.currency.findAll({orderBy: 'code'});

      expect(descending.map((currency) => currency.code)).toEqual(['USD', 'RUB', 'EUR']);
      expect(ascending.map((currency) => currency.code)).toEqual(['EUR', 'RUB', 'USD']);
    });

    it('returns nothing when the codes filter is empty', async () => {
      await relationalDataService.currency.insert({id: 'c-usd', code: CurrencyCode.USD, createdAt: new Date(), updatedAt: new Date()});

      const [found] = await relationalDataService.currency.findAll({codes: []});

      expect(found).toEqual([]);
    });
```

Add a new `describe('supported currencies')`:

```ts
  describe('supported currencies', () => {
    const unsupported = {id: 'c-xxx', code: 'XXX' as CurrencyCode, createdAt: new Date(), updatedAt: new Date()};
    const usd = {id: 'c-usd', code: CurrencyCode.USD, createdAt: new Date(), updatedAt: new Date()};

    it('excludes codes outside SUPPORTED_CURRENCY_CODES from findAllSupported', async () => {
      await relationalDataService.currency.insert([usd, unsupported]);

      const [found] = await relationalDataService.currency.findAllSupported();

      expect(found.map((currency) => currency.id)).toEqual(['c-usd']);
    });

    it('returns null from findSupportedById for an unsupported code', async () => {
      await relationalDataService.currency.insert([usd, unsupported]);

      const [found] = await relationalDataService.currency.findSupportedById('c-xxx');
      const [foundUsd] = await relationalDataService.currency.findSupportedById('c-usd');

      expect(found).toBeNull();
      expect(foundUsd).toMatchObject({id: 'c-usd'});
    });
  });
```

Import `CurrencyCode` from `#domain/entities/currency.entity` if the file does not already.

- [ ] **Step 3: Add the version query test to `currency-rate.repository.spec.ts`**

```ts
  describe('findSupportedCurrenciesWithRatesVersionByDate', () => {
    it('returns the rate timestamp and supported currency timestamps ordered by code', async () => {
      await relationalDataService.currency.insert([
        {id: 'c-usd', code: CurrencyCode.USD, createdAt: new Date('2023-01-02T00:00:00Z'), updatedAt: new Date('2023-01-02T00:00:00Z')},
        {id: 'c-eur', code: CurrencyCode.EUR, createdAt: new Date('2023-01-01T00:00:00Z'), updatedAt: new Date('2023-01-01T00:00:00Z')},
        {id: 'c-xxx', code: 'XXX' as CurrencyCode, createdAt: new Date('2023-01-03T00:00:00Z'), updatedAt: new Date('2023-01-03T00:00:00Z')},
      ]);
      await relationalDataService.currencyRate.insert({
        date: '2026-01-06',
        rate: {USD: 1, EUR: 0.9},
        createdAt: new Date('2026-01-06T12:00:00Z'),
        updatedAt: new Date('2026-01-06T12:00:00Z'),
      });

      const [version, details] = await relationalDataService.currencyRate.findSupportedCurrenciesWithRatesVersionByDate(
        '2026-01-06',
        undefined,
      );

      expect(version).toEqual({
        rateUpdatedAt: new Date('2026-01-06T12:00:00Z'),
        currenciesUpdatedAt: [new Date('2023-01-01T00:00:00Z'), new Date('2023-01-02T00:00:00Z')],
      });
      expect(details).toMatchSnapshot();
    });

    it('returns null when no rate exists for the date', async () => {
      const [version] = await relationalDataService.currencyRate.findSupportedCurrenciesWithRatesVersionByDate('2026-01-06', undefined);

      expect(version).toBeNull();
    });
  });
```

Write the one new snapshot with `npx jest --runInBand -u <this spec>`; confirm the diff adds exactly one entry containing `json_agg` and changes nothing else.

- [ ] **Step 4: Add a rollback test to `event.repository.spec.ts`** inside `describe('update')`:

```ts
    it('discards the update when the transaction throws', async () => {
      const event = {
        id: 'event-1',
        name: 'Test Event',
        currencyId: 'currency-1',
        pinCode: '1234',
        createdAt: new Date('2023-01-01T00:00:00Z'),
        updatedAt: new Date('2023-01-01T00:00:00Z'),
        deletedAt: null,
      };
      await relationalDataService.event.insert(event);

      await expect(
        relationalDataService.transaction(async (ctx) => {
          await relationalDataService.event.update('event-1', {deletedAt: new Date('2023-01-02T00:00:00Z')}, {ctx});
          throw new Error('boom');
        }),
      ).rejects.toThrow('boom');
      const [found] = await relationalDataService.event.findById('event-1');

      expect(found?.deletedAt).toBeNull();
    });
```

- [ ] **Step 5: Document the snapshot policy in `AGENTS.md`** under `## Testing`, after the command block:

```markdown
Repository specs assert `queryDetails` with `toMatchSnapshot()`. Those snapshots are the record of the SQL TypeORM
generates and are the guard for TypeORM upgrades: `npm test` runs with `--ci`, so a changed query fails instead of
rewriting the snapshot. Update them only on purpose with `npx jest --runInBand -u <spec>` and review the `.snap` diff
in the same change. Put behavioural assertions next to the snapshot when the SQL shape encodes a rule.
```

- [ ] **Step 6: Verify**

```bash
npm run format && npm test
```

Expected: `43 snapshots passed` (39 existing + 4 new), zero obsolete. Then the full verification command.

- [ ] **Step 7: Commit**

```bash
git add src AGENTS.md
git commit -m "test(backend): add repository behaviour tests and idempotency-key repository spec"
```

---

### Task 9: Schema drift guard

**Files:**
- Create: `src/frameworks/relational-data-service/postgres/__tests__/schema-drift.spec.ts`
- Modify: `AGENTS.md`

- [ ] **Step 1: Write the spec**

```ts
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {createTestRelationalDataService} from '#test-support/db';

describe('database schema', () => {
  let relationalDataService: RelationalDataService;

  beforeAll(async () => {
    relationalDataService = createTestRelationalDataService();
    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  it('has every migration applied', async () => {
    await expect(relationalDataService.dataSource.showMigrations()).resolves.toBe(false);
  });

  it('matches the entity metadata with no pending schema changes', async () => {
    const pending = await relationalDataService.dataSource.driver.createSchemaBuilder().log();

    expect(pending.upQueries.map((query) => query.query)).toEqual([]);
  });
});
```

- [ ] **Step 2: Run it**

```bash
npx jest --runInBand --ci src/frameworks/relational-data-service/postgres/__tests__/schema-drift.spec.ts
```

Expected: 2 passed. If the second test prints pending SQL, stop and report the statements; do not generate a migration in this task.

- [ ] **Step 3: Document** in `AGENTS.md` `## Development Workflow`, after the migration bullets:

```markdown
    - `schema-drift.spec.ts` fails when the entities and the applied migrations disagree; generate a migration with
      `npm run db:migrate:new` instead of editing the test
```

- [ ] **Step 4: Verify and commit**

```bash
npm run format && npm test
git add src AGENTS.md
git commit -m "test(backend): fail when TypeORM entities drift from the applied migrations"
```

---

### Task 10: Unit tests for value objects, packages and EventService

**Files:**
- Create: `src/domain/value-objects/__tests__/value-objects.test.ts`, `src/packages/__tests__/date-utils.test.ts`, `src/packages/__tests__/result.test.ts`, `src/frameworks/event-service/__tests__/event-service.test.ts`

- [ ] **Step 1: Write `value-objects.test.ts`**

```ts
import {ExpenseType} from '#domain/entities/expense.entity';
import {CurrencyRateValueObject} from '#domain/value-objects/currency-rate.value-object';
import {CurrencyValueObject} from '#domain/value-objects/currency.value-object';
import {EventShareTokenValueObject} from '#domain/value-objects/event-share-token.value-object';
import {EventValueObject} from '#domain/value-objects/event.value-object';
import {ExpenseValueObject} from '#domain/value-objects/expense.value-object';
import {IdempotencyKeyValueObject} from '#domain/value-objects/idempotency-key.value-object';
import {UserInfoValueObject} from '#domain/value-objects/user-info.value-object';
import {CurrencyCode} from '#domain/entities/currency.entity';

const ULID = /^[0-9A-HJKMNP-TV-Z]{26}$/;
const now = new Date('2026-01-01T00:00:00.000Z');

describe('value objects', () => {
  beforeEach(() => {
    jest.useFakeTimers({now});
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('fills id and timestamps on EventValueObject and defaults deletedAt to null', () => {
    const {value} = new EventValueObject({name: 'Trip', currencyId: 'c-usd', pinCode: '1234'});

    expect(value).toEqual({
      id: expect.stringMatching(ULID),
      name: 'Trip',
      currencyId: 'c-usd',
      pinCode: '1234',
      createdAt: now,
      updatedAt: now,
      deletedAt: null,
    });
  });

  it('keeps provided values over defaults', () => {
    const createdAt = new Date('2020-01-01T00:00:00Z');

    const {value} = new EventValueObject({id: 'fixed', name: 'Trip', currencyId: 'c', pinCode: '1', createdAt});

    expect(value).toMatchObject({id: 'fixed', createdAt});
  });

  it('treats an explicit undefined as absent so class-validator output does not erase defaults', () => {
    // exactOptionalPropertyTypes forbids writing `id: undefined` directly; class-validator produces it at runtime.
    const explicitUndefinedId = {id: undefined} as unknown as {id?: string};

    const {value} = new ExpenseValueObject({
      ...explicitUndefinedId,
      description: 'Lunch',
      userWhoPaidId: 'u1',
      currencyId: 'c',
      eventId: 'e',
      expenseType: ExpenseType.Expense,
      splitInformation: [],
      isCustomRate: false,
    });

    expect(value.id).toMatch(ULID);
  });

  it('fills id and timestamps on UserInfoValueObject and CurrencyValueObject', () => {
    expect(new UserInfoValueObject({name: 'Alice', eventId: 'e'}).value).toMatchObject({
      id: expect.stringMatching(ULID),
      createdAt: now,
      updatedAt: now,
    });
    expect(new CurrencyValueObject({code: CurrencyCode.USD}).value).toMatchObject({
      id: expect.stringMatching(ULID),
      createdAt: now,
      updatedAt: now,
    });
  });

  it('fills timestamps on CurrencyRateValueObject', () => {
    expect(new CurrencyRateValueObject({date: '2026-01-01', rate: {USD: 1}}).value).toEqual({
      date: '2026-01-01',
      rate: {USD: 1},
      createdAt: now,
      updatedAt: now,
    });
  });

  it('generates a 64-hex token that expires in 14 days', () => {
    const {value} = new EventShareTokenValueObject({eventId: 'e'});

    expect(value.token).toMatch(/^[0-9a-f]{64}$/);
    expect(value.expiresAt).toEqual(new Date('2026-01-15T00:00:00.000Z'));
    expect(value.createdAt).toEqual(now);
  });

  it('expires an idempotency key after 24 hours', () => {
    const {value} = new IdempotencyKeyValueObject({key: 'k', url: '/u', requestHash: 'h', response: {}, statusCode: 200});

    expect(value.expiresAt).toEqual(new Date('2026-01-02T00:00:00.000Z'));
    expect(value.createdAt).toEqual(now);
  });
});
```

- [ ] **Step 2: Write `date-utils.test.ts`**

```ts
import {getCurrentDateWithoutTimeUTC, getDateWithoutTimeUTC} from '#packages/date-utils';

describe('date-utils', () => {
  it('formats the UTC calendar date with zero padding', () => {
    expect(getDateWithoutTimeUTC(new Date('2024-03-05T10:00:00Z'))).toBe('2024-03-05');
  });

  it('uses the UTC date, not the local one', () => {
    expect(getDateWithoutTimeUTC(new Date('2024-03-05T23:30:00-05:00'))).toBe('2024-03-06');
  });

  it('returns today in UTC', () => {
    jest.useFakeTimers({now: new Date('2026-01-06T23:59:59Z')});

    expect(getCurrentDateWithoutTimeUTC()).toBe('2026-01-06');

    jest.useRealTimers();
  });
});
```

- [ ] **Step 3: Write `result.test.ts`**

```ts
import {error, isError, isSuccess, success} from '#packages/result';

describe('result', () => {
  it('wraps a value as success', () => {
    const result = success<number, string>(1);

    expect(result).toEqual({result: 'success', value: 1});
    expect(isSuccess(result)).toBe(true);
    expect(isError(result)).toBe(false);
  });

  it('wraps an error as error', () => {
    const result = error<number, string>('boom');

    expect(result).toEqual({result: 'error', error: 'boom'});
    expect(isError(result)).toBe(true);
    expect(isSuccess(result)).toBe(false);
  });
});
```

- [ ] **Step 4: Write `event-service.test.ts`**

```ts
import {error, success} from '#packages/result';

import {IEvent} from '#domain/entities/event.entity';
import {EventDeletedError, EventNotFoundError, InvalidPinCodeError} from '#domain/errors/errors';

import {EventService} from '#frameworks/event-service/event-service';

const event: IEvent = {
  id: 'event-1',
  name: 'Trip',
  currencyId: 'c-usd',
  pinCode: '1234',
  createdAt: new Date('2023-01-01T00:00:00Z'),
  updatedAt: new Date('2023-01-01T00:00:00Z'),
  deletedAt: null,
};

describe('EventService', () => {
  const service = new EventService();

  it.each([
    ['returns EventNotFoundError for null', null, '1234', error(new EventNotFoundError())],
    ['returns EventNotFoundError for undefined', undefined, '1234', error(new EventNotFoundError())],
    ['returns EventDeletedError for a deleted event', {...event, deletedAt: new Date()}, '1234', error(new EventDeletedError())],
    ['returns InvalidPinCodeError for a wrong pin', event, '0000', error(new InvalidPinCodeError())],
    ['returns success for a live event with the right pin', event, '1234', success(true)],
  ])('%s', (_name, input, pinCode, expected) => {
    expect(service.isValidEvent(input, pinCode)).toEqual(expected);
  });

  it('checks deletion before the pin code', () => {
    expect(service.isValidEvent({...event, deletedAt: new Date()}, '0000')).toEqual(error(new EventDeletedError()));
  });
});
```

- [ ] **Step 5: Verify and commit**

```bash
npm run format && npx jest --runInBand --ci src/domain src/packages src/frameworks/event-service
git add src
git commit -m "test(backend): unit-test value objects, date utils, result and EventService"
```

Then the full verification command.

---

### Task 11: Unit tests for CurrencyRateService, guard, filters, scheduler and config

**Files:**
- Modify: `src/frameworks/currency-rate-service/currency-rate-service.ts` (injectable retry options), `src/config.ts` (exported parser)
- Create: `src/frameworks/currency-rate-service/__tests__/currency-rate-service.test.ts`, `src/api/http/devtools/guards/__tests__/devtools-secret.guard.spec.ts`, `src/api/http/filters/__tests__/validation-exception.filter.spec.ts`, `src/api/cron/__tests__/currency-rate-scheduler.controller.spec.ts`, `src/__tests__/config.test.ts`
- Modify: `src/api/http/filters/__tests__/business-error.filter.spec.ts`

- [ ] **Step 1: Make retry timing injectable in `CurrencyRateService`**

```ts
import {HttpService} from '@nestjs/axios';
import retry from 'async-retry';

import {CurrencyRateServiceAbstract} from '#domain/abstracts/currency-rate-service/currency-rate-service';
import {ICurrencyRate} from '#domain/entities/currency-rate.entity';

import {env} from '../../config';

export const DEFAULT_CURRENCY_RATE_RETRY_OPTIONS: retry.Options = {retries: 3};

export class CurrencyRateService implements CurrencyRateServiceAbstract {
  constructor(
    private readonly httpService: HttpService,
    private readonly retryOptions: retry.Options = DEFAULT_CURRENCY_RATE_RETRY_OPTIONS,
  ) {}

  getCurrencyRate: (date: ICurrencyRate['date']) => Promise<Record<string, number> | null> = async (date) => {
    const result = await retry(
      async () =>
        this.httpService.axiosRef.get<{rates?: Record<string, number>}>(
          `https://openexchangerates.org/api/historical/${date}.json?app_id=${env.OPEN_EXCHANGE_RATES_API_ID}&base=USD`,
        ),
      this.retryOptions,
    );

    const rate = result.data.rates;

    return rate ?? null;
  };
}
```

- [ ] **Step 2: Write `currency-rate-service.test.ts`**

```ts
import {HttpService} from '@nestjs/axios';

import {CurrencyRateService} from '#frameworks/currency-rate-service/currency-rate-service';

import {env} from '../../../config';

describe('CurrencyRateService', () => {
  const get = jest.fn<Promise<{data: {rates?: Record<string, number>}}>, [string]>();
  const httpService = {axiosRef: {get}} as unknown as HttpService;
  const service = new CurrencyRateService(httpService, {retries: 3, minTimeout: 0, maxTimeout: 0});

  it('requests the historical endpoint for the date with the configured app id', async () => {
    get.mockResolvedValue({data: {rates: {USD: 1, EUR: 0.9}}});

    const rates = await service.getCurrencyRate('2026-01-06');

    expect(get).toHaveBeenCalledWith(
      `https://openexchangerates.org/api/historical/2026-01-06.json?app_id=${env.OPEN_EXCHANGE_RATES_API_ID}&base=USD`,
    );
    expect(rates).toEqual({USD: 1, EUR: 0.9});
  });

  it('returns null when the response has no rates', async () => {
    get.mockResolvedValue({data: {}});

    await expect(service.getCurrencyRate('2026-01-06')).resolves.toBeNull();
  });

  it('retries failed requests up to three times', async () => {
    get.mockRejectedValueOnce(new Error('503')).mockRejectedValueOnce(new Error('503')).mockResolvedValue({data: {rates: {USD: 1}}});

    await expect(service.getCurrencyRate('2026-01-06')).resolves.toEqual({USD: 1});
    expect(get).toHaveBeenCalledTimes(3);
  });

  it('throws the last error after the retries are exhausted', async () => {
    get.mockRejectedValue(new Error('down'));

    await expect(service.getCurrencyRate('2026-01-06')).rejects.toThrow('down');
    expect(get).toHaveBeenCalledTimes(4);
  });
});
```

- [ ] **Step 3: Write `devtools-secret.guard.spec.ts`**

```ts
import {ExecutionContext, UnauthorizedException} from '@nestjs/common';

import {DevtoolsSecretGuard} from '#api/http/devtools/guards/devtools-secret.guard';

import {env} from '../../../../../config';

const contextWithHeaders = (headers: Record<string, string | string[] | undefined>): ExecutionContext =>
  ({switchToHttp: () => ({getRequest: () => ({headers})})}) as unknown as ExecutionContext;

describe('DevtoolsSecretGuard', () => {
  const guard = new DevtoolsSecretGuard();

  it('allows a request carrying the configured secret', () => {
    expect(guard.canActivate(contextWithHeaders({'x-devtools-secret': env.DEVTOOLS_SECRET}))).toBe(true);
  });

  it('uses the first value of a repeated header', () => {
    expect(guard.canActivate(contextWithHeaders({'x-devtools-secret': [env.DEVTOOLS_SECRET, 'other']}))).toBe(true);
  });

  it('rejects a missing header', () => {
    expect(() => guard.canActivate(contextWithHeaders({}))).toThrow(UnauthorizedException);
  });

  it('rejects a wrong secret', () => {
    expect(() => guard.canActivate(contextWithHeaders({'x-devtools-secret': `${env.DEVTOOLS_SECRET}x`}))).toThrow(
      'Invalid devtools secret',
    );
  });

  it('rejects everything when no secret is configured', () => {
    jest.replaceProperty(env, 'DEVTOOLS_SECRET', '');

    expect(() => guard.canActivate(contextWithHeaders({'x-devtools-secret': ''}))).toThrow(
      'Devtools secret is not configured',
    );
  });
});
```

- [ ] **Step 4: Write `validation-exception.filter.spec.ts`**

```ts
import {ArgumentsHost, BadRequestException, HttpStatus} from '@nestjs/common';
import {AbstractHttpAdapter} from '@nestjs/core';

import {ValidationExceptionFilter} from '#api/http/filters/validation-exception.filter';

const run = (message: unknown): unknown[] => {
  const reply = jest.fn();
  const response = {};
  const host = {switchToHttp: () => ({getResponse: (): object => response})} as ArgumentsHost;

  new ValidationExceptionFilter({reply} as unknown as AbstractHttpAdapter).catch(
    new BadRequestException({message}),
    host,
  );

  return reply.mock.calls[0] ?? [];
};

describe('ValidationExceptionFilter', () => {
  it.each([
    ['passes a string message through', 'bad', 'bad'],
    ['joins an array of messages with a semicolon', ['a', 'b'], 'a; b'],
    ['serialises an object message', {field: 'x'}, '{"field":"x"}'],
    ['stringifies other values', 42, '42'],
  ])('%s', (_name, message, expected) => {
    expect(run(message)).toEqual([
      expect.anything(),
      {statusCode: HttpStatus.BAD_REQUEST, code: 'B4006', message: expected},
      HttpStatus.BAD_REQUEST,
    ]);
  });
});
```

- [ ] **Step 5: Extend `business-error.filter.spec.ts` to every error**

Replace the file body with an `it.each` table:

```ts
import {ArgumentsHost, HttpStatus} from '@nestjs/common';
import {FILTER_CATCH_EXCEPTIONS} from '@nestjs/common/constants';
import {AbstractHttpAdapter} from '@nestjs/core';

import {
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
  EventDeletedError,
  EventNotFoundError,
  EventOperationConflictError,
  IdempotencyHashMismatchError,
  InconsistentExchangedAmountError,
  InvalidPinCodeError,
  InvalidTokenError,
  TokenExpiredError,
} from '#domain/errors/errors';

import {BusinessErrorFilter} from '#api/http/filters/business-error.filter';

describe('BusinessErrorFilter', () => {
  it.each([
    [EventNotFoundError, HttpStatus.NOT_FOUND, 'B4001', 'Event not found'],
    [EventDeletedError, HttpStatus.GONE, 'B4002', 'Event is deleted'],
    [InvalidPinCodeError, HttpStatus.FORBIDDEN, 'B4003', 'Invalid pin code'],
    [CurrencyNotFoundError, HttpStatus.NOT_FOUND, 'B4004', 'Currency not found'],
    [CurrencyRateNotFoundError, HttpStatus.NOT_FOUND, 'B4005', 'Currency rate not found'],
    [InvalidTokenError, HttpStatus.UNAUTHORIZED, 'B4008', 'Invalid token'],
    [TokenExpiredError, HttpStatus.UNAUTHORIZED, 'B4009', 'Token has expired'],
    [InconsistentExchangedAmountError, HttpStatus.BAD_REQUEST, 'B4010', 'All splitInfo must have exchangedAmount when custom rate is used'],
    [IdempotencyHashMismatchError, HttpStatus.UNPROCESSABLE_ENTITY, 'B4011', 'Idempotency key reused with different request body'],
    [EventOperationConflictError, HttpStatus.CONFLICT, 'B4015', 'Another operation is in progress for this event'],
  ])('maps %p to its status, code and message', (ErrorClass, statusCode, code, message) => {
    const reply = jest.fn();
    const response = {};
    const host = {switchToHttp: () => ({getResponse: (): object => response})} as ArgumentsHost;

    new BusinessErrorFilter({reply} as unknown as AbstractHttpAdapter).catch(new ErrorClass(), host);

    expect(Reflect.getMetadata(FILTER_CATCH_EXCEPTIONS, BusinessErrorFilter)).toContain(ErrorClass);
    expect(reply).toHaveBeenCalledWith(response, {statusCode, code, message}, statusCode);
  });
});
```

- [ ] **Step 6: Write `currency-rate-scheduler.controller.spec.ts`**

```ts
import {SCHEDULE_CRON_OPTIONS} from '@nestjs/schedule/dist/schedule.constants';

import {CleanupIdempotencyKeysUseCase} from '#usecases/cron/cleanup-idempotency-keys.usecase';
import {FetchDailyCurrencyRatesUseCase} from '#usecases/cron/fetch-daily-currency-rates.usecase';

import {CurrencyRateSchedulerController} from '#api/cron/currency-rate-scheduler.controller';

describe('CurrencyRateSchedulerController', () => {
  const fetchDaily = {execute: jest.fn().mockResolvedValue(undefined)};
  const cleanup = {execute: jest.fn().mockResolvedValue(undefined)};
  const controller = new CurrencyRateSchedulerController(
    fetchDaily as unknown as FetchDailyCurrencyRatesUseCase,
    cleanup as unknown as CleanupIdempotencyKeysUseCase,
  );

  it.each(['handleCron', 'handleIdempotencyCleanup'] as const)('schedules %s daily at midnight UTC', (method) => {
    expect(Reflect.getMetadata(SCHEDULE_CRON_OPTIONS, controller[method])).toEqual({
      cronTime: '0 0 * * *',
      timeZone: 'UTC',
    });
  });

  it('delegates to the use cases', async () => {
    await controller.handleCron();
    await controller.handleIdempotencyCleanup();

    expect(fetchDaily.execute).toHaveBeenCalledTimes(1);
    expect(cleanup.execute).toHaveBeenCalledTimes(1);
  });
});
```

If `Reflect.getMetadata` returns `undefined`, inspect `node_modules/@nestjs/schedule/dist/decorators/cron.decorator.js` for the metadata key and target it uses, adjust the lookup, and note it in the summary.

- [ ] **Step 7: Expose the env parser in `src/config.ts` and test it**

Change `const validatedEnv = envSchema.parse(process.env);` to:

```ts
export const parseEnv = (raw: NodeJS.ProcessEnv): z.infer<typeof envSchema> => envSchema.parse(raw);

const validatedEnv = parseEnv(process.env);
```

`src/__tests__/config.test.ts`:

```ts
import {ZodError} from 'zod';

import {parseEnv} from '../config';

const complete = {
  POSTGRES_PORT: '5432',
  POSTGRES_USER_NAME: 'postgres',
  POSTGRES_PASSWORD: 'postgres',
  POSTGRES_DATABASE: 'database',
  POSTGRES_HOST: 'localhost',
  POSTGRES_SCHEMA: 'public',
  OPEN_EXCHANGE_RATES_API_ID: 'id',
  DEVTOOLS_SECRET: 'secret',
};

describe('parseEnv', () => {
  it('accepts a complete environment and defaults the OTel service name', () => {
    expect(parseEnv(complete)).toMatchObject({...complete, OTEL_SERVICE_NAME: 'commonex-backend'});
  });

  it('keeps an explicit OTel service name', () => {
    expect(parseEnv({...complete, OTEL_SERVICE_NAME: 'x'}).OTEL_SERVICE_NAME).toBe('x');
  });

  it('rejects a missing required variable', () => {
    const {POSTGRES_HOST: _host, ...incomplete} = complete;

    expect(() => parseEnv(incomplete)).toThrow(ZodError);
  });
});
```

- [ ] **Step 8: Verify and commit**

```bash
npm run format && npx jest --runInBand --ci src/frameworks/currency-rate-service src/api src/__tests__
git add src
git commit -m "test(backend): unit-test currency rate client, devtools guard, filters, scheduler and env parsing"
```

Then the full verification command.

---

### Task 12: DB-backed tests for cleanup and currency seeding

**Files:**
- Create: `src/usecases/cron/__tests__/cleanup-idempotency-keys.usecase.test.ts`, `src/frameworks/__tests__/frameworks.layer.test.ts`

- [ ] **Step 1: Write `cleanup-idempotency-keys.usecase.test.ts`**

```ts
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {CleanupIdempotencyKeysUseCase} from '#usecases/cron/cleanup-idempotency-keys.usecase';

import {createTestRelationalDataService, truncateAllTables} from '#test-support/db';
import {prepareInitRelationalState} from '#test-support/relational-state';

describe('CleanupIdempotencyKeysUseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: CleanupIdempotencyKeysUseCase;

  beforeAll(async () => {
    relationalDataService = createTestRelationalDataService();
    useCase = new CleanupIdempotencyKeysUseCase(relationalDataService);
    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
  });

  it('deletes expired keys and keeps live ones', async () => {
    const hour = 60 * 60 * 1000;
    const base = {url: '/u', requestHash: 'h', response: {}, statusCode: 200, createdAt: new Date()};
    await prepareInitRelationalState({
      rDataService: relationalDataService,
      initState: {
        idempotencyKeys: [
          {...base, key: 'expired', expiresAt: new Date(Date.now() - hour)},
          {...base, key: 'live', expiresAt: new Date(Date.now() + hour)},
        ],
      },
    });

    await useCase.execute();
    const [remaining] = await relationalDataService.idempotencyKey.findAll({limit: 10});

    expect(remaining.map((record) => record.key)).toEqual(['live']);
  });
});
```

- [ ] **Step 2: Write `frameworks.layer.test.ts`**

```ts
import {CurrencyCode} from '#domain/entities/currency.entity';

import {initOrUpdateCurrencies} from '#frameworks/frameworks.layer';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {createTestRelationalDataService, truncateAllTables} from '#test-support/db';

import {CURRENCIES_LIST} from '../../constants';

describe('initOrUpdateCurrencies', () => {
  let relationalDataService: RelationalDataService;
  const expectedCodes = CURRENCIES_LIST.map(({code}) => code).sort();

  beforeAll(async () => {
    relationalDataService = createTestRelationalDataService();
    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
  });

  it('seeds every supported currency into an empty table', async () => {
    await initOrUpdateCurrencies(relationalDataService);

    const [currencies] = await relationalDataService.currency.findAllSupported({orderBy: 'code'});

    expect(currencies.map(({code}) => code)).toEqual(expectedCodes);
  });

  it('backfills missing currencies without touching existing rows', async () => {
    await relationalDataService.currency.insert({
      id: 'existing-usd',
      code: CurrencyCode.USD,
      createdAt: new Date('2020-01-01T00:00:00Z'),
      updatedAt: new Date('2020-01-01T00:00:00Z'),
    });

    await initOrUpdateCurrencies(relationalDataService);

    const [currencies] = await relationalDataService.currency.findAllSupported({orderBy: 'code'});
    const usd = currencies.find(({code}) => code === CurrencyCode.USD);

    expect(currencies.map(({code}) => code)).toEqual(expectedCodes);
    expect(usd).toMatchObject({id: 'existing-usd'});
  });

  it('changes nothing when every currency exists', async () => {
    await initOrUpdateCurrencies(relationalDataService);
    const [before] = await relationalDataService.currency.findAllSupported({orderBy: 'code'});

    await initOrUpdateCurrencies(relationalDataService);
    const [after] = await relationalDataService.currency.findAllSupported({orderBy: 'code'});

    expect(after).toEqual(before);
  });
});
```

- [ ] **Step 3: Verify and commit**

```bash
npm run format && npx jest --runInBand --ci src/usecases/cron src/frameworks/__tests__
git add src
git commit -m "test(backend): cover idempotency cleanup and startup currency seeding"
```

Then the full verification command.

---

### Task 13: Coverage in CI, thresholds, and the testing guide

**Files:**
- Modify: `package.json` (`coverageThreshold`), `.github/workflows/main.yml`, `AGENTS.md`

- [ ] **Step 1: Measure**

```bash
npm run test:cov 2>&1 | tail -8
```

Note the four `text-summary` percentages (statements, branches, functions, lines).

- [ ] **Step 2: Set thresholds**

Add to the jest block in `package.json`, using each measured value rounded down to a whole number minus 5:

```json
    "coverageThreshold": {
      "global": {
        "statements": <measured - 5>,
        "branches": <measured - 5>,
        "functions": <measured - 5>,
        "lines": <measured - 5>
      }
    },
```

Put the measured values in the commit message body.

- [ ] **Step 3: CI**

In `.github/workflows/main.yml`, job `backend-checks`, change the `Test` step to `run: npm run test:cov`. Keep the `E2E test` step after it.

- [ ] **Step 4: Rewrite the `## Testing` section of `AGENTS.md`**

Replace the whole section with:

````markdown
## Testing

```bash
npm run test        # unit and DB-backed tests under src/ (jest --ci, snapshots never rewritten implicitly)
npm run test:e2e    # application tests under test/ (HTTP via Fastify inject, gRPC via a real client)
npm run test:cov    # same as test with coverage; CI runs this and enforces coverageThreshold in package.json
```

Tiers:

- Unit tests sit next to the code in `__tests__/` and need no database: value objects, `EventService`,
  `CurrencyRateService` with a mocked `HttpService`, filters, guards, controllers with mocked use cases.
- DB-backed tests (`src/usecases/**`, `src/frameworks/**`) open a real Postgres through
  `createTestRelationalDataService()` from `src/test-support/db.ts` and truncate with `truncateAllTables()`.
  Use-case tests are table-driven with `TestCase` from `src/test-support/relational-state.ts`; outcomes are produced
  by database state, never by stubbing `EventService` or `IdempotencySharedUseCase`.
- Application tests (`test/**/*.e2e-spec.ts`) boot `AppModule` through `src/app.factory.ts`, the same code
  `src/main.ts` uses. `createTestApp()` in `test/support/test-app.ts` returns the app, the data service, an optional
  gRPC url, and `reset()` which truncates and re-seeds currencies.

Repository specs assert `queryDetails` with `toMatchSnapshot()`. Those snapshots are the record of the SQL TypeORM
generates and are the guard for TypeORM upgrades: `npm test` runs with `--ci`, so a changed query fails instead of
rewriting the snapshot. Update them only on purpose with `npx jest --runInBand -u <spec>` and review the `.snap` diff
in the same change. Put behavioural assertions next to the snapshot when the SQL shape encodes a rule.
`schema-drift.spec.ts` fails when the entities and the applied migrations disagree; add a migration, not an exception.

Test names are English and start with a third-person verb describing the outcome.

Tests need a live PostgreSQL. `docker-compose.test.yml` starts one that reuses the production `db` service definition
from `infra/docker-compose-prod.yml` and reads credentials from `.env` (`cp example.env .env`):

```bash
docker compose -f docker-compose.test.yml up --wait db
npm run db:migrate
npm run test
npm run test:e2e
docker compose -f docker-compose.test.yml down -v
```

For PowerShell-specific notes, see [`docs/troubleshooting.md`](docs/troubleshooting.md).

CI (`.github/workflows/main.yml`, job `backend-checks`) runs `typecheck`, `lint:check`, `format:check`, `db:migrate`,
`test:cov` and `test:e2e` on every pull request or push that touches `backend/` or `infra/`; production deploys wait
for it.
````

Remove the earlier snapshot paragraph and the earlier `schema-drift` bullet added in Tasks 8 and 9 if they now duplicate this section. Update `## Validation Steps` to list `npm run test:e2e` after `npm run test`.

- [ ] **Step 5: Verify and commit**

```bash
npm run test:cov && npm run test:e2e
git add package.json .github/workflows/main.yml AGENTS.md
git commit -m "ci(backend): enforce coverage thresholds and run e2e tests in backend-checks"
```

Then the full verification command one last time, plus `npm run build`.
