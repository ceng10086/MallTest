# 软件质量保证与测试技术 — 测试报告

## mall 开源电商项目综合测试

---

# 1、范围

## 1.1 标识

| 项目 | 说明 |
|------|------|
| **项目名称** | MallTest — mall 开源商城系统测试 |
| **被测系统** | mall（Spring Boot 微服务后端）+ mall-admin-web（Vue3 管理前端） |
| **项目来源** | https://github.com/macrozheng/mall (60k+ stars) |
| **测试仓库** | https://github.com/ceng10086/MallTest |
| **文档编号** | MALL-TEST-FINAL-001 |
| **缩略语** | UT=单元测试, IT=集成测试, ST=系统测试, PT=性能测试, SA=静态分析, E2E=端到端测试, CI=持续集成 |

## 1.2 系统概述

mall 是一套完整的电商系统，技术栈：

- **后端**：Spring Boot 2.7.5 + MyBatis + MySQL 5.7 + Redis 7 + Elasticsearch 7.17 + RabbitMQ 3.9 + MongoDB 4
- **前端**：Vue 3 + TypeScript + Vite 7 + Element Plus
- **架构**：微服务模块化（mall-admin 后台管理、mall-portal 前台商城、mall-search 搜索服务、mall-demo 演示模块）
- **安全**：Spring Security + JWT (HS512) + 动态权限控制

## 1.3 文档概述

本报告是 mall 项目软件测试课程设计的最终汇总文档，覆盖从静态分析到 CI/CD 的完整测试生命周期，包含 6 个测试阶段的全部产出。

---

# 2、测试计划

## 2.1 测试进度

| 阶段 | 内容 | 工具/方法 | 状态 |
|------|------|----------|------|
| Phase 1 | 静态分析 + 单元测试 | SpotBugs, PMD, JUnit 5, Mockito | ✅ |
| Phase 2 | Mock 测试 + 集成测试 | Mockito, @SpringBootTest, MockMvc | ✅ |
| Phase 3 | E2E 自动化 + 性能测试 | Playwright, JMeter 5.6.3 | ✅ |
| Phase 4 | 安全测试 | OWASP ZAP | ✅ |
| Phase 5 | CI/CD 自动化 | GitHub Actions | ✅ |
| Phase 6 | 报告整理与总结 | — | ✅ |

## 2.2 测试人员

个人独立完成全部测试工作。

## 2.3 测试环境

| 层级 | 组件 | 版本 |
|------|------|------|
| OS | Ubuntu 24.04 (WSL2) | 6.6.87.2-microsoft-standard-WSL2 |
| JDK | OpenJDK 1.8.0_442 (Temurin) | 8u442 |
| 构建 | Maven 3.8.7 | — |
| 数据库 | MySQL 5.7 (Docker) | root/root, 3306 |
| 缓存 | Redis 7 (Docker) | 6379 |
| 搜索引擎 | Elasticsearch 7.17.3 (Docker) | 9200, IK 分词器 |
| 消息队列 | RabbitMQ 3.9.11 (Docker) | mall/mall, /mall, 5672 |
| NoSQL | MongoDB 4 (Docker) | 27017 |
| 对象存储 | MinIO (Docker) | minioadmin/minioadmin, 9090 |
| 前端 | Node.js 22 + Vite 7 | 5173 |
| 后端服务 | mall-admin :8080, mall-search :8081, mall-portal :8085, mall-demo :8082 | Spring Boot 2.7.5 |

## 2.4 测试工具

| 工具 | 用途 | 版本 |
|------|------|------|
| JUnit 5 | 单元测试框架 | 5.8.2 (随 Spring Boot 2.7.5) |
| Mockito | Mock 对象框架 | 4.x |
| SpotBugs | Java 静态缺陷检测 | 4.7.3.6 |
| PMD | Java 代码规范检查 | 7.7.0 |
| Playwright | 前端 E2E 自动化 | 1.52.x |
| JMeter | 性能/压力测试 | 5.6.3 |
| OWASP ZAP | Web 安全扫描 | 2.16.x (Docker) |
| GitHub Actions | CI/CD 自动化 | — |

## 2.5 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 中间件未启动 | 集成测试无法执行 | CI 中使用 Docker services + health check |
| SNAPSHOT 依赖解析失败 | CI 构建失败 | `mvn install` 预安装本地模块 |
| @SpringBootTest 启动慢 | CI 耗时过长 | 拆分纯 Mock 测试和集成测试为独立 job |
| ES 索引未初始化 | 搜索测试无结果 | 接受空结果作为合法响应 |
| WSL2 IP 变化 | ZAP 扫描目标不可达 | 每次扫描前动态获取 IP |

