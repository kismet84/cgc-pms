import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    exclude: ['e2e/**', 'node_modules/**'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json-summary', 'html'],
      include: ['src/**/*.{ts,vue}'],
      exclude: ['src/**/*.d.ts', 'src/main.ts', 'src/components.d.ts', 'src/types/**'],
      thresholds: {
        // 当前基线：lines 9.79%, functions 7.84%, branches 10.22%, statements 9.71%
        // 目标：通过 composable + component 测试逐步提升至 55/45/40/55
        lines: 9,
        functions: 7,
        branches: 10,
        statements: 9,
      },
    },
  },
})
