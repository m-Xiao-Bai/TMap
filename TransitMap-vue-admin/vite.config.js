import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  base: '/admin/',
  plugins: [
    vue(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5174,
    proxy: {
      '/transitMap-admin': {
        target: 'http://localhost:8889',
        changeOrigin: true,
      },
      '/data': {
        target: 'http://localhost:8889',
        changeOrigin: true,
      },
    },
  },
})
