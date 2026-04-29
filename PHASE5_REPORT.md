# 阶段 5 测试报告：CI/CD 自动化流水线

## mall 项目 — 软件质量保证与测试技术课程设计

---

# 1、CI/CD 流水线设计

## 1.1 标识

| 项目 | 说明 |
|------|------|
| **测试阶段** | 阶段 5 — CI/CD 自动化流水线 |
| **CI 平台** | GitHub Actions |
| **工作流文件** | `.github/workflows/mall-ci.yml` |
| **触发方式** | push (master) / pull_request / workflow_dispatch (手动) |
| **文档编号** | MALL-TEST-REPORT-P5-001 |
| **测试日期** | 2026-04-29 |

## 1.2 流水线架构

```
┌──────────────────────────────────────────────────────────────┐
│                    GitHub Actions CI/CD                      │
├──────────────────────────────────────────────────────────────┤
│  Job 1: build-and-test (ubuntu-latest, JDK 8)               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ 1. mvn install -DskipTests (安装本地 SNAPSHOT 模块)      │ │
│  │ 2. SpotBugs scan (静态分析)                              │ │
│  │ 3. PMD scan (代码规范检查)                               │ │
│  │ 4. mall-security unit tests (20 tests)                  │ │
│  │ 5. mall-admin unit tests (7 mock tests)                 │ │
│  │ 6. mall-portal unit tests (3 mock tests)                │ │
│  └─────────────────────────────────────────────────────────┘ │
│                            ↓                                  │
│  Job 2: integration-tests (ubuntu-latest, JDK 8)            │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ Services: MySQL 5.7 + Redis 7 (Docker containers)       │ │
│  │ 1. Import mall.sql → MySQL                              │ │
│  │ 2. DAO integration tests (8 tests against real DB)      │ │
│  │ 3. Service integration tests (4 tests, Service→DB)      │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

## 1.3 流水线设计决策

| 决策 | 理由 |
|------|------|
| 拆分为 2 个 Job | unit tests 无外部依赖可快速失败；integration tests 需要 MySQL/Redis services，独立调度 |
| `mvn install` 作为第一步 | mall-common/mall-mbg 等模块是 SNAPSHOT 版本，不存于公共仓库，须先安装到本地 `.m2` |
| `-Ddocker.skip=true` | CI 环境无需构建 Docker 镜像，跳过 docker-maven-plugin |
| 集成测试仅启用 MySQL + Redis | ES/MongoDB/RabbitMQ 等服务配置复杂，仅对非必需中间件的测试做降级 |
| `if: always()` 上传 artifacts | 即使测试失败也保留报告，方便事后排查 |

---

# 2、CI 运行结果

## 2.1 最新 CI 运行

| 属性 | 值 |
|------|-----|
| Run ID | 25110983814 |
| Branch | master |
| Trigger | push |
| Commit | `b624e3c` — Fix CI: separate pure unit tests from Spring-based integration tests |

## 2.2 各 Job 测试结果

### Job 1: build-and-test ✅

| 步骤 | 模块 | 测试数 | 结果 |
|------|------|--------|------|
| SpotBugs | mall-security, mall-admin, mall-portal | — | ✅ |
| PMD | mall-security, mall-admin, mall-portal | — | ✅ |
| Unit tests | mall-security (JwtTokenUtilTest) | 20 | ✅ 0 失败 |
| Unit tests | mall-admin (JwtFilter + UmsAdmin + PmsProduct) | 7 | ✅ 0 失败 |
| Unit tests | mall-portal (OmsPortalOrderMock + OmsPromotion + Coupon) | 3 | ✅ 0 失败 |

### Job 2: integration-tests ✅

| 步骤 | 测试类 | 测试数 | 结果 |
|------|--------|--------|------|
| DAO 层 | OmsOrderDaoTest | 4 | ✅ 0 失败 |
| DAO 层 | UmsAdminRoleRelationDaoTest | 4 | ✅ 0 失败 |
| Service 层 | UmsAdminServiceTest | 4 | ✅ 0 失败 |

## 2.3 总览

| 指标 | 数值 |
|------|------|
| CI Job 数 | 2 |
| CI 通过率 | **100%** (2/2) |
| 单元测试 | 30 通过, 0 失败 |
| 集成测试 | 12 通过, 0 失败 |
| SpotBugs 问题 | 3 (无新增) |
| PMD violations | 81 (无新增) |
| 总耗时 | ~9 min (build-and-test: ~6 min, integration-tests: ~3 min) |

---

# 3、CI 工作流关键配置

### 3.1 工作流文件

```yaml
# .github/workflows/mall-ci.yml
name: Mall CI Test Suite

on:
  push:
    branches: [master]
  pull_request:
    branches: [master]
  workflow_dispatch:   # 支持手动触发

jobs:
  build-and-test:      # Job 1: 静态分析 + 单元测试
  integration-tests:   # Job 2: 集成测试 (MySQL + Redis)
```

### 3.2 MySQL + Redis 服务定义

```yaml
services:
  mysql:
    image: mysql:5.7
    env:
      MYSQL_ROOT_PASSWORD: root
    ports:
      - 3306:3306
    options: >-
      --health-cmd="mysqladmin ping -h localhost -u root -proot"
      --health-interval=10s
      --health-timeout=5s
      --health-retries=5
  redis:
    image: redis:7
    ports:
      - 6379:6379
    options: >-
      --health-cmd="redis-cli ping"
      --health-interval=10s
```

### 3.3 Maven 依赖缓存

使用 `actions/setup-java@v4` 的 `cache: maven` 参数，自动缓存 `~/.m2/repository`，加速后续构建。

---

# 4、CI/CD 流水线演进记录

| 版本 | 提交 | 问题 | 修复 |
|------|------|------|------|
| v1 | `0e116f2` | SNAPSHOT 依赖解析失败 (`mall-common:jar:1.0-SNAPSHOT` not found) | 添加 `mvn install` 步骤 |
| v2 | `fdeef5b` | mall-admin 单元测试启动 Spring 上下文失败 (无 MySQL) | 拆分纯 Mock 测试和 @SpringBootTest 集成测试 |
| v3 | `b624e3c` | ✅ 全部通过 | — |

---

# 5、与本地测试的对比

| 维度 | 本地 (WSL2) | CI (GitHub Actions) |
|------|------------|---------------------|
| OS | Ubuntu 24.04 | ubuntu-latest (22.04) |
| JDK | 1.8.0_442 | 1.8.0_482 |
| Maven | 3.8.7 | 3.9.x (bundled) |
| 中间件 | Docker (全量: MySQL, Redis, ES, Mongo, RabbitMQ, MinIO, Nginx, Logstash, Kibana) | Docker services (MySQL + Redis 仅必需) |
| 全部测试 | 87 unit + 19 integration = 106 | 30 unit + 12 integration = 42 |
| CI 未覆盖 | — | Controller 全栈集成测试 (需 ES/Mongo/RabbitMQ) |

---

# 6、累计测试进度

| 阶段 | 内容 | 用例数 | 状态 |
|------|------|--------|------|
| Phase 1 | 静态分析 + 单元测试 | 63 + 84 findings | ✅ |
| Phase 2 | Mock 测试 + 集成测试 | +24 | ✅ |
| Phase 3 | E2E 自动化 + 性能测试 | +6 E2E + 350 JMeter | ✅ |
| Phase 4 | 安全测试 (OWASP ZAP) | 256 URLs, 0 FAIL | ✅ |
| Phase 5 | CI/CD (GitHub Actions) | 2 jobs, 42 CI tests | ✅ |
| Phase 6 | 报告整理 + 总结 | 待做 | ⏳ |
