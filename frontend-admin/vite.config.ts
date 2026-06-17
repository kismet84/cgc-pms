import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    Components({
      resolvers: [
        AntDesignVueResolver({
          importStyle: false, // ant-design-vue 4.x uses CSS-in-JS, no manual style import needed
        }),
      ],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    host: true, // expose to Docker network (required for HMR + dev container)
    watch: { usePolling: true }, // Docker for Windows: filesystem events don't propagate
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 500,
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          if (id.includes('node_modules')) {
            // Specific checks FIRST — generic 'vue' would also match ant-design-vue, vue-echarts
            if (id.includes('ant-design-vue') || id.includes('@ant-design/icons-vue')) {
              return 'vendor-antd'
            }
            if (id.includes('echarts') || id.includes('vue-echarts')) {
              return 'vendor-echarts'
            }
            if (id.includes('vxe-table') || id.includes('vxe-pc-ui')) {
              return 'vendor-vxe'
            }
            if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) {
              return 'vendor-vue'
            }
            return 'vendor'
          }
          // Return undefined for src/ files — let Vite handle lazy page chunks
          return undefined
        },
      },
    },
  },
})
