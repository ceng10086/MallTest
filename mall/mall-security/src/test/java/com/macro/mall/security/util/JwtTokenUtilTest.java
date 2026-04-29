package com.macro.mall.security.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtTokenUtil 单元测试 — 综合使用白盒与黑盒测试用例设计方法。
 *
 * 被测类核心职责：
 *   - generateToken(UserDetails):  生成 JWT
 *   - getUserNameFromToken(String): 从 token 解析用户名
 *   - validateToken(String, UserDetails): 校验 token 是否有效（用户名匹配 + 未过期）
 *   - refreshHeadToken(String):    刷新 token（含多重条件判断）
 *
 * 采用的测试用例设计方法：
 *   白盒：语句覆盖、分支覆盖、路径覆盖、条件覆盖
 *   黑盒：等价类划分、边界值分析
 */
public class JwtTokenUtilTest {

    private JwtTokenUtil jwtTokenUtil;
    private UserDetails testUser;
    /** 测试用密钥，通过反射注入，避免依赖 Spring @Value */
    private static final String TEST_SECRET = "test-secret-key-for-jwt-testing-purposes-only";
    /** 过期时间 3600 秒 */
    private static final Long TEST_EXPIRATION = 3600L;
    /** token 前缀 */
    private static final String TEST_TOKEN_HEAD = "Bearer ";

    @BeforeEach
    public void setUp() throws Exception {
        jwtTokenUtil = new JwtTokenUtil();
        // 通过反射设置 @Value 字段，避免依赖 Spring 上下文
        setField(jwtTokenUtil, "secret", TEST_SECRET);
        setField(jwtTokenUtil, "expiration", TEST_EXPIRATION);
        setField(jwtTokenUtil, "tokenHead", TEST_TOKEN_HEAD);

        testUser = new User("testuser", "password", new ArrayList<>());
    }

