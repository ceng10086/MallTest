import { defineConfig } from '@playwright/test';

/**
 * Playwright E2E 测试配置 — mall-admin-web 前端自动化测试。
 *
 * 运行方式：
 *   npx playwright test           # 无头模式运行全部测试
 *   npx playwright test --headed  # 有头模式（可视化调试）
 *   npx playwright test --ui      # 交互式 UI 模式
 */
export default defineConfig({
  testDir: './specs',
  /* 单个测试超时 30 秒 */
  timeout: 30_000,
  /* 全局 expect 断言超时 10 秒 */
  expect: { timeout: 10_000 },
  /* CI 环境重试 1 次 */
  retries: process.env.CI ? 1 : 0,
  /* 无头模式 */
  use: {
    headless: true,
    /* 后端 API 地址在 .env.development 中配置，默认 localhost:8080 */
    baseURL: 'http://localhost:5173',
    /* 截图仅在失败时 */
    screenshot: 'only-on-failure',
    /* 视频仅在失败时保留 */
    video: 'retain-on-failure',
    /* 浏览器窗口 */
    viewport: { width: 1440, height: 900 },
  },
  /* 输出目录 */
  outputDir: './test-results',
});
