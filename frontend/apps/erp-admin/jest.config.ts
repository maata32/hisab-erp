export default {
  displayName: 'erp-admin',
  preset: '../../jest.preset.js',
  setupFilesAfterEnv: ['<rootDir>/src/test-setup.ts'],
  coverageDirectory: '../../coverage/apps/erp-admin',
  transform: {
    '^.+\\.(ts|mjs|js|html)$': [
      'jest-preset-angular',
      {
        tsconfig: '<rootDir>/tsconfig.spec.json',
        stringifyContentPathRegex: '\\.(html|svg)$',
      },
    ],
  },
  transformIgnorePatterns: [
    'node_modules/(?!.*\\.mjs$|@angular|@ngx-translate|lucide-angular|primeng|rxjs)',
  ],
  moduleNameMapper: {
    '^@hisaberp/shared-api$': '<rootDir>/../../libs/shared-api/src/index.ts',
    '^@hisaberp/shared-auth$': '<rootDir>/../../libs/shared-auth/src/index.ts',
    '^@hisaberp/shared-ui$': '<rootDir>/../../libs/shared-ui/src/index.ts',
    '^@hisaberp/shared-i18n$': '<rootDir>/../../libs/shared-i18n/src/index.ts',
  },
  snapshotSerializers: [
    'jest-preset-angular/build/serializers/no-ng-attributes',
    'jest-preset-angular/build/serializers/ng-snapshot',
    'jest-preset-angular/build/serializers/html-comment',
  ],
};
