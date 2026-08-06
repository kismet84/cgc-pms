import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  define: {
    'import.meta.env.VITE_FEATURE_PWA': JSON.stringify('true'),
    'import.meta.env.VITE_FEATURE_OFFLINE_DRAFT': JSON.stringify('true'),
    'import.meta.env.VITE_FEATURE_OFFLINE_SYNC': JSON.stringify('true'),
    'import.meta.env.VITE_FEATURE_FIELD_DAILY_LOG': JSON.stringify('true'),
    'import.meta.env.VITE_FEATURE_FIELD_QUALITY_SAFETY': JSON.stringify('true'),
    'import.meta.env.VITE_FEATURE_NOTIFICATION_MULTI_CLIENT': JSON.stringify('true'),
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
  },
})
