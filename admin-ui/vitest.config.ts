import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@mui/material/styles": path.resolve(
        __dirname,
        "node_modules/@mui/material/styles/index.js"
      ),
      "@mui/material/colors": path.resolve(
        __dirname,
        "node_modules/@mui/material/colors/index.js"
      ),
      "@mui/material/utils": path.resolve(
        __dirname,
        "node_modules/@mui/material/utils/index.js"
      ),
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./tests/setup.ts"],
    include: ["tests/**/*.test.{ts,tsx}"],
    server: {
      deps: {
        inline: [/^(?!.*vitest).*$/],
      },
    },
  },
});
