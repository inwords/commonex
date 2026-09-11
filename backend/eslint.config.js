const {defineConfig} = require('eslint/config');
const tsPlugin = require('@typescript-eslint/eslint-plugin');
const prettier = require('eslint-config-prettier/flat');
const globals = require('globals');

module.exports = defineConfig([
  {
    ignores: ['dist/**', 'coverage/**'],
  },
  {
    files: ['**/*.ts'],
    extends: [tsPlugin.configs['flat/recommended-type-checked'], prettier],

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
                'EventOperationConflictError',
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
    files: ['**/*.test.ts', '**/*.spec.ts', '**/__tests__/**/*.ts'],

    rules: {
      // Jest asymmetric matchers such as expect.any() are typed as any.
      '@typescript-eslint/no-unsafe-assignment': 'off',
    },
  },
]);
