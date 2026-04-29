package com.macro.mall.service;

import com.macro.mall.dto.UmsAdminParam;
import com.macro.mall.model.UmsAdmin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Service 层集成测试 — 用户认证与注册。
 *
 * 集成范围：UmsAdminService → Mapper → MySQL + Redis (Docker)
 * 测试真实的数据访问和事务行为。
 *
 * 集成测试关注点：
 *   - 注册 → 持久化到 MySQL → 查询返回正确数据
 *   - 登录 → 密码校验 → JWT 生成（真实 token）
 *   - 缓存：getAdminByUsername 先从缓存取再查 DB
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UmsAdminServiceTest {

    @Autowired
    private UmsAdminService adminService;

    /* 种子数据中已存在的管理员 */
    private static final String EXISTING_USER = "admin";
    private static final String EXISTING_PASSWORD = "macro123";

    @Test
    @Order(1)
    @DisplayName("集成测试: 查询已存在的管理员 — 验证种子数据")
    void testGetExistingAdmin() {
        UmsAdmin admin = adminService.getAdminByUsername(EXISTING_USER);
        assertNotNull(admin, "种子数据中的 'admin' 用户应存在");
        assertEquals(EXISTING_USER, admin.getUsername());
        assertNotNull(admin.getPassword(), "密码不应为空（BCrypt 编码）");
    }

    @Test
    @Order(2)
    @DisplayName("集成测试: 登录已存在的管理员 — 验证真实 JWT 生成")
    void testLoginWithValidCredentials() {
        String token = adminService.login(EXISTING_USER, EXISTING_PASSWORD);
        assertNotNull(token, "有效凭证登录应返回 token");
        assertTrue(token.length() > 20, "JWT token 应有足够长度");
        // token 应包含三段（header.payload.signature）
        assertTrue(token.contains("."), "JWT token 应包含 '.' 分隔符");
    }

    @Test
    @Order(3)
    @DisplayName("集成测试: 登录失败 — 错误密码")
    void testLoginWithWrongPassword() {
        assertThrows(Exception.class,
                () -> adminService.login(EXISTING_USER, "wrong_password_12345"),
                "错误密码应抛出异常");
    }

    @Test
    @Order(4)
    @DisplayName("集成测试: 注册新用户 → 查询验证 → 持久化成功")
    @Transactional
    void testRegisterAndVerifyPersistence() {
        UmsAdminParam param = new UmsAdminParam();
        param.setUsername("integration_test_user_" + System.currentTimeMillis());
        param.setPassword("testPassword123");
        param.setNickName("集成测试用户");

        UmsAdmin registered = adminService.register(param);
        assertNotNull(registered, "注册应返回非 null 的 UmsAdmin");
        assertNotNull(registered.getId(), "注册后应有自增 ID");

        // 验证持久化：直接从 DB 查询
        UmsAdmin fromDb = adminService.getAdminByUsername(param.getUsername());
        assertNotNull(fromDb, "注册后应能从数据库查询到");
        assertEquals(param.getUsername(), fromDb.getUsername());
        // 密码应为 BCrypt 编码（不以明文存储）
        assertNotEquals(param.getPassword(), fromDb.getPassword(),
                "密码应被 BCrypt 加密存储");
    }
}
