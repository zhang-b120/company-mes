package com.aimes.modules.workreport.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.workreport.entity.BizExceptionReport;
import com.aimes.modules.workreport.entity.BizTeamReportDetail;
import com.aimes.modules.workreport.entity.BizTeamWorkReport;
import com.aimes.modules.workreport.entity.BizWorkReport;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.workreport.service.ExceptionReportService;
import com.aimes.modules.workreport.service.WorkReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 报工与异常上报控制器
 * 报工接口: /api/workreport/personal/...
 * 异常接口: /api/workreport/exceptions/...
 */
@RestController
@RequestMapping("/api/workreport")
@RequiredArgsConstructor
public class WorkReportController {

    private final WorkReportService workReportService;
    private final ExceptionReportService exceptionReportService;

    // ==================== 报工管理 ====================

    @GetMapping("/my-tasks")
    public R<List<Map<String, Object>>> getMyTasks(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status) {
        Long uid = userId != null ? userId : SecurityUtil.getCurrentUserId();
        return R.ok(workReportService.getMyTasks(uid, status));
    }

    @GetMapping("/personal/page")
    public R<PageResult<Map<String, Object>>> getReportPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long workOrderId,
            @RequestParam(required = false) String woNo,
            @RequestParam(required = false) String processName,
            @RequestParam(required = false) String reporterName,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(workReportService.getPage(keyword, status, workOrderId, woNo, processName, reporterName, pageNum, pageSize));
    }

    @PostMapping("/personal")
    public R<Void> submitPersonalReport(@RequestBody BizWorkReport report) {
        workReportService.submitPersonalReport(report);
        return R.ok();
    }

    @GetMapping("/personal/{id}")
    public R<BizWorkReport> getReportDetail(@PathVariable Long id) {
        return R.ok(workReportService.getById(id));
    }

    @GetMapping("/personal/{id}/full")
    public R<Map<String, Object>> getReportFullDetail(@PathVariable Long id) {
        return R.ok(workReportService.getFullDetail(id));
    }

    @PutMapping("/personal/{id}/reject")
    public R<Void> rejectReport(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null && body.get("reason") != null ? body.get("reason").toString() : null;
        workReportService.reject(id, reason);
        return R.ok();
    }

    // ==================== 工位机报工 ====================

    /**
     * 工位机刷卡：根据员工账号查询待报工任务
     * 班组任务仅组长可见，普通成员不展示
     */
    @GetMapping("/workstation/tasks")
    public R<Map<String, Object>> getWorkstationTasks(@RequestParam String username) {
        return R.ok(workReportService.getWorkstationTasks(username));
    }

    // ==================== 班组报工 ====================

    @PostMapping("/team")
    public R<Void> submitTeamReport(@RequestBody Map<String, Object> body) {
        BizTeamWorkReport teamReport = new BizTeamWorkReport();
        teamReport.setTaskId(Long.valueOf(body.get("taskId").toString()));
        teamReport.setWorkOrderId(Long.valueOf(body.get("workOrderId").toString()));
        teamReport.setTeamId(Long.valueOf(body.get("teamId").toString()));
        teamReport.setLeaderId(Long.valueOf(body.get("leaderId").toString()));
        teamReport.setQualifiedQty(new java.math.BigDecimal(body.get("qualifiedQty").toString()));
        teamReport.setDefectQty(body.get("defectQty") != null
                ? new java.math.BigDecimal(body.get("defectQty").toString()) : java.math.BigDecimal.ZERO);
        teamReport.setTotalWage(new java.math.BigDecimal(body.get("totalWage").toString()));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detailMaps = (List<Map<String, Object>>) body.get("details");
        List<BizTeamReportDetail> details = new java.util.ArrayList<>();
        if (detailMaps != null) {
            for (Map<String, Object> dm : detailMaps) {
                BizTeamReportDetail d = new BizTeamReportDetail();
                d.setUserId(Long.valueOf(dm.get("userId").toString()));
                d.setWeight(new java.math.BigDecimal(dm.get("weight").toString()));
                details.add(d);
            }
        }
        workReportService.submitTeamReport(teamReport, details);
        return R.ok();
    }

    // ==================== 异常上报 ====================

    @GetMapping("/exceptions/page")
    public R<PageResult<Map<String, Object>>> getExceptionPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String exceptionType,
            @RequestParam(required = false) String urgency,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(exceptionReportService.getPageWithUrgency(keyword, status, exceptionType, urgency, pageNum, pageSize));
    }

    @PostMapping("/exceptions")
    public R<Void> createException(@RequestBody BizExceptionReport report) {
        exceptionReportService.create(report);
        return R.ok();
    }

    @PutMapping("/exceptions/{id}/handle")
    public R<Void> handleException(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long handlerId = Long.valueOf(body.get("handlerId").toString());
        String handleResult = (String) body.get("handleResult");
        exceptionReportService.handle(id, handlerId, handleResult);
        return R.ok();
    }
}
