package com.aimes.modules.inventory.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.inventory.service.BizInvHistoryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api/inventory/history")
@RequiredArgsConstructor
public class BizInvHistoryController {

    private final BizInvHistoryService historyService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> getPage(
            @RequestParam(required = false) String historyNo,
            @RequestParam(required = false) String taskNo,
            @RequestParam(required = false) String relatedOrderNo,
            @RequestParam(required = false) String invType,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String materialNo,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String spec,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String dateStart,
            @RequestParam(required = false) String dateEnd,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(historyService.getPage(historyNo, taskNo, relatedOrderNo, invType, direction, status,
                materialId, materialNo, materialName, spec, categoryCode, dateStart, dateEnd, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> getDetail(@PathVariable Long id) {
        return R.ok(historyService.getDetail(id));
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) String invType,
                       @RequestParam(required = false) String direction,
                       @RequestParam(required = false) String materialNo,
                       @RequestParam(required = false) String materialName,
                       @RequestParam(required = false) String spec,
                       @RequestParam(required = false) String relatedOrderNo,
                       @RequestParam(required = false) String historyNo,
                       @RequestParam(required = false) String taskNo,
                       @RequestParam(required = false) String dateStart,
                       @RequestParam(required = false) String dateEnd,
                       @RequestParam(required = false) String status) throws IOException {
        PageResult<Map<String, Object>> page = historyService.getPage(
                historyNo, taskNo, relatedOrderNo, invType, direction, status,
                null, materialNo, materialName, spec, null, dateStart, dateEnd, 1, 10000);

        List<String> headers = List.of("历史单号", "出入库类型", "关联单据号", "物料编号", "物料名称",
                "规格", "单位", "数量", "辅助单位", "辅助数量", "批次号", "仓库", "库位",
                "状态", "申请人", "操作人", "操作时间");

        Map<String, String> typeMap = Map.ofEntries(
                Map.entry("purchase_in", "采购入库"), Map.entry("production_in", "生产入库"),
                Map.entry("semi_in", "半成品入库"), Map.entry("outsource_in", "外协入库"),
                Map.entry("return_in", "退货入库"), Map.entry("other_in", "其他入库"),
                Map.entry("production_out", "生产领料"), Map.entry("sales_out", "销售发货"),
                Map.entry("outsource_out", "外协发料"), Map.entry("scrap_out", "报废出库"),
                Map.entry("other_out", "其他出库"), Map.entry("transfer_out", "调拨出库"),
                Map.entry("transfer_in", "调拨入库"), Map.entry("stocktaking_in", "盘点入库"),
                Map.entry("stocktaking_out", "盘点出库"), Map.entry("purchase_return", "采购退货"),
                Map.entry("production_return", "生产退料"));

        List<Function<Map<String, Object>, Object>> extractors = List.of(
                m -> m.get("historyNo"),
                m -> typeMap.getOrDefault(String.valueOf(m.getOrDefault("invType", "")), String.valueOf(m.getOrDefault("invType", ""))),
                m -> m.get("relatedOrderNo"),
                m -> m.get("materialNo"),
                m -> m.get("materialName"),
                m -> m.get("spec"),
                m -> m.get("unit"),
                m -> m.get("quantity"),
                m -> m.get("secondaryUnit"),
                m -> m.get("secondaryQty"),
                m -> m.get("batchNo"),
                m -> m.get("warehouseName"),
                m -> m.get("locationName"),
                m -> "normal".equals(String.valueOf(m.getOrDefault("status", ""))) ? "正常" : "已撤销",
                m -> m.get("applicantName"),
                m -> m.get("operatorName"),
                m -> m.get("createTime")
        );

        ExcelUtil.export(response, "出入库历史", headers, page.getRecords(), extractors);
    }

    @PutMapping("/{id}/revoke")
    public R<Void> revoke(@PathVariable Long id) {
        historyService.revoke(id);
        return R.ok();
    }

    @PutMapping("/items/{itemId}/revoke")
    public R<Void> revokeItem(@PathVariable Long itemId) {
        historyService.revokeItem(itemId);
        return R.ok();
    }
}
