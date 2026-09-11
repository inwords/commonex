const {
    defineConfig,
} = require("eslint/config");

const tsParser = require("@typescript-eslint/parser");
const typescriptEslintEslintPlugin = require("@typescript-eslint/eslint-plugin");
const globals = require("globals");
const js = require("@eslint/js");

const {
    FlatCompat,
} = require("@eslint/eslintrc");

const compat = new FlatCompat({
    baseDirectory: __dirname,
    recommendedConfig: js.configs.recommended,
    allConfig: js.configs.all
});

module.exports = defineConfig([{
    languageOptions: {
        parser: tsParser,
        sourceType: "module",

        parserOptions: {
            project: "tsconfig.json",
            tsconfigRootDir: __dirname,
        },

        globals: {
            ...globals.node,
            ...globals.jest,
        },
    },

    plugins: {
        "@typescript-eslint": typescriptEslintEslintPlugin,
    },

    extends: compat.extends("plugin:@typescript-eslint/recommended-type-checked", "prettier"),

    rules: {
        "@typescript-eslint/interface-name-prefix": "off",
        "@typescript-eslint/explicit-function-return-type": "error",
        "@typescript-eslint/explicit-module-boundary-types": "error",
        "@typescript-eslint/no-explicit-any": "error",

        // Domain errors are plain classes mapped by exception filters; they intentionally do not extend Error.
        "@typescript-eslint/only-throw-error": ["error", {
            allow: [{
                from: "file",
                path: "src/domain/errors/errors.ts",
                name: [
                    "EventNotFoundError",
                    "EventDeletedError",
                    "InvalidPinCodeError",
                    "InvalidTokenError",
                    "TokenExpiredError",
                    "CurrencyNotFoundError",
                    "CurrencyRateNotFoundError",
                    "InconsistentExchangedAmountError",
                    "EventOperationConflictError",
                    "IdempotencyHashMismatchError",
                ],
            }],
        }],
        "max-len": ["error", {
            code: 160,
            ignoreUrls: true,
            ignoreStrings: true,
            ignoreTemplateLiterals: true,
            ignoreRegExpLiterals: true,
        }],
    },
}, {
    files: ["**/*.test.ts", "**/*.spec.ts", "**/__tests__/**/*.ts"],

    rules: {
        // Jest asymmetric matchers such as expect.any() are typed as any.
        "@typescript-eslint/no-unsafe-assignment": "off",
    },
}]);
