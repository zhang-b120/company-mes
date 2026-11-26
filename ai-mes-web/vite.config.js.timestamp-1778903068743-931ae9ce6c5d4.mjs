// vite.config.js
import { defineConfig, loadEnv } from "file:///D:/qingyi/ai-mes/ai-mes-web/node_modules/vite/dist/node/index.js";
import vue from "file:///D:/qingyi/ai-mes/ai-mes-web/node_modules/@vitejs/plugin-vue/dist/index.mjs";
import AutoImport from "file:///D:/qingyi/ai-mes/ai-mes-web/node_modules/unplugin-auto-import/dist/vite.js";
import Components from "file:///D:/qingyi/ai-mes/ai-mes-web/node_modules/unplugin-vue-components/dist/vite.js";
import { ElementPlusResolver } from "file:///D:/qingyi/ai-mes/ai-mes-web/node_modules/unplugin-vue-components/dist/resolvers.js";
import path from "path";
var __vite_injected_original_dirname = "D:\\qingyi\\ai-mes\\ai-mes-web";
var vite_config_default = defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd());
  const apiBase = env.VITE_API_BASE || "/api";
  const uploadsBase = env.VITE_UPLOADS_BASE || "/uploads";
  const backendTarget = env.VITE_BACKEND_TARGET || "http://localhost:8094";
  return {
    base: "/mes-front-baihui/",
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
        // 所有以 /uploads- 开头的自定义前缀都转发到后端(如 /uploads-enlechi, /uploads-baihui 等)
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
//# sourceMappingURL=data:application/json;base64,ewogICJ2ZXJzaW9uIjogMywKICAic291cmNlcyI6IFsidml0ZS5jb25maWcuanMiXSwKICAic291cmNlc0NvbnRlbnQiOiBbImNvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9kaXJuYW1lID0gXCJEOlxcXFxxaW5neWlcXFxcYWktbWVzXFxcXGFpLW1lcy13ZWJcIjtjb25zdCBfX3ZpdGVfaW5qZWN0ZWRfb3JpZ2luYWxfZmlsZW5hbWUgPSBcIkQ6XFxcXHFpbmd5aVxcXFxhaS1tZXNcXFxcYWktbWVzLXdlYlxcXFx2aXRlLmNvbmZpZy5qc1wiO2NvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9pbXBvcnRfbWV0YV91cmwgPSBcImZpbGU6Ly8vRDovcWluZ3lpL2FpLW1lcy9haS1tZXMtd2ViL3ZpdGUuY29uZmlnLmpzXCI7aW1wb3J0IHsgZGVmaW5lQ29uZmlnLCBsb2FkRW52IH0gZnJvbSAndml0ZSdcclxuaW1wb3J0IHZ1ZSBmcm9tICdAdml0ZWpzL3BsdWdpbi12dWUnXHJcbmltcG9ydCBBdXRvSW1wb3J0IGZyb20gJ3VucGx1Z2luLWF1dG8taW1wb3J0L3ZpdGUnXHJcbmltcG9ydCBDb21wb25lbnRzIGZyb20gJ3VucGx1Z2luLXZ1ZS1jb21wb25lbnRzL3ZpdGUnXHJcbmltcG9ydCB7IEVsZW1lbnRQbHVzUmVzb2x2ZXIgfSBmcm9tICd1bnBsdWdpbi12dWUtY29tcG9uZW50cy9yZXNvbHZlcnMnXHJcbmltcG9ydCBwYXRoIGZyb20gJ3BhdGgnXHJcblxyXG5leHBvcnQgZGVmYXVsdCBkZWZpbmVDb25maWcoKHsgbW9kZSB9KSA9PiB7XHJcbiAgY29uc3QgZW52ID0gbG9hZEVudihtb2RlLCBwcm9jZXNzLmN3ZCgpKVxyXG4gIGNvbnN0IGFwaUJhc2UgPSBlbnYuVklURV9BUElfQkFTRSB8fCAnL2FwaSdcclxuICBjb25zdCB1cGxvYWRzQmFzZSA9IGVudi5WSVRFX1VQTE9BRFNfQkFTRSB8fCAnL3VwbG9hZHMnXHJcbiAgY29uc3QgYmFja2VuZFRhcmdldCA9IGVudi5WSVRFX0JBQ0tFTkRfVEFSR0VUIHx8ICdodHRwOi8vbG9jYWxob3N0OjgwODMnXHJcblxyXG4gIHJldHVybiB7XHJcbiAgICBiYXNlOiAnL21lcy1mcm9udC1iYWlodWkvJyxcclxuICAgIHBsdWdpbnM6IFtcclxuICAgICAgdnVlKCksXHJcbiAgICAgIEF1dG9JbXBvcnQoe1xyXG4gICAgICAgIHJlc29sdmVyczogW0VsZW1lbnRQbHVzUmVzb2x2ZXIoKV0sXHJcbiAgICAgICAgaW1wb3J0czogWyd2dWUnLCAndnVlLXJvdXRlcicsICdwaW5pYSddXHJcbiAgICAgIH0pLFxyXG4gICAgICBDb21wb25lbnRzKHtcclxuICAgICAgICByZXNvbHZlcnM6IFtFbGVtZW50UGx1c1Jlc29sdmVyKCldXHJcbiAgICAgIH0pXHJcbiAgICBdLFxyXG4gICAgcmVzb2x2ZToge1xyXG4gICAgICBhbGlhczoge1xyXG4gICAgICAgICdAJzogcGF0aC5yZXNvbHZlKF9fZGlybmFtZSwgJ3NyYycpXHJcbiAgICAgIH1cclxuICAgIH0sXHJcbiAgICBvcHRpbWl6ZURlcHM6IHtcclxuICAgICAgaW5jbHVkZTogWydkYWdyZScsICdAdnVlLWZsb3cvY29yZScsICdAdnVlLWZsb3cvYmFja2dyb3VuZCcsICdAdnVlLWZsb3cvY29udHJvbHMnXVxyXG4gICAgfSxcclxuICAgIHNlcnZlcjoge1xyXG4gICAgICBob3N0OiAnMC4wLjAuMCcsXHJcbiAgICAgIHBvcnQ6IDUxNzMsXHJcbiAgICAgIHByb3h5OiB7XHJcbiAgICAgICAgW2FwaUJhc2VdOiB7XHJcbiAgICAgICAgICB0YXJnZXQ6IGJhY2tlbmRUYXJnZXQsXHJcbiAgICAgICAgICBjaGFuZ2VPcmlnaW46IHRydWUsXHJcbiAgICAgICAgICByZXdyaXRlOiBwYXRoID0+IHBhdGgucmVwbGFjZShuZXcgUmVnRXhwKGBeJHthcGlCYXNlfWApLCAnL2FwaScpXHJcbiAgICAgICAgfSxcclxuICAgICAgICBbdXBsb2Fkc0Jhc2VdOiB7XHJcbiAgICAgICAgICB0YXJnZXQ6IGJhY2tlbmRUYXJnZXQsXHJcbiAgICAgICAgICBjaGFuZ2VPcmlnaW46IHRydWUsXHJcbiAgICAgICAgICByZXdyaXRlOiBwYXRoID0+IHBhdGgucmVwbGFjZShuZXcgUmVnRXhwKGBeJHt1cGxvYWRzQmFzZX1gKSwgJy91cGxvYWRzJylcclxuICAgICAgICB9LFxyXG4gICAgICAgIC8vIFx1NjI0MFx1NjcwOVx1NEVFNSAvdXBsb2Fkcy0gXHU1RjAwXHU1OTM0XHU3Njg0XHU4MUVBXHU1QjlBXHU0RTQ5XHU1MjREXHU3RjAwXHU5MEZEXHU4RjZDXHU1M0QxXHU1MjMwXHU1NDBFXHU3QUVGKFx1NTk4MiAvdXBsb2Fkcy1lbmxlY2hpLCAvdXBsb2Fkcy1iYWlodWkgXHU3QjQ5KVxyXG4gICAgICAgICdeL3VwbG9hZHMtLionOiB7XHJcbiAgICAgICAgICB0YXJnZXQ6IGJhY2tlbmRUYXJnZXQsXHJcbiAgICAgICAgICBjaGFuZ2VPcmlnaW46IHRydWVcclxuICAgICAgICB9XHJcbiAgICAgIH1cclxuICAgIH1cclxuICB9XHJcbn0pXHJcbiJdLAogICJtYXBwaW5ncyI6ICI7QUFBMlEsU0FBUyxjQUFjLGVBQWU7QUFDalQsT0FBTyxTQUFTO0FBQ2hCLE9BQU8sZ0JBQWdCO0FBQ3ZCLE9BQU8sZ0JBQWdCO0FBQ3ZCLFNBQVMsMkJBQTJCO0FBQ3BDLE9BQU8sVUFBVTtBQUxqQixJQUFNLG1DQUFtQztBQU96QyxJQUFPLHNCQUFRLGFBQWEsQ0FBQyxFQUFFLEtBQUssTUFBTTtBQUN4QyxRQUFNLE1BQU0sUUFBUSxNQUFNLFFBQVEsSUFBSSxDQUFDO0FBQ3ZDLFFBQU0sVUFBVSxJQUFJLGlCQUFpQjtBQUNyQyxRQUFNLGNBQWMsSUFBSSxxQkFBcUI7QUFDN0MsUUFBTSxnQkFBZ0IsSUFBSSx1QkFBdUI7QUFFakQsU0FBTztBQUFBLElBQ0wsTUFBTTtBQUFBLElBQ04sU0FBUztBQUFBLE1BQ1AsSUFBSTtBQUFBLE1BQ0osV0FBVztBQUFBLFFBQ1QsV0FBVyxDQUFDLG9CQUFvQixDQUFDO0FBQUEsUUFDakMsU0FBUyxDQUFDLE9BQU8sY0FBYyxPQUFPO0FBQUEsTUFDeEMsQ0FBQztBQUFBLE1BQ0QsV0FBVztBQUFBLFFBQ1QsV0FBVyxDQUFDLG9CQUFvQixDQUFDO0FBQUEsTUFDbkMsQ0FBQztBQUFBLElBQ0g7QUFBQSxJQUNBLFNBQVM7QUFBQSxNQUNQLE9BQU87QUFBQSxRQUNMLEtBQUssS0FBSyxRQUFRLGtDQUFXLEtBQUs7QUFBQSxNQUNwQztBQUFBLElBQ0Y7QUFBQSxJQUNBLGNBQWM7QUFBQSxNQUNaLFNBQVMsQ0FBQyxTQUFTLGtCQUFrQix3QkFBd0Isb0JBQW9CO0FBQUEsSUFDbkY7QUFBQSxJQUNBLFFBQVE7QUFBQSxNQUNOLE1BQU07QUFBQSxNQUNOLE1BQU07QUFBQSxNQUNOLE9BQU87QUFBQSxRQUNMLENBQUMsT0FBTyxHQUFHO0FBQUEsVUFDVCxRQUFRO0FBQUEsVUFDUixjQUFjO0FBQUEsVUFDZCxTQUFTLENBQUFBLFVBQVFBLE1BQUssUUFBUSxJQUFJLE9BQU8sSUFBSSxPQUFPLEVBQUUsR0FBRyxNQUFNO0FBQUEsUUFDakU7QUFBQSxRQUNBLENBQUMsV0FBVyxHQUFHO0FBQUEsVUFDYixRQUFRO0FBQUEsVUFDUixjQUFjO0FBQUEsVUFDZCxTQUFTLENBQUFBLFVBQVFBLE1BQUssUUFBUSxJQUFJLE9BQU8sSUFBSSxXQUFXLEVBQUUsR0FBRyxVQUFVO0FBQUEsUUFDekU7QUFBQTtBQUFBLFFBRUEsZ0JBQWdCO0FBQUEsVUFDZCxRQUFRO0FBQUEsVUFDUixjQUFjO0FBQUEsUUFDaEI7QUFBQSxNQUNGO0FBQUEsSUFDRjtBQUFBLEVBQ0Y7QUFDRixDQUFDOyIsCiAgIm5hbWVzIjogWyJwYXRoIl0KfQo=
