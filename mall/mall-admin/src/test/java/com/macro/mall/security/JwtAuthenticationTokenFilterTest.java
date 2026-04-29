package com.macro.mall.security;

import com.macro.mall.security.component.JwtAuthenticationTokenFilter;
import com.macro.mall.security.util.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import javax.servlet.FilterChain;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JwtAuthenticationTokenFilter Mock 隔离测试 — 6 个测试用例。
 *
 * 验证 JWT 认证过滤器的核心行为：
 *   1. 无 Auth header → 跳过认证，放行
 *   2. 非 Bearer header → 跳过认证，放行
 *   3. token 解析失败 → 放行
 *   4. 合法 token → 加载用户 + 设置 SecurityContext
 *   5. token 校验失败 → 不设置 SecurityContext
 *   6. 每次请求都调用 chain.doFilter 放行
 *
 * Mock 技巧：@Mock/@InjectMocks 隔离 Spring 容器，
 * verify() 验证 mock 对象交互行为。
 */
@ExtendWith(MockitoExtension.class)
public class JwtAuthenticationTokenFilterTest {

    @Mock private JwtTokenUtil jwtTokenUtil;
    @Mock private UserDetailsService userDetailsService;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationTokenFilter filter;

    private Method doFilterInternalMethod;

    @BeforeEach
    public void setUp() throws Exception {
        SecurityContextHolder.clearContext();
        // 反射获取 protected doFilterInternal，绕过 OncePerRequestFilter 的 final doFilter
        doFilterInternalMethod = JwtAuthenticationTokenFilter.class
                .getDeclaredMethod("doFilterInternal",
                        javax.servlet.http.HttpServletRequest.class,
                        javax.servlet.http.HttpServletResponse.class,
                        FilterChain.class);
        doFilterInternalMethod.setAccessible(true);
        // 反射设置 @Value 字段（单元测试中无 Spring 容器）
        setField(filter, "tokenHeader", "Authorization");
        setField(filter, "tokenHead", "Bearer ");
    }

    private void invokeFilter(MockHttpServletRequest req, MockHttpServletResponse res) throws Exception {
        doFilterInternalMethod.invoke(filter, req, res, filterChain);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    // ================================================================
    //  TEST-1: 无 Authorization header → 不调用 UserDetailsService，放行
    // ================================================================
    @Test
    @DisplayName("无 Authorization header → 不加载用户，直接放行")
    void testNoAuthHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        invokeFilter(request, response);

        verify(userDetailsService, never()).loadUserByUsername(anyString());
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // ================================================================
    //  TEST-2: 非 Bearer header → 跳过 JWT 解析
    // ================================================================
    @Test
    @DisplayName("Authorization header 为 Basic（非 Bearer）→ 跳过 JWT 解析")
    void testNonBearerHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic YWRtaW46MTIzNDU2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        invokeFilter(request, response);

        verify(jwtTokenUtil, never()).getUserNameFromToken(anyString());
        verify(filterChain).doFilter(request, response);
    }

    // ================================================================
    //  TEST-3: token 解析失败 → 不加载用户，放行
    // ================================================================
    @Test
    @DisplayName("token 解析失败返回 null → 不加载用户，放行")
    void testUnparseableToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer garbage.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenUtil.getUserNameFromToken("garbage.token")).thenReturn(null);

        invokeFilter(request, response);

        verify(userDetailsService, never()).loadUserByUsername(anyString());
        verify(filterChain).doFilter(request, response);
    }

    // ================================================================
    //  TEST-4: 合法 token → 加载用户 + 设置 SecurityContext
    // ================================================================
    @Test
    @DisplayName("合法 token → 加载用户 → 校验通过 → 设置 SecurityContext")
    void testValidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenUtil.getUserNameFromToken("valid.jwt.token")).thenReturn("testuser");
        UserDetails mockUser = mock(UserDetails.class);
        when(mockUser.getUsername()).thenReturn("testuser");
        when(mockUser.getAuthorities()).thenReturn(new java.util.ArrayList<>());
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(mockUser);
        when(jwtTokenUtil.validateToken(eq("valid.jwt.token"), any(UserDetails.class))).thenReturn(true);

        invokeFilter(request, response);

        verify(userDetailsService).loadUserByUsername("testuser");
        verify(jwtTokenUtil).validateToken(eq("valid.jwt.token"), any(UserDetails.class));
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("testuser", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(filterChain).doFilter(request, response);
    }

    // ================================================================
    //  TEST-5: token 校验失败 → 不设置 SecurityContext
    // ================================================================
    @Test
    @DisplayName("token 校验失败（validateToken=false）→ 不设置 Authentication")
    void testInvalidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenUtil.getUserNameFromToken("invalid.token")).thenReturn("testuser");
        UserDetails mockUser = mock(UserDetails.class);
        // lenient: validateToken 返回 false 时不会继续到 UserDetails 后续使用
        lenient().when(userDetailsService.loadUserByUsername("testuser")).thenReturn(mockUser);
        lenient().when(jwtTokenUtil.validateToken(eq("invalid.token"), any(UserDetails.class))).thenReturn(false);

        invokeFilter(request, response);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // ================================================================
    //  TEST-6: verify — 每次请求都继续过滤链
    // ================================================================
    @Test
    @DisplayName("verify: 每次请求 filter 都调用 chain.doFilter 放行")
    void testFilterAlwaysProceedsChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer some.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenUtil.getUserNameFromToken("some.token")).thenReturn(null);

        invokeFilter(request, response);

        verify(filterChain).doFilter(request, response);
    }
}
