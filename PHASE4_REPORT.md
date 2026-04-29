# 阶段 4 测试报告：安全测试

## mall 项目 — 软件质量保证与测试技术课程设计

---

# 1、测试范围

## 1.1 标识

| 项目 | 说明 |
|------|------|
| **测试阶段** | 阶段 4 — 安全测试 |
| **测试工具** | OWASP ZAP 2.16.x (Docker) |
| **被测目标** | mall-admin API (localhost:8080) |
| **扫描范围** | baseline scan（基础被动扫描）+ api scan（基于 Swagger 的主动扫描） |
| **文档编号** | MALL-TEST-REPORT-P4-001 |
| **测试日期** | 2026-04-29 |

## 1.2 扫描配置

| 配置项 | baseline scan | api scan |
|--------|--------------|----------|
| 目标 | `http://172.24.17.190:8080` | `http://172.24.17.190:8080/v2/api-docs` |
| 发现 URL 数 | 3 | **253** |
| 扫描模式 | 被动扫描 | 主动扫描 (含 SQLi, XSS, Buffer Overflow 等攻击探测) |
| 规则集 | 默认规则 + alpha 被动规则 | 默认 + 主动攻击规则 |

---

# 2、Baseline Scan 结果（被动扫描）

## 2.1 总体统计

| 级别 | 数量 |
|------|------|
| FAIL | 0 |
| WARN | 2 |
| PASS | 65 |

## 2.2 WARN 发现

| 规则 ID | 规则名称 | 影响 URL | 说明 |
|---------|----------|----------|------|
| 10049 | Storable but Non-Cacheable Content | 3 URLs (首页, robots.txt, sitemap.xml) | 响应内容可存储但未设置缓存头，可能导致缓存不一致 |
| 10098 | Cross-Domain Misconfiguration | 2 URLs (首页, robots.txt) | CORS 配置不完整，`Access-Control-Allow-Origin` 未设置 |

### 手动验证

```bash
# 验证 CORS 配置
curl -sI http://localhost:8080/admin/login | grep -iE "access-control"
# 结果: Vary: Origin, Vary: Access-Control-Request-Method (Allow-Origin 未返回)
```

**结论**：Spring Boot 的 CORS 配置启用了但 `Access-Control-Allow-Origin` 未在所有端点正确返回，可能导致跨域请求被拒绝。

---

# 3、API Scan 结果（主动扫描）

## 3.1 总体统计

| 级别 | 数量 |
|------|------|
| FAIL | **0** |
| WARN | **9** |
| PASS | 111 |

### 各严重级别 URL 覆盖情况

```
扫描端点分布：
  /admin/*     — 管理接口（权限、角色、用户管理）
  /brand/*     — 品牌接口
  /product/*   — 商品接口
  /order/*     — 订单接口
  /minio/*     — 文件上传接口
  /actuator/*  — Spring Actuator 监控端点
```

## 3.2 WARN 发现详情

### 严重级别 1: 需要关注

| # | 规则 ID | 规则名称 | 影响端点 | 风险 |
|---|---------|----------|----------|------|
| 1 | **40042** | **Spring Actuator Information Leak** | `GET /actuator/health` | **中** |
| 2 | **10098** | **Cross-Domain Misconfiguration** | 12 个端点 | **中** |
| 3 | **10023** | **Information Disclosure - Debug Error** | `/admin/logout` (500) | 低 |

### 严重级别 2: 低风险 / 误报

| # | 规则 ID | 规则名称 | 影响端点 | 分析 |
|---|---------|----------|----------|------|
| 4 | 100000 | Server Error Response (500) | 3 端点 | ZAP 发送异常参数导致服务端 500，非安全漏洞 |
| 5 | 30001 | Buffer Overflow | 2 端点 | ZAP 发送超长输入，服务端返回 500，无溢出 |
| 6 | 30002 | Format String Error | `/admin/register` | ZAP 发送格式字符串 `%n%s`，服务端正常拒绝 |
| 7 | **40018** | **SQL Injection** | `/admin/register` | **误报**：MyBatis 使用参数化查询，非字符串拼接 |
| 8 | 90004 | Cross-Origin-Resource-Policy Missing | 5 端点 | 缺少 CORP 头（开发环境可接受） |
| 9 | 90022 | Application Error Disclosure | `/admin/logout` | ZAP 请求异常参数导致 400，非敏感泄露 |

## 3.3 关键发现分析

### 发现 1: Spring Actuator 信息泄露 🔴

**风险等级**：中

**详情**：`/actuator/health` 端点未经认证即返回系统健康状态：

```bash
$ curl http://localhost:8080/actuator/health
{"status":"UP"}
```

