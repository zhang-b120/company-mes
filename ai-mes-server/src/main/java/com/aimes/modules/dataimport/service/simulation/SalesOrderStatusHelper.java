package com.aimes.modules.dataimport.service.simulation;

import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.mapper.BizSalesOrderItemMapper;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 数据生成过程中的销售订单状态推进工具
 *
 * 官方状态流：
 *   draft → pending_approval → confirmed
 *   confirmed → mrp_planned / in_production / outsourcing / cancelled
 *   mrp_planned → in_production / cancelled
 *   in_production → partial_shipped / completed / cancelled
 *   outsourcing → partial_shipped / completed / cancelled
 *   partial_shipped → shipped / partial_shipped
 *   shipped → settled
 *   completed → partial_shipped
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SalesOrderStatusHelper {

    private final BizSalesOrderMapper salesOrderMapper;
    private final BizSalesOrderItemMapper salesOrderItemMapper;

    /** 只从这些状态可以被推进 */
    private static final Set<String> UPGRADABLE_FROM_CONFIRMED =
            Set.of("confirmed", "mrp_planned");
    private static final Set<String> UPGRADABLE_FROM_PRODUCTION =
            Set.of("confirmed", "mrp_planned", "in_production");
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("cancelled", "voided", "settled");

    /**
     * 推进到 in_production（生产中）
     * 从 confirmed/mrp_planned 升级到 in_production
     * 如果订单已经是 in_production 或更高，则不动
     */
    public void advanceToInProduction(Long orderId) {
        if (orderId == null) return;
        BizSalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) return;
        if (!UPGRADABLE_FROM_CONFIRMED.contains(order.getStatus())) return;
        order.setStatus("in_production");
        salesOrderMapper.updateById(order);
        log.debug("销售订单[{}] 状态推进: {} → in_production", order.getOrderNo(), order.getStatus());
    }

    /**
     * 推进到 mrp_planned（已计划）
     * 从 confirmed 升级到 mrp_planned
     */
    public void advanceToMrpPlanned(Long orderId) {
        if (orderId == null) return;
        BizSalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) return;
        if (!"confirmed".equals(order.getStatus())) return;
        order.setStatus("mrp_planned");
        salesOrderMapper.updateById(order);
    }

    /**
     * 推进到 outsourcing（外协中）
     * 流程改为 ①计划→②工单→③报工→④外协 后，进入外协时订单已经是 in_production/completed，
     * 因此白名单放开：除终态外都允许覆盖为 outsourcing，
     * 但不允许从已发货阶段(partial_shipped/shipped)回退。
     */
    public void advanceToOutsourcing(Long orderId) {
        if (orderId == null) return;
        BizSalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) return;
        String s = order.getStatus();
        if (s == null) return;
        if (TERMINAL_STATUSES.contains(s)) return;
        // 已经发货过的订单不再回退到 outsourcing
        if ("partial_shipped".equals(s) || "shipped".equals(s)) return;
        if ("outsourcing".equals(s)) return; // 已经是外协中，无需更新
        order.setStatus("outsourcing");
        salesOrderMapper.updateById(order);
        log.debug("销售订单[{}] 状态推进: {} → outsourcing", order.getOrderNo(), s);
    }

    /**
     * 根据订单行项发货数量，推进订单为 partial_shipped / shipped
     * 全部发货完 → shipped；部分发货 → partial_shipped
     * 允许从 in_production / outsourcing / completed / partial_shipped 推进
     */
    public void advanceByShipment(Long orderId) {
        if (orderId == null) return;
        BizSalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) return;
        if (TERMINAL_STATUSES.contains(order.getStatus())) return;

        // 允许从这些状态推进
        Set<String> allowed = Set.of("in_production", "outsourcing", "completed", "partial_shipped");
        if (!allowed.contains(order.getStatus())) return;

        List<BizSalesOrderItem> items = salesOrderItemMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrderItem>()
                        .eq(BizSalesOrderItem::getOrderId, orderId)
                        .eq(BizSalesOrderItem::getDeleted, 0));
        if (items.isEmpty()) return;

        boolean allShipped = items.stream().allMatch(item -> {
            BigDecimal shipped = item.getShippedQty() != null ? item.getShippedQty() : BigDecimal.ZERO;
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            return qty.compareTo(BigDecimal.ZERO) > 0 && shipped.compareTo(qty) >= 0;
        });
        boolean anyShipped = items.stream().anyMatch(item ->
                item.getShippedQty() != null && item.getShippedQty().compareTo(BigDecimal.ZERO) > 0);

        if (allShipped) {
            order.setStatus("shipped");
            // 全部发完 → 自动从主计划剔除 (master_plan_added 置 0), 不再在主计划列表显示
            // 主计划 listOrders() 里也有 lazy 兜底, 这里主动清是为了发货那一刻就生效
            order.setMasterPlanAdded(0);
            order.setMasterPlanAddedAt(null);
        } else if (anyShipped) {
            order.setStatus("partial_shipped");
        }
        salesOrderMapper.updateById(order);
    }

    /**
     * 推进到 completed（生产完成）
     * 当订单所有产品行都生产完成（producedQty >= quantity）时，订单从 in_production/outsourcing 推进到 completed
     * 表示订单已完成生产阶段，进入待发货/已发货流程
     */
    public void advanceToCompleted(Long orderId) {
        if (orderId == null) return;
        BizSalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) return;
        if (TERMINAL_STATUSES.contains(order.getStatus())) return;

        // 只允许从 in_production / outsourcing 推进到 completed
        if (!List.of("in_production", "outsourcing").contains(order.getStatus())) return;

        List<BizSalesOrderItem> items = salesOrderItemMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrderItem>()
                        .eq(BizSalesOrderItem::getOrderId, orderId)
                        .eq(BizSalesOrderItem::getDeleted, 0));
        if (items.isEmpty()) return;

        boolean allProduced = items.stream().allMatch(item -> {
            BigDecimal produced = item.getProducedQty() != null ? item.getProducedQty() : BigDecimal.ZERO;
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            return qty.compareTo(BigDecimal.ZERO) > 0 && produced.compareTo(qty) >= 0;
        });
        if (allProduced) {
            order.setStatus("completed");
            salesOrderMapper.updateById(order);
            log.debug("销售订单[{}] 状态推进: → completed（生产完成）", order.getOrderNo());
        }
    }

    /**
     * 推进到 settled（已结算）
     * 从 shipped 推进到 settled
     */
    public void advanceToSettled(Long orderId) {
        if (orderId == null) return;
        BizSalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) return;
        if (!"shipped".equals(order.getStatus())) return;
        order.setStatus("settled");
        salesOrderMapper.updateById(order);
    }
}