---

# 3、测试概述

## 3.1 功能测试概述

### 测试用例设计方法

| 方法 | 类型 | 应用场景 | 用例数 |
|------|------|----------|--------|
| 语句覆盖 | 白盒 | JwtTokenUtil.generateToken(), handleSkuStockCode() | 8 |
| 分支覆盖 | 白盒 | OmsPromotionServiceImpl 4 种促销类型, refreshHeadToken 6 分支 | 12 |
| 路径覆盖 | 白盒 | UmsAdminServiceImpl.updatePassword() 4 条路径 | 4 |
| 条件覆盖 | 白盒 | JwtTokenUtil.validateToken() 复合条件 | 3 |
| 等价类划分 | 黑盒 | JwtTokenUtil 有效/无效/篡改/空 token | 5 |
| 边界值分析 | 黑盒 | 促销阶梯阈值, 满减金额阈值, SKU 编码边界 | 11 |
| 判定表 | 黑盒 | updatePassword 4 输入条件组合 | 4 |

### 测试结果一览表

| 测试套件 | 用例数 | 通过 | 失败 | 发现缺陷 |
|----------|--------|------|------|----------|
| TEST-AUTH (认证) | 20 | 20 | 0 | MALL-BUG-001 (NPE) |
| TEST-PROMOTION (促销) | 16 | 16 | 0 | MALL-BUG-002 (intValue截断) |
| TEST-USER (用户管理) | 11 (UT) + 4 (IT) | 15 | 0 | — |
| TEST-COUPON (优惠券) | 6 | 6 | 0 | — |
| TEST-PRODUCT (商品) | 8 (UT) + 8 (DAO) | 16 | 0 | — |
| TEST-ORDER (订单) | 4 (DAO) | 4 | 0 | — |
| TEST-FILTER (安全过滤) | 6 | 6 | 0 | — |
| TEST-CONTROLLER (API) | 7 | 7 | 0 | — |

## 3.2 性能测试概述

| 测试场景 | 接口 | 并发 | 平均响应 | 吞吐量 | 错误率 |
|----------|------|------|----------|--------|--------|
| 登录负载 | POST `/admin/login` | 20 thread × 10 loop | ~100ms | — | 0% |
| 商品列表负载 | GET `/product/list` | 20 thread × 5 loop | ~2ms | — | 0% |
| 搜索负载 | GET `/esProduct/search/simple` | 10 thread × 5 loop | ~12ms | — | 0% |
| **合计** | | 350 请求 | **53ms** | **63.6 req/s** | **0%** |

### 性能瓶颈分析

1. 登录接口 ~100ms（BCrypt 密码哈希 + JWT HS512 签名 + MySQL INSERT 登录日志）是最大瓶颈
2. 商品列表 ~2ms（Druid 连接池 + MyBatis 一级缓存 + PageHelper）
3. ES 搜索 ~12ms（IK 中文分词 + ES 索引查询）
4. 系统整体在 20 并发内稳定，无性能劣化

---

# 4、单元测试

## 4.1 测试范围

为 6 个核心业务类编写了单元测试，综合使用白盒与黑盒测试用例设计方法。

| 被测类 | 测试文件 | 用例 | 方法覆盖 |
|--------|----------|------|----------|
| `JwtTokenUtil` | JwtTokenUtilTest.java | 20 | 语句/分支/路径/条件 + 等价类/边界值 |
| `OmsPromotionServiceImpl` | OmsPromotionServiceImplTest.java | 16 | 分支/条件 + 等价类/边界值 |
| `UmsAdminServiceImpl` | UmsAdminServiceImplTest.java | 11 | 路径 + 等价类/判定表 |
| `PmsProductServiceImpl` | PmsProductServiceImplTest.java | 8 | 语句 + 边界值 |
| `UmsMemberCouponServiceImpl` | UmsMemberCouponServiceImplTest.java | 6 | 语句 + 边界值 |
| `JwtAuthenticationTokenFilter` | JwtAuthenticationTokenFilterTest.java | 6 | Mock 隔离 + 反射 |

## 4.2 白盒测试示例 — updatePassword 路径覆盖

**被测方法**：`UmsAdminServiceImpl.updatePassword(UpdateAdminPasswordParam)`

