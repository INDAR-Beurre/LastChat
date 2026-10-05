import { reactRouter } from "@react-router/dev/vite";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";
import tsconfigPaths from "vite-tsconfig-paths";
import svgr from "vite-plugin-svgr";
import { fileURLToPath, URL } from "node:url";

export default defineConfig({
  // Relative asset URLs: the built SPA is served from the app's assets directory by the
  // loopback server, so a root-absolute "/assets/..." URL would not resolve.
  base: "./",
  plugins: [tailwindcss(), reactRouter(), tsconfigPaths(), svgr()],
  resolve: {
    alias: {
      "lucide-react": fileURLToPath(new URL("./app/lib/material-icons.tsx", import.meta.url)),
    },
  },
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
