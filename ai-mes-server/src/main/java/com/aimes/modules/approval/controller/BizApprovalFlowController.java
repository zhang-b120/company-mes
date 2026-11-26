package com.aimes.modules.approval.controller;

import com.aimes.common.result.R;
import com.aimes.common.result.PageResult;
import com.aimes.modules.approval.service.BizApprovalFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/approval/flows")
@RequiredArgsConstructor
public class BizApprovalFlowController {

    private final BizApprovalFlowService flowService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String businessType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(flowService.getPage(keyword, businessType, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(flowService.getDetail(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        flowService.createFlow(body);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        flowService.updateFlow(id, body);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        flowService.deleteFlow(id);
        return R.ok();
    }

    @PutMapping("/{id}/toggle")
    public R<Void> toggle(@PathVariable Long id) {
        flowService.toggleEnabled(id);
        return R.ok();
    }
}
