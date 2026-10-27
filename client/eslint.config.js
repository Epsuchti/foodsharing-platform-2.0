// @ts-check
const eslint = require('@eslint/js');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

/**
 * D5.3: application endpoints are only reached through the generated OpenAPI client (src/app/api).
 * Raw HttpClient access is limited to core/ (interceptors, i18n catalogs, framework endpoints such as
 * /webauthn/** and /oauth2/**) and the generated api/ folder. Spec files may use the HTTP testing API.
 */
const restrictHttp = {
  'no-restricted-imports': [
    'error',
    {
      paths: [
        {
          name: '@angular/common/http',
          message:
            'Use the generated services from src/app/api (or a core/ service). Raw HttpClient is only allowed in src/app/core/** and src/app/api/**.',
        },
      ],
    },
  ],
};

module.exports = tseslint.config(
  {
    ignores: ['src/app/api/**', 'dist/**', '.angular/**', 'node_modules/**', 'public/**'],
  },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...tseslint.configs.stylistic,
      ...angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'fs', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: ['fs', 'app'], style: 'kebab-case' },
      ],
      '@angular-eslint/prefer-on-push-component-change-detection': 'error',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      ...restrictHttp,
    },
  },
  {
    files: ['src/app/core/**/*.ts', 'src/app/api/**/*.ts', 'src/app/app.config.ts', 'src/**/*.spec.ts'],
    rules: {
      'no-restricted-imports': 'off',
    },
  },
  {
    files: ['**/*.html'],
    extends: [...angular.configs.templateRecommended, ...angular.configs.templateAccessibility],
    rules: {},
  },
);