**控制流图**：4 条独立路径

```
输入 → 参数校验 → -1
输入 → 用户查询 → 不存在 → -2
输入 → 用户查询 → 旧密码校验 → 不匹配 → -3
输入 → 用户查询 → 旧密码校验 → 更新 → 1
```

**测试用例**（判定表）：

| 规则 | username 非空 | 用户存在 | 旧密码正确 | ⇒ 返回码 | 结果 |
|------|-------------|---------|-----------|----------|------|
| R1 | F | — | — | -1 | ✅ |
| R2 | T | F | — | -2 | ✅ |
| R3 | T | T | F | -3 | ✅ |
| R4 | T | T | T | 1 | ✅ |

## 4.3 黑盒测试示例 — 满减金额边界值分析

**被测方法**：`OmsPromotionServiceImpl.getProductFullReduction()`

**测试数据**：

| 用例 | 购物金额 | 满减门槛 | 预期 | 实际 | 结论 |
|------|----------|----------|------|------|------|
| 刚好达到 | 200 | 满200减30 | 匹配 | 匹配 ✅ | |
| 刚好不足 | 199.99 | 满200减30 | 不匹配 | **错误匹配** ❌ | **发现 Bug** |

**Bug 根源**：`totalAmount.subtract(fullPrice).intValue() >= 0`，`199.99-200=-0.01` → `intValue()` 截断为 `0` → `0>=0` 为 true，错误返回满减规则。

**修复**：应改为 `totalAmount.subtract(fullPrice).compareTo(BigDecimal.ZERO) >= 0`。

## 4.4 白盒 vs 黑盒方法比较

| 维度 | 白盒 | 黑盒 |
|------|------|------|
| 关注点 | 代码内部结构和逻辑路径 | 输入输出行为 |
| 优势 | 发现逻辑遗漏、死代码、异常处理缺陷 | 发现"代码做了但不该做"的 Bug |
| 劣势 | 条件组合多时可能组合爆炸 | 无法发现未覆盖代码的缺陷 |
| **本项目的发现** | 路径覆盖发现 `validateToken` 的 NPE | 边界值分析发现 `intValue()` 截断 Bug |

**结论**：两种方法互补——白盒发现"实现错了什么"，黑盒发现"行为不符合预期"。同一项目使用两类方法分别独立发现了缺陷。

---

# 5、集成测试

## 5.1 集成策略：自底向上

```
Layer 1: DAO → MySQL (8 tests, @SpringBootTest + @Transactional)
    ↓
Layer 2: Service → DAO → MySQL + Redis (4 tests, full Spring context)
    ↓
Layer 3: Controller → MockMvc → Service → DAO → MySQL (7 tests, HTTP-based)
    ↓
Layer 4: JWT Filter → SecurityContext (6 tests, Mockito 隔离)
```

## 5.2 Mock 测试与集成测试对比

| 维度 | Mock 隔离测试 | 集成测试 |
|------|-------------|----------|
| 速度 | < 1s | 9-12s (Spring Boot 启动) |
| 依赖 | 无 | MySQL + Redis 运行 |
| 适用场景 | 逻辑验证、异常路径 | 数据流闭环、事务验证 |

**关键实战技巧**：
- `OncePerRequestFilter.doFilter()` 是 final → 反射调用 `doFilterInternal`
- `@Value` 字段在 UT 中为 null → 反射手动注入
- `@InjectMocks` 对 9+ 依赖的类有局限 → 改用 `@SpringBootTest`

## 5.3 CI/CD 集成

使用 GitHub Actions 实现持续集成流水线：

```
.git push → GitHub Actions
  ├─ Job 1: build-and-test (静态分析 + 30 单元测试)
  └─ Job 2: integration-tests (MySQL/Redis services + 12 集成测试)
```

3 次迭代修复：SNAPSHOT 依赖 → 拆分 Mock/Spring 测试 → ✅ 全绿。

---

# 6、系统测试

## 6.1 功能自动化测试（Playwright E2E）

6 个 Playwright 测试用例覆盖登录和商品管理核心流程：

| 用例 | 场景 | 结果 |
|------|------|------|
| E2E-001 | admin/macro123 登录 → 跳转首页 | ✅ |
| E2E-002 | 错误密码 → 停留登录页 | ✅ |
| E2E-003 | 空表单提交 → 表单校验 | ✅ |
| E2E-004 | 商品列表页面渲染 | ✅ |
| E2E-005 | 商品搜索"手机" | ✅ |
| E2E-006 | 商品详情查看 | ✅ |