Spring Boot Actuator 默认暴露 `health` 端点，在生产环境中应：
- 通过 Spring Security 配置限制访问
- 或关闭非必要的 Actuator 端点
- 在 `application-prod.yml` 中添加：
  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: none
  ```

### 发现 2: CORS 跨域配置不完整 🟡

**风险等级**：中

**详情**：虽然 Spring Boot 配置了 CORS 支持（返回 `Vary: Origin`），但 `Access-Control-Allow-Origin` 未在所有接口正确返回。12 个端点被检测到缺少 CORS 允许头。

### 发现 3: SQL 注入测试 — 误报 🟢

**详情**：ZAP 的 SQL Injection 规则对 `/admin/register` 报了 WARN，但经过手动验证：

- mall 项目使用 MyBatis 参数化查询（`#{param}` 占位符），SQL 语句在执行前由 JDBC PreparedStatement 预编译
- ZAP 发送的 SQL 注入 payload（如 `ZAP' OR '1'='1`）被作为普通字符串处理，不会改变 SQL 语义
- 服务端返回 `{"code":500,"message":"操作失败"}` 而非数据库错误，说明输入经过了正常的业务逻辑校验

**结论**：该项目使用 MyBatis，对 SQL 注入有天然防护（参数化查询），不存在 SQL 注入漏洞。

### 发现 4: 安全头配置良好 🟢

| 安全头 | 状态 | 值 |
|--------|------|-----|
| X-Content-Type-Options | ✅ 已设置 | `nosniff` |
| X-XSS-Protection | ✅ 已设置 | `1; mode=block` |
| X-Frame-Options | ✅ 已设置 | `DENY` |
| Content-Security-Policy | ❌ 未设置 | — |
| Strict-Transport-Security | ❌ 未设置 | —（HTTP 环境不适用） |

---

# 4、安全测试总体评估

## 4.1 评分

| 维度 | 评分 | 说明 |
|------|------|------|
| **SQL 注入防护** | ✅ 优秀 | MyBatis 参数化查询，无注入风险 |
| **XSS 防护** | ✅ 良好 | X-XSS-Protection 头 + 后端 JSON API（无 HTML 渲染） |
| **认证安全** | ⚠️ 中等 | JWT 方案成熟，但 Actuator 端点未受保护 |
| **CORS 配置** | ⚠️ 中等 | 部分端点缺少 Allow-Origin 头 |
| **错误消息** | ✅ 良好 | 生产应关闭 DEBUG，当前 dev 环境可接受 |
| **安全头** | ✅ 良好 | 3/5 核心安全头已设置 |
| **总体风险** | 🟡 低-中 | 2 个中风险（Actuator + CORS），无高危 |

## 4.2 修复优先级

| 优先级 | 问题 | 建议 |
|--------|------|------|
| P1 | Actuator 暴露 | `management.endpoints.web.exposure.include=none` 或 Security 保护 |
| P2 | CORS 配置 | 检查全局 CORS 配置是否覆盖所有 Controller |
| P3 | CSP 头缺失 | 添加 Content-Security-Policy 头 |
| P4 | CORP 头缺失 | 添加 Cross-Origin-Resource-Policy 头 |

## 4.3 安全测试产物

```
mall/test/security/
├── report.html              # baseline scan HTML 报告
├── report.md                # baseline scan Markdown 报告
├── api-scan-report.html     # API active scan HTML 报告
└── api-scan-report.md       # API active scan Markdown 报告
```

## 4.4 复现步骤

```bash
# 1. 确保后端运行中
curl http://localhost:8080/actuator/health

# 2. 运行 ZAP baseline scan（被动扫描，约 2 分钟）
docker run --rm \
  -v $(pwd)/mall/test/security:/zap/wrk:rw \
  ghcr.io/zaproxy/zaproxy:stable \
  zap-baseline.py -t http://$(hostname -I | awk '{print $1}'):8080 \
  -r /zap/wrk/report.html -w /zap/wrk/report.md

# 3. 运行 ZAP API scan（主动扫描，约 5 分钟）
docker run --rm \
  -v $(pwd)/mall/test/security:/zap/wrk:rw \
  ghcr.io/zaproxy/zaproxy:stable \
  zap-api-scan.py -t http://$(hostname -I | awk '{print $1}'):8080/v2/api-docs \
  -f openapi -r /zap/wrk/api-scan-report.html -w /zap/wrk/api-scan-report.md
```

---

# 5、累计测试进度

| 阶段 | 内容 | 用例数 | 状态 |
|------|------|--------|------|
| Phase 1 | 静态分析 + 单元测试 | 63 + 84 findings | ✅ |
| Phase 2 | Mock 测试 + 集成测试 | +24 | ✅ |
| Phase 3 | E2E 自动化 + 性能测试 | +6 E2E + 350 JMeter | ✅ |
| Phase 4 | 安全测试 (OWASP ZAP) | 256 URLs, 0 FAIL | ✅ |
| Phase 5 | CI/CD (GitHub Actions) | 待做 | ⏳ |
| Phase 6 | 报告整理 + 总结 | 待做 | ⏳ |
