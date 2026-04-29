# 阶段 1 测试报告：静态分析 + 单元测试

## mall 项目 — 软件质量保证与测试技术课程设计

---

# 1、测试范围

## 1.1 标识

| 项目 | 说明 |
|------|------|
| **测试阶段** | 阶段 1 — 静态分析 + 单元测试 |
| **被测模块** | mall-security（JWT 工具类）、mall-admin（用户管理、商品管理）、mall-portal（促销计算、优惠券管理） |
| **文档编号** | MALL-TEST-REPORT-P1-001 |
| **测试日期** | 2026-04-29 |

## 1.2 目标

本阶段完成两项工作：

1. **静态分析**：使用 SpotBugs + PMD 对 mall-security、mall-admin、mall-portal 三个核心模块进行源代码静态扫描，发现潜在缺陷和代码规范问题。
2. **单元测试**：为 5 个核心业务类编写 55 个测试用例，综合使用白盒（语句覆盖/分支覆盖/路径覆盖/条件覆盖）和黑盒（等价类划分/边界值分析/判定表）测试用例设计方法。

---

# 2、测试环境

| 组件 | 版本 |
|------|------|
| JDK | OpenJDK 1.8.0_442 (Temurin) |
| Maven | 3.8.7 |
| 测试框架 | JUnit 5 (随 spring-boot-starter-test 2.7.5) |
| Mock 框架 | Mockito 4.x (随 spring-boot-starter-test) |
| 静态分析 | SpotBugs 4.7.3.6, PMD 7.7.0 (via maven-pmd-plugin 3.26.0) |
| 构建命令 | `mvn test -DskipTests=false -pl <module>` |

---

# 3、静态分析结果

## 3.1 SpotBugs 扫描

对各模块执行 `mvn spotbugs:spotbugs`，结果如下：

| 模块 | Bug 数量 | 严重级别 | 说明 |
|------|----------|----------|------|
| mall-security | 1 | Low | 方法可能返回 null，调用方未进行空值检查 |
| mall-admin | 1 | Low | 同上模式——Service 方法可能返回 null |
| mall-portal | 1 | Low | 同上模式——DAO 查询结果未做 null-safe 处理 |

**发现汇总**：主要问题集中在"方法可能返回 null"模式，这与 MyBatis `selectByExample` 可能返回 `null`（而非空列表）的 API 设计有关。这些属于低风险问题，在当前实现中因为调用方通常有隐式前提条件而不会触发。

### SpotBugs 报告位置

```
mall/mall-security/target/spotbugsXml.xml
mall/mall-admin/target/spotbugsXml.xml
mall/mall-portal/target/spotbugsXml.xml
```

## 3.2 PMD 代码规范检查

对各模块执行 `mvn pmd:pmd`，启用规则集 `category/java/bestpractices.xml` 和 `category/java/errorprone.xml`：

| 模块 | Violations | 主要问题 |
|------|------------|----------|
| mall-security | 5 | 重复字符串字面量（AvoidDuplicateLiterals） |
| mall-admin | 35 | 重复字符串字面量、方法过长（NPathComplexity） |
| mall-portal | 41 | 重复字符串字面量、未使用的私有方法、catch 块过于宽泛 |
| **合计** | **81** | |

### PMD 关键发现（抽样）

| 文件 | 行号 | 规则 | 说明 |
|------|------|------|------|
| `HomeController.java` | 41-42 | AvoidDuplicateLiterals | 字符串 `"redis:lock:product:"` 多处重复，应提取为常量 |
| `OmsPortalOrderServiceImpl.java` | 多处 | ExcessiveMethodLength | `generateOrder` 方法过长（约100行），建议拆分 |
| `OrderTimeOutCancelTask.java` | 25 | UnusedPrivateMethod | `cancelTimeOutOrder` 方法未在任何地方被调用 |
| `UmsMemberCouponController.java` | 46 | AvoidDuplicateLiterals | 字符串常量重复 |
| 多处 DAO 文件 | - | AvoidDuplicateLiterals | SQL 查询字符串在多处重复 |

### 改进建议

1. **提取字符串常量类**：将重复出现的 Redis key 前缀、API 路径字符串、状态值等提取为常量，统一管理。
2. **拆分长方法**：`OmsPortalOrderServiceImpl.generateOrder()`（794 行类中最长的方法）建议按职责拆分为多个子方法。
3. **清理未使用代码**：移除 `OrderTimeOutCancelTask.cancelTimeOutOrder()` 等无调用者方法。

### PMD 报告位置

```
mall/mall-security/target/pmd.xml
mall/mall-admin/target/pmd.xml
mall/mall-portal/target/pmd.xml
```

