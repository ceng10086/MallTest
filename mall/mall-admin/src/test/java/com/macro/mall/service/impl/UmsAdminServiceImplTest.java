package com.macro.mall.service.impl;

import com.macro.mall.dao.UmsAdminRoleRelationDao;
import com.macro.mall.dto.UpdateAdminPasswordParam;
import com.macro.mall.mapper.UmsAdminLoginLogMapper;
import com.macro.mall.mapper.UmsAdminMapper;
import com.macro.mall.mapper.UmsAdminRoleRelationMapper;
import com.macro.mall.model.UmsAdmin;
import com.macro.mall.model.UmsAdminExample;
import com.macro.mall.model.UmsResource;
import com.macro.mall.security.util.JwtTokenUtil;
import com.macro.mall.service.UmsAdminCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UmsAdminServiceImpl 单元测试 — 用户认证与管理逻辑。
 *
 * 注意：UmsAdminServiceImpl.getCacheService() 内部调用
 * SpringUtil.getBean(UmsAdminCacheService.class)，单元测试中 Spring 上下文不可用，
 * 因此使用 @Spy + @InjectMocks 结合，mock 掉 getCacheService()。
 *
 * 采用的测试用例设计方法：
 *   白盒：路径覆盖（updatePassword 的 4 条路径）
 *   黑盒：等价类划分（注册成功/重复用户名）、判定表（updatePassword 输入组合）
 */
@ExtendWith(MockitoExtension.class)
public class UmsAdminServiceImplTest {

    @Mock private UmsAdminMapper adminMapper;
    @Mock private UmsAdminRoleRelationMapper adminRoleRelationMapper;
    @Mock private UmsAdminRoleRelationDao adminRoleRelationDao;
    @Mock private UmsAdminLoginLogMapper loginLogMapper;
    @Mock private JwtTokenUtil jwtTokenUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock(lenient = true) private UmsAdminCacheService cacheService;

    /**
     * 使用 Spy 包裹真实对象，以便 mock getCacheService() 方法。
     * (源码中 getCacheService() 调用 SpringUtil.getBean()，单元测试中不可用)
     */
    private UmsAdminServiceImpl adminService;

    @BeforeEach
    public void setUp() throws Exception {
        // 手动构造 Spy + 注入 mock 字段
        adminService = org.mockito.Mockito.spy(UmsAdminServiceImpl.class);
        // 通过反射将 mock 注入 spy 对象
        java.lang.reflect.Field[] fields = UmsAdminServiceImpl.class.getDeclaredFields();
        for (java.lang.reflect.Field field : fields) {
            field.setAccessible(true);
            if (field.getName().equals("adminMapper")) field.set(adminService, adminMapper);
            else if (field.getName().equals("adminRoleRelationMapper")) field.set(adminService, adminRoleRelationMapper);
            else if (field.getName().equals("adminRoleRelationDao")) field.set(adminService, adminRoleRelationDao);
            else if (field.getName().equals("loginLogMapper")) field.set(adminService, loginLogMapper);
            else if (field.getName().equals("jwtTokenUtil")) field.set(adminService, jwtTokenUtil);
            else if (field.getName().equals("passwordEncoder")) field.set(adminService, passwordEncoder);
        }
        // mock 掉 getCacheService()（lenient：并非所有测试都会调用它）
        lenient().doReturn(cacheService).when(adminService).getCacheService();
    }

    // ================================================================
    //  4.2.2.1  黑盒测试 — 等价类划分（注册）
    //  目标：划分"有效注册请求"和"重复用户名"两个等价类
    // ================================================================

    @Nested
    @DisplayName("黑盒：等价类划分 — 用户注册")
    class RegisterEquivalenceTests {

        @Test
        @DisplayName("有效等价类: 新用户名注册 → 成功返回 UmsAdmin")
        void testRegisterWithNewUsername() {
            // 模拟：数据库中无同名用户
            when(adminMapper.selectByExample(any(UmsAdminExample.class))).thenReturn(new ArrayList<>());
            when(passwordEncoder.encode(anyString())).thenReturn("$encoded$password");
            when(adminMapper.insert(any(UmsAdmin.class))).thenReturn(1);

            com.macro.mall.dto.UmsAdminParam param = new com.macro.mall.dto.UmsAdminParam();
            param.setUsername("newuser");
            param.setPassword("abc123");
            param.setNickName("新用户");

            UmsAdmin result = adminService.register(param);

            assertNotNull(result, "注册新用户应返回非 null");
            assertEquals("newuser", result.getUsername());
            verify(passwordEncoder).encode("abc123");
            verify(adminMapper).insert(any(UmsAdmin.class));
        }

