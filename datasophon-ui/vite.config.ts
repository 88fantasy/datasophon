import { defineConfig, type PluginOption } from "vite";
import react from "@vitejs/plugin-react-swc";
import tailwindcss from "@tailwindcss/vite";
import pkgJson from "./package.json";

// https://vite.dev/config/
export default defineConfig({
  base: `/${pkgJson.name}`,
  optimizeDeps: {
    esbuildOptions: {
      // Node.js global to browser globalThis
      define: {
        global: "globalThis",
      },
      pure: process.env.NODE_ENV === "production" ? ["console.log"] : [],
    },
  },
  build: {
    assetsDir: `static`,
  },
  plugins: [react(), tailwindcss()] as PluginOption[],
  server: {
    port: 5180,
    proxy: {
      "^/ddh/dev-mock": {
        rewrite: (path) => {
          // console.log('rewrite', path);
          return path.replace(/\/dev-mock/, "");
        },
        target: "http://192.168.2.48:8081/",
        target: "http://192.168.2.230:8081/",
        // target: "http://192.168.2.146:8081/",
        changeOrigin: true,
      },
    },
  },
});