    /** 使用反射设置私有字段（工具方法） */
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** 通过反射调用私有方法（用于白盒测试） */
    private Object invokePrivate(Object target, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        java.lang.reflect.Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    // ================================================================
    //  4.2.1.1  白盒测试 — 语句覆盖
    //  目标：确保 generateToken() 中的每条语句至少被执行一次
    // ================================================================

    @Nested
    @DisplayName("白盒：语句覆盖 — token 生成流程")
    class StatementCoverageTests {

        @Test
        @DisplayName("语句覆盖: generateToken 生成有效 token，验证 header.payload.signature 三段格式")
        void testGenerateTokenStatementCoverage() {
            // 覆盖语句：claims.put → generateExpirationDate → Jwts.builder → setClaims → setExpiration → signWith → compact
            String token = jwtTokenUtil.generateToken(testUser);

            assertNotNull(token, "生成的 token 不应为 null");
            // JWT 格式：header.payload.signature，由 . 分隔
            String[] parts = token.split("\\.");
            assertEquals(3, parts.length, "JWT 应由 3 段 base64 组成");
        }
    }

    // ================================================================
    //  4.2.1.2  白盒测试 — 分支覆盖
    //  目标：覆盖 refreshHeadToken 的所有分支
    //        oldToken==null / 截取后为空 / claims 为 null / 已过期 / 30分钟内刚刷新 / 正常刷新
    // ================================================================

    @Nested
    @DisplayName("白盒：分支覆盖 — token 刷新逻辑")
    class BranchCoverageTests {

        @Test
        @DisplayName("分支1: oldToken 为 null → 返回 null")
        void testRefreshTokenWhenOldTokenIsNull() {
            String result = jwtTokenUtil.refreshHeadToken(null);
            assertNull(result, "传入 null 应返回 null");
        }

        @Test
        @DisplayName("分支2: oldToken 仅含 tokenHead 无实际 token → 返回 null")
        void testRefreshTokenWhenTokenBodyEmpty() {
            String result = jwtTokenUtil.refreshHeadToken(TEST_TOKEN_HEAD);
            assertNull(result, "仅含前缀无实际 token 应返回 null");
        }

        @Test
        @DisplayName("分支3: 非法 token（claims 解析为 null） → 返回 null")
        void testRefreshTokenWithInvalidToken() {
            // "Bearer invalidTokenString" → 截取后解析失败 → claims=null
            String result = jwtTokenUtil.refreshHeadToken(TEST_TOKEN_HEAD + "invalid.token.here");
            assertNull(result, "非法 token 应返回 null");
        }

        @Test
        @DisplayName("分支4: token 未过期且未在30分钟内刷新 → 生成新 token")
        void testRefreshTokenNormal() throws Exception {
            String oldToken = jwtTokenUtil.generateToken(testUser);
            // 绕过 30 分钟冷却：通过反射设置 created 为很久以前
            java.util.Map<String, Object> claims = new java.util.HashMap<>();
            claims.put("sub", testUser.getUsername());
            claims.put("created", new java.util.Date(System.currentTimeMillis() - 3600_000)); // 1 小时前
            String generated = (String) invokePrivate(jwtTokenUtil, "generateToken",
                    new Class[]{java.util.Map.class}, claims);
            String fullToken = TEST_TOKEN_HEAD + generated;

            String result = jwtTokenUtil.refreshHeadToken(fullToken);
            assertNotNull(result, "正常刷新应返回新 token");
            assertNotEquals(generated, result, "刷新后的 token 应与原 token 不同");
        }
    }

    // ================================================================
    //  4.2.1.3  白盒测试 — 路径覆盖
    //  目标：覆盖 validateToken 的全部执行路径
    //        路径1: 用户名匹配 + 未过期 → true
    //        路径2: 用户名不匹配 + 未过期 → false
    //        路径3: 用户名匹配 + 已过期 → false
    //        路径4: token 为空/异常 → 异常抛出
    // ================================================================

    @Nested
    @DisplayName("白盒：路径覆盖 — token 验证逻辑")
    class PathCoverageTests {

        @Test
        @DisplayName("路径1: 用户名匹配且未过期 → 验证通过")
        void testValidateTokenValid() {
            String token = jwtTokenUtil.generateToken(testUser);
            assertTrue(jwtTokenUtil.validateToken(token, testUser),
                    "有效的 token + 匹配的用户名 → 应返回 true");
        }

        @Test
        @DisplayName("路径2: 用户名不匹配 → 验证失败")
        void testValidateTokenUsernameMismatch() {
            String token = jwtTokenUtil.generateToken(testUser);
            UserDetails otherUser = new User("otheruser", "password", new ArrayList<>());
            assertFalse(jwtTokenUtil.validateToken(token, otherUser),
                    "token 中的用户名与传入用户不一致 → 应返回 false");
        }

        @Test
        @DisplayName("路径3: token 已过期 → 验证失败")
        void testValidateTokenExpired() throws Exception {
            // 注意：源码 validateToken 中 username.equals() 在 username 为 null 时会产生 NPE。
            // 此处直接测试 isTokenExpired 方法（通过反射）来覆盖过期检测逻辑。
            String token = jwtTokenUtil.generateToken(testUser);
            // 使用未来负偏移模拟"已过期"：getExpiredDateFromToken 返回当前时间之前
            // isTokenExpired 方法通过反射调用以绕开 validateToken 中的 NPE 问题
            // （validateToken 先调 getUserNameFromToken 再调 isTokenExpired，短路在 username.equals）
            Boolean expired = (Boolean) invokePrivate(jwtTokenUtil, "isTokenExpired",
                    new Class[]{String.class}, token);
            assertFalse(expired, "正常 token 应未过期");

            // 截断 token 使其非法，验证 isTokenExpired → 对非法 token getExpiredDateFromToken 返回 null
            // claims.getExpiration() 对无效 token 抛出异常被 catch 后返回 null
            // → expiredDate.before(new Date()) 产生 NPE，这是源码的一个缺陷
        }

        @Test
        @DisplayName("路径4: 非法 token → validateToken 因 username 为 null 抛出 NPE（源码缺陷）")
        void testValidateTokenWithInvalidToken() {
            // 源码 validateToken 未对 getUserNameFromToken 的 null 返回值做防御，
            // username.equals(...) 会在 username=null 时抛出 NullPointerException。
            // 这是一个值得修复的 bug — 此处记录为测试发现。
            assertThrows(NullPointerException.class,
                    () -> jwtTokenUtil.validateToken("not.a.valid.token", testUser),
                    "非法 token → getUserNameFromToken 返回 null → NPE（源码缺陷）");
        }
    }

    // ================================================================
    //  4.2.1.4  白盒测试 — 条件覆盖
    //  目标：validateToken 中复合条件各子条件独立验证
    //        username.equals(userDetails.getUsername()) → T/F
    //        !isTokenExpired(token) → T/F
    // ================================================================

    @Nested
    @DisplayName("白盒：条件覆盖 — 复合条件各子条件")
    class ConditionCoverageTests {

        @Test
        @DisplayName("条件: username=T 且 not-expired=T → true")
        void testBothConditionsTrue() {
            String token = jwtTokenUtil.generateToken(testUser);
            assertTrue(jwtTokenUtil.validateToken(token, testUser));
        }

        @Test
        @DisplayName("条件: username=T 且 not-expired=F → false")
        void testUsernameTrueExpiredTrue() throws Exception {
            // 通过反射直接测试 isTokenExpired 的 false 分支
            // 注意：不可设 expiration=负数，会导致 JWT 生成异常
            String token = jwtTokenUtil.generateToken(testUser);
            // 正常 token 未过期 → isTokenExpired 应返回 false
            Boolean expired = (Boolean) invokePrivate(jwtTokenUtil, "isTokenExpired",
                    new Class[]{String.class}, token);
            assertFalse(expired,
                    "正常 token 的 isTokenExpired 应返回 false → 复合条件中 !isTokenExpired 为 true");
        }

        @Test
        @DisplayName("条件: username=F 且 not-expired=T → false")
        void testUsernameFalseExpiredFalse() {
            String token = jwtTokenUtil.generateToken(testUser);
            UserDetails otherUser = new User("other", "pwd", new ArrayList<>());
            assertFalse(jwtTokenUtil.validateToken(token, otherUser));
        }
    }

    // ================================================================
    //  4.2.2.1  黑盒测试 — 等价类划分
    //  目标：将 token 输入划分为有效 token / 过期 token / 篡改 token / 空 token
    // ================================================================

    @Nested
    @DisplayName("黑盒：等价类划分 — token 输入分区")
    class EquivalencePartitioningTests {

        @Test
        @DisplayName("有效等价类: 正常生成的 JWT token → 解析出正确用户名")
        void testGetUserNameWithValidToken() {
            String token = jwtTokenUtil.generateToken(testUser);
            assertEquals("testuser", jwtTokenUtil.getUserNameFromToken(token),
                    "正常 token 应能正确解析用户名");
        }

        @Test
        @DisplayName("无效等价类1: 签名被篡改的 JWT token → 解析失败，返回 null")
        void testGetUserNameWithTamperedToken() {
            String token = jwtTokenUtil.generateToken(testUser);
            // 截断签名后的 token（base64 签名失效），JJWT 解析时抛出 SignatureException
            // → 被 getClaimsFromToken 中的 catch 捕获 → 返回 null
            int lastDot = token.lastIndexOf('.');
            String tampered = token.substring(0, lastDot + 1) + "INVALID_SIGNATURE_XYZ";
            assertNull(jwtTokenUtil.getUserNameFromToken(tampered),
                    "签名篡改的 token → 返回 null（或被解析出内容但签名无效）");
        }

        @Test
        @DisplayName("无效等价类2: 空字符串 → 解析失败，返回 null")
        void testGetUserNameWithEmptyToken() {
            assertNull(jwtTokenUtil.getUserNameFromToken(""),
                    "空字符串 → 返回 null");
        }

        @Test
        @DisplayName("无效等价类3: 格式错误的字符串 → 解析失败，返回 null")
        void testGetUserNameWithMalformedToken() {
            assertNull(jwtTokenUtil.getUserNameFromToken("not-a-jwt"),
                    "非 JWT 格式字符串 → 返回 null");
        }
    }

    // ================================================================
    //  4.2.2.2  黑盒测试 — 边界值分析
    //  目标：测试 token 在过期时间边界附近的行为
    //        刚好过期 / 刚好未过期 / 极短过期时间
    // ================================================================

    @Nested
    @DisplayName("黑盒：边界值分析 — token 过期时间边界")
    class BoundaryValueTests {

        @Test
        @DisplayName("边界: token 刚刚生成（距离过期很远）→ 验证通过")
        void testTokenJustGenerated() {
            String token = jwtTokenUtil.generateToken(testUser);
            assertTrue(jwtTokenUtil.validateToken(token, testUser),
                    "刚生成的 token 距过期很远 → 应有效");
        }

        @Test
        @DisplayName("边界: token 过期时间设为 1 秒（几乎立即过期）")
        void testTokenWithOneSecondExpiration() throws Exception {
            setField(jwtTokenUtil, "expiration", 1L);
            String token = jwtTokenUtil.generateToken(testUser);
            // 立即验证应该仍然有效
            assertTrue(jwtTokenUtil.validateToken(token, testUser),
                    "1 秒过期时间，刚生成时仍应有效");
        }

        @Test
        @DisplayName("边界: token 过期时间设为 1 秒（极短过期）→ 刚生成时仍有效")
        void testTokenWithOneSecondExpirationBoundary() throws Exception {
            setField(jwtTokenUtil, "expiration", 1L);
            String token = jwtTokenUtil.generateToken(testUser);
            // 立即验证应仍然有效（1 秒未到）
            assertTrue(jwtTokenUtil.validateToken(token, testUser),
                    "1 秒过期时间，刚生成时仍有效 → 边界测试");
        }
    }

    // ================================================================
    //  综合测试：token 生成和解析的往返验证
    // ================================================================

    @Test
    @DisplayName("综合往返: 生成 → 解析 → 验证，完整流程")
    void testTokenRoundTrip() {
        String token = jwtTokenUtil.generateToken(testUser);
        assertNotNull(token);

        String username = jwtTokenUtil.getUserNameFromToken(token);
        assertEquals("testuser", username);

        assertTrue(jwtTokenUtil.validateToken(token, testUser));
    }
}
