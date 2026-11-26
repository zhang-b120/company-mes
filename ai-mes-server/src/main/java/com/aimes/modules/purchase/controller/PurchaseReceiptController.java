package com.aimes.modules.purchase.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.purchase.service.PurchaseReceiptService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase/receipts")
@RequiredArgsConstructor
public class PurchaseReceiptController {

    private final PurchaseReceiptService receiptService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String receiptNo,
            @RequestParam(required = false) String poNo,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String qcStatus,
            @RequestParam(required = false) String materialName,
            @RequestParam(defaultValue = "false") boolean grouped,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (grouped) {
            return R.ok(receiptService.getGroupedPage(receiptNo, poNo, supplierName, status, qcStatus, materialName, pageNum, pageSize));
        }
        return R.ok(receiptService.getPage(receiptNo, poNo, supplierName, status, qcStatus, materialName, pageNum, pageSize));
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String receiptNo,
            @RequestParam(required = false) String poNo,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String qcStatus,
            @RequestParam(required = false) String materialName,
            HttpServletResponse response) throws IOException {
        receiptService.export(receiptNo, poNo, supplierName, status, qcStatus, materialName, response);
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(receiptService.getDetail(id));
    }

    /** 同组所有收料明细 (用于列表折叠展开) */
    @GetMapping("/group/{groupId}")
    public R<java.util.List<Map<String, Object>>> getByGroup(@PathVariable String groupId) {
        return R.ok(receiptService.getByGroup(groupId));
    }

    /** 打印到货对账单 (Excel, 旧路径保留以防外部调用) */
    @GetMapping("/group/{groupId}/print")
    public void printGroup(@PathVariable String groupId, HttpServletResponse response) throws IOException {
        receiptService.printGroupReconciliation(groupId, response);
    }

    /** 打印对账单数据 (JSON, 给 hiprint 前端模板用) */
    @GetMapping("/group/{groupId}/print-data")
    public R<Map<String, Object>> printGroupData(@PathVariable String groupId) {
        return R.ok(receiptService.getGroupPrintData(groupId));
    }

    /**
     * 整组汇总收料: body = { warehouseId: Long, needQc: boolean }
     * 把该批次组下所有待收料的 receipt 一次性收料.
     */
    @PostMapping("/group/{groupId}/batch-receive")
    public R<Integer> batchReceiveByGroup(@PathVariable String groupId, @RequestBody Map<String, Object> body) {
        Object w = body.get("warehouseId");
        Long warehouseId = w != null ? Long.valueOf(w.toString()) : null;
        boolean needQc = Boolean.parseBoolean(body.getOrDefault("needQc", false).toString());
        return R.ok(receiptService.batchReceiveByGroup(groupId, warehouseId, needQc));
    }

    /**
     * 收料操作: body = { warehouseId: Long, needQc: boolean }
     */
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

    /**
     * 办结收料单
     */
    @PutMapping("/{id}/close")
    public R<Void> close(@PathVariable Long id) {
        receiptService.closeReceipt(id);
        return R.ok();
    }

    /**
     * 关联的质检任务列表
     */
    @GetMapping("/{id}/qc-tasks")
    public R<List<Map<String, Object>>> qcTasks(@PathVariable Long id) {
        return R.ok(receiptService.getRelatedQcTasks(id));
    }

    /**
     * 关联的库存任务列表
     */
    @GetMapping("/{id}/inv-tasks")
    public R<List<Map<String, Object>>> invTasks(@PathVariable Long id) {
        return R.ok(receiptService.getRelatedInvTasks(id));
    }
}
