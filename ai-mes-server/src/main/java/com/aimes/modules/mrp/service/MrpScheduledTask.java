package com.aimes.modules.mrp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * MRP定时运算任务
 * 运行频率通过配置文件控制
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MrpScheduledTask {

    private final BizMrpService mrpService;

    @Value("${mrp.schedule.enabled:false}")
    private boolean enabled;

    /**
     * 定时MRP运算
     * 默认每天凌晨2:00运行，通过配置文件覆盖
     */
    @Scheduled(cron = "${mrp.schedule.cron:0 0 2 * * ?}")
    public void scheduledMrpRun() {
        if (!enabled) {
            log.debug("MRP定时任务未启用");
            return;
        }

        log.info("MRP定时运算开始...");
        try {
            Long runId = mrpService.runScheduledMrp();
            log.info("MRP定时运算完成, runId={}", runId);
        } catch (Exception e) {
            log.error("MRP定时运算异常: {}", e.getMessage(), e);
        }
    }
}
