package com.aimes.modules.inventory.task;

import com.aimes.modules.inventory.service.BizInvSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventorySnapshotTask {

    private final BizInvSnapshotService snapshotService;

    /**
     * 每天00:00备份前一天的库存台账
     */
    @Scheduled(cron = "0 */5 0 * * ?")
    public void dailySnapshot() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("开始生成库存快照, date={}", yesterday);
        try {
            snapshotService.createSnapshot(yesterday);
            log.info("库存快照生成完成, date={}", yesterday);
        } catch (Exception e) {
            log.error("库存快照生成失败: {}", e.getMessage(), e);
        }
    }
}
