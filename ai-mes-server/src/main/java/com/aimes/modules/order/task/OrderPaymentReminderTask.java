package com.aimes.modules.order.task;

import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.service.BizSalesOrderService;
import com.aimes.modules.system.service.SysNotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 订单回款提醒定时任务
 * - 每天 09:00 扫描"现结"已发货订单, 未回款则推送提醒(同一订单不重复推, 由通知中心去重)
 * - 每月月末 09:00 额外扫描"月结"订单, 未回款则推送提醒
 *
 * 提醒去重: 通过 {@link SysNotificationService} 检查同 (userId, relatedType, relatedId, type) 是否已存在未读通知
 * 对应任务12里 paymentAmount 字段为已收款金额, totalAmount 为订单含税总金额
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentReminderTask {

    private final BizSalesOrderService salesOrderService;
    private final SysNotificationService notificationService;

    /** 每天 09:00 扫描现结+月结订单, 推送未回款提醒 */
    @Scheduled(cron = "0 0 9 * * ?")
    public void scan() {
        try {
            scanCashShippedUnpaid();
            // 月末当天才扫月结
            LocalDate today = LocalDate.now();
            if (today.equals(today.withDayOfMonth(today.lengthOfMonth()))) {
                scanMonthlyUnpaid();
            }
        } catch (Exception e) {
            log.error("回款提醒任务异常", e);
        }
    }

    private void scanCashShippedUnpaid() {
        List<BizSalesOrder> orders = salesOrderService.list(new LambdaQueryWrapper<BizSalesOrder>()
                .eq(BizSalesOrder::getStatus, "shipped")
                .and(w -> w.eq(BizSalesOrder::getSettlementType, "现结")
                        .or().eq(BizSalesOrder::getSettlementType, "cash")));
        for (BizSalesOrder o : orders) {
            if (isUnpaid(o)) pushReminder(o, "现结订单未回款", "订单 " + o.getOrderNo() + " 已发货, 现结尚有未回款金额, 请尽快催款");
        }
    }

    private void scanMonthlyUnpaid() {
        List<BizSalesOrder> orders = salesOrderService.list(new LambdaQueryWrapper<BizSalesOrder>()
                .in(BizSalesOrder::getStatus, "confirmed", "in_production", "shipped", "completed")
                .like(BizSalesOrder::getSettlementType, "月结"));
        for (BizSalesOrder o : orders) {
            if (isUnpaid(o)) pushReminder(o, "月结订单未回款", "月末提醒: 订单 " + o.getOrderNo() + " 仍有未回款金额, 请关注回款进度");
        }
    }

    private boolean isUnpaid(BizSalesOrder o) {
        BigDecimal total = o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = o.getPaymentAmount() != null ? o.getPaymentAmount() : BigDecimal.ZERO;
        return total.signum() > 0 && paid.compareTo(total) < 0;
    }

    private void pushReminder(BizSalesOrder o, String title, String content) {
        Long target = o.getSalesmanId() != null ? o.getSalesmanId() : o.getCreateBy();
        if (target == null) return;
        try {
            notificationService.notifyUser(target, title, content, "warning", "sales_order", o.getId());
        } catch (Exception e) {
            log.warn("回款提醒推送失败 order={}, err={}", o.getOrderNo(), e.getMessage());
        }
    }
}
