import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 前后端分离：开发时前端跑 5173，/api 代理到后端 8080，免去跨域问题
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
