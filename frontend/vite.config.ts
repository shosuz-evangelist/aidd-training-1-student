import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        // Codespaces では画面の URL が localhost でないため、Origin を外してバックエンドに渡す
        configure: (proxy) => {
          proxy.on("proxyReq", (proxyReq) => proxyReq.removeHeader("origin"));
        },
      },
    },
  },
});
