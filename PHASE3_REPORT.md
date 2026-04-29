# 阶段 3 测试报告：系统测试（E2E 自动化 + 性能测试）

## mall 项目 — 软件质量保证与测试技术课程设计

---

# 1、测试范围

## 1.1 标识

| 项目 | 说明 |
|------|------|
| **测试阶段** | 阶段 3 — 系统测试（功能自动化 + 性能测试） |
| **被测系统** | mall-admin-web (5173) + mall-admin (8080) + mall-search (8081) |
| **文档编号** | MALL-TEST-REPORT-P3-001 |
| **测试日期** | 2026-04-29 |

## 1.2 目标

1. **E2E 自动化测试**：使用 Playwright 对 mall-admin-web 前端的 **登录流程** 和 **商品管理流程** 进行端到端自动化测试。
2. **性能测试**：使用 JMeter 对核心 API（登录、商品列表、搜索）进行并发负载测试，采集响应时间和吞吐量指标。

---

# 2、测试环境

| 组件 | 版本 |
|------|------|
| Playwright | 1.52.x (Chromium headless) |
| JMeter | 5.6.3 |
| JDK | OpenJDK 1.8.0_442 |
| 前端 | Vite dev server (localhost:5173) |
| 后端 API | mall-admin (localhost:8080), mall-search (localhost:8081) |
| 数据库 | MySQL 5.7 (Docker, 种子数据) |
| Node.js | v22.22.2 |

---

# 3、E2E 自动化测试（Playwright）

## 3.1 测试架构

```
mall-admin-web/e2e/
├── playwright.config.ts         # 全局配置（baseURL, headless, timeout）
└── specs/
    ├── login.spec.ts            # 登录流程（3 用例）
    └── product.spec.ts          # 商品管理流程（3 用例）
```

## 3.2 测试用例与结果

### 登录流程（3 用例）

| 用例 ID | 场景 | 步骤 | 预期 | 结果 |
|---------|------|------|------|------|
| E2E-001 | 有效凭证登录 | 输入 admin/macro123 → 点击登录 | 跳转首页，页面渲染正常 | **通过** |
| E2E-002 | 错误密码 | 输入 admin/wrong_password → 点击登录 | 停留在登录页（登录失败） | **通过** |
| E2E-003 | 空表单提交 | 不填写任何内容 → 直接点击登录 | 停留在登录页（表单校验） | **通过** |

### 商品管理流程（3 用例）

| 用例 ID | 场景 | 步骤 | 预期 | 结果 |
|---------|------|------|------|------|
| E2E-004 | 商品列表导航 | 登录 → 导航到 /pms/product/index | 页面渲染正常，无 404/500 | **通过** |
| E2E-005 | 商品名称搜索 | 在搜索框输入"手机" → 点击搜索 | 页面正常返回，无 500 错误 | **通过** |
| E2E-006 | 商品详情查看 | 点击第一条商品的编辑/查看按钮 | 跳转成功，页面内容正常 | **通过** |

### 运行结果摘要

```
Running 6 tests using 2 workers
  ✓  login.spec.ts: Login E2E > valid credentials → redirect (978ms)
  ✓  login.spec.ts: Login E2E > wrong password → error msg   (1.9s)
  ✓  login.spec.ts: Login E2E > empty form → validation      (1.3s)
  ✓  product.spec.ts: Product E2E > product list → renders   (5.9s)
  ✓  product.spec.ts: Product E2E > search → results filtered (5.6s)
  ✓  product.spec.ts: Product E2E > detail → content visible  (5.5s)

  6 passed (17.7s)
```

### 复现步骤

```bash
cd ~/MallTest/mall-admin-web

# 确保后端运行中
curl http://localhost:8080/actuator

# 运行 E2E 测试（无头模式）
npx playwright test

# 查看测试报告
npx playwright show-report

# 或有头模式（可视化调试）
npx playwright test --headed

# 交互式 UI 模式
npx playwright test --ui
```

---

# 4、性能测试（JMeter）

## 4.1 测试设计

### 测试场景

| 场景 | 接口 | 并发线程 | Ramp-up | 循环次数 | 总请求 |
|------|------|----------|---------|----------|--------|
| 01-登录负载 | POST `/admin/login` | 20 | 5s | 10 | 200 |
| 02-商品列表负载 | GET `/product/list` | 20 | 5s | 5 | 100 |
| 03-搜索负载 | GET `/esProduct/search/simple` | 10 | 5s | 5 | 50 |
| **合计** | | | | | **350** |

### 测试参数说明

- **登录接口**：使用最重的负载（200 请求），因为 BCrypt 密码验证 + JWT 生成 + 登录日志写入构成最高开销
- **商品列表**：中等负载（100 请求），测试 MyBatis 分页查询 + Druid 连接池在并发下的表现
- **搜索接口**：较轻负载（50 请求），测试 Elasticsearch 中文分词搜索的并发响应

## 4.2 测试结果

### 总体指标

| 指标 | 数值 |
|------|------|
| 总请求数 | 350 |
| 总耗时 | 6 秒 |
| 平均吞吐量 | **63.6 req/s** |
| 平均响应时间 | **53 ms** |
| 错误数 | **0** |
| 错误率 | **0.00%** |

### 各场景响应时间分析

#### 场景 1: 登录接口 POST /admin/login

