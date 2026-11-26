import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  const apiBase = env.VITE_API_BASE || '/api'
  const uploadsBase = env.VITE_UPLOADS_BASE || '/uploads'
  const backendTarget = env.VITE_BACKEND_TARGET || 'http://localhost:8083'

  return {
    base: '/mes-front-/',
    plugins: [
      vue(),
      AutoImport({
        resolvers: [ElementPlusResolver()],
        imports: ['vue', 'vue-router', 'pinia']
      }),
      Components({
        resolvers: [ElementPlusResolver()]
      })
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    optimizeDeps: {
      include: ['dagre', '@vue-flow/core', '@vue-flow/background', '@vue-flow/controls']
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        [apiBase]: {
          target: backendTarget,
          changeOrigin: true,
          rewrite: path => path.replace(new RegExp(`^${apiBase}`), '/api')
        },
        [uploadsBase]: {
          target: backendTarget,
          changeOrigin: true,
          rewrite: path => path.replace(new RegExp(`^${uploadsBase}`), '/uploads')
        },
        // 所有以 /uploads- 开头的自定义前缀都转发到后端(如 /uploads-enlechi, /uploads- 等)
        '^/uploads-.*': {
          target: backendTarget,
          changeOrigin: true
        }
      }
    }
  }
})
