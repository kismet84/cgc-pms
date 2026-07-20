import pluginVue from 'eslint-plugin-vue'
import vueTsEslintConfig from '@vue/eslint-config-typescript'
import prettier from '@vue/eslint-config-prettier'

const legacyImportPatterns = [
  '../frontend-admin/**',
  '../../frontend-admin/**',
  '../../../frontend-admin/**',
  'frontend-admin/**',
  '@legacy/**',
]

export default [
  {
    name: 'v2/files-to-lint',
    files: ['**/*.{ts,mts,tsx,vue}'],
  },
  {
    name: 'v2/files-to-ignore',
    ignores: [
      '**/dist/**',
      '**/node_modules/**',
      '**/.pnpm-store/**',
      '**/*.d.ts',
      'tests/fixtures/**',
    ],
  },
  ...pluginVue.configs['flat/essential'],
  ...vueTsEslintConfig(),
  prettier,
  {
    rules: {
      'vue/multi-word-component-names': 'off',
      'vue/no-mutating-props': ['error', { shallowOnly: true }],
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: legacyImportPatterns,
              message: 'Clean-room V2 禁止导入 Legacy frontend-admin 源码、组件或样式。',
            },
          ],
        },
      ],
    },
  },
]
