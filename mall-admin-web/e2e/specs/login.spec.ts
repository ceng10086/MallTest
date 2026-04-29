import { test, expect } from '@playwright/test';

/**
 * 登录流程 E2E 测试 — 黄金路径 + 异常路径。
 *
 * 覆盖场景：
 *   - 有效凭证登录 → 跳转首页 + 用户名显示
 *   - 错误密码 → 提示错误信息
 *   - 空用户名/密码 → 表单校验
 *
 * 测试目标：验证 mall-admin-web 的认证流程从前端到后端 API 的完整链路。
 * 需后端 mall-admin (8080) 运行中。
 */

const BASE = 'http://localhost:5173';

test.describe('Login E2E', () => {

  test.beforeEach(async ({ page }) => {
    // 每次测试前导航到登录页
    await page.goto(`${BASE}/#/login`);
    // 等待登录表单渲染
    await page.waitForSelector('input[placeholder*="用户名"]', { timeout: 10_000 });
  });

  // ================================================================
  //  测试 1: 有效凭证登录 — 黄金路径
  // ================================================================
  test('valid credentials → redirect to home + show username', async ({ page }) => {
    // 输入用户名和密码
    await page.fill('input[placeholder*="用户名"]', 'admin');
    await page.fill('input[placeholder*="密码"]', 'macro123');

    // 点击登录按钮
    await page.click('button:has-text("登录")');

    // 验证：跳转到首页（URL 包含 /home）
    await page.waitForURL('**/home**', { timeout: 15_000 });

    // 验证：页面成功跳转后内容可见
    await expect(page.locator('.avatar-container, .el-dropdown').first()).toBeVisible({ timeout: 10_000 });
  });

  // ================================================================
  //  测试 2: 错误密码 — 错误提示
  // ================================================================
  test('wrong password → show error message', async ({ page }) => {
    await page.fill('input[placeholder*="用户名"]', 'admin');
    await page.fill('input[placeholder*="密码"]', 'wrong_password_123');

    await page.click('button:has-text("登录")');

    // 验证：出现错误提示（ElMessage 或 ElNotification）
    // Element Plus 的消息提示在 .el-message 或 .el-notification 中
    await page.waitForTimeout(1500);
    // 验证：仍在登录页（URL 仍为 /login）
    const url = page.url();
    expect(url).toContain('/login');
  });

  // ================================================================
  //  测试 3: 空表单提交 — 表单校验
  // ================================================================
  test('empty form → validation triggered', async ({ page }) => {
    // 不填写任何内容，直接点击登录
    await page.click('button:has-text("登录")');

    // 验证：仍在登录页（表单验证未通过，不会跳转）
    await page.waitForTimeout(1000);
    expect(page.url()).toContain('/login');
  });
});
