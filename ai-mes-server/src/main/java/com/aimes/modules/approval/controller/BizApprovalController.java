package com.aimes.modules.approval.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.approval.service.BizApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/approval")
@RequiredArgsConstructor
public class BizApprovalController {

    private final BizApprovalService approvalService;

    @PostMapping("/start")
    public R<Long> start(@RequestBody Map<String, Object> body) {
        String businessType = (String) body.get("businessType");
        Long businessId = Long.valueOf(body.get("businessId").toString());
        Long applicantId = SecurityUtil.getCurrentUserId();
        Long instanceId = approvalService.startApproval(businessType, businessId, applicantId);
        return R.ok(instanceId);
    }

    @PutMapping("/{instanceId}/approve")
    public R<Void> approve(@PathVariable Long instanceId, @RequestBody(required = false) Map<String, String> body) {
        Long approverId = SecurityUtil.getCurrentUserId();
        String remark = body != null ? body.get("remark") : null;
        approvalService.approve(instanceId, approverId, remark);
        return R.ok();
    }

    @PutMapping("/{instanceId}/reject")
    public R<Void> reject(@PathVariable Long instanceId, @RequestBody(required = false) Map<String, String> body) {
        Long approverId = SecurityUtil.getCurrentUserId();
        String remark = body != null ? body.get("remark") : null;
        approvalService.reject(instanceId, approverId, remark);
        return R.ok();
    }

    @PutMapping("/{instanceId}/cancel")
    public R<Void> cancel(@PathVariable Long instanceId) {
        Long userId = SecurityUtil.getCurrentUserId();
        approvalService.cancel(instanceId, userId);
        return R.ok();
    }

    @GetMapping("/progress")
    public R<Map<String, Object>> progress(
            @RequestParam String businessType,
            @RequestParam Long businessId) {
        return R.ok(approvalService.getApprovalProgress(businessType, businessId));
    }

    @GetMapping("/my-tasks")
    public R<List<Map<String, Object>>> myTasks() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(approvalService.getMyPendingTasks(userId));
    }

    @GetMapping("/my-tasks/page")
    public R<PageResult<Map<String, Object>>> myTasksPage(
            @RequestParam(defaultValue = "pending") String tab,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(approvalService.getMyApprovalPage(userId, tab, pageNum, pageSize));
    }
}
