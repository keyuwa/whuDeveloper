import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { vitePluginForArco } from '@arco-plugins/vite-vue'

export default defineConfig({
  plugins: [
    vue(),
    vitePluginForArco({ style: 'css' }),
  ],
  server: {
    port: 5174,
    proxy: {
      '/api': 'http://localhost:8081',
    },
  },
})
