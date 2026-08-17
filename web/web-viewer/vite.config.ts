import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import { fileURLToPath, URL } from "node:url";

const elementPlusResolver = ElementPlusResolver({
  importStyle: "sass",
});

// https://vite.dev/config/
export default defineConfig({
  envPrefix: "DYNAMSOFT_",
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@use "@/style/index.scss" as *;`,
      },
    },
  },
  plugins: [
    vue(),
    AutoImport({
      resolvers: [elementPlusResolver],
    }),
    Components({
      resolvers: [elementPlusResolver],
    }),
  ],
  base: "./",
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          tools: ["@azure/msal-browser", "jszip", "papaparse"],
          vue: ["pinia", "vue"],
        },
        entryFileNames: "js/[name]-[hash].js",
        chunkFileNames: "js/[name]-[hash].js",
        assetFileNames: (assets) => {
          if (/\.(css)$/.test(assets.names?.[0] ?? "")) {
            return "css/[name]-[hash][extname]";
          }
          if (/\.(png|jpg|jpeg|gif|svg)$/.test(assets.names?.[0] ?? "")) {
            return "images/[name]-[hash][extname]";
          }
          if (/\.(woff2?|ttf|eot)$/.test(assets.names?.[0] ?? "")) {
            return "fonts/[name]-[hash][extname]";
          }
          return "assets/[name]-[hash][extname]";
        },
      },
    },
  },
});
