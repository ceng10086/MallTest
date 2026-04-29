package com.macro.mall.dao;

import com.macro.mall.dto.OmsOrderQueryParam;
import com.macro.mall.model.OmsOrder;
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
 * DAO 层集成测试 — 订单查询。
 *
 * 集成范围：OmsOrderDao → MyBatis → MySQL (Docker)
 * 数据来源：mall.sql 种子数据（包含订单测试数据）
 *
 * 集成测试关注点：
 *   - SQL 动态条件拼接是否正确
 *   - delete_status=0 过滤是否生效
 *   - 分页查询（PageHelper）是否正常工作
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
@Transactional
public class OmsOrderDaoTest {

    @Autowired
    private OmsOrderDao orderDao;

    @Test
    @DisplayName("集成测试: getList — 无条件查询订单列表")
    void testGetOrderListAll() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        com.github.pagehelper.PageHelper.startPage(1, 5);

        List<OmsOrder> orders = orderDao.getList(param);

        assertNotNull(orders, "订单列表不应为 null");
        // 验证返回的订单都满足 delete_status=0
        for (OmsOrder order : orders) {
            assertEquals(0, order.getDeleteStatus(),
                    "查询结果应只包含 delete_status=0 的订单");
        }
    }

    @Test
    @DisplayName("集成测试: getList — 按订单状态筛选")
    void testGetOrderListFilterByStatus() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setStatus(0); // 待付款状态

        List<OmsOrder> orders = orderDao.getList(param);

        assertNotNull(orders);
        for (OmsOrder order : orders) {
            assertEquals(0, order.getStatus(),
                    "按 status=0 筛选的结果中所有订单 status 应为 0");
        }
    }

    @Test
    @DisplayName("集成测试: getList — 按订单号搜索")
    void testGetOrderListFilterByOrderSn() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setOrderSn("2022"); // 搜索包含 "2022" 的订单号

        List<OmsOrder> orders = orderDao.getList(param);

        assertNotNull(orders);
        // 如果有结果，验证订单号包含搜索关键字
        for (OmsOrder order : orders) {
            assertTrue(order.getOrderSn().contains("2022"),
                    "搜索 '2022' 的结果中订单号应包含 '2022'");
        }
    }

    @Test
    @DisplayName("集成测试: getList — 不存在的订单号返回空列表")
    void testGetOrderListWithNonExistentOrderSn() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setOrderSn("NONEXISTENT_ORDER_12345");

        List<OmsOrder> orders = orderDao.getList(param);

        assertNotNull(orders);
        assertTrue(orders.isEmpty(), "不存在的订单号应返回空列表");
    }
}