        @Test
        @DisplayName("无效等价类: 重复用户名注册 → 返回 null")
        void testRegisterWithDuplicateUsername() {
            // 模拟：数据库中已有同名用户
            List<UmsAdmin> existingList = new ArrayList<>();
            existingList.add(new UmsAdmin());
            when(adminMapper.selectByExample(any(UmsAdminExample.class))).thenReturn(existingList);

            com.macro.mall.dto.UmsAdminParam param = new com.macro.mall.dto.UmsAdminParam();
            param.setUsername("admin"); // admin 已存在
            param.setPassword("abc123");

            UmsAdmin result = adminService.register(param);

            assertNull(result, "重复用户名注册应返回 null");
            // 密码编码和 insert 不应被调用
            verify(passwordEncoder, never()).encode(anyString());
            verify(adminMapper, never()).insert(any(UmsAdmin.class));
        }
    }

    // ================================================================
    //  4.2.1.3  白盒测试 — 路径覆盖（updatePassword）
    //  目标：覆盖 updatePassword 的全部 4 条返回路径
    //        路径1: 参数为空（username/oldPassword/newPassword 任一为空）→ -1
    //        路径2: 用户不存在 → -2
    //        路径3: 旧密码不匹配 → -3
    //        路径4: 校验通过 → 更新密码 → 返回 1
    // ================================================================

    @Nested
    @DisplayName("白盒：路径覆盖 — 密码更新 updatePassword")
    class PasswordUpdatePathTests {

        @Test
        @DisplayName("路径1: username 为空 → 返回 -1（参数校验失败）")
        void testUpdatePasswordMissingUsername() {
            UpdateAdminPasswordParam param = new UpdateAdminPasswordParam();
            param.setUsername("");  // 空
            param.setOldPassword("old");
            param.setNewPassword("new");

            int result = adminService.updatePassword(param);
            assertEquals(-1, result, "用户名/旧密码/新密码任一为空 → 返回 -1");
            // 不应查询数据库
            verify(adminMapper, never()).selectByExample(any());
        }

        @Test
        @DisplayName("路径2: 用户不存在 → 返回 -2")
        void testUpdatePasswordUserNotFound() {
            UpdateAdminPasswordParam param = new UpdateAdminPasswordParam();
            param.setUsername("nonexistent");
            param.setOldPassword("old");
            param.setNewPassword("new");

            when(adminMapper.selectByExample(any(UmsAdminExample.class))).thenReturn(new ArrayList<>());

            int result = adminService.updatePassword(param);
            assertEquals(-2, result, "用户不存在 → 返回 -2");
            verify(adminMapper).selectByExample(any(UmsAdminExample.class));
            // 密码验证和更新不应被调用
            verify(passwordEncoder, never()).matches(anyString(), anyString());
            verify(adminMapper, never()).updateByPrimaryKey(any());
        }

        @Test
        @DisplayName("路径3: 旧密码不匹配 → 返回 -3")
        void testUpdatePasswordWrongOldPassword() {
            UpdateAdminPasswordParam param = new UpdateAdminPasswordParam();
            param.setUsername("testuser");
            param.setOldPassword("wrong-old-password");
            param.setNewPassword("new-password");

            UmsAdmin existingAdmin = new UmsAdmin();
            existingAdmin.setId(1L);
            existingAdmin.setUsername("testuser");
            existingAdmin.setPassword("$encoded$realOldPassword");
            List<UmsAdmin> adminList = Arrays.asList(existingAdmin);

            when(adminMapper.selectByExample(any(UmsAdminExample.class))).thenReturn(adminList);
            when(passwordEncoder.matches("wrong-old-password", "$encoded$realOldPassword")).thenReturn(false);

            int result = adminService.updatePassword(param);
            assertEquals(-3, result, "旧密码不匹配 → 返回 -3");
            verify(passwordEncoder).matches("wrong-old-password", "$encoded$realOldPassword");
            verify(adminMapper, never()).updateByPrimaryKey(any());
        }

        @Test
        @DisplayName("路径4: 全部校验通过 → 密码更新成功 → 返回 1")
        void testUpdatePasswordSuccess() {
            UpdateAdminPasswordParam param = new UpdateAdminPasswordParam();
            param.setUsername("testuser");
            param.setOldPassword("correct-old");
            param.setNewPassword("new-password");

            UmsAdmin existingAdmin = new UmsAdmin();
            existingAdmin.setId(1L);
            existingAdmin.setUsername("testuser");
            existingAdmin.setPassword("$encoded$correctOld");
            List<UmsAdmin> adminList = Arrays.asList(existingAdmin);

            when(adminMapper.selectByExample(any(UmsAdminExample.class))).thenReturn(adminList);
            when(passwordEncoder.matches("correct-old", "$encoded$correctOld")).thenReturn(true);
            when(passwordEncoder.encode("new-password")).thenReturn("$encoded$newPassword");
            when(adminMapper.updateByPrimaryKey(any(UmsAdmin.class))).thenReturn(1);

            int result = adminService.updatePassword(param);
            assertEquals(1, result, "全部校验通过 → 返回 1");
            verify(passwordEncoder).encode("new-password");
            verify(adminMapper).updateByPrimaryKey(any(UmsAdmin.class));
        }
    }

