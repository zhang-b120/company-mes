package com.aimes.modules.dashboard.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.inventory.service.BizInvHistoryService;
import com.aimes.modules.inventory.service.InventoryService;
import com.aimes.modules.order.service.BizSalesOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 大屏专用数据接口（无需登录，只读聚合展示）
 */
@RestController
@RequestMapping("/api/tv")
@RequiredArgsConstructor
public class TvController {

    private final BizSalesOrderService salesOrderService;
    private final InventoryService inventoryService;
    private final BizInvHistoryService invHistoryService;

    /** 销售订单分页（大屏用，支持 deliveryDateRange 过滤） */
    @GetMapping("/sales-orders")
    public R<PageResult<Map<String, Object>>> salesOrders(
            @RequestParam(required = false) String deliveryDateRange,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize) {
        return R.ok(salesOrderService.getPageAdvanced(
                null, null, null, null, null, null,
                null, null, null, null, null,
                deliveryDateRange,
                null, null, null,
                pageNum, pageSize));
    }

    /** 库存分页（大屏用） */
    @GetMapping("/inventory")
    public R<PageResult<Map<String, Object>>> inventory(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "200") int pageSize) {
        String warehouseIds = warehouseId != null ? String.valueOf(warehouseId) : null;
        return R.ok(inventoryService.getPage(null, null, null, warehouseIds, category, null, pageNum, pageSize));
    }

    /** 库存历史（大屏用，用于今日入库统计） */
    @GetMapping("/inv-history")
    public R<PageResult<Map<String, Object>>> invHistory(
            @RequestParam(required = false) String dateStart,
            @RequestParam(required = false) String dateEnd,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String invType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "200") int pageSize) {
        return R.ok(invHistoryService.getPage(null, null, null, invType, direction, null, null,
                null, null, null, dateStart, dateEnd, pageNum, pageSize));
    }
}
