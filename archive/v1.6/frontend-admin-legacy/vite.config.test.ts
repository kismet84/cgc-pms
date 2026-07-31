import { describe, it, expect } from 'vitest'

// Replicate the manualChunks logic as a pure function for testing.
// This mirrors the expected rollupOptions.output.manualChunks in vite.config.ts.
function manualChunks(id: string): string | undefined {
  if (id.includes('node_modules')) {
    // Check specific packages FIRST before generic 'vue' substring check.
    // 'ant-design-vue', '@ant-design/icons-vue', and 'vue-echarts' all
    // contain 'vue' — ordering matters to avoid mis-classification.
    if (id.includes('ant-design-vue') || id.includes('@ant-design/icons-vue')) {
      return 'vendor-antd'
    }
    if (id.includes('vue-echarts')) {
      return 'vendor-echarts'
    }
    if (id.includes('echarts')) {
      return 'vendor-echarts'
    }
    if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) {
      return 'vendor-vue'
    }
    if (id.includes('vxe-table') || id.includes('vxe-pc-ui')) {
      return 'vendor-vxe'
    }
    return 'vendor'
  }
  return undefined
}

describe('vite manualChunks', () => {
  describe('vendor-vue chunk', () => {
    it('node_modules/vue → vendor-vue', () => {
      expect(manualChunks('/node_modules/vue/dist/vue.runtime.esm.js')).toBe('vendor-vue')
    })
    it('node_modules/vue-router → vendor-vue', () => {
      expect(manualChunks('/node_modules/vue-router/dist/vue-router.mjs')).toBe('vendor-vue')
    })
    it('node_modules/pinia → vendor-vue', () => {
      expect(manualChunks('/node_modules/pinia/dist/pinia.mjs')).toBe('vendor-vue')
    })
  })

  describe('vendor-antd chunk', () => {
    it('node_modules/ant-design-vue → vendor-antd', () => {
      expect(manualChunks('/node_modules/ant-design-vue/es/button/index.mjs')).toBe('vendor-antd')
    })
    it('node_modules/@ant-design/icons-vue → vendor-antd', () => {
      expect(manualChunks('/node_modules/@ant-design/icons-vue/es/index.js')).toBe('vendor-antd')
    })
  })

  describe('vendor-echarts chunk', () => {
    it('node_modules/echarts → vendor-echarts', () => {
      expect(manualChunks('/node_modules/echarts/core.js')).toBe('vendor-echarts')
    })
    it('node_modules/vue-echarts → vendor-echarts', () => {
      expect(manualChunks('/node_modules/vue-echarts/dist/index.mjs')).toBe('vendor-echarts')
    })
  })

  describe('vendor-vxe chunk', () => {
    it('node_modules/vxe-table → vendor-vxe', () => {
      expect(manualChunks('/node_modules/vxe-table/es/index.js')).toBe('vendor-vxe')
    })
    it('node_modules/vxe-pc-ui → vendor-vxe', () => {
      expect(manualChunks('/node_modules/vxe-pc-ui/es/index.js')).toBe('vendor-vxe')
    })
  })

  describe('general vendor chunk', () => {
    it('other node_modules → vendor', () => {
      expect(manualChunks('/node_modules/axios/lib/axios.js')).toBe('vendor')
    })
  })

  describe('application code (no chunk)', () => {
    it('src/ files → undefined (lazy page chunks)', () => {
      expect(manualChunks('/src/pages/dashboard/index.vue')).toBeUndefined()
      expect(manualChunks('/src/layouts/BasicLayout.vue')).toBeUndefined()
      expect(manualChunks('/src/components/SidebarMenu.vue')).toBeUndefined()
    })
  })
})
