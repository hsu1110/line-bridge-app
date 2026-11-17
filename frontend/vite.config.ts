import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 純 Web 版建置與開發設定：直接打包輸出至 Spring Boot static 目錄
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:3000',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
  },
})
