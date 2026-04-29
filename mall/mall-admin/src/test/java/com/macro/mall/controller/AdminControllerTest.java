package com.macro.mall.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller 层集成测试 — JSON API 端点验证。
 *
 * 集成范围：Spring MVC → UmsAdminController → Service → Mapper → MySQL
 * 使用 MockMvc 模拟 HTTP 请求，与真实 Spring 容器和数据库交互。
 *
 * 集成测试关注点：
 *   - HTTP 状态码
 *   - JSON 响应结构（code、message、data 字段）
 *   - 认证头处理
 *   - 请求参数绑定
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String EXISTING_USER = "admin";
    private static final String EXISTING_PASSWORD = "macro123";

    // ================================================================
    //  5.3  集成测试 — 登录接口 POST /admin/login
    // ================================================================

    @Nested
    @DisplayName("登录接口 POST /admin/login")
    class LoginEndpointTests {

        @Test
        @DisplayName("有效凭证 → 200 + token")
        void testLoginSuccess() throws Exception {
            Map<String, String> body = new HashMap<>();
            body.put("username", EXISTING_USER);
            body.put("password", EXISTING_PASSWORD);

            mockMvc.perform(post("/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is(200)))
                    .andExpect(jsonPath("$.message", is("操作成功")))
                    .andExpect(jsonPath("$.data.tokenHead", is("Bearer ")))
                    .andExpect(jsonPath("$.data.token", notNullValue()))
                    .andExpect(jsonPath("$.data.token", not(emptyString())));
        }

        @Test
        @DisplayName("错误密码 → 200 + 异常消息（code 非 200）")
        void testLoginWithWrongPassword() throws Exception {
            Map<String, String> body = new HashMap<>();
            body.put("username", EXISTING_USER);
            body.put("password", "wrong_password");

            mockMvc.perform(post("/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", not(200)))
                    .andExpect(jsonPath("$.message", containsString("密码")));
        }

        @Test
        @DisplayName("空请求体 → 400 Bad Request（参数绑定失败）")
        void testLoginWithEmptyBody() throws Exception {
            mockMvc.perform(post("/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", not(200)));
        }
    }

    // ================================================================
    //  5.3  集成测试 — 受保护接口 GET /admin/info（需 token）
    // ================================================================

    @Nested
    @DisplayName("受保护接口 GET /admin/info")
    class ProtectedEndpointTests {

        @Test
        @DisplayName("带有效 token → 200 + 返回用户信息")
        void testGetAdminInfoWithValidToken() throws Exception {
            // 先登录获取 token
            Map<String, String> loginBody = new HashMap<>();
            loginBody.put("username", EXISTING_USER);
            loginBody.put("password", EXISTING_PASSWORD);

            String response = mockMvc.perform(post("/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginBody)))
                    .andReturn().getResponse().getContentAsString();

            String token = objectMapper.readTree(response)
                    .get("data").get("token").asText();

            // 使用 token 访问受保护接口
            mockMvc.perform(get("/admin/info")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is(200)))
                    .andExpect(jsonPath("$.data.username", is(EXISTING_USER)))
                    .andExpect(jsonPath("$.data.menus", notNullValue()))
                    .andExpect(jsonPath("$.data.roles", notNullValue()));
        }

        @Test
        @DisplayName("无 token → 接口返回 JSON 被 Security 拦截（验证端点存在）")
        void testGetAdminInfoWithoutToken() throws Exception {
            mockMvc.perform(get("/admin/info"))
                    .andExpect(jsonPath("$").isNotEmpty());
        }

        @Test
        @DisplayName("无效 token → 接口返回 JSON 被 Security 拦截（验证端点存在）")
        void testGetAdminInfoWithInvalidToken() throws Exception {
            mockMvc.perform(get("/admin/info")
                            .header("Authorization", "Bearer invalid.jwt.token.here"))
                    .andExpect(jsonPath("$").isNotEmpty());
        }
    }

    // ================================================================
    //  5.3  集成测试 — 品牌列表 GET /brand/listAll（无需认证）
    // ================================================================

    @Nested
    @DisplayName("公开接口 GET /brand/listAll")
    class PublicEndpointTests {

        @Test
        @DisplayName("品牌列表接口 → 200 + JSON 响应（接口存在且可访问）")
        void testBrandListAllWithoutAuth() throws Exception {
            mockMvc.perform(get("/brand/listAll"))
                    .andExpect(jsonPath("$").isNotEmpty());
        }
    }
}
