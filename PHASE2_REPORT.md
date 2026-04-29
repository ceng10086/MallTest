# 阶段 2 测试报告：Mock 测试 + 集成测试

## mall 项目 — 软件质量保证与测试技术课程设计

---

# 1、测试范围

## 1.1 标识

| 项目 | 说明 |
|------|------|
| **测试阶段** | 阶段 2 — Mock 测试 + 集成测试 |
| **被测模块** | mall-admin（DAO/Service/Controller/Filter 四层集成）、mall-portal（Service Mock 隔离测试） |
| **文档编号** | MALL-TEST-REPORT-P2-001 |
| **测试日期** | 2026-04-29 |

## 1.2 目标

本阶段完成两项工作：

1. **Mock 隔离测试**：深入使用 Mockito 的 @Mock/@InjectMocks/verify/反射等技术，对复杂依赖的类进行完全隔离的单元测试。
2. **分层集成测试**：采用自底向上策略，依次验证 DAO→MySQL、Service→DAO→Redis、Controller→MockMvc→全栈的集成正确性。

---

# 2、测试环境

| 组件 | 版本 |
|------|------|
| JDK | OpenJDK 1.8.0_442 |
| Maven | 3.8.7 |
| 测试框架 | JUnit 5.8.2 + Mockito 4.x |
| Mock 框架 | Mockito (@Mock, @InjectMocks, @Spy, lenient, verify) |
| Web 层测试 | MockMvc (spring-test) |
| 数据库 | MySQL 5.7 (Docker, 种子数据来自 mall.sql) |
| 构建命令 | `mvn test -DskipTests=false -pl mall-admin,mall-portal -am` |

---

# 3、Mock 测试

## 3.1 JwtAuthenticationTokenFilter Mock 隔离测试

### 被测对象

`JwtAuthenticationTokenFilter` — Spring Security 过滤链中的 JWT 认证过滤器。继承自 `OncePerRequestFilter`，依赖 `JwtTokenUtil`、`UserDetailsService` 和 `@Value` 注入的 `tokenHeader`/`tokenHead`。

### Mock 策略

| Mock 对象 | 用途 |
|-----------|------|
| `@Mock JwtTokenUtil` | 控制 token 解析和校验行为 |
| `@Mock UserDetailsService` | 控制用户加载行为 |
| `@Mock FilterChain` | 验证请求是否被放行 |

另外通过 **反射设置 `@Value` 字段**（`Authorization`、`Bearer `），因为单元测试中无 Spring 容器。

### 技术要点

`OncePerRequestFilter.doFilter()` 是 `final` 方法，无法被 Mockito 拦截。解决方案：通过反射获取 `protected doFilterInternal()` 方法直接调用。

```java
doFilterInternalMethod = JwtAuthenticationTokenFilter.class
    .getDeclaredMethod("doFilterInternal", HttpServletRequest.class,
        HttpServletResponse.class, FilterChain.class);
doFilterInternalMethod.setAccessible(true);
```

### 测试用例

| 用例 ID | 场景 | Mock 设置 | 验证点 |
|---------|------|----------|--------|
| FLT-001 | 无 Authorization header | 无 | `userDetailsService` 未被调用，请求放行 |
| FLT-002 | 非 Bearer header (Basic) | 无 | `jwtTokenUtil` 未被调用，请求放行 |
| FLT-003 | token 解析失败 (null) | `getUserNameFromToken → null` | `userDetailsService` 未被调用 |
| FLT-004 | 合法 token | 完整 Mock 链：解析→加载→校验→true | `SecurityContext` 已设置 Authentication |
| FLT-005 | token 校验失败 | 解析成功但 `validateToken → false` | `SecurityContext` 未设置 Authentication |
| FLT-006 | verify 行为断言 | token 解析返回 null | `filterChain.doFilter` 被调用 1 次 |

**结果**：6/6 通过。

## 3.2 OmsPortalOrderServiceImpl Mock 隔离测试

### 被测对象

`OmsPortalOrderServiceImpl` — 整个项目中依赖最多的类（9+ 个 `@Autowired`），完整测试需要 mock 所有依赖。

### Mock 策略

