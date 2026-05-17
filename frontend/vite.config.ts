import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/vditor')) {
            return 'vendor-vditor'
          }
          if (id.includes('node_modules/@ant-design/icons-vue')) {
            return 'vendor-icons'
          }
          if (id.includes('node_modules/ant-design-vue')) {
            return 'vendor-antd'
          }
          if (id.includes('node_modules/highlight.js')) {
            return 'vendor-highlight'
          }
          if (id.includes('node_modules')) {
            return 'vendor'
          }
        }
      }
    }
  }
})
