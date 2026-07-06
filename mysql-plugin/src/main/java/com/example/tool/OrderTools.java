package com.example.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

@Service
public class OrderTools {

    private static final Logger log = LoggerFactory.getLogger(OrderTools.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private DataSource dataSource;

    @Tool(description = "【修改订单状态的专用工具】根据订单ID更新订单的当前状态。需要修改订单数据请优先使用此工具，不要用 SQL 直接修改。\n" +
            "状态映射关系 1待发货 2已发货 3配送中 4已收货 5已完成 6已取消 7退货中 8已退货 9已退款")
    public String updateOrderStatus(
            @ToolParam(description = "订单ID") String orderId,
            @ToolParam(description = "目标状态值\n" +
                    "1待发货 2已发货 3配送中 4已收货 5已完成 6已取消 7退货中 8已退货 9已退款") String status) {
        try {
            log.info("updateOrderStatus called, orderId={}, status={}", orderId, status);

            if (orderId == null || orderId.trim().isEmpty()) {
                return "参数校验失败: 订单ID不能为空";
            }
            if (status == null || status.trim().isEmpty()) {
                return "参数校验失败: 状态不能为空";
            }

            String trimmedOrderId = orderId.trim();
            String trimmedStatus = status.trim();

            int affectedRows;
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(
                         "UPDATE sys_order SET status = ? WHERE id = ?")) {
                pstmt.setString(1, trimmedStatus);
                pstmt.setString(2, trimmedOrderId);
                affectedRows = pstmt.executeUpdate();
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", affectedRows > 0);
            result.put("affectedRows", affectedRows);
            result.put("message", affectedRows > 0
                    ? "订单状态已更新为: " + trimmedStatus
                    : "未找到订单: " + trimmedOrderId);
            if (affectedRows > 0) {
                result.put("orderId", trimmedOrderId);
                result.put("newStatus", trimmedStatus);
            }

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("修改订单状态失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "修改订单状态失败: " + e.getMessage());
            try {
                return objectMapper.writeValueAsString(error);
            } catch (Exception jsonEx) {
                return "修改订单状态失败: " + e.getMessage();
            }
        }
    }

    @Tool(description = "批量修改订单状态，将多个订单的状态更新为目标状态\n" +
            "状态映射关系：\n" +
            "  pending=待支付，paid=已支付，shipped=已发货，delivered=已送达，cancelled=已取消，refunded=已退款，completed=已完成")
    public String batchUpdateOrderStatus(
            @ToolParam(description = "订单ID列表，多个ID用逗号分隔") String orderIds,
            @ToolParam(description = "目标状态值\n" +
                    "pending=待支付，paid=已支付，shipped=已发货，delivered=已送达，cancelled=已取消，refunded=已退款，completed=已完成") String status) {
        try {
            log.info("batchUpdateOrderStatus called, orderIds={}, status={}", orderIds, status);

            if (orderIds == null || orderIds.trim().isEmpty()) {
                return "参数校验失败: 订单ID列表不能为空";
            }
            if (status == null || status.trim().isEmpty()) {
                return "参数校验失败: 状态不能为空";
            }

            String trimmedStatus = status.trim();
            String[] ids = orderIds.trim().split("\\s*,\\s*");
            int totalAffected = 0;

            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(
                         "UPDATE sys_order SET status = ? WHERE id = ?")) {
                for (String id : ids) {
                    if (!id.isEmpty()) {
                        pstmt.setString(1, trimmedStatus);
                        pstmt.setString(2, id);
                        totalAffected += pstmt.executeUpdate();
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", totalAffected > 0);
            result.put("totalIds", ids.length);
            result.put("affectedRows", totalAffected);
            result.put("message", totalAffected > 0
                    ? "成功更新 " + totalAffected + " 个订单状态为: " + trimmedStatus
                    : "未找到任何匹配的订单");
            result.put("newStatus", trimmedStatus);

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("批量修改订单状态失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "批量修改订单状态失败: " + e.getMessage());
            try {
                return objectMapper.writeValueAsString(error);
            } catch (Exception jsonEx) {
                return "批量修改订单状态失败: " + e.getMessage();
            }
        }
    }

    @Tool(description = "查询指定订单的详细信息\n" +
            "sys_order 表字段说明：\n" +
            "  id             - 订单ID（主键）\n" +
            "  order_no       - 订单编号\n" +
            "  user_id        - 用户ID\n" +
            "  status         - 订单状态（pending/paid/shipped/delivered/cancelled/refunded/completed）\n" +
            "  total_amount   - 订单总金额\n" +
            "  created_at     - 创建时间\n" +
            "  updated_at     - 更新时间")
    public String getOrderInfo(
            @ToolParam(description = "订单ID") String orderId) {
        try {
            log.info("getOrderInfo called, orderId={}", orderId);

            if (orderId == null || orderId.trim().isEmpty()) {
                return "参数校验失败: 订单ID不能为空";
            }

            Map<String, Object> orderInfo = null;
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM sys_order WHERE id = ?")) {
                pstmt.setString(1, orderId.trim());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        orderInfo = new HashMap<>();
                        ResultSetMetaData metaData = rs.getMetaData();
                        for (int i = 1; i <= metaData.getColumnCount(); i++) {
                            orderInfo.put(metaData.getColumnName(i), rs.getObject(i));
                        }
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            if (orderInfo != null) {
                result.put("success", true);
                result.put("order", orderInfo);
            } else {
                result.put("success", false);
                result.put("message", "未找到订单: " + orderId);
            }

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("查询订单信息失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "查询订单信息失败: " + e.getMessage());
            try {
                return objectMapper.writeValueAsString(error);
            } catch (Exception jsonEx) {
                return "查询订单信息失败: " + e.getMessage();
            }
        }
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
