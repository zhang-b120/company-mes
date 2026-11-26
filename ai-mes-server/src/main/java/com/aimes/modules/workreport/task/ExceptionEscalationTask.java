package com.aimes.modules.workreport.task;

import com.aimes.modules.system.service.SysNotificationService;
import com.aimes.modules.workreport.entity.BizExceptionReport;
import com.aimes.modules.workreport.mapper.BizExceptionReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异常上报自动升级定时任务
 * 每5分钟检查一次，超过30分钟未处理的异常自动升级通知老板
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExceptionEscalationTask {

    private final BizExceptionReportMapper exceptionReportMapper;
    private final SysNotificationService notificationService;

    @Scheduled(fixedRate = 300000) // 5分钟检查一次
    public void escalateUnhandledExceptions() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);

            // 查询超过30分钟未处理且未升级的异常
            List<BizExceptionReport> unhandled = exceptionReportMapper.selectList(
                    new LambdaQueryWrapper<BizExceptionReport>()
                            .eq(BizExceptionReport::getStatus, "pending")
                            .le(BizExceptionReport::getCreateTime, threshold)
                            .and(w -> w.isNull(BizExceptionReport::getIsEscalated)
                                    .or().eq(BizExceptionReport::getIsEscalated, 0)));

            for (BizExceptionReport report : unhandled) {
                // 标记为已升级
                report.setIsEscalated(1);
                report.setEscalatedAt(LocalDateTime.now());
                exceptionReportMapper.updateById(report);

                // 通知老板角色
                notificationService.notifyRole("admin",
                        "异常升级通知",
                        "异常上报[" + report.getExceptionType() + "]超过30分钟未处理, 请关注! 工单ID:" + report.getWorkOrderId(),
                        "alert", "exception_report", report.getId());

                log.info("异常已升级: exceptionId={}, workOrderId={}", report.getId(), report.getWorkOrderId());
            }

            if (!unhandled.isEmpty()) {
                log.info("异常升级检查完成, 共升级{}条", unhandled.size());
            }
        } catch (Exception e) {
            log.error("异常升级定时任务执行失败", e);
        }
    }
}