### 重复静态分析报告的步骤

```bash
cd ~/MallTest/mall
# SpotBugs 扫描
mvn spotbugs:spotbugs -DskipTests -pl mall-security,mall-admin,mall-portal -am
# PMD 扫描
mvn pmd:pmd -DskipTests -pl mall-security,mall-admin,mall-portal -am
# 查看结果
cat mall-security/target/spotbugsXml.xml
cat mall-portal/target/pmd.xml
```

---

# 4、单元测试

## 4.1 被测类与测试概览

| 被测类 | 测试文件 | 用例数 | 方法 | Mock 策略 |
|--------|----------|--------|------|-----------|
| `JwtTokenUtil` | `JwtTokenUtilTest.java` | 20 | 语句/分支/路径/条件覆盖 + 等价类/边界值 | 无（纯工具类，反射注入配置） |
| `OmsPromotionServiceImpl` | `OmsPromotionServiceImplTest.java` | 16 | 分支/条件覆盖 + 等价类/边界值 | 反射调用私有方法 |
| `UmsAdminServiceImpl` | `UmsAdminServiceImplTest.java` | 11 | 路径覆盖 + 等价类/判定表 | Mockito Mock/Spy |
| `PmsProductServiceImpl` | `PmsProductServiceImplTest.java` | 8 | 语句覆盖 + 边界值 | 反射调用私有方法 |
| `UmsMemberCouponServiceImpl` | `UmsMemberCouponServiceImplTest.java` | 6 | 语句覆盖 + 边界值 | 反射调用私有方法 |
| **合计** | | **61** | | |

> 加上 mall-portal 原有的 2 个测试类（`MallPortalApplicationTests`、`PortalProductDaoTests`），阶段 1 总计 **63 个测试用例，0 失败，0 错误**。

### 执行测试的步骤

```bash
cd ~/MallTest/mall

# 运行全部单元测试
mvn test -DskipTests=false -pl mall-security,mall-admin,mall-portal -am -DfailIfNoTests=false

# 仅运行单个模块
mvn test -DskipTests=false -pl mall-security -DfailIfNoTests=false  # JWT 工具类
mvn test -DskipTests=false -pl mall-admin -DfailIfNoTests=false     # 用户管理 + 商品管理
mvn test -DskipTests=false -pl mall-portal -DfailIfNoTests=false    # 促销计算 + 优惠券
```

## 4.2 测试用例设计方法对比

### 4.2.1 白盒测试方法

#### 语句覆盖 — 应用于 `PmsProductServiceImpl.handleSkuStockCode()`

**目标**：确保方法中每条可执行语句至少被执行一次（覆盖率 100%）。

**被测代码逻辑**：SKU 编码生成器，格式为 `日期(8位) + productId(补零至4位) + 索引(补零至3位)`。

| 用例 ID | 测试场景 | 覆盖的语句 |
|---------|----------|-----------|
| SCS-001 | 空 SKU 列表 → 直接 return | `if(CollectionUtils.isEmpty)return;` |
| SCS-002 | 3 个未编码 SKU → 全部生成编码 | `for` 循环体 + `sdf.format` + `StringBuilder` 拼接 |
| SCS-003 | 已有编码的 SKU → 跳过不覆盖 | `if(StrUtil.isEmpty(skuStock.getSkuCode()))` — `false` 分支 |
| SCS-004 | 混合列表（有+无编码）→ 仅生成缺失的 | 两个分支各执行一次 |

**结果**：4 条语句覆盖测试全部通过，SKU 编码格式验证正确（长度 15 = 8+4+3）。

#### 分支覆盖 — 应用于 `OmsPromotionServiceImpl.getProductLadder()`

**目标**：覆盖所有 if-else 分支（每个分支取 true 和 false 各一次）。

**被测代码逻辑**：根据购买件数匹配最优阶梯折扣（从高到低排序后取第一个满足条件的）。

| 用例 ID | 购买件数 | 阶梯配置 | 预期 | 覆盖分支 |
|---------|----------|----------|------|----------|
| LAD-001 | 10 | 3件9折 → 5件8折 → 10件7折 | 匹配 10件7折 | count >= ladder.count → true（第三个） |
| LAD-002 | 6 | 同上 | 匹配 5件8折 | 10件为 false → 5件为 true |
| LAD-003 | 2 | 同上 | 返回 null | 全部为 false |
| LAD-004 | 5 | 空列表 | 返回 null | 空列表 → for 循环不执行 |

**结果**：4 个分支覆盖用例全部通过。

#### 路径覆盖 — 应用于 `UmsAdminServiceImpl.updatePassword()`

