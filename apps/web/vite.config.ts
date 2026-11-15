import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  // Lee el .env de la raíz del monorepo. API_PROXY_TARGET indica a qué API se
  // envían las llamadas a /api: NestJS (puerto 3000, por defecto) o Spring Boot
  // (API_PROXY_TARGET=http://localhost:8080).
  const env = loadEnv(mode, "../..", "");

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        "/api": env.API_PROXY_TARGET || "http://localhost:3000"
      }
    }
  };
});
