package com.aimes.modules.bom.listener;

import com.aimes.common.event.ApprovalCompletedEvent;
import com.aimes.common.event.ApprovalRejectedEvent;
import com.aimes.modules.bom.service.BizBomService;
import com.aimes.modules.system.service.SysNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * BOM 激活审批事件监听:
 *  - 审批通过 → 真正激活版本(设为 active, 旧 active 退为 deprecated)
 *  - 审批驳回 → 版本从 pending_approval 退回 draft
 * business_type 约定: bom_version_activation
 * business_id = bom_version_id
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BomApprovalListener {

    private static final String BUSINESS_TYPE = "bom_version_activation";

    @Lazy
    private final BizBomService bomService;
    private final SysNotificationService notificationService;

    @EventListener
    public void onApprovalCompleted(ApprovalCompletedEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) return;
        Long versionId = event.getBusinessId();
        log.info("BOM 版本 [{}] 审批通过,执行激活", versionId);
        bomService.doActivate(versionId);
    }

    @EventListener
    public void onApprovalRejected(ApprovalRejectedEvent event) {
        log.info("[BOM][listener] 收到驳回事件 businessType={} businessId={}", event.getBusinessType(), event.getBusinessId());
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) return;
        Long versionId = event.getBusinessId();
        log.info("[BOM][listener] 版本 [{}] 审批驳回,调用 markRejected", versionId);
        bomService.markRejected(versionId);
    }
}
