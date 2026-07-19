import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  base: process.env.VITE_BASE_PATH || '/v2/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5174,
    host: true,
    allowedHosts: ['localhost', '127.0.0.1'],
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
        xfwd: true,
        configure(proxy) {
          proxy.on('proxyReq', (proxyRequest) => {
            // Browser Origin describes the Vite origin, not the upstream hop.
            // Dropping it keeps this development reverse proxy same-origin to Spring.
            proxyRequest.removeHeader('origin')
          })
        },
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 300,
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          if (id.includes('node_modules')) return 'vendor-vue'
          return undefined
        },
      },
    },
  },
})