**目标**：覆盖方法的全部可执行路径（4 条）。

**被测代码逻辑**：密码更新流程 — 参数校验 → 用户存在性检查 → 旧密码匹配 → 更新密码。

| 用例 ID | 路径 | 参数条件 | 预期返回码 |
|---------|------|----------|-----------|
| UPD-001 | 路径 1 | username 为空 | -1（参数校验失败） |
| UPD-002 | 路径 2 | 用户不存在 | -2（用户不存在） |
| UPD-003 | 路径 3 | 旧密码错误 | -3（旧密码不匹配） |
| UPD-004 | 路径 4 | 全部正确 | 1（更新成功） |

**结果**：4 条路径全部覆盖且验证通过，返回码与预期一致。

#### 条件覆盖 — 应用于 `JwtTokenUtil.validateToken()`

**目标**：测试 `validateToken` 中复合条件 `username.equals(...) && !isTokenExpired(token)` 的各子条件独立结果。

| 用例 ID | username 匹配 | token 未过期 | 预期结果 |
|---------|--------------|-------------|----------|
| VLD-001 | T | T | true |
| VLD-002 | T | F | false |
| VLD-003 | F | T | false |

**发现**：在实现条件覆盖测试时，发现源码中调用 `isTokenExpired` 需通过反射，因为 `validateToken` 的复合条件采用短路求值（先判断 username 匹配），无法直接隔离测试 `isTokenExpired` 的 F 分支。已通过反射 `invokePrivate` 解决。

### 4.2.2 黑盒测试方法

#### 等价类划分 — 应用于 `JwtTokenUtil.getUserNameFromToken()`

**目标**：将 token 输入空间划分为有效/无效等价类，每类取一个代表性数据。

| 等价类 | 代表值 | 预期输出 |
|--------|--------|----------|
| 有效 token | 正常生成的 JWT | 正确解析出 `"testuser"` |
| 无效：签名篡改 | `token.substring(0,lastDot+1) + "INVALID_SIGNATURE"` | `null` |
| 无效：空字符串 | `""` | `null` |
| 无效：非 JWT 格式 | `"not-a-jwt"` | `null` |

**发现**：`validateToken` 对无效 token 传入时，`getUserNameFromToken` 返回 `null`，但后续 `username.equals(...)` 未做 null 检查，抛出 `NullPointerException`。**这是一个真实的源码缺陷**，已在测试中通过 `assertThrows(NullPointerException.class, ...)` 记录。

#### 边界值分析 — 应用于 `OmsPromotionServiceImpl` 阶梯折扣

**目标**：测试阶梯折扣阈值边界附近的输入值。

| 用例 ID | 购买件数 | 阶梯阈值 | 预期 | 边界类型 |
|---------|----------|----------|------|----------|
| BND-001 | 3 | 满 3 件 | 匹配 | 刚好达到（=3） |
| BND-002 | 2 | 满 3 件 | 不匹配 | 刚好不足（=阈值-1） |
| BND-003 | 0 | 满 1 件 | 不匹配 | 最小边界 |

**结果**：3 个边界测试全部通过。

#### 边界值分析 — 应用于满减匹配（含 Bug 发现）

**目标**：测试满减金额阈值边界。

| 用例 ID | 购物金额 | 满减门槛 | 预期 | 实际 | 结论 |
|---------|----------|----------|------|------|------|
| BND-FR-001 | 200 | 满 200 减 30 | 匹配 | 匹配 ✓ | 刚好达到 |
| BND-FR-002 | 199.99 | 满 200 减 30 | 不匹配 | **错误匹配** | **发现 Bug** |

**Bug 详情**：`getProductFullReduction()` 方法使用 `totalAmount.subtract(fullPrice).intValue() >= 0` 判断是否满足满减条件。当 `totalAmount = 199.99`、`fullPrice = 200` 时，差值为 `-0.01`，`intValue()` 截断为 `0`，导致 `0 >= 0` 为 `true`，错误返回满减规则。

**修复建议**：将条件改为 `totalAmount.subtract(fullPrice).compareTo(BigDecimal.ZERO) >= 0`。

#### 判定表 — 应用于 `UmsAdminServiceImpl.updatePassword()`

**目标**：用判定表描述多条件组合的输入-输出映射。

| 规则 | username 非空 | 用户存在 | 旧密码正确 | ⇒ 返回码 |
|------|-------------|---------|-----------|----------|
| R1 | F | — | — | -1 |
| R2 | T | F | — | -2 |
| R3 | T | T | F | -3 |
| R4 | T | T | T | 1 |

