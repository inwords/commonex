import {createRequire} from 'node:module';
import {resolve} from 'path';

import {ESLint, Linter} from 'eslint';

const backendRoot = resolve(__dirname, '../..');
const loadConfig = createRequire(__filename);
const eslint = new ESLint({
  cwd: backendRoot,
  overrideConfigFile: true,
  overrideConfig: loadConfig('../../eslint.config.js') as Linter.Config[],
});
const linter = new Linter({cwd: backendRoot, configType: 'flat'});

interface ImportCase {
  name: string;
  filePath: string;
  source: string;
  allowed: boolean;
}

interface CalculatedLintConfig {
  languageOptions: {
    parser: Linter.Parser;
    parserOptions?: Linter.ParserOptions;
    sourceType?: 'script' | 'module' | 'commonjs';
  };
  rules: Record<string, Linter.RuleEntry | undefined>;
}

const importCases: ImportCase[] = [
  {
    name: 'allows domain imports from packages and platform libraries',
    filePath: 'src/domain/entities/example.ts',
    source:
      "import {createHash} from 'node:crypto'; import {ulid} from 'ulid'; import {Result} from '#packages/result';",
    allowed: true,
  },
  {
    name: 'allows domain imports from OpenTelemetry API',
    filePath: 'src/domain/example.ts',
    source: "import {trace} from '@opentelemetry/api';",
    allowed: true,
  },
  {
    name: 'allows domain imports across internal domain modules',
    filePath: 'src/domain/entities/example.ts',
    source:
      "import {User} from '#domain/entities/user.entity'; import {Money} from '#domain/value-objects/money'; import {IClock} from '#domain/abstracts/clock.abstract';",
    allowed: true,
  },
  {
    name: 'allows use cases to import domain ports and Nest common DI',
    filePath: 'src/usecases/example.usecase.ts',
    source: "import {Injectable} from '@nestjs/common'; import {IEvent} from '#domain/entities/event.entity';",
    allowed: true,
  },
  {
    name: 'allows use cases to import concrete framework adapters by alias and relative path',
    filePath: 'src/usecases/users/example.usecase.ts',
    source:
      "import {EventRepository} from '#frameworks/relational-data-service/event.repository'; import {adapter} from '../../frameworks/adapters/example';",
    allowed: true,
  },
  {
    name: 'allows use cases to use a namespace Nest common import',
    filePath: 'src/usecases/example.usecase.ts',
    source: "import * as NestCommon from '@nestjs/common';",
    allowed: true,
  },
  {
    name: 'allows framework adapters to import domain ports and TypeORM',
    filePath: 'src/frameworks/example.adapter.ts',
    source: "import {DataSource} from 'typeorm'; import {IEvent} from '#domain/entities/event.entity';",
    allowed: true,
  },
  {
    name: 'allows API to import domain, use case, and framework layers',
    filePath: 'src/api/http/example.controller.ts',
    source:
      "import {IEvent} from '#domain/entities/event.entity'; import {SaveEventUseCase} from '#usecases/users/save-event.usecase'; import {EventRepository} from '#frameworks/event.repository';",
    allowed: true,
  },
  {
    name: 'allows the exact FrameworksLayer alias in the composition file',
    filePath: 'src/usecases/usecases.layer.ts',
    source: "import {FrameworksLayer} from '#frameworks/frameworks.layer';",
    allowed: true,
  },
  {
    name: 'allows the exact FrameworksLayer relative path in the composition file',
    filePath: 'src/usecases/usecases.layer.ts',
    source: "import {FrameworksLayer} from '../frameworks/frameworks.layer';",
    allowed: true,
  },
  {
    name: 'allows only the supported Nest composition symbols in the composition file',
    filePath: 'src/usecases/usecases.layer.ts',
    source: "import {Injectable, Module, Provider} from '@nestjs/common';",
    allowed: true,
  },
  {
    name: 'allows layer imports from unit-test files',
    filePath: 'src/domain/__tests__/example.spec.ts',
    source: "import {FrameworksLayer} from '#frameworks/frameworks.layer'; import {Controller} from '@nestjs/common';",
    allowed: true,
  },
  {
    name: 'allows layer imports from test support and application tests',
    filePath: 'test/example.e2e-spec.ts',
    source: "import {DataSource} from 'typeorm'; import {UserController} from '#api/user.controller';",
    allowed: true,
  },
  {
    name: 'allows layer imports from a source spec file outside __tests__',
    filePath: 'src/domain/example.spec.ts',
    source: "import {UserController} from '#api/http/user.controller';",
    allowed: true,
  },
  {
    name: 'allows layer imports from a source test file outside __tests__',
    filePath: 'src/usecases/example.test.ts',
    source: "import {EventRepository} from '#frameworks/event.repository';",
    allowed: true,
  },
  {
    name: 'blocks domain imports into API',
    filePath: 'src/domain/example.ts',
    source: "import type {UserController} from '#api/http/user.controller';",
    allowed: false,
  },
  {
    name: 'blocks domain imports into use cases',
    filePath: 'src/domain/example.ts',
    source: "import {SaveEventUseCase} from '#usecases/users/save-event.usecase';",
    allowed: false,
  },
  {
    name: 'blocks domain imports into frameworks',
    filePath: 'src/domain/example.ts',
    source: "import {EventRepository} from '#frameworks/relational-data-service/event.repository';",
    allowed: false,
  },
  {
    name: 'blocks relative domain exports into an outer layer',
    filePath: 'src/domain/entities/example.ts',
    source: "export type {UserController} from '../../api/http/user.controller';",
    allowed: false,
  },
  {
    name: 'blocks domain imports from Nest subpaths',
    filePath: 'src/domain/example.ts',
    source: "import type {HttpStatus} from '@nestjs/common'; import {Reflector} from '@nestjs/core';",
    allowed: false,
  },
  {
    name: 'blocks domain imports from TypeORM subpaths',
    filePath: 'src/domain/example.ts',
    source: "import type {EntityManager} from 'typeorm/entity-manager';",
    allowed: false,
  },
  {
    name: 'blocks domain imports from gRPC packages',
    filePath: 'src/domain/example.ts',
    source: "import {Metadata} from '@grpc/grpc-js';",
    allowed: false,
  },
  {
    name: 'blocks domain imports from HTTP server packages',
    filePath: 'src/domain/example.ts',
    source: "import type {FastifyRequest} from 'fastify/types/request'; import type {Request} from 'express';",
    allowed: false,
  },
  {
    name: 'blocks domain imports from Fastify subpackages',
    filePath: 'src/domain/example.ts',
    source: "import {staticPlugin} from '@fastify/static';",
    allowed: false,
  },
  {
    name: 'blocks domain imports from transport validation packages',
    filePath: 'src/domain/example.ts',
    source: "import {IsString} from 'class-validator'; import {Type} from 'class-transformer';",
    allowed: false,
  },
  {
    name: 'blocks use case imports into API',
    filePath: 'src/usecases/example.usecase.ts',
    source: "export {UserController} from '#api/http/user.controller';",
    allowed: false,
  },
  {
    name: 'allows use case imports into concrete frameworks',
    filePath: 'src/usecases/example.usecase.ts',
    source: "import type {EventRepository} from '#frameworks/relational-data-service/event.repository';",
    allowed: true,
  },
  {
    name: 'blocks relative use case imports into API and frameworks',
    filePath: 'src/usecases/users/example.usecase.ts',
    source:
      "import {Controller} from '../../api/http/user.controller'; export {Adapter} from '../../frameworks/adapter';",
    allowed: false,
  },
  {
    name: 'allows use cases to import TypeORM',
    filePath: 'src/usecases/example.usecase.ts',
    source: "import type {FindOptionsWhere} from 'typeorm/find-options/FindOptionsWhere';",
    allowed: true,
  },
  {
    name: 'allows use cases to import transport packages',
    filePath: 'src/usecases/example.usecase.ts',
    source: "import {status} from '@grpc/grpc-js'; import type {FastifyReply} from 'fastify';",
    allowed: true,
  },
  {
    name: 'allows use cases to import Nest packages',
    filePath: 'src/usecases/example.usecase.ts',
    source: "import {HttpStatus} from '@nestjs/common'; import {Reflector} from '@nestjs/core';",
    allowed: true,
  },
  {
    name: 'allows use cases to import Nest subpaths',
    filePath: 'src/usecases/example.usecase.ts',
    source: "import {Injectable} from '@nestjs/common/decorators';",
    allowed: true,
  },
  {
    name: 'allows default and namespace Nest imports in use cases',
    filePath: 'src/usecases/example.usecase.ts',
    source: "import NestCommon from '@nestjs/common'; import * as NestCore from '@nestjs/common';",
    allowed: true,
  },
  {
    name: 'blocks framework imports into API',
    filePath: 'src/frameworks/example.adapter.ts',
    source: "import {UserController} from '#api/http/user.controller';",
    allowed: false,
  },
  {
    name: 'blocks framework imports into use cases',
    filePath: 'src/frameworks/example.adapter.ts',
    source: "export type {SaveEventUseCase} from '#usecases/users/save-event.usecase';",
    allowed: false,
  },
  {
    name: 'blocks relative framework imports into API and use cases',
    filePath: 'src/frameworks/adapter/example.ts',
    source:
      "import {Controller} from '../../api/http/user.controller'; export {UseCase} from '../../usecases/example.usecase';",
    allowed: false,
  },
  {
    name: 'allows any named import from the frameworks layer in composition',
    filePath: 'src/usecases/usecases.layer.ts',
    source: "import {FrameworksLayer, InternalDatabaseAdapter} from '#frameworks/frameworks.layer';",
    allowed: true,
  },
  {
    name: 'allows default imports from the frameworks layer in composition',
    filePath: 'src/usecases/usecases.layer.ts',
    source: "import FrameworksLayer from '#frameworks/frameworks.layer';",
    allowed: true,
  },
  {
    name: 'allows namespace imports from the frameworks layer in composition',
    filePath: 'src/usecases/usecases.layer.ts',
    source: "import * as Frameworks from '#frameworks/frameworks.layer';",
    allowed: true,
  },
  {
    name: 'allows any Nest symbol in the composition file',
    filePath: 'src/usecases/usecases.layer.ts',
    source: "import {HttpStatus} from '@nestjs/common';",
    allowed: true,
  },
  {
    name: 'allows default and namespace Nest imports in the composition file',
    filePath: 'src/usecases/usecases.layer.ts',
    source: "import NestCommon from '@nestjs/common'; import * as NestCore from '@nestjs/common';",
    allowed: true,
  },
  {
    name: 'allows other framework aliases in the composition file',
    filePath: 'src/usecases/usecases.layer.ts',
    source: "import {DatabaseAdapter} from '#frameworks/database.adapter';",
    allowed: true,
  },
  {
    name: 'allows framework subpaths in the composition file',
    filePath: 'src/usecases/usecases.layer.ts',
    source: "import {FrameworksLayer} from '#frameworks/frameworks.layer/extra';",
    allowed: true,
  },
  {
    name: 'allows relative framework paths in the composition file',
    filePath: 'src/usecases/usecases.layer.ts',
    source: "import {FrameworksLayer} from '../frameworks/frameworks.layer-extra';",
    allowed: true,
  },
  {
    name: 'blocks test-support aliases in domain production files',
    filePath: 'src/domain/example.ts',
    source: "import {fixture} from '#test-support/fixture';",
    allowed: false,
  },
  {
    name: 'blocks relative test-support paths in domain production files',
    filePath: 'src/domain/entities/example.ts',
    source: "import {fixture} from '../../test-support/fixture';",
    allowed: false,
  },
  {
    name: 'blocks test-support aliases in use case production files',
    filePath: 'src/usecases/example.ts',
    source: "import {fixture} from '#test-support/fixture';",
    allowed: false,
  },
  {
    name: 'blocks relative test-support paths in use case production files',
    filePath: 'src/usecases/example.ts',
    source: "import {fixture} from '../test-support/fixture';",
    allowed: false,
  },
  {
    name: 'blocks test-support aliases in framework production files',
    filePath: 'src/frameworks/example.ts',
    source: "import {fixture} from '#test-support/fixture';",
    allowed: false,
  },
  {
    name: 'blocks relative test-support paths in framework production files',
    filePath: 'src/frameworks/adapters/example.ts',
    source: "import {fixture} from '../../test-support/fixture';",
    allowed: false,
  },
  {
    name: 'blocks test-support aliases in the composition file',
    filePath: 'src/usecases/usecases.layer.ts',
    source: "import {fixture} from '#test-support/fixture';",
    allowed: false,
  },
  {
    name: 'blocks relative test-support paths in the composition file',
    filePath: 'src/usecases/usecases.layer.ts',
    source: "import {fixture} from '../test-support/fixture';",
    allowed: false,
  },
  {
    name: 'exempts unit tests from production layer restrictions',
    filePath: 'src/usecases/__tests__/example.spec.ts',
    source: "import {Controller} from '#api/http/user.controller'; import {Adapter} from '#frameworks/adapter';",
    allowed: true,
  },
  {
    name: 'exempts source test-support files from production layer restrictions',
    filePath: 'src/test-support/example.ts',
    source: "import {Controller} from '#api/http/user.controller'; import {Adapter} from '#frameworks/adapter';",
    allowed: true,
  },
  {
    name: 'blocks use case API imports hidden behind domain alias traversal',
    filePath: 'src/usecases/example.ts',
    source: "import {Controller} from '#domain/../api/http/user.controller';",
    allowed: false,
  },
  {
    name: 'blocks domain framework imports hidden behind domain alias traversal',
    filePath: 'src/domain/example.ts',
    source: "import {Adapter} from '#domain/../frameworks/adapter';",
    allowed: false,
  },
  {
    name: 'blocks framework usecase imports hidden behind domain alias traversal',
    filePath: 'src/frameworks/example.ts',
    source: "export * from '#domain/../usecases/example';",
    allowed: false,
  },
  {
    name: 'blocks production test helpers hidden behind packages alias traversal',
    filePath: 'src/api/example.ts',
    source: "import type {Fixture} from '#packages/types/../../test-support/db';",
    allowed: false,
  },
];