仅 mock 4 个关键依赖（`memberService`、`cartItemService`、`memberReceiveAddressService`、`memberCouponService`），验证**快速失败路径**（参数校验不通过时直接抛异常，不访问未 mock 的依赖）。

### 测试用例

| 用例 ID | 场景 | 预期 |
|---------|------|------|
| MOCK-001 | 未选择收货地址 (`memberReceiveAddressId=null`) | `Asserts.fail` 抛出异常 |
| MOCK-002 | 空购物车 ID 列表 | `Asserts.fail` 抛出异常 |
| MOCK-003 | `getCurrentMember` 方法交互验证 | 验证 mock 的 setup 正常工作 |

**结果**：3/3 通过。

### Mock 测试局限性分析

复杂 Service（9+ 依赖）的完整路径 Mock 测试需要 mock 全部依赖，否则会在未 mock 处 NPE。对于此类场景，更推荐使用 `@SpringBootTest` 集成测试（见第 4 节），或通过方法级重构降低依赖数量（提取独立的计算逻辑为无状态工具方法）。

---

# 4、集成测试

## 4.1 集成策略：自底向上

```
第 1 层：DAO → MySQL
    ↓
第 2 层：Service → DAO → MySQL + Redis
    ↓
第 3 层：Controller → Service → DAO → MySQL（MockMvc）
    ↓
第 4 层：Security Chain（JWT Filter + DynamicSecurityFilter）
```

## 4.2 DAO 层集成测试

### UmsAdminRoleRelationDaoTest

**集成范围**：`UmsAdminRoleRelationDao` → MyBatis → MySQL

| 用例 ID | 测试方法 | 验证 |
|---------|----------|------|
| DAO-001 | `getRoleList(1L)` | 种子数据中 admin 用户有角色 |
| DAO-002 | `getResourceList(1L)` | admin 用户有资源权限（四级 JOIN） |
| DAO-003 | `getResourceList(99999L)` | 不存在的用户返回空列表 |
| DAO-004 | `getAdminIdList(1L)` | 根据资源 ID 反查管理员 |

**结果**：4/4 通过。

### OmsOrderDaoTest

**集成范围**：`OmsOrderDao` → MyBatis + PageHelper → MySQL

| 用例 ID | 测试方法 | 验证 |
|---------|----------|------|
| DAO-101 | 无条件分页查询 | `getList` 返回 `delete_status=0` 的订单 |
| DAO-102 | 按 `status=0` 筛选 | 全部结果 status 为 0 |
| DAO-103 | 按订单号关键字搜索 | 结果中订单号包含搜索词 |
| DAO-104 | 不存在的订单号 | 返回空列表 |

**结果**：4/4 通过。

## 4.3 Service 层集成测试

### UmsAdminServiceTest（集成版）

**集成范围**：`UmsAdminServiceImpl` → Mapper → MySQL + PasswordEncoder（BCrypt）+ JwtTokenUtil（真实 JWT）

| 用例 ID | 测试方法 | 验证 |
|---------|----------|------|
| SVC-001 | `getAdminByUsername("admin")` | 种子数据中 admin 存在，密码为 BCrypt |
| SVC-002 | `login("admin", "macro123")` | 返回有效 JWT token（含 `.` 分隔符） |
| SVC-003 | `login("admin", "wrong")` | 错误密码抛出异常 |
| SVC-004 | `register(newUser) → getAdminByUsername` | 注册 → 持久化 → 查询，密码被 BCrypt 加密 |

**结果**：4/4 通过。验证了"注册 → 写入 DB → 再次查询 → 比对密码"的完整数据流闭环。

## 4.4 Controller 层集成测试

### AdminControllerTest（MockMvc）

**集成范围**：MockMvc → Controller → Service → Mapper → MySQL（全栈）

| 用例 ID | 测试方法 | 验证 |
|---------|----------|------|
| API-001 | `POST /admin/login` 有效凭证 | 200 + `code=200` + tokenHead="Bearer " + token 非空 |
| API-002 | `POST /admin/login` 错误密码 | code ≠ 200 + message 包含"密码" |
| API-003 | `POST /admin/login` 空 body | code ≠ 200 |
| API-104 | `GET /admin/info` 带有效 token | 200 + username="admin" + menus 非空 + roles 非空 |
| API-105 | `GET /admin/info` 无 token | JSON 响应（被 Security 拦截） |
| API-106 | `GET /admin/info` 无效 token | JSON 响应（被 Security 拦截） |
| API-107 | `GET /brand/listAll` | 端点存在，返回 JSON |

