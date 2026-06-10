import pluginVue from 'eslint-plugin-vue'
import vueTsEslintConfig from '@vue/eslint-config-typescript'
import prettier from '@vue/eslint-config-prettier'

export default [
  {
    name: 'app/files-to-lint',
    files: ['**/*.{ts,mts,tsx,vue}'],
  },
  {
    name: 'app/files-to-ignore',
    ignores: ['**/dist/**', '**/node_modules/**', '**/*.d.ts'],
  },
  ...pluginVue.configs['flat/essential'],
  ...vueTsEslintConfig(),
  prettier,
  {
    rules: {
      'vue/multi-word-component-names': 'off',
    },
  },
]
