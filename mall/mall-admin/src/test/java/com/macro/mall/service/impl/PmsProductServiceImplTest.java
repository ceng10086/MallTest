package com.macro.mall.service.impl;

import com.macro.mall.model.PmsSkuStock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PmsProductServiceImpl 单元测试 — 商品SKU编码生成与批量状态管理。
 *
 * 核心逻辑：
 *   - handleSkuStockCode(List<PmsSkuStock>, Long productId):
 *       为 SKU 自动生成编码，格式：日期(8位) + productId(补零至4位) + 索引(补零至3位)
 *       例：20260429_0001_001
 *   - updateVerifyStatus / updatePublishStatus 等：批量状态更新
 *   - handleUpdateSkuStockList: 增量 SKU 更新（新增/修改/删除区分逻辑）
 *
 * 采用的测试用例设计方法：
 *   白盒：语句覆盖（handleSkuStockCode 每条语句 + 分支）
 *   黑盒：等价类划分（空list / 已有编码 / 无编码的SKU）
 *   黑盒：边界值分析（index 边界：第1个SKU/第999个SKU）
 */
public class PmsProductServiceImplTest {

    private PmsProductServiceImpl service;
    /* 反射句柄 */
    private Method handleSkuStockCodeMethod;

    @BeforeEach
    public void setUp() throws Exception {
        service = new PmsProductServiceImpl();
        handleSkuStockCodeMethod = PmsProductServiceImpl.class.getDeclaredMethod(
                "handleSkuStockCode", List.class, Long.class);
        handleSkuStockCodeMethod.setAccessible(true);
    }

    // ================================================================
    //  4.2.1.1  白盒测试 — 语句覆盖（handleSkuStockCode）
    //  目标：确保方法内每条语句至少执行一次
    // ================================================================

    @Nested
    @DisplayName("白盒：语句覆盖 — SKU 编码生成")
    class SkuCodeStatementCoverageTests {

        @Test
        @DisplayName("语句覆盖: 空的 SKU 列表 → 直接 return（不生成任何编码）")
        void testHandleSkuStockCodeWithEmptyList() throws Exception {
            List<PmsSkuStock> emptyList = new ArrayList<>();
            // 不应抛出异常
            handleSkuStockCodeMethod.invoke(service, emptyList, 1L);
            // 空列表应安全跳过
            assertTrue(emptyList.isEmpty(), "空列表应保持不变");
        }

        @Test
        @DisplayName("语句覆盖: 3个未编码SKU → 生成日期+productId+index 格式的编码")
        void testHandleSkuStockCodeGeneratesFormat() throws Exception {
            List<PmsSkuStock> skuList = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                PmsSkuStock sku = new PmsSkuStock();
                sku.setSkuCode(null); // 未设置编码 → 应自动生成
                skuList.add(sku);
            }

            handleSkuStockCodeMethod.invoke(service, skuList, 42L);

            // 验证格式：yyyyMMdd + 4位productId + 3位Index(从1开始)
            for (int i = 0; i < 3; i++) {
                String code = skuList.get(i).getSkuCode();
                assertNotNull(code, "SKU[" + i + "] 编码不应为 null");
                // 总长度：8(日期) + 4(productId) + 3(索引) = 15
                assertEquals(15, code.length(),
                        "编码长度应为15, 实际: " + code + " (长度" + code.length() + ")");
                // productId 部分：0042
                assertTrue(code.contains("0042"),
                        "编码应包含补零的 productId '0042', 实际: " + code);
                // 索引部分：第i个是 i+1（补零至3位）
                String expectedIndex = String.format("%03d", i + 1);
                assertTrue(code.endsWith(expectedIndex),
                        "SKU[" + i + "] 索引应为 " + expectedIndex + ", 实际: " + code);
            }
        }

        @Test
        @DisplayName("语句覆盖: 已有编码的SKU → 跳过不覆盖（if-StrUtil.isEmpty 分支）")
        void testHandleSkuStockCodePreservesExistingCode() throws Exception {
            List<PmsSkuStock> skuList = new ArrayList<>();
            PmsSkuStock sku = new PmsSkuStock();
            sku.setSkuCode("CUSTOM-CODE-001"); // 已有编码
            skuList.add(sku);

            handleSkuStockCodeMethod.invoke(service, skuList, 1L);

            assertEquals("CUSTOM-CODE-001", skuList.get(0).getSkuCode(),
                    "已有编码的 SKU 不应被覆盖");
        }

        @Test
        @DisplayName("语句覆盖: 混合列表（有编码+无编码）→ 仅生成缺失的编码")
        void testHandleSkuStockCodeMixedList() throws Exception {
            List<PmsSkuStock> skuList = new ArrayList<>();

            PmsSkuStock skuWithCode = new PmsSkuStock();
            skuWithCode.setSkuCode("EXISTING");
            skuList.add(skuWithCode);

            PmsSkuStock skuWithoutCode = new PmsSkuStock();
            skuWithoutCode.setSkuCode(null);
            skuList.add(skuWithoutCode);

            handleSkuStockCodeMethod.invoke(service, skuList, 99L);

            assertEquals("EXISTING", skuList.get(0).getSkuCode(), "已有编码应保留");
            assertNotNull(skuList.get(1).getSkuCode(), "无编码的应生成");
            assertNotEquals("EXISTING", skuList.get(1).getSkuCode());
        }
    }

    // ================================================================
    //  黑盒测试 — 边界值分析（handleSkuStockCode 的 productId 和 index）
    // ================================================================

    @Nested
    @DisplayName("黑盒：边界值分析 — productId 和 index 边界")
    class SkuCodeBoundaryTests {

        @Test
        @DisplayName("边界: productId=1 → 补零为 0001")
        void testBoundaryProductIdMinimum() throws Exception {
            List<PmsSkuStock> list = new ArrayList<>();
            list.add(new PmsSkuStock());
            handleSkuStockCodeMethod.invoke(service, list, 1L);
            // productId 占位 0001
            assertTrue(list.get(0).getSkuCode().contains("0001"),
                    "productId=1 应补零为 0001");
        }

        @Test
        @DisplayName("边界: productId=9999 → 恰好4位不补零")
        void testBoundaryProductIdFourDigits() throws Exception {
            List<PmsSkuStock> list = new ArrayList<>();
            list.add(new PmsSkuStock());
            handleSkuStockCodeMethod.invoke(service, list, 9999L);
            assertTrue(list.get(0).getSkuCode().contains("9999"),
                    "productId=9999 保持不变");
        }

        @Test
        @DisplayName("边界: productId=10000 → 溢出4位，仍用 %04d 格式化为 10000")
        void testBoundaryProductIdOverflow() throws Exception {
            List<PmsSkuStock> list = new ArrayList<>();
            list.add(new PmsSkuStock());
            handleSkuStockCodeMethod.invoke(service, list, 10000L);
            // String.format("%04d", 10000) = "10000"
            assertTrue(list.get(0).getSkuCode().contains("10000"),
                    "productId=10000 超出4位限制");
        }

        @Test
        @DisplayName("边界: 单个商品的第1个SKU → index=001")
        void testBoundaryIndexFirst() throws Exception {
            List<PmsSkuStock> list = new ArrayList<>();
            list.add(new PmsSkuStock());
            handleSkuStockCodeMethod.invoke(service, list, 1L);
            assertTrue(list.get(0).getSkuCode().endsWith("001"),
                    "第1个SKU index=001");
        }
    }
}