    // ================================================================
    //  黑盒测试 — 判定表（updatePassword 输入组合）
    //  输入条件: username 是否为空 / 用户是否存在 / 旧密码是否正确
    //  输出: 返回码 -1 / -2 / -3 / 1
    // ================================================================

    @Nested
    @DisplayName("黑盒：判定表 — updatePassword 输入组合")
    class PasswordDecisionTableTests {

        @Test
        @DisplayName("判定规则R1: 空字段 → -1（不管其他条件）")
        void testDecisionRuleR1() {
            UpdateAdminPasswordParam param = new UpdateAdminPasswordParam();
            param.setUsername(null); // 空
            param.setOldPassword("pw");
            param.setNewPassword("pw");

            assertEquals(-1, adminService.updatePassword(param));
        }

        @Test
        @DisplayName("判定规则R2: 有效字段 + 用户不存在 → -2")
        void testDecisionRuleR2() {
            UpdateAdminPasswordParam param = new UpdateAdminPasswordParam();
            param.setUsername("nonexist");
            param.setOldPassword("pw");
            param.setNewPassword("pw");

            when(adminMapper.selectByExample(any())).thenReturn(new ArrayList<>());
            assertEquals(-2, adminService.updatePassword(param));
        }

        @Test
        @DisplayName("判定规则R3: 有效字段 + 用户存在 + 密码错误 → -3")
        void testDecisionRuleR3() {
            UpdateAdminPasswordParam param = new UpdateAdminPasswordParam();
            param.setUsername("user");
            param.setOldPassword("wrong");
            param.setNewPassword("new");

            UmsAdmin admin = new UmsAdmin();
            admin.setPassword("$encoded$real");
            when(adminMapper.selectByExample(any())).thenReturn(Arrays.asList(admin));
            when(passwordEncoder.matches("wrong", "$encoded$real")).thenReturn(false);

            assertEquals(-3, adminService.updatePassword(param));
        }

        @Test
        @DisplayName("判定规则R4: 有效字段 + 用户存在 + 密码正确 → 1")
        void testDecisionRuleR4() {
            UpdateAdminPasswordParam param = new UpdateAdminPasswordParam();
            param.setUsername("user");
            param.setOldPassword("correct");
            param.setNewPassword("new");

            UmsAdmin admin = new UmsAdmin();
            admin.setId(1L);
            admin.setPassword("$encoded$correct");
            when(adminMapper.selectByExample(any())).thenReturn(Arrays.asList(admin));
            when(passwordEncoder.matches("correct", "$encoded$correct")).thenReturn(true);
            when(passwordEncoder.encode("new")).thenReturn("$encoded$new");
            when(adminMapper.updateByPrimaryKey(any())).thenReturn(1);

            assertEquals(1, adminService.updatePassword(param));
        }
    }

    // ================================================================
    //  综合测试
    // ================================================================

    @Test
    @DisplayName("登录失败: 密码不匹配时应抛出异常，不生成 token")
    void testLoginWithWrongPassword() {
        // 模拟 loadUserByUsername 内部逻辑：getAdminByUsername + getResourceList
        UmsAdmin admin = new UmsAdmin();
        admin.setId(1L);
        admin.setUsername("testuser");
        admin.setPassword("$encoded$realPwd");
        admin.setStatus(1);
        List<UmsResource> resources = new ArrayList<>();

        // lenient: login 在密码不匹配时抛异常，不会执行后续的 SecurityContext 和 jwtTokenUtil.generateToken
        lenient().when(adminMapper.selectByExample(any(UmsAdminExample.class))).thenReturn(Arrays.asList(admin));
        lenient().when(adminRoleRelationDao.getResourceList(1L)).thenReturn(resources);
        when(passwordEncoder.matches("wrongPwd", "$encoded$realPwd")).thenReturn(false);

        // login 内部：loadUserByUsername → passwordEncoder.matches 返回 false → Asserts.fail 抛出异常
        assertThrows(Exception.class, () -> adminService.login("testuser", "wrongPwd"),
                "密码错误应触发 Asserts.fail 异常");
    }
}
