/**
 * 开发服务器端口 5173。/api 代理到 127.0.0.1:8080，这样关掉 Mock 时浏览器仍访问 5173，不必改后端 CORS。
 * @ 指向 src。Vitest 跑 tests 目录里的 spec，环境是 node，不启动浏览器。
 */
import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  preview: {
    port: 4173,
  },
  test: {
    environment: 'node',
    include: ['tests/**/*.spec.ts'],
  },
})
