# 软件质量保证与测试技术 — 测试计划

## mall 项目测试方案

---

# 1、范围

## 1.1 标识

| 项目 | 说明 |
|------|------|
| **项目名称** | MallTest — mall 开源商城系统测试 |
| **被测系统** | mall（Spring Boot 微服务后端） + mall-admin-web（Vue3 管理前端） |
| **文档编号** | MALL-TEST-PLAN-001 |
| **缩略语** | UT=单元测试, IT=集成测试, ST=系统测试, PT=性能测试, SA=静态分析, E2E=端到端测试 |
| **被测 CSCI** | mall-admin (8080), mall-search (8081), mall-portal (8085), mall-admin-web (5173) |

## 1.2 系统概述

[mall](https://github.com/macrozheng/mall) 是一套完整的电商系统，包含商品管理、订单管理、营销推广、用户权限等模块。

- **后端**：Spring Boot 2.7.5 + MyBatis + MySQL + Redis + Elasticsearch + RabbitMQ + MongoDB
- **前端**：Vue 3 + TypeScript + Vite + Element Plus
- **测试重点模块**：mall-admin（后台管理）、mall-portal（前台商城）、mall-security（认证鉴权）

## 1.3 文档概述

本文档是 mall 项目的完整测试计划，涵盖测试进度安排、测试环境、测试工具选择、测试用例设计方法，以及单元测试、集成测试、系统测试、性能测试、安全测试、自动化测试的具体实施方案。

---

# 2、测试计划

## 2.1 测试进度

计划分 **6 个阶段** 迭代推进，每阶段产出可运行的测试代码和对应报告章节。

| 阶段 | 内容 | 产出 |
|------|------|------|
| **第一阶段** | 静态分析 + 单元测试（白盒 + 黑盒方法） | SpotBugs 报告、JUnit 测试代码（含多种用例设计方法） |
| **第二阶段** | Mock 测试 + 集成测试 | Mockito 隔离测试、Spring Boot IT 测试 |
| **第三阶段** | 系统测试（E2E 自动化 + 性能测试） | Playwright 脚本、JMeter 脚本及报告 |
| **第四阶段** | 安全测试 | OWASP ZAP 扫描报告 |
| **第五阶段** | CI/CD 自动化流水线 | GitHub Actions 配置、自动运行截图 |
| **第六阶段** | 报告整理 + 总结 | 完整测试报告、Jira 问题追踪记录 |

## 2.2 测试人员

| 角色 | 职责 |
|------|------|
| 测试工程师 | 全部测试用例设计、代码编写、执行、报告编写 |

## 2.3 测试环境

| 层级 | 组件 | 版本/配置 |
|------|------|----------|
| **OS** | Ubuntu 24.04 (WSL2) | 6.6.87.2-microsoft-standard-WSL2 |
| **JDK** | OpenJDK 1.8.0_442 (Temurin) | 8u442 |
| **构建工具** | Maven 3.8.7 | - |
| **数据库** | MySQL 5.7 (Docker) | root/root, 端口 3306 |
| **缓存** | Redis 7 (Docker) | 端口 6379 |
| **搜索引擎** | Elasticsearch 7.17.3 (Docker) | 端口 9200, IK 分词器 |
| **消息队列** | RabbitMQ 3.9.11 (Docker) | mall/mall, vhost=/mall, 端口 5672 |
| **NoSQL** | MongoDB 4 (Docker) | 端口 27017 |
| **对象存储** | MinIO (Docker) | minioadmin/minioadmin, 端口 9090 |
| **前端** | Node.js 22 + Vite 7 | 端口 5173 |
| **被测服务** | mall-admin :8080, mall-search :8081, mall-portal :8085, mall-demo :8082 | Spring Boot 2.7.5 |

## 2.4 测试工具

| 工具 | 用途 | 版本 | 来源 |
|------|------|------|------|
| **JUnit 5** | 单元测试 / 集成测试框架 | 5.x (随 Spring Boot 2.7.5) | Maven |
| **Mockito** | Mock 对象创建与验证 | 随 spring-boot-starter-test | Maven |
| **SpotBugs** | Java 静态分析 | 4.9.x | Maven 插件 |
| **PMD** | Java 代码规范检查 | 7.x | Maven 插件 |
| **JMeter** | 性能 / 压力测试 | 5.6.x | apt 安装 |
| **Playwright** | 前端 E2E 自动化测试 | 1.52.x | npm 安装 |
| **OWASP ZAP** | 安全扫描 | 2.16.x | Docker |
| **GitHub Actions** | CI/CD 自动化流水线 | - | GitHub |

> **工具选择说明**：
> - **JUnit 5 + Mockito**：课程使用的 EasyMock 与 Mockito 同属 Mock 框架，Mockito 在 Spring Boot 生态中集成更紧密，且与课程教学原理相通。
> - **JProfiler**：依赖 GUI，WSL 无图形环境，暂不采用。
> - **QTP**：仅支持 Windows，不兼容。
> - **JIRA**：使用 Markdown 表格模拟 Jira Issue 追踪（Jira REST API 需要企业账号）。
> - **JMeter / Playwright**：均可在 headless 模式下运行。

## 2.5 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 中间件（Docker 容器）未启动 | 集成测试无法执行 | 测试前运行 `docker compose ps` 检查，CI 中配置 health check |
| ES 索引未初始化 | mall-search 测试失败 | 测试前调用索引初始化 API |
| 数据库种子数据不一致 | 部分用例预期结果错误 | 编写独立的测试数据初始化脚本 |
| Mock 对象与真实行为偏差 | Mock 测试通过但真实调用失败 | 关键路径同时编写 Mock 测试和集成测试 |
| 前端 API 变更 | E2E 测试失效 | Playwright 测试使用 data-testid 选择器而非 CSS 类名 |

---

# 3、测试概述

## 3.1 功能测试概述

功能测试覆盖 mall 系统的 **4 个核心业务流程**：

| 测试套件 | 测试内容 | 测试方法 | 优先级 |
|----------|----------|----------|--------|
| **TEST-AUTH** | 用户登录 / 注册 / JWT 鉴权 / 权限控制 | 等价类划分 + 边界值分析 + 判定表 | P0 |
| **TEST-PRODUCT** | 商品 CRUD / 分类管理 / SKU 库存 / 属性管理 | 等价类划分 + 状态迁移 | P1 |
| **TEST-ORDER** | 订单生成 / 支付 / 发货 / 退货 / 取消 | 场景法 + 判定表 | P0 |
| **TEST-COUPON** | 优惠券领取 / 使用 / 折扣计算 | 等价类划分 + 边界值 + 判定表 | P1 |

### 3.1.1 测试结果一览表（模板）

| 测试项 | 成功 | 失败/错误 | 问题报告 | 评语 |
|--------|------|-----------|----------|------|
| TEST-AUTH-CASE-001 | | | | |
| TEST-AUTH-CASE-002 | | | | |
| TEST-PRODUCT-CASE-001 | | | | |
| TEST-ORDER-CASE-001 | | | | |
| TEST-COUPON-CASE-001 | | | | |
| ... | | | | |

## 3.2 性能测试概述

使用 JMeter 对核心 API 进行负载测试。

| 测试场景 | 并发用户 | 持续时间 | 关注指标 |
|----------|----------|----------|----------|
| 登录接口 `/admin/login` | 50 / 100 / 200 | 5 min | 平均响应时间, TPS, 错误率 |
| 商品列表 `/product/list` | 50 / 100 / 200 | 5 min | 平均响应时间, TPS, 错误率 |
| 搜索接口 `/esProduct/search/simple` | 50 / 100 | 5 min | 平均响应时间, TPS, 错误率 |

---

# 4、单元测试

> **要求**：采用多种测试用例设计方法并做比较分析。测试代码须有注释。

## 4.1 测试目标

覆盖项目中 **业务逻辑最集中** 的 6 个类，使用 **白盒 + 黑盒** 方法设计用例。

| 被测类 | 所属模块 | 行数 | 核心逻辑 |
|--------|----------|------|----------|
| `JwtTokenUtil` | mall-security | 170 | JWT 生成/解析/校验/刷新 |
| `OmsPromotionServiceImpl` | mall-portal | 273 | 4 种促销类型计算（单品/阶梯/满减/无） |
| `UmsAdminServiceImpl` | mall-admin | 287 | 注册/登录/密码验证/角色分配 |
| `UmsMemberCouponServiceImpl` | mall-portal | 244 | 优惠券领取/核验/发放 |
| `PmsProductServiceImpl` | mall-admin | 327 | 商品创建/SKU编码生成/批量状态更新 |
| `OmsPortalOrderServiceImpl` (关键方法) | mall-portal | 794 | `calcPromotionAmount`, `calcCouponAmount`, `generateOrderSn` |

## 4.2 测试用例设计方法

### 4.2.1 白盒测试方法

| 方法 | 应用类 | 说明 |
|------|--------|------|
| **语句覆盖** | `JwtTokenUtil.generateToken()` | 覆盖所有可执行语句 |
| **分支覆盖** | `OmsPromotionServiceImpl.calcCartPromotion()` | 覆盖 4 种促销类型分支 |
| **路径覆盖** | `UmsAdminServiceImpl.register()` | 覆盖用户名重复/成功/异常路径 |
| **条件覆盖** | `UmsMemberCouponServiceImpl.listCart()` | 覆盖 useType=0,1,2 的所有条件组合 |

### 4.2.2 黑盒测试方法

| 方法 | 应用类 | 说明 |
|------|--------|------|
| **等价类划分** | `JwtTokenUtil` | 有效 token / 过期 token / 篡改 token / 空 token |
| **边界值分析** | `OmsPromotionServiceImpl.getProductLadder()` | 刚好达到阶梯 / 刚好未达到 / 0 件 |
| **判定表** | `OmsPortalOrderServiceImpl` 优惠券叠加 | 有/无优惠券 × 有/无积分 × 满减/阶梯 |
| **状态迁移** | 订单状态流转 | 待付款→已付款→已发货→已完成 / 已取消 |

## 4.3 单元测试代码结构

```
mall/
├── mall-admin/src/test/java/com/macro/mall/
│   ├── service/impl/
│   │   ├── UmsAdminServiceImplTest.java      # 用户认证逻辑
│   │   └── PmsProductServiceImplTest.java    # 商品 SKU 逻辑
│   └── security/
│       └── JwtTokenUtilTest.java             # JWT 令牌逻辑
├── mall-portal/src/test/java/com/macro/mall/portal/
│   └── service/impl/
│       ├── OmsPromotionServiceImplTest.java  # 促销计算逻辑
│       ├── UmsMemberCouponServiceImplTest.java # 优惠券逻辑
│       └── OmsPortalOrderServiceImplTest.java  # 订单金额计算
└── mall-security/src/test/java/com/macro/mall/
    └── util/
        └── JwtTokenUtilWhiteBoxTest.java     # 白盒路径测试
```

---

# 5、集成测试

> **要求**：采用合适集成模式，例如 CI。

## 5.1 集成策略

采用 **自底向上** 集成策略，分层测试：

```
第 1 层：DAO 层集成测试（Mapper + 真实 MySQL）
    ↓
第 2 层：Service 层集成测试（Service + DAO + Redis）
    ↓
第 3 层：Controller 层集成测试（MockMvc + 完整 Spring 上下文）
    ↓
第 4 层：安全链集成测试（Filter + JWT + 权限校验）
```

## 5.2 集成测试内容

| 测试类 | 集成范围 | 测试方法 |
|--------|----------|----------|
| `UmsAdminDaoIT` | AdminMapper → MySQL | `@MybatisTest` |
| `UmsAdminServiceIT` | Service + Mapper + Redis | `@SpringBootTest` |
| `AdminControllerIT` | Controller + Service + DB | `@WebMvcTest` + MockMvc |
| `SecurityFilterChainIT` | JwtFilter + DynamicSecurityFilter | `@SpringBootTest` + TestRestTemplate |
| `OmsOrderIntegrationIT` | OrderService → Mapper → MySQL | `@SpringBootTest` + 事务回滚 |
| `ProductSearchIT` | SearchController → ES | `@SpringBootTest` |

## 5.3 CI/CD 集成

使用 GitHub Actions 实现持续集成流水线：

```yaml
# .github/workflows/test.yml
name: Mall Test Suite
on: [push, pull_request]
jobs:
  unit-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '8', distribution: 'temurin' }
      - run: mvn test -pl mall-common,mall-mbg,mall-security,mall-admin,mall-portal
  integration-test:
    needs: unit-test
    runs-on: ubuntu-latest
    services:
      mysql: { image: mysql:5.7, env: { MYSQL_ROOT_PASSWORD: root } }
      redis: { image: redis:7 }
    steps:
      - run: mvn verify -P integration-test
```

---

# 6、系统测试

> **要求**：功能自动化测试、性能测试和实验数据分析，可选安全测试。

## 6.1 功能自动化测试（Playwright E2E）

### 测试范围

使用 Playwright 对 mall-admin-web 的 **3 个核心流程** 进行端到端自动化测试：

| 测试脚本 | 覆盖页面 | 操作步骤 |
|----------|----------|----------|
| `login.spec.ts` | 登录页 → 首页 | 输入用户名密码 → 登录 → 验证跳转首页 → 验证用户名显示 |
| `product.spec.ts` | 商品管理 | 登录 → 商品列表 → 按名称搜索 → 查看详情 → 批量操作 |
| `order.spec.ts` | 订单管理 | 登录 → 订单列表 → 查看详情 → 修改收货人 → 发货 |

### 测试项目结构

```
mall-admin-web/
├── e2e/
│   ├── playwright.config.ts     # Playwright 配置
│   ├── fixtures/
│   │   └── auth.ts              # 登录态共享 fixture
│   ├── specs/
│   │   ├── login.spec.ts        # 登录流程
│   │   ├── product.spec.ts      # 商品管理流程
│   │   └── order.spec.ts        # 订单管理流程
│   └── utils/
│       └── selectors.ts         # data-testid 选择器常量
```

## 6.2 性能测试（JMeter）

### 测试计划

| 线程组 | 接口 | 并发阶梯 | 断言 |
|--------|------|----------|------|
| TG-Login | POST `/admin/login` | 10→50→100 | 响应码 200, 响应时间 < 2s |
| TG-ProductList | GET `/product/list` | 10→50→100 | 响应码 200, 响应时间 < 3s |
| TG-Search | GET `/esProduct/search/simple` | 10→50 | 响应码 200, 响应时间 < 1s |

### JMeter 脚本结构

```
mall/test/jmeter/
├── mall-test-plan.jmx           # 主测试计划
├── login-thread-group.jmx       # 登录场景
├── product-list-thread-group.jmx # 商品列表场景
└── dashboard/                   # 生成的 HTML 报告
```

## 6.3 安全测试（OWASP ZAP）

### 扫描配置

使用 OWASP ZAP Docker 镜像执行自动化安全扫描：

| 扫描类型 | 目标 | 说明 |
|----------|------|------|
| 主动扫描 | `http://localhost:8080` | 后端 API 安全漏洞 |
| 被动扫描 | `http://localhost:5173` | 前端 XSS/CSRF 等 |
| API 模糊测试 | `/admin/login`, `/admin/register` | 注入攻击检测 |

主要关注：SQL 注入、XSS、CSRF、JWT 安全、敏感信息泄露、不安全的 HTTP 头。

---

# 7、测试总结和评价

## 7.1 问题追踪（Jira 模拟）

使用 Markdown 表格模拟 Jira Issue 追踪：

| Issue ID | 类型 | 优先级 | 状态 | 描述 | 关联测试 |
|----------|------|--------|------|------|----------|
| MALL-001 | Bug | P1 | Open | 密码更新接口未校验旧密码匹配 | TEST-AUTH-CASE-005 |
| MALL-002 | Bug | P2 | Open | 商品 SKU 编码在并发下可能重复 | TEST-PRODUCT-CASE-008 |
| MALL-003 | Improvement | P2 | Open | 优惠券领取缺少幂等性保护 | TEST-COUPON-CASE-004 |
| MALL-004 | Bug | P3 | Open | order/close 接口缺少超时订单自动关闭确认 | TEST-ORDER-CASE-010 |

## 7.2 测试覆盖率目标

| 层级 | 目标覆盖率 | 说明 |
|------|------------|------|
| 核心 Service 方法 | ≥ 80% 行覆盖 | `OmsPromotionServiceImpl`, `JwtTokenUtil` 等 |
| 关键 Controller | ≥ 70% | 认证、订单、商品相关接口 |
| DAO/Mapper | ≥ 60% | 自定义 DAO 方法 |
| 前端工具函数 | ≥ 90% | `validate.ts`, `datetime.ts` |
| 前端 E2E 核心流程 | 100% 场景覆盖 | 3 条黄金路径 |

---

# 8、项目对社会可持续发展产生的影响

通过对 mall 开源电商项目进行系统化测试，本项目在以下方面对社会可持续发展具有积极意义：

1. **提升开源软件质量**：mall 项目在 GitHub 拥有 60k+ star，被大量开发者用于学习和二次开发。系统的测试能发现并修复隐藏缺陷，提升整个生态的可靠性。

2. **电商系统的稳定性**：mall 涉及真实的交易、支付、库存场景，软件缺陷可能导致经济损失。严格的测试流程保障了线上运行的可靠性。

3. **测试方法论的实践推广**：本项目展示了一套完整的开源项目测试方法论（静态→单元→集成→系统→性能→安全→自动化），可作为其他开源项目的测试参考模板。

4. **自动化测试降低人力成本**：通过 CI/CD 自动化流水线，每次代码提交都能自动执行测试，减少人工回归测试的碳排放和资源消耗。

# 9、个人学习总结

（待测试完成后撰写）

---

## 附录 A：测试环境搭建快速命令

```bash
# 1. 启动所有中间件
cd ~/MallTest/mall/document/docker
docker compose -f docker-compose-env.yml up -d

# 2. 等待 MySQL 就绪
until docker exec mysql mysqladmin ping -h localhost -u root -proot --silent; do sleep 2; done

# 3. 导入数据库（如尚未导入）
docker exec -i mysql mysql -u root -proot mall < ~/MallTest/mall/document/sql/mall.sql

# 4. 构建项目
cd ~/MallTest/mall
mvn install -DskipTests -Ddocker.skip=true

# 5. 启动后端服务
java -jar mall-admin/target/mall-admin-1.0-SNAPSHOT.jar --spring.profiles.active=dev &
java -jar mall-search/target/mall-search-1.0-SNAPSHOT.jar --spring.profiles.active=dev &
java -jar mall-portal/target/mall-portal-1.0-SNAPSHOT.jar --spring.profiles.active=dev &

# 6. 启动前端
cd ~/MallTest/mall-admin-web
npm run dev &

# 7. 执行测试
cd ~/MallTest/mall
mvn test                                    # 单元测试
mvn verify -P integration-test              # 集成测试
cd ~/MallTest/mall-admin-web && npx playwright test  # E2E 测试
```

## 附录 B：测试用例设计方法比较表

| 方法 | 类型 | 优点 | 缺点 | 本项目适用场景 |
|------|------|------|------|---------------|
| 等价类划分 | 黑盒 | 减少用例数量，覆盖代表性数据 | 可能遗漏边界组合 | 登录参数校验、金额计算 |
| 边界值分析 | 黑盒 | 高效发现边界错误 | 依赖等价类先划分 | 促销阶梯阈值、分页参数 |
| 判定表 | 黑盒 | 逻辑条件组合完整 | 条件多时组合爆炸 | 优惠券叠加规则、订单状态流转 |
| 语句覆盖 | 白盒 | 最简单，确保每行都执行 | 不能发现逻辑遗漏 | JWT 工具类基础测试 |
| 分支覆盖 | 白盒 | 覆盖 if-else 所有分支 | 复杂条件的组合未覆盖 | 促销类型分发逻辑 |
| 路径覆盖 | 白盒 | 覆盖所有执行路径 | 循环导致路径爆炸 | 注册/登录关键流程 |