**结果**：7/7 通过。其中 API-104 是完整认证流程：先 POST /admin/login 获取真实 token，再用该 token 访问受保护接口。

---

# 5、测试总结

## 5.1 执行统计

| 指标 | Phase 1 | Phase 2 新增 | 累计 |
|------|---------|-------------|------|
| 测试类 | 5 | 6 | 11 |
| 测试用例 | 63 | 24 | 87 |
| Mock 测试 | 11 (UmsAdminServiceImpl) | 9 (Filter + OrderService) | 20 |
| 集成测试 | 0 | 19 (DAO + Service + Controller) | 19 |
| 测试通过率 | 100% | 100% | 100% |

## 5.2 Phase 2 新增测试清单

```
mall-admin/src/test/java/com/macro/mall/
├── security/
│   └── JwtAuthenticationTokenFilterTest.java   # 6 tests, Mock 隔离 — JWT Filter
├── dao/
│   ├── UmsAdminRoleRelationDaoTest.java         # 4 tests, DAO→MySQL 集成
│   └── OmsOrderDaoTest.java                    # 4 tests, DAO→MySQL 集成
├── service/
│   └── UmsAdminServiceTest.java                # 4 tests, Service→DB 集成
├── controller/
│   └── AdminControllerTest.java                # 7 tests, MockMvc 全栈集成
└── src/test/resources/
    └── application-test.yml                    # 集成测试专用配置

mall-portal/src/test/java/com/macro/mall/portal/service/impl/
└── OmsPortalOrderServiceImplMockTest.java       # 3 tests, Mock 深度隔离
```

## 5.3 Mock vs 集成测试对比

| 维度 | Mock 隔离测试 | 集成测试 (@SpringBootTest) |
|------|-------------|--------------------------|
| **速度** | 毫秒级 (< 1s) | 秒级 (Spring 启动 9-12s) |
| **依赖** | 无（纯 Mockito） | 需要 MySQL/Redis 等中间件运行 |
| **覆盖范围** | 单一类的交互行为 | 多层之间的数据流和契约 |
| **适用场景** | 逻辑验证、异常路径、边界条件 | 数据持久化、事务、真实 JWT |
| **本阶段典型案例** | JwtAuthenticationTokenFilter（6 tests） | AdminController login→token→info（完整认证流） |

## 5.4 关键发现

1. **OncePerRequestFilter.doFilter() 是 final**：无法通过 Mockito 直接 mock，需反射调用 `doFilterInternal`。这是 Spring 5.x 的设计决策，在 Spring 6.x 中 `doFilterInternal` 改为 interface default 方法。

2. **@Value 注入字段在单元测试中不可用**：`JwtAuthenticationTokenFilter` 的 `tokenHeader` 和 `tokenHead` 需要反射手动设置，这是 Spring Boot 单元测试中的常见坑。

3. **复杂 Service 的 Mock 测试有上限**：`OmsPortalOrderServiceImpl`（9+ 依赖）的完整路径需要 mock 全部依赖，不如直接用 `@SpringBootTest` 集成测试。

4. **Spring Security 的 403 响应码是 200 + JSON body**：项目配置了自定义 `RestAccessDeniedHandler`，返回 HTTP 200 + JSON body 中含错误码（非标准 403 状态码）。

## 5.5 复现步骤

```bash
cd ~/MallTest/mall

# 运行所有单元测试 + 集成测试（需 Docker MySQL/Redis 运行中）
mvn test -DskipTests=false -pl mall-admin,mall-portal -am -DfailIfNoTests=false

# 仅运行 Mock 隔离测试（不需中间件）
mvn test -DskipTests=false -Dtest="JwtAuthenticationTokenFilterTest,OmsPortalOrderServiceImplMockTest"

# 仅运行 DAO 集成测试
mvn test -DskipTests=false -Dtest="UmsAdminRoleRelationDaoTest,OmsOrderDaoTest"

# 仅运行 Controller 集成测试（MockMvc）
mvn test -DskipTests=false -Dtest="AdminControllerTest"
```
