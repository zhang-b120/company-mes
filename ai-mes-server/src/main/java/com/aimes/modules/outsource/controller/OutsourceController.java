package com.aimes.modules.outsource.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.outsource.entity.BizOutsourceRequest;
import com.aimes.modules.outsource.service.OutsourceRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/outsource/requests")
@RequiredArgsConstructor
public class OutsourceController {

    private final OutsourceRequestService requestService;

    /**
     * 分页查询外协申请
     */
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> getPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String workOrderNo,
            @RequestParam(required = false) String stepName,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(requestService.getPage(keyword, status, workOrderNo, stepName, productId, startDate, endDate, pageNum, pageSize));
    }

    /**
     * 创建外协申请
     */
    @PostMapping
    public R<Void> create(@RequestBody BizOutsourceRequest request) {
        requestService.create(request);
        return R.ok();
    }

    /**
     * 审批通过（填写供应商、单价、税率，自动创建外协加工单）
     */
    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        if (body == null) body = Map.of();
        Long approvedBy = body.get("approvedBy") != null ? Long.valueOf(body.get("approvedBy").toString()) : null;
        String approveRemark = body.get("approveRemark") != null ? body.get("approveRemark").toString() : null;
        Long supplierId = body.get("supplierId") != null ? Long.valueOf(body.get("supplierId").toString()) : null;
        java.math.BigDecimal unitPrice = body.get("unitPrice") != null ? new java.math.BigDecimal(body.get("unitPrice").toString()) : null;
        java.math.BigDecimal taxRate = body.get("taxRate") != null ? new java.math.BigDecimal(body.get("taxRate").toString()) : null;
        java.time.LocalDate deliveryDate = body.get("deliveryDate") != null && !body.get("deliveryDate").toString().isEmpty()
                ? java.time.LocalDate.parse(body.get("deliveryDate").toString()) : null;
        requestService.approve(id, approvedBy, approveRemark, supplierId, unitPrice, taxRate, deliveryDate);
        return R.ok();
    }

    /**
     * 审批驳回
     */
    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        if (body == null) body = Map.of();
        Long approvedBy = body.get("approvedBy") != null ? Long.valueOf(body.get("approvedBy").toString()) : null;
        String approveRemark = body.get("approveRemark") != null ? body.get("approveRemark").toString() : null;
        requestService.reject(id, approvedBy, approveRemark);
        return R.ok();
    }
}
