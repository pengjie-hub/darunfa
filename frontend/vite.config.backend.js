/**
 * 后台（管理端）专用构建配置 —— 产物直接输出到后端 static/app，由 Spring Boot 托管。
 *
 * 为什么不用命令行 `--base=/app/ --outDir=...`：
 *   Windows 的 Git Bash（MSYS2）会把以 / 开头的参数当成文件系统路径做转换，
 *   `--base=/app/` 会变成 `C:/Program Files/Git/app/`，导致 index.html 里的
 *   资源引用变成绝对盘符路径，部署后必然 404。
 *   把 base 与 outDir 写在配置文件里，就不经过 shell 参数转换，Windows/Linux 行为一致。
 *
 * 用法：npm run build:backend
 */
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  base: '/app/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    // 指向 backend/src/main/resources/static/app（与 vite.config.js 的默认 dist 区分开）
    outDir: fileURLToPath(new URL('../backend/src/main/resources/static/app', import.meta.url)),
    emptyOutDir: true,
    assetsDir: 'assets',
    sourcemap: false
  }
})