## 6.2 性能测试（JMeter）

JMeter 5.6.3 对核心 API 进行并发负载测试，350 请求 0 错误。
HTML 仪表板报告含 APDEX 评分、响应时间曲线和 TPS 图。

## 6.3 安全测试（OWASP ZAP）

- Baseline scan: 65 PASS, 2 WARN
- API active scan: 111 PASS, 9 WARN, **0 FAIL**
- 关键发现：Actuator `/health` 暴露（中风险）、CORS 配置不完整（中风险）
- SQL 注入告警为误报（MyBatis 参数化查询天然防护）
- 安全头（X-Content-Type, X-XSS, X-Frame）均正确配置

---

# 7、测试总结和评价

## 7.1 测试统计

| 指标 | 数值 |
|------|------|
| 测试阶段 | 6 |
| 测试文件 | 17 个 Java 测试类 + 2 个 Playwright spec + 1 个 JMeter plan |
| 单元/集成测试用例 | **106** (Phase 1: 63 + Phase 2: 24 + Phase 5 CI: 19) |
| E2E 测试用例 | 6 (Playwright) |
| 性能采样 | 350 请求 (JMeter) |
| 安全扫描 URL | 256 (OWASP ZAP) |
| 静态分析发现 | 84 (3 SpotBugs + 81 PMD) |
| 测试通过率 | **100%** |
| CI/CD 通过率 | **100%** (2/2 jobs) |

## 7.2 发现的缺陷

| Issue ID | 类型 | 严重度 | 位置 | 描述 |
|----------|------|--------|------|------|
| MALL-BUG-001 | Bug | P2 | `JwtTokenUtil.java:94-95` | `validateToken` 在 token 无效时产生 NPE，缺少 null 检查 |
| MALL-BUG-002 | Bug | P1 | `OmsPromotionServiceImpl.java:184` | `intValue()` 截断导致满减金额边界判断错误 |
| MALL-SEC-001 | 安全 | P2 | Actuator `/health` | 端点未认证暴露系统健康状态 |
| MALL-SEC-002 | 安全 | P3 | CORS 配置 | 12 个端点缺少 `Access-Control-Allow-Origin` |
| MALL-PMD-001~081 | 规范 | P3 | 多处 | 字符串字面量重复、长方法 |

## 7.3 测试覆盖率评估

| 层级 | 目标 | 实际 |
|------|------|------|
| 核心 Service 方法 | ≥ 80% 行覆盖 | 6 个核心类全覆盖 |
| 关键 Controller | ≥ 70% | AdminController 7 个端点 |
| DAO/Mapper | ≥ 60% | 4 个 DAO 各 4 用例 |
| 前端 E2E | 核心流程 100% | 登录 + 商品管理 2 条黄金路径 |
| 安全 | 无高危漏洞 | 0 FAIL |

## 7.4 测试工具使用总结

| 课程工具 | 本项目使用 | 替代/补充 |
|----------|-----------|----------|
| JUnit | ✅ JUnit 5.8.2 | — |
| EasyMock | ✅ 同原理工具 Mockito 4.x | Mockito 与 Spring Boot 集成更紧密 |
| JAVA SSH | — (不适用) | — |
| JProfiler | ❌ (需 GUI) | JMeter 性能测试代替 profiling |
| JMeter | ✅ JMeter 5.6.3 | — |
| QTP | ❌ (仅 Windows) | Playwright 1.52 代替 |
| JIRA | ✅ Markdown Issue 表 | Jira 需企业账号，用表格模拟 |

---

# 8、项目对社会可持续发展产生的影响

1. **提升开源软件质量**：mall 项目在 GitHub 拥有 60k+ star，被大量开发者用于学习和二次开发。本次测试发现 2 个真实代码缺陷和 2 个安全配置问题，有助于提升整个生态的可靠性。

2. **测试方法论的实践推广**：本项目从零开始为大型开源项目建立完整测试体系（静态→单元→集成→E2E→性能→安全→CI/CD），包含 106 个自动化测试用例和 350 次性能采样，为其他开源项目提供了可复制的测试参考模板。

3. **自动化降本增效**：通过 GitHub Actions CI/CD 流水线，每次代码提交自动执行 42 个测试用例和静态分析，减少人工回归测试的时间成本和碳排放。