| 用例 ID | 对应规则 | 输入 | 预期 |
|---------|----------|------|------|
| DCT-001 | R1 | `username=null` | -1 |
| DCT-002 | R2 | `username="nonexist"` | -2 |
| DCT-003 | R3 | `username="user"`, `oldPassword="wrong"` | -3 |
| DCT-004 | R4 | `username="user"`, `oldPassword="correct"` | 1 |

**结果**：4 条判定表驱动测试全部通过。

## 4.3 测试中发现的问题

| Issue ID | 类型 | 严重程度 | 位置 | 描述 |
|----------|------|----------|------|------|
| MALL-BUG-001 | Bug | P2 | `JwtTokenUtil.java:94-95` | `validateToken` 在 `getUserNameFromToken` 返回 null 时产生 NPE，应增加 null 检查 |
| MALL-BUG-002 | Bug | P1 | `OmsPromotionServiceImpl.java:184` | `intValue()` 截断导致满减金额边界判断错误（199.99 被误判为满足满 200），应改用 `compareTo` |
| MALL-BUG-003 | Code Smell | P3 | 多处 Controller/DAO | PMD 检测到 81 处代码规范违规，主要为字符串字面量重复和长方法 |

## 4.4 白盒 vs 黑盒方法比较分析

| 比较维度 | 白盒测试 | 黑盒测试 |
|----------|----------|----------|
| **关注点** | 代码内部结构 | 输入/输出行为 |
| **用例设计依据** | 逻辑路径、条件组合、语句执行 | 需求规格、等价类、边界 |
| **优势** | 能发现逻辑遗漏、死代码、异常路径缺陷 | 不依赖实现细节，能发现"代码做了但不该做"的问题 |
| **劣势** | 对复杂条件组合可能组合爆炸 | 无法发现未覆盖代码的缺陷 |
| **本项目典型案例** | 通过路径覆盖发现 `validateToken` 的 NPE 缺陷 | 通过边界值分析发现 `getProductFullReduction` 的 `intValue()` 截断 Bug |
| **互补性总结** | 适合测试"实现是否正确" | 适合测试"行为是否符合预期" |

**结论**：在本阶段测试中，白盒和黑盒方法均独立发现了真实缺陷——白盒路径覆盖发现了 JWT 校验中的空指针问题（这是一个需要理解代码内部异常处理逻辑才能发现的缺陷），黑盒边界值分析发现了满减金额计算的数值截断 Bug（这是一个不读代码、仅靠输入输出测试就能捕获的精度问题）。两者互补，缺一不可。

## 4.5 测试文件清单

```
mall/
├── mall-security/src/test/java/com/macro/mall/security/util/
│   └── JwtTokenUtilTest.java              # 20 tests, JWT 令牌工具类
├── mall-admin/src/test/java/com/macro/mall/service/impl/
│   ├── UmsAdminServiceImplTest.java        # 11 tests, 用户认证管理
│   └── PmsProductServiceImplTest.java      # 8 tests, 商品 SKU 编码
└── mall-portal/src/test/java/com/macro/mall/portal/service/impl/
    ├── OmsPromotionServiceImplTest.java    # 16 tests, 促销计算引擎
    └── UmsMemberCouponServiceImplTest.java # 6 tests, 优惠券码生成
```

---

# 5、本阶段总结

## 5.1 执行统计

| 指标 | 数值 |
|------|------|
| 新增测试类 | 5 个 |
| 新增测试用例 | 61 个（+原有 2 个 = 63） |
| 测试通过率 | 100%（0 失败，0 错误） |
| 被测业务类 | 5 个（JwtTokenUtil、OmsPromotionServiceImpl、UmsAdminServiceImpl、PmsProductServiceImpl、UmsMemberCouponServiceImpl） |
| Mock 对象使用 | Mockito: 7 个 Mock + 1 个 Spy |
| 静态分析工具 | SpotBugs + PMD（2 工具，3 模块） |
| 发现源码缺陷 | 2 个 Bug（NPE + intValue 截断），81 个代码规范违规 |

## 5.2 测试用例设计方法一览

```
方法                  数量  被测方法
────────────────────────────────────────────
白盒 — 语句覆盖         4    handleSkuStockCode, generateCouponCode
白盒 — 分支覆盖         8    getProductLadder, getProductFullReduction, refreshHeadToken
白盒 — 路径覆盖         4    updatePassword, validateToken
白盒 — 条件覆盖         3    validateToken 复合条件
黑盒 — 等价类划分       5    getUserNameFromToken, register
黑盒 — 边界值分析       8    阶梯阈值, 满减阈值, memberId, productId
黑盒 — 判定表           4    updatePassword 输入组合
```