describe('production layer import restrictions', () => {
  const getRestrictedImportMessages = async (testCase: ImportCase): Promise<Linter.LintMessage[]> => {
    const filename = resolve(backendRoot, testCase.filePath);
    const effectiveConfig = (await eslint.calculateConfigForFile(filename)) as CalculatedLintConfig | undefined;
    if (effectiveConfig === undefined) {
      throw new Error(`No ESLint configuration for ${testCase.filePath}`);
    }

    const noRestrictedImports = effectiveConfig.rules['no-restricted-imports'];
    const messages = linter.verify(
      testCase.source,
      {
        files: ['**/*.ts'],
        languageOptions: {
          parser: effectiveConfig.languageOptions.parser,
          parserOptions: {
            ...effectiveConfig.languageOptions.parserOptions,
            projectService: false,
          },
          sourceType: effectiveConfig.languageOptions.sourceType,
        },
        rules: noRestrictedImports === undefined ? {} : {'no-restricted-imports': noRestrictedImports},
      },
      filename,
    );

    expect(messages.filter((message) => message.fatal === true || message.ruleId === null)).toEqual([]);

    return messages.filter((message) => message.ruleId === 'no-restricted-imports');
  };

  it.each(importCases)('$name', async (testCase) => {
    const messages = await getRestrictedImportMessages(testCase);

    if (testCase.allowed) {
      expect(messages).toEqual([]);
    } else {
      expect(messages.length).toBeGreaterThan(0);
    }
  });
});