4. **电商系统的可靠性保障**：mall 涉及真实的交易、支付、库存场景，软件缺陷可能导致直接经济损失。严格的测试流程为线上运行提供了质量保障。

# 9、个人学习总结

通过为期 6 个阶段的 mall 项目系统化测试实践，我深刻体会到软件测试在工程实践中的核心地位：

1. **测试是开发的一部分，而非附加品**：从 Phase 1 的单元测试开始，我就将测试融入开发流程。每发现一个 Bug（如 `intValue()` 截断问题），都在代码层面理解了其根本原因。好的测试不是"证明代码正确"，而是"发现代码在何种条件下不正确"。

2. **白盒与黑盒互补**：白盒路径覆盖帮我发现了 JWT 校验中的 NPE（不读代码就不可能构造出让 `getUserNameFromToken` 返回 null 的场景），而黑盒边界值分析发现了满减金额的数值截断 Bug（不读代码也能发现，但白盒分析帮我快速定位了根因）。

3. **工具链的价值**：SpotBugs 3 分钟扫出 3 个潜在 null-return 缺陷，ZAP 自动化探测 256 个 API 端点发现 2 个安全配置问题，JMeter 在 6 秒内完成 350 次并发请求采样——工具极大地提升了测试效率和覆盖度。

4. **CI/CD 是测试的最终归宿**：本地测试通过 ≠ 环境一致性保障。从 GitHub Actions v1 的 SNAPSHOT 依赖失败到 v3 的全绿，我亲身体会到 CI 流水线是如何倒逼项目配置规范化、依赖管理显式化。

5. **大型项目的测试策略选择**：面对 60k+ star 的复杂项目，不可能也没有必要"全量覆盖"。关键是识别核心业务路径（认证→促销→订单）和脆弱点（JWT 安全、满减计算），将有限时间投入到最有价值的测试中。

---

## 附录 A：全部测试产物清单

```
MallTest/
├── .github/workflows/mall-ci.yml          # CI/CD 流水线
├── TEST_PLAN.md                           # 测试计划
├── PHASE1_REPORT.md                       # 阶段1: 静态分析 + 单元测试
├── PHASE2_REPORT.md                       # 阶段2: Mock + 集成测试
├── PHASE3_REPORT.md                       # 阶段3: E2E + 性能测试
├── PHASE4_REPORT.md                       # 阶段4: 安全测试
├── PHASE5_REPORT.md                       # 阶段5: CI/CD
├── FINAL_REPORT.md                        # 最终综合报告(本文档)
├── DEPLOY.md                              # 部署指南
├── mall/
│   ├── mall-security/src/test/.../        # JwtTokenUtilTest.java
│   ├── mall-admin/src/test/.../
│   │   ├── service/impl/                  # UmsAdminServiceImplTest, PmsProductServiceImplTest
│   │   ├── dao/                           # UmsAdminRoleRelationDaoTest, OmsOrderDaoTest
│   │   ├── controller/                    # AdminControllerTest
│   │   ├── security/                      # JwtAuthenticationTokenFilterTest
│   │   └── service/                       # UmsAdminServiceTest
│   ├── mall-portal/src/test/.../
│   │   └── service/impl/                  # OmsPromotionServiceImplTest, UmsMemberCouponServiceImplTest
│   │                                      # OmsPortalOrderServiceImplMockTest
│   └── test/jmeter/                       # mall-perf-test.jmx
├── mall-admin-web/
│   └── e2e/
│       ├── playwright.config.ts
│       └── specs/                         # login.spec.ts, product.spec.ts
└── mall/test/security/                    # ZAP 扫描报告
```

## 附录 B：运行全部测试的命令

```bash
# 1. 单元测试 (不需要中间件)
cd ~/MallTest/mall
mvn test -pl mall-security -DskipTests=false

# 2. 集成测试 (需要 Docker MySQL/Redis)
mvn test -pl mall-admin -DskipTests=false

# 3. E2E 测试
cd ~/MallTest/mall-admin-web
npx playwright test

# 4. 性能测试
/opt/jmeter/bin/jmeter -n -t mall/test/jmeter/mall-perf-test.jmx -l results.jtl

# 5. 安全扫描
docker run --rm -v $(pwd)/mall/test/security:/zap/wrk ghcr.io/zaproxy/zaproxy:stable \
  zap-baseline.py -t http://$(hostname -I | awk '{print $1}'):8080

# 6. 全部一起 (通过 CI)
# git push 即可，GitHub Actions 自动运行
```
