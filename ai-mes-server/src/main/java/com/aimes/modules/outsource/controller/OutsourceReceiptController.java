package com.aimes.modules.outsource.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.outsource.service.OutsourceReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/outsource/receipts")
@RequiredArgsConstructor
public class OutsourceReceiptController {

    private final OutsourceReceiptService receiptService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String receiptNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String qcStatus,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(receiptService.getPage(receiptNo, orderNo, supplierName, productName, status, qcStatus, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(receiptService.getDetail(id));
    }

    @PostMapping("/{id}/receive")
    public R<Void> receive(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long warehouseId = Long.valueOf(body.get("warehouseId").toString());
        boolean needQc = Boolean.parseBoolean(body.getOrDefault("needQc", false).toString());
        if (needQc) {
            receiptService.receiveWithQc(id, warehouseId);
        } else {
            receiptService.receiveWithoutQc(id, warehouseId);
        }
        return R.ok();
    }

    @PutMapping("/{id}/close")
    public R<Void> close(@PathVariable Long id) {
        receiptService.closeReceipt(id);
        return R.ok();
    }

    @GetMapping("/{id}/qc-tasks")
    public R<List<Map<String, Object>>> qcTasks(@PathVariable Long id) {
        return R.ok(receiptService.getRelatedQcTasks(id));
    }

    @GetMapping("/{id}/inv-tasks")
    public R<List<Map<String, Object>>> invTasks(@PathVariable Long id) {
        return R.ok(receiptService.getRelatedInvTasks(id));
    }
}
