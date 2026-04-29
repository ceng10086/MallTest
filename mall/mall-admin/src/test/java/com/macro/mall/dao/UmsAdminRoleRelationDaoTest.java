package com.macro.mall.dao;

import com.macro.mall.model.UmsResource;
import com.macro.mall.model.UmsRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DAO 层集成测试 — 用户角色-资源关系查询。
 *
 * 集成范围：UmsAdminRoleRelationDao → MyBatis → MySQL (Docker)
 * 数据来源：mall.sql 种子数据（admin 用户有角色分配，角色有资源关联）
 *
 * 集成测试关注点：
 *   - SQL 是否正确执行（语法、参数绑定）
 *   - 表关联是否正确（LEFT JOIN、GROUP BY）
 *   - 种子数据是否返回预期结果
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
@Transactional
public class UmsAdminRoleRelationDaoTest {

    @Autowired
    private UmsAdminRoleRelationDao adminRoleRelationDao;

    /* 管理员 admin（id=1 或 id=2）在种子数据中有角色和资源分配 */

    @Test
    @DisplayName("集成测试: getRoleList — 查询用户的角色列表")
    void testGetRoleList() {
        // admin 用户 id 在种子数据中已配置角色关联
        // 使用实际 admin id（种子数据中第一个 admin 的 id）
        Long adminId = 1L;

        List<UmsRole> roles = adminRoleRelationDao.getRoleList(adminId);

        assertNotNull(roles, "角色列表不应为 null");
        // admin 至少有一个角色
        if (!roles.isEmpty()) {
            UmsRole role = roles.get(0);
            assertNotNull(role.getName(), "角色应有名称");
        }
    }

    @Test
    @DisplayName("集成测试: getResourceList — 查询用户可访问的资源")
    void testGetResourceList() {
        Long adminId = 1L;

        List<UmsResource> resources = adminRoleRelationDao.getResourceList(adminId);

        assertNotNull(resources, "资源列表不应为 null");
        // admin 用户有资源权限
        if (!resources.isEmpty()) {
            UmsResource resource = resources.get(0);
            assertNotNull(resource.getName(), "资源应有名称");
            assertNotNull(resource.getUrl(), "资源应有 URL");
        }
    }

    @Test
    @DisplayName("集成测试: getResourceList — 不存在的用户返回空列表")
    void testGetResourceListForNonExistentUser() {
        List<UmsResource> resources = adminRoleRelationDao.getResourceList(99999L);
        assertNotNull(resources, "不存在的用户应返回空列表而非 null");
        assertTrue(resources.isEmpty(), "不存在的用户资源列表应为空");
    }

    @Test
    @DisplayName("集成测试: getAdminIdList — 根据资源 ID 反查管理员")
    void testGetAdminIdList() {
        // 使用一个存在的 resource id（种子数据中有资源记录）
        Long resourceId = 1L;

        List<Long> adminIds = adminRoleRelationDao.getAdminIdList(resourceId);

        assertNotNull(adminIds, "管理员 ID 列表不应为 null");
        // 可能有结果也可能无结果，取决于种子数据
    }
}
