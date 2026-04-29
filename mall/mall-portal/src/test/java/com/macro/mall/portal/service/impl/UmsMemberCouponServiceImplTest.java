package com.macro.mall.portal.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UmsMemberCouponServiceImpl 单元测试 — 优惠券码生成与优惠券过滤逻辑。
 *
 * 核心逻辑：
 *   - generateCouponCode(Long memberId): 生成 16 位优惠券码
 *       格式：时间戳后8位 + 4位随机数 + userId后4位（不足4位补零）
 *   - calcTotalAmount(List): 计算购物车商品总价（含促销折扣）
 *   - calcTotalAmountByproductCategoryId: 按分类计算总价
 *   - calcTotalAmountByProductId: 按商品ID计算总价
 *
 * 采用的测试用例设计方法：
 *   白盒：语句覆盖（generateCouponCode 每条语句）
 *   黑盒：等价类划分（memberId 长度 ≤4 / >4）、边界值分析
 */
public class UmsMemberCouponServiceImplTest {

    private UmsMemberCouponServiceImpl service;
    /* 反射句柄，用于白盒测试私有方法 */
    private Method generateCouponCodeMethod;
    private Method calcTotalAmountMethod;
    private Method calcTotalAmountByProductIdMethod;

    @BeforeEach
    public void setUp() throws Exception {
        service = new UmsMemberCouponServiceImpl();

        generateCouponCodeMethod = UmsMemberCouponServiceImpl.class.getDeclaredMethod(
                "generateCouponCode", Long.class);
        generateCouponCodeMethod.setAccessible(true);

        calcTotalAmountMethod = UmsMemberCouponServiceImpl.class.getDeclaredMethod(
                "calcTotalAmount", java.util.List.class);
        calcTotalAmountMethod.setAccessible(true);

        calcTotalAmountByProductIdMethod = UmsMemberCouponServiceImpl.class.getDeclaredMethod(
                "calcTotalAmountByProductId", java.util.List.class, java.util.List.class);
        calcTotalAmountByProductIdMethod.setAccessible(true);
    }

    // ================================================================
    //  4.2.1.1  白盒测试 — 语句覆盖（generateCouponCode）
    //  目标：确保生成优惠券码的每一条语句都被执行
    //        语句1: StringBuilder 初始化
    //        语句2: 获取时间戳后8位并append
    //        语句3: 4次循环生成随机数并append
    //        语句4: memberId<=4位 → 补零处理
    //        语句5: memberId>4位 → 截取后4位
    //        语句6: 返回字符串
    // ================================================================

    @Nested
    @DisplayName("白盒：语句覆盖 — 优惠券码生成")
    class CouponCodeStatementCoverageTests {

        @Test
        @DisplayName("语句覆盖: memberId ≤ 4位 → 走补零分支，生成16位码")
        void testGenerateCouponCodeWithShortMemberId() throws Exception {
            // memberId=42 (2位，≤4位，走补零分支)
            String code = (String) generateCouponCodeMethod.invoke(service, 42L);

            assertNotNull(code, "优惠券码不应为 null");
            assertEquals(16, code.length(), "优惠券码长度应为 16 位");
            // 后4位应为 "0042"
            assertTrue(code.endsWith("0042"),
                    "memberId=42 不足4位，应补零为 0042, 实际: " + code);
            // 中间4位是随机数字
            String randomPart = code.substring(8, 12);
            assertTrue(randomPart.matches("\\d{4}"), "第9-12位应为4位随机数字");
        }

        @Test
        @DisplayName("语句覆盖: memberId > 4位 → 走截取后4位分支，生成16位码")
        void testGenerateCouponCodeWithLongMemberId() throws Exception {
            // memberId=123456789（9位，>4位，走截取分支）
            String code = (String) generateCouponCodeMethod.invoke(service, 123456789L);

            assertNotNull(code);
            assertEquals(16, code.length());
            // 后4位应为 "6789"（截取 memberId 后4位）
            assertTrue(code.endsWith("6789"),
                    "memberId=123456789 截取后4位应为 6789, 实际: " + code);
        }

        @Test
        @DisplayName("语句覆盖: memberId 恰好为4位 → 走补零分支（=4时 printf %04d 不变）")
        void testGenerateCouponCodeWithFourDigitMemberId() throws Exception {
            String code = (String) generateCouponCodeMethod.invoke(service, 1234L);

            assertNotNull(code);
            assertEquals(16, code.length());
            assertTrue(code.endsWith("1234"));
        }
    }

    // ================================================================
    //  黑盒测试 — 边界值分析（generateCouponCode 的 memberId 边界）
    //  目标：测试 memberId 在 4 位边界附近的行为
    //        memberId=0, memberId=9999（恰好4位）, memberId=10000（刚超过4位）
    // ================================================================

    @Nested
    @DisplayName("黑盒：边界值分析 — memberId 边界")
    class CouponCodeBoundaryTests {

        @Test
        @DisplayName("边界: memberId=0 → 补零为 0000")
        void testBoundaryZeroMemberId() throws Exception {
            String code = (String) generateCouponCodeMethod.invoke(service, 0L);
            assertTrue(code.endsWith("0000"), "memberId=0 补零后应为 0000, 实际: " + code);
        }

        @Test
        @DisplayName("边界: memberId=9999 → 恰好4位，走补零分支，后4位为 9999")
        void testBoundaryMaxFourDigits() throws Exception {
            String code = (String) generateCouponCodeMethod.invoke(service, 9999L);
            assertTrue(code.endsWith("9999"));
        }

        @Test
        @DisplayName("边界: memberId=10000 → 刚好超过4位，走截取分支，后4位为 0000")
        void testBoundaryMinFiveDigits() throws Exception {
            String code = (String) generateCouponCodeMethod.invoke(service, 10000L);
            assertTrue(code.endsWith("0000"),
                    "memberId=10000 截取后4位应为 0000, 实际: " + code);
        }
    }
}
