import { defineConfig, type PluginOption } from "vite";
import react from "@vitejs/plugin-react-swc";
import tailwindcss from "@tailwindcss/vite";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()] as PluginOption[],
  server: {
    proxy: {
      "^/dev-mock": {
        rewrite: (path) => {
          // console.log('rewrite', path);
          return path.replace(/\/dev-mock/, "");
        },
        target: "http://192.168.2.48:8081/",
        changeOrigin: true,
      },
    },
  },
});
