import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) return;
          if (/\/(react|react-dom|scheduler)\//.test(id)) return "react-vendor";
          if (/\/(framer-motion|motion-dom|motion-utils)\//.test(id)) return "motion";
          if (/\/(zod|react-hook-form|@hookform\/resolvers)\//.test(id)) return "forms";
          if (/\/lucide-react\//.test(id)) return "icons";
          if (/\/(@tanstack\/react-query|axios|zustand)\//.test(id)) return "data";
        },
      },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: true,
  },
});