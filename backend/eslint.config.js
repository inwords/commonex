const {defineConfig} = require('eslint/config');
const tsPlugin = require('@typescript-eslint/eslint-plugin');
const prettier = require('eslint-config-prettier/flat');
const globals = require('globals');

const productionTestFiles = ['**/*.test.ts', '**/*.spec.ts', '**/__tests__/**/*.ts', 'src/test-support/**/*.ts'];
const testSupportPatterns = [
  {regex: '^#test-support/', message: 'Test helpers must not be imported from production code.'},
  {regex: '(^|/)test-support/', message: 'Test helpers must not be imported from production code.'},
];
const transportPattern = {
  regex: '^(typeorm|@grpc/[^/]+|@fastify/[^/]+|fastify|express|class-validator|class-transformer)(/|$)',
  message: 'Persistence and transport dependencies belong in adapters.',
};
const layerPattern = (layers) => ({
  regex: `^(?:#(?:${layers})(/|$)|\\.{1,2}/(?:[^/]+/)*(?:${layers})(/|$))`,
  message: 'This layer must not import the specified layer.',
});
const restrictedImports = (patterns) => [
  'error',
  {
    patterns: [
      ...testSupportPatterns,
      {
        regex: '^#(api|domain|usecases|frameworks|packages|test-support)/(?:[^/]+/)*\\.\\.(/|$)',
        message: 'Use a canonical alias path without parent traversal so layer restrictions cannot be bypassed.',
      },
      ...patterns,
    ],
  },
];

module.exports = defineConfig([
  {
    ignores: ['dist/**', 'coverage/**'],
  },
  {
    files: ['**/*.ts'],
    extends: [tsPlugin.configs['flat/strict-type-checked'], tsPlugin.configs['flat/stylistic-type-checked'], prettier],

    languageOptions: {
      sourceType: 'module',

      parserOptions: {
        projectService: true,
        tsconfigRootDir: __dirname,
      },

      globals: {
        ...globals.node,
        ...globals.jest,
      },
    },

    rules: {
      '@typescript-eslint/explicit-function-return-type': 'error',
      '@typescript-eslint/explicit-module-boundary-types': 'error',
      '@typescript-eslint/no-explicit-any': 'error',
      // strict-type-checked forbids numbers in template literals; they format predictably, so allow them.
      '@typescript-eslint/restrict-template-expressions': ['error', {allowNumber: true}],
      // Nest modules are decorated classes with no members.
      '@typescript-eslint/no-extraneous-class': ['error', {allowWithDecorator: true}],

      // Domain errors are plain classes mapped by exception filters; they intentionally do not extend Error.
      '@typescript-eslint/only-throw-error': [
        'error',
        {
          allow: [
            {
              from: 'file',
              path: 'src/domain/errors/errors.ts',
              name: [
                'EventNotFoundError',
                'EventDeletedError',
                'InvalidPinCodeError',
                'InvalidTokenError',
                'TokenExpiredError',
                'CurrencyNotFoundError',
                'CurrencyRateNotFoundError',
                'InconsistentExchangedAmountError',
                'IdempotencyHashMismatchError',
              ],
            },
          ],
        },
      ],
      'max-len': [
        'error',
        {
          code: 160,
          ignoreUrls: true,
          ignoreStrings: true,
          ignoreTemplateLiterals: true,
          ignoreRegExpLiterals: true,
        },
      ],
    },
  },
  {
    files: ['src/api/**/*.controller.ts'],

    rules: {
      // DTOs are data-only classes; spreading them into use case inputs is the intended mapping.
      '@typescript-eslint/no-misused-spread': 'off',
    },
  },
  {
    files: ['src/**/*.ts'],
    ignores: productionTestFiles,

    rules: {
      // Test helpers must never reach production code; the build exclude alone does not stop an import.
      // `regex` is required here: plain string patterns are matched gitignore-style, so a leading `#` would be
      // read as a comment and the rule would silently never fire. The second pattern closes the relative-path
      // escape hatch (`../../test-support/db`).
      'no-restricted-imports': restrictedImports([]),
    },
  },
  {
    files: ['src/domain/**/*.ts'],
    ignores: productionTestFiles,
    rules: {
      'no-restricted-imports': restrictedImports([
        layerPattern('api|usecases|frameworks'),
        transportPattern,
        {regex: '^@nestjs(/|$)', message: 'Domain must not depend on Nest.'},
      ]),
    },
  },
  {
    files: ['src/usecases/**/*.ts'],
    ignores: productionTestFiles,
    rules: {
      'no-restricted-imports': restrictedImports([layerPattern('api')]),
    },
  },
  {
    files: ['src/frameworks/**/*.ts'],
    ignores: productionTestFiles,
    rules: {
      'no-restricted-imports': restrictedImports([layerPattern('api|usecases')]),
    },
  },
  {
    files: ['**/*.test.ts', '**/*.spec.ts', '**/__tests__/**/*.ts', 'test/**/*.ts', 'src/test-support/**/*.ts'],

    rules: {
      // Jest asymmetric matchers such as expect.any() are typed as any.
      '@typescript-eslint/no-unsafe-assignment': 'off',
    },
  },
]);