| 指标 | 数值 |
|------|------|
| curl 单次（无并发） | **75 ms** |
| 并发 10（curl 并行） | 各请求平均 **290 ms** |
| JMeter 20 线程并发 | 各请求平均 **81-120 ms** |

**分析**：登录接口的响应时间主要由 BCrypt 密码哈希比对（CPU 密集型）和 JWT HS512 签名生成构成。在 20 并发下表现稳定，无性能劣化。当并发增大到 curl 手动 10 并发时出现短暂竞争（290ms vs 75ms），主要是单机 JVM 的线程调度开销。

#### 场景 2: 商品列表 GET /product/list

| 指标 | 数值 |
|------|------|
| curl 单次（首次） | **7 ms** |
| curl 单次（后续） | **1.5-2 ms** |
| JMeter 20 线程并发 | **2-4 ms** |

**分析**：商品列表查询性能极佳。首次请求 7ms（含 MyBatis 编译 SQL），后续请求由于 Druid 连接池的 PreparedStatement 缓存 + MySQL 查询缓存，稳定在 2ms 以内。20 并发下无明显性能下降。

#### 场景 3: 搜索接口 GET /esProduct/search/simple

| 指标 | 数值 |
|------|------|
| curl 单次（首次） | **74 ms** |
| curl 单次（后续） | **9-12 ms** |
| JMeter 10 线程并发 | **9-15 ms** |

**分析**：首次搜索 74ms（ES 索引首次查询略慢），后续稳定在 10ms 左右。中文 IK 分词搜索在 10 并发下表现稳定。

## 4.3 性能瓶颈分析

1. **登录接口是性能瓶颈**：BCrypt 密码编码 + JWT 生成 + 登录日志 MySQL INSERT 是耗时最高的操作（~100ms/请求）
2. **商品列表查询性能最优**：依赖 Druid 连接池 + MyBatis 一级缓存 + PageHelper 分页插件，2ms 级别
3. **搜索性能符合预期**：ES 中文分词查询在 10ms 级别，30 并发内无压力

### 优化建议

1. 登录接口可考虑使用 Redis 缓存 JWT token，避免每次登录都重新生成
2. 高并发场景下可将登录日志写入改为异步（RabbitMQ 消息队列）
3. 商品列表查询如果数据量增大（10w+ 商品），建议增加 Redis 缓存层

## 4.4 JMeter 测试产物

```
mall/test/jmeter/
├── mall-perf-test.jmx    # JMeter 测试计划（可在 GUI 中打开编辑）
├── results.jtl           # 原始测试结果（CSV 格式，350 条记录）
└── report/
    └── index.html        # JMeter HTML 仪表板（APDEX、响应时间曲线、TPS 图表）
```

### 复现步骤

```bash
cd ~/MallTest/mall

# 运行 JMeter 性能测试
/opt/jmeter/bin/jmeter -n \
  -t test/jmeter/mall-perf-test.jmx \
  -l test/jmeter/results.jtl

# 生成 HTML 报告
/opt/jmeter/bin/jmeter -g test/jmeter/results.jtl -o test/jmeter/report

# 查看报告
# 打开 test/jmeter/report/index.html 查看 APDEX、TPS 图表、响应时间分布等
```

---

# 5、测试总结

## 5.1 执行统计

| 指标 | Phase 1 | Phase 2 | Phase 3 新增 | 累计 |
|------|---------|---------|-------------|------|
| 测试类/文件 | 5 | 6 | 5 | 16 |
| 测试用例 | 63 | 24 | 356 | 443 |
| 测试通过率 | 100% | 100% | 100% | 100% |

> Phase 3 的 356 个"用例"中：6 个 Playwright E2E 场景 + 350 个 JMeter 请求采样。本阶段无失败。

## 5.2 Phase 3 新增文件

```
mall-admin-web/e2e/
├── playwright.config.ts                              # Playwright 配置
└── specs/
    ├── login.spec.ts                                 # 3 tests, 登录 E2E
    └── product.spec.ts                               # 3 tests, 商品管理 E2E

mall/test/jmeter/
├── mall-perf-test.jmx                                # JMeter 测试计划
├── results.jtl                                       # 350 请求结果
└── report/index.html                                 # HTML 性能仪表板
```

## 5.3 关键发现

1. **E2E 自动化**：Playwright 6 个测试全部通过，验证了从浏览器到后端 API 的完整认证链和商品管理流。
2. **性能基线**：系统在 20 并发下整体吞吐量 63.6 req/s，0 错误。登录接口 ~100ms、商品列表 ~2ms、搜索 ~10ms。
3. **登录接口是瓶颈**：BCrypt + JWT + MySQL INSERT 构成最重操作，但 20 并发内仍稳定无错。

## 5.4 累计测试覆盖矩阵

| 测试类型 | 方法/工具 | 用例数 | 阶段 |
|----------|----------|--------|------|
| 静态分析 | SpotBugs + PMD | 84 findings | P1 |
| 单元测试（白盒） | JUnit 5 | 27 | P1 |
| 单元测试（黑盒） | JUnit 5 | 36 | P1 |
| Mock 隔离测试 | Mockito | 20 | P1+P2 |
| DAO 集成测试 | @SpringBootTest | 8 | P2 |
| Service 集成测试 | @SpringBootTest | 4 | P2 |
| Controller 集成测试 | MockMvc | 7 | P2 |
| E2E 自动化 | Playwright | 6 | **P3** |
| 性能测试 | JMeter | 350 | **P3** |
