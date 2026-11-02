import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 后端地址。默认 8080；8080 被别的进程占着时，启动前设一下 LOL_API_TARGET 就能换，
// 不用改这个文件。
const API_TARGET = process.env.LOL_API_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 开发环境把后端请求代理过去，避免跨域问题
    proxy: {
      '/api': {
        target: API_TARGET,
        changeOrigin: true
      },
      // 聊天里发的图片和视频由后端直接提供
      '/uploads': {
        target: API_TARGET,
        changeOrigin: true
      },
      // 房间聊天的长连接。不加 ws: true，握手会被当成普通 HTTP 请求转发，连不上。
      '/ws': {
        target: API_TARGET.replace(/^http/, 'ws'),
        ws: true,
        changeOrigin: true
      }
    }
  }
})
