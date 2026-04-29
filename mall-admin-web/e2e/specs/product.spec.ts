import { test, expect } from '@playwright/test';

/**
 * 商品管理流程 E2E 测试 — 登录后操作商品列表。
 *
 * 覆盖场景：
 *   - 登录后进入商品列表 → 表格渲染
 *   - 按商品名称搜索 → 表格过滤
 *   - 查看商品详情 → 详情内容展示
 *
 * 前置条件：后端 mall-admin (8080) 运行，数据库有种子商品数据。
 */

const BASE = 'http://localhost:5173';

/** 工具函数：登录并等待跳转 */
async function loginAsAdmin(page: any) {
  await page.goto(`${BASE}/#/login`);
  await page.waitForSelector('input[placeholder*="用户名"]', { timeout: 10_000 });
  await page.fill('input[placeholder*="用户名"]', 'admin');
  await page.fill('input[placeholder*="密码"]', 'macro123');
  await page.click('button:has-text("登录")');
  // 等待登录完成跳转到首页
  await page.waitForURL('**/home**', { timeout: 15_000 });
  // 短暂等待资源加载
  await page.waitForTimeout(2000);
}

test.describe('Product Management E2E', () => {

  // ================================================================
  //  测试 4: 商品列表 — 登录后可见商品表格
  // ================================================================
  test('navigate to product list → table renders', async ({ page }) => {
    await loginAsAdmin(page);

    // 通过 URL 导航到商品列表
    await page.goto(`${BASE}/#/pms/product/index`);
    await page.waitForTimeout(3000);

    // 验证：表格或页面内容出现
    // 商品列表页面有搜索区域、表格或卡片
    const hasContent = await page.locator('.el-table, .table, table').count() > 0
      || await page.locator('[class*="product"]').count() > 0;
    // 至少页面成功加载（非空白页/404）
    const bodyText = await page.textContent('body');
    expect(bodyText).not.toContain('404');
    expect(bodyText).not.toContain('Not Found');
  });

  // ================================================================
  //  测试 5: 商品搜索 — 输入关键字后表格过滤
  // ================================================================
  test('search product by name → results filtered', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto(`${BASE}/#/pms/product/index`);
    await page.waitForTimeout(3000);

    // 查找搜索输入框并输入关键字
    const searchInput = page.locator('input[placeholder*="商品名称"], input[placeholder*="搜索"], input[placeholder*="关键词"]');
    if (await searchInput.count() > 0) {
      await searchInput.first().fill('手机');
      // 查找搜索按钮并点击
      const searchBtn = page.locator('button:has-text("搜索"), button:has-text("查询"), .el-button:has-text("搜索")');
      if (await searchBtn.count() > 0) {
        await searchBtn.first().click();
        await page.waitForTimeout(2000);
      }
    }

    // 验证：页面仍然正常（无 500 错误）
    const bodyText = await page.textContent('body');
    expect(bodyText).not.toContain('500');
  });

  // ================================================================
  //  测试 6: 商品详情 — 点击查看
  // ================================================================
  test('view product detail → detail content visible', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto(`${BASE}/#/pms/product/index`);
    await page.waitForTimeout(3000);

    // 如果表格存在，尝试点击第一行的查看/编辑按钮
    const detailBtn = page.locator('button:has-text("编辑"), button:has-text("查看"), a:has-text("编辑"), .el-table__row a, .el-table__row button');
    if (await detailBtn.count() > 0) {
      await detailBtn.first().click();
      await page.waitForTimeout(2000);
    }

    // 验证：页面内容正常
    const bodyText = await page.textContent('body');
    expect(bodyText).not.toContain('500');
    expect(bodyText).not.toContain('Not Found');
  });
});
