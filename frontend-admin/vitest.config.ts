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
        // 55% 目标声明 — 从 ~6.7% 基线出发，通过页面拆分 + composable 测试逐步达成
        lines: 55,
        functions: 45,
        branches: 40,
        statements: 55,
      },
    },
  },
})
