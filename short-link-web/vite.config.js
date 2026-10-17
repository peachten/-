import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// 开发期跨域交给 Vite 代理，后端不需要写 CORS 配置
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8000',
        changeOrigin: true
      }
    }
  }
})
