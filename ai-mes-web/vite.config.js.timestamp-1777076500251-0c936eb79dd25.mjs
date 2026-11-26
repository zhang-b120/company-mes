// vite.config.js
import { defineConfig, loadEnv } from "file:///F:/ai-mes/ai-mes-web/node_modules/vite/dist/node/index.js";
import vue from "file:///F:/ai-mes/ai-mes-web/node_modules/@vitejs/plugin-vue/dist/index.mjs";
import AutoImport from "file:///F:/ai-mes/ai-mes-web/node_modules/unplugin-auto-import/dist/vite.js";
import Components from "file:///F:/ai-mes/ai-mes-web/node_modules/unplugin-vue-components/dist/vite.js";
import { ElementPlusResolver } from "file:///F:/ai-mes/ai-mes-web/node_modules/unplugin-vue-components/dist/resolvers.js";
import path from "path";
var __vite_injected_original_dirname = "F:\\ai-mes\\ai-mes-web";
var vite_config_default = defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd());
  const apiBase = env.VITE_API_BASE || "/api";
  const uploadsBase = env.VITE_UPLOADS_BASE || "/uploads";
  const backendTarget = env.VITE_BACKEND_TARGET || "http://localhost:8083";
  return {
    base: "/mes-front-/",
    plugins: [
      vue(),
      AutoImport({
        resolvers: [ElementPlusResolver()],
        imports: ["vue", "vue-router", "pinia"]
      }),
      Components({
        resolvers: [ElementPlusResolver()]
      })
    ],
    resolve: {
      alias: {
        "@": path.resolve(__vite_injected_original_dirname, "src")
      }
    },
    optimizeDeps: {
      include: ["dagre", "@vue-flow/core", "@vue-flow/background", "@vue-flow/controls"]
    },
    server: {
      host: "0.0.0.0",
      port: 5173,
      proxy: {
        [apiBase]: {
          target: backendTarget,
          changeOrigin: true,
          rewrite: (path2) => path2.replace(new RegExp(`^${apiBase}`), "/api")
        },
        [uploadsBase]: {
          target: backendTarget,
          changeOrigin: true,
          rewrite: (path2) => path2.replace(new RegExp(`^${uploadsBase}`), "/uploads")
        },
        // 所有以 /uploads- 开头的自定义前缀都转发到后端(如 /uploads-enlechi, /uploads- 等)
        "^/uploads-.*": {
          target: backendTarget,
          changeOrigin: true
        }
      }
    }
  };
});
export {
  vite_config_default as default
};
//# sourceMappingURL=data:application/json;base64,ewogICJ2ZXJzaW9uIjogMywKICAic291cmNlcyI6IFsidml0ZS5jb25maWcuanMiXSwKICAic291cmNlc0NvbnRlbnQiOiBbImNvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9kaXJuYW1lID0gXCJGOlxcXFxhaS1tZXNcXFxcYWktbWVzLXdlYlwiO2NvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9maWxlbmFtZSA9IFwiRjpcXFxcYWktbWVzXFxcXGFpLW1lcy13ZWJcXFxcdml0ZS5jb25maWcuanNcIjtjb25zdCBfX3ZpdGVfaW5qZWN0ZWRfb3JpZ2luYWxfaW1wb3J0X21ldGFfdXJsID0gXCJmaWxlOi8vL0Y6L2FpLW1lcy9haS1tZXMtd2ViL3ZpdGUuY29uZmlnLmpzXCI7aW1wb3J0IHsgZGVmaW5lQ29uZmlnLCBsb2FkRW52IH0gZnJvbSAndml0ZSdcclxuaW1wb3J0IHZ1ZSBmcm9tICdAdml0ZWpzL3BsdWdpbi12dWUnXHJcbmltcG9ydCBBdXRvSW1wb3J0IGZyb20gJ3VucGx1Z2luLWF1dG8taW1wb3J0L3ZpdGUnXHJcbmltcG9ydCBDb21wb25lbnRzIGZyb20gJ3VucGx1Z2luLXZ1ZS1jb21wb25lbnRzL3ZpdGUnXHJcbmltcG9ydCB7IEVsZW1lbnRQbHVzUmVzb2x2ZXIgfSBmcm9tICd1bnBsdWdpbi12dWUtY29tcG9uZW50cy9yZXNvbHZlcnMnXHJcbmltcG9ydCBwYXRoIGZyb20gJ3BhdGgnXHJcblxyXG5leHBvcnQgZGVmYXVsdCBkZWZpbmVDb25maWcoKHsgbW9kZSB9KSA9PiB7XHJcbiAgY29uc3QgZW52ID0gbG9hZEVudihtb2RlLCBwcm9jZXNzLmN3ZCgpKVxyXG4gIGNvbnN0IGFwaUJhc2UgPSBlbnYuVklURV9BUElfQkFTRSB8fCAnL2FwaSdcclxuICBjb25zdCB1cGxvYWRzQmFzZSA9IGVudi5WSVRFX1VQTE9BRFNfQkFTRSB8fCAnL3VwbG9hZHMnXHJcbiAgY29uc3QgYmFja2VuZFRhcmdldCA9IGVudi5WSVRFX0JBQ0tFTkRfVEFSR0VUIHx8ICdodHRwOi8vbG9jYWxob3N0OjgwOTcnXHJcblxyXG4gIHJldHVybiB7XHJcbiAgICBiYXNlOiAnL21lcy1mcm9udC1rYWlydWl0ZS8nLFxyXG4gICAgcGx1Z2luczogW1xyXG4gICAgICB2dWUoKSxcclxuICAgICAgQXV0b0ltcG9ydCh7XHJcbiAgICAgICAgcmVzb2x2ZXJzOiBbRWxlbWVudFBsdXNSZXNvbHZlcigpXSxcclxuICAgICAgICBpbXBvcnRzOiBbJ3Z1ZScsICd2dWUtcm91dGVyJywgJ3BpbmlhJ11cclxuICAgICAgfSksXHJcbiAgICAgIENvbXBvbmVudHMoe1xyXG4gICAgICAgIHJlc29sdmVyczogW0VsZW1lbnRQbHVzUmVzb2x2ZXIoKV1cclxuICAgICAgfSlcclxuICAgIF0sXHJcbiAgICByZXNvbHZlOiB7XHJcbiAgICAgIGFsaWFzOiB7XHJcbiAgICAgICAgJ0AnOiBwYXRoLnJlc29sdmUoX19kaXJuYW1lLCAnc3JjJylcclxuICAgICAgfVxyXG4gICAgfSxcclxuICAgIG9wdGltaXplRGVwczoge1xyXG4gICAgICBpbmNsdWRlOiBbJ2RhZ3JlJywgJ0B2dWUtZmxvdy9jb3JlJywgJ0B2dWUtZmxvdy9iYWNrZ3JvdW5kJywgJ0B2dWUtZmxvdy9jb250cm9scyddXHJcbiAgICB9LFxyXG4gICAgc2VydmVyOiB7XHJcbiAgICAgIGhvc3Q6ICcwLjAuMC4wJyxcclxuICAgICAgcG9ydDogNTE3MyxcclxuICAgICAgcHJveHk6IHtcclxuICAgICAgICBbYXBpQmFzZV06IHtcclxuICAgICAgICAgIHRhcmdldDogYmFja2VuZFRhcmdldCxcclxuICAgICAgICAgIGNoYW5nZU9yaWdpbjogdHJ1ZSxcclxuICAgICAgICAgIHJld3JpdGU6IHBhdGggPT4gcGF0aC5yZXBsYWNlKG5ldyBSZWdFeHAoYF4ke2FwaUJhc2V9YCksICcvYXBpJylcclxuICAgICAgICB9LFxyXG4gICAgICAgIFt1cGxvYWRzQmFzZV06IHtcclxuICAgICAgICAgIHRhcmdldDogYmFja2VuZFRhcmdldCxcclxuICAgICAgICAgIGNoYW5nZU9yaWdpbjogdHJ1ZSxcclxuICAgICAgICAgIHJld3JpdGU6IHBhdGggPT4gcGF0aC5yZXBsYWNlKG5ldyBSZWdFeHAoYF4ke3VwbG9hZHNCYXNlfWApLCAnL3VwbG9hZHMnKVxyXG4gICAgICAgIH0sXHJcbiAgICAgICAgLy8gXHU2MjQwXHU2NzA5XHU0RUU1IC91cGxvYWRzLSBcdTVGMDBcdTU5MzRcdTc2ODRcdTgxRUFcdTVCOUFcdTRFNDlcdTUyNERcdTdGMDBcdTkwRkRcdThGNkNcdTUzRDFcdTUyMzBcdTU0MEVcdTdBRUYoXHU1OTgyIC91cGxvYWRzLWVubGVjaGksIC91cGxvYWRzLWthaXJ1aXRlIFx1N0I0OSlcclxuICAgICAgICAnXi91cGxvYWRzLS4qJzoge1xyXG4gICAgICAgICAgdGFyZ2V0OiBiYWNrZW5kVGFyZ2V0LFxyXG4gICAgICAgICAgY2hhbmdlT3JpZ2luOiB0cnVlXHJcbiAgICAgICAgfVxyXG4gICAgICB9XHJcbiAgICB9XHJcbiAgfVxyXG59KVxyXG4iXSwKICAibWFwcGluZ3MiOiAiO0FBQW9QLFNBQVMsY0FBYyxlQUFlO0FBQzFSLE9BQU8sU0FBUztBQUNoQixPQUFPLGdCQUFnQjtBQUN2QixPQUFPLGdCQUFnQjtBQUN2QixTQUFTLDJCQUEyQjtBQUNwQyxPQUFPLFVBQVU7QUFMakIsSUFBTSxtQ0FBbUM7QUFPekMsSUFBTyxzQkFBUSxhQUFhLENBQUMsRUFBRSxLQUFLLE1BQU07QUFDeEMsUUFBTSxNQUFNLFFBQVEsTUFBTSxRQUFRLElBQUksQ0FBQztBQUN2QyxRQUFNLFVBQVUsSUFBSSxpQkFBaUI7QUFDckMsUUFBTSxjQUFjLElBQUkscUJBQXFCO0FBQzdDLFFBQU0sZ0JBQWdCLElBQUksdUJBQXVCO0FBRWpELFNBQU87QUFBQSxJQUNMLE1BQU07QUFBQSxJQUNOLFNBQVM7QUFBQSxNQUNQLElBQUk7QUFBQSxNQUNKLFdBQVc7QUFBQSxRQUNULFdBQVcsQ0FBQyxvQkFBb0IsQ0FBQztBQUFBLFFBQ2pDLFNBQVMsQ0FBQyxPQUFPLGNBQWMsT0FBTztBQUFBLE1BQ3hDLENBQUM7QUFBQSxNQUNELFdBQVc7QUFBQSxRQUNULFdBQVcsQ0FBQyxvQkFBb0IsQ0FBQztBQUFBLE1BQ25DLENBQUM7QUFBQSxJQUNIO0FBQUEsSUFDQSxTQUFTO0FBQUEsTUFDUCxPQUFPO0FBQUEsUUFDTCxLQUFLLEtBQUssUUFBUSxrQ0FBVyxLQUFLO0FBQUEsTUFDcEM7QUFBQSxJQUNGO0FBQUEsSUFDQSxjQUFjO0FBQUEsTUFDWixTQUFTLENBQUMsU0FBUyxrQkFBa0Isd0JBQXdCLG9CQUFvQjtBQUFBLElBQ25GO0FBQUEsSUFDQSxRQUFRO0FBQUEsTUFDTixNQUFNO0FBQUEsTUFDTixNQUFNO0FBQUEsTUFDTixPQUFPO0FBQUEsUUFDTCxDQUFDLE9BQU8sR0FBRztBQUFBLFVBQ1QsUUFBUTtBQUFBLFVBQ1IsY0FBYztBQUFBLFVBQ2QsU0FBUyxDQUFBQSxVQUFRQSxNQUFLLFFBQVEsSUFBSSxPQUFPLElBQUksT0FBTyxFQUFFLEdBQUcsTUFBTTtBQUFBLFFBQ2pFO0FBQUEsUUFDQSxDQUFDLFdBQVcsR0FBRztBQUFBLFVBQ2IsUUFBUTtBQUFBLFVBQ1IsY0FBYztBQUFBLFVBQ2QsU0FBUyxDQUFBQSxVQUFRQSxNQUFLLFFBQVEsSUFBSSxPQUFPLElBQUksV0FBVyxFQUFFLEdBQUcsVUFBVTtBQUFBLFFBQ3pFO0FBQUE7QUFBQSxRQUVBLGdCQUFnQjtBQUFBLFVBQ2QsUUFBUTtBQUFBLFVBQ1IsY0FBYztBQUFBLFFBQ2hCO0FBQUEsTUFDRjtBQUFBLElBQ0Y7QUFBQSxFQUNGO0FBQ0YsQ0FBQzsiLAogICJuYW1lcyI6IFsicGF0aCJdCn0K
