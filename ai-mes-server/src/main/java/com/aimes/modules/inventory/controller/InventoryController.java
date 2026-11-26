package com.aimes.modules.inventory.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.inventory.entity.BizInventory;
import com.aimes.modules.inventory.service.InventoryService;
import com.aimes.modules.inventory.service.ProductionStockInService;
import com.aimes.modules.inventory.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final ReservationService reservationService;
    private final ProductionStockInService productionStockInService;

    /**
     * 分页查询库存
     */
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> getPage(
            @RequestParam(required = false) String materialNo,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String spec,
            @RequestParam(required = false) String warehouseIds,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String stockStatus,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(inventoryService.getPage(materialNo, materialName, spec, warehouseIds, category, stockStatus, pageNum, pageSize));
    }

    /**
     * 导出库存台账（最多导出 10000 条）
     */
    @GetMapping("/export")
    public void exportInventory(
            @RequestParam(required = false) String materialNo,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String spec,
            @RequestParam(required = false) String warehouseIds,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String stockStatus,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        PageResult<Map<String, Object>> data = inventoryService.getPage(
                materialNo, materialName, spec, warehouseIds, category, stockStatus, 1, 10000);
        java.util.List<String> headers = java.util.List.of(
                "物料编码", "物料名称", "规格", "单位", "仓库", "总数量", "锁定数量", "可用数量", "最小库存", "最大库存");
        java.util.List<java.util.function.Function<Map<String, Object>, Object>> extractors = java.util.List.of(
                r -> r.get("materialNo"), r -> r.get("materialName"), r -> r.get("spec"), r -> r.get("unit"),
                r -> r.get("warehouseName"), r -> r.get("totalQty"), r -> r.get("lockedQty"), r -> r.get("availableQty"),
                r -> r.get("minStock"), r -> r.get("maxStock"));
        com.aimes.common.utils.ExcelUtil.export(response, "库存台账", headers, data.getRecords(), extractors);
    }

    /**
     * 查询出入库记录（分页）
     */
    @GetMapping("/transactions")
    public R<PageResult<Map<String, Object>>> getTransactions(
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String materialName,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(inventoryService.getTransactions(materialId, warehouseId, type, keyword, materialName, pageNum, pageSize));
    }

    @GetMapping("/reservations/page")
    public R<PageResult<Map<String, Object>>> getReservationPage(
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String reservationType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(reservationService.getPage(materialId, keyword, reservationType, status, pageNum, pageSize));
    }

    /**
     * 按物料 + 仓库 查实时可用库存(已扣 pending 出库任务预占)
     * 用于领料/发货/外协等弹窗切换仓库后即时刷新该行的可用量,避免显示默认仓数字与实际所选仓不一致
     * warehouseId 缺省 → 返回该物料全仓汇总
     */
    @GetMapping("/available")
    public R<java.math.BigDecimal> getAvailable(@RequestParam Long materialId,
                                                @RequestParam(required = false) Long warehouseId) {
        java.math.BigDecimal v = warehouseId != null
                ? inventoryService.getAvailableQty(materialId, warehouseId)
                : inventoryService.getTotalAvailableQty(materialId);
        return R.ok(v != null ? v : java.math.BigDecimal.ZERO);
    }

    /**
     * 按物料 + 仓库 查在库 + 可用库存
     * 仓库 dropdown 切换时一次拿到 totalQty / availableQty,避免发两次请求
     * warehouseId 缺省 → 全仓汇总
     */
    @GetMapping("/stock")
    public R<Map<String, Object>> getStock(@RequestParam Long materialId,
                                            @RequestParam(required = false) Long warehouseId) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (warehouseId != null) {
            com.aimes.modules.inventory.entity.BizInventory inv =
                    inventoryService.getOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.aimes.modules.inventory.entity.BizInventory>()
                            .eq(com.aimes.modules.inventory.entity.BizInventory::getMaterialId, materialId)
                            .eq(com.aimes.modules.inventory.entity.BizInventory::getWarehouseId, warehouseId));
            result.put("totalQty", inv != null && inv.getTotalQty() != null ? inv.getTotalQty() : java.math.BigDecimal.ZERO);
            result.put("availableQty", inventoryService.getAvailableQty(materialId, warehouseId));
        } else {
            Map<Long, Map<String, Object>> sum = inventoryService.getMaterialStockSummary(java.util.List.of(materialId));
            Map<String, Object> m = sum.getOrDefault(materialId, java.util.Map.of("totalQty", java.math.BigDecimal.ZERO, "availableQty", java.math.BigDecimal.ZERO));
            result.put("totalQty", m.getOrDefault("totalQty", java.math.BigDecimal.ZERO));
            result.put("availableQty", inventoryService.getTotalAvailableQty(materialId));
        }
        return R.ok(result);
    }

    /**
     * 批量查询物料库存汇总（用于物料档案列表展示当前库存/可用库存）
     */
    @GetMapping("/material-stocks")
    public R<Map<Long, Map<String, Object>>> getMaterialStocks(@RequestParam String materialIds) {
        List<Long> ids = Arrays.stream(materialIds.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::parseLong).collect(java.util.stream.Collectors.toList());
        return R.ok(inventoryService.getMaterialStockSummary(ids));
    }

    /**
     * 库存预警列表
     */
    @GetMapping("/alerts")
    public R<List<BizInventory>> getAlerts() {
        return R.ok(inventoryService.getAlerts());
    }

    /**
     * 入库
     */
    @PostMapping("/stock-in")
    public R<Void> stockIn(@RequestBody Map<String, Object> body) {
        Long materialId = Long.valueOf(body.get("materialId").toString());
        Long warehouseId = Long.valueOf(body.get("warehouseId").toString());
        BigDecimal qty = getBigDecimal(body, "qty", "quantity");
        String type = getString(body, "type", "transactionType");
        String batchNo = body.get("batchNo") != null ? body.get("batchNo").toString() : null;
        String relatedType = getString(body, "relatedType", "relatedOrderType");
        Long relatedId = getLong(body, "relatedId", "relatedOrderId");
        String secondaryUnit = getString(body, "secondaryUnit");
        BigDecimal secondaryQty = getBigDecimal(body, "secondaryQty", "secondaryQuantity");
        inventoryService.stockIn(materialId, warehouseId, qty, type, batchNo, relatedType, relatedId, secondaryUnit, secondaryQty);
        return R.ok();
    }

    /**
     * 出库
     */
    @PostMapping("/stock-out")
    public R<Void> stockOut(@RequestBody Map<String, Object> body) {
        Long materialId = Long.valueOf(body.get("materialId").toString());
        Long warehouseId = Long.valueOf(body.get("warehouseId").toString());
        BigDecimal qty = getBigDecimal(body, "qty", "quantity");
        String type = getString(body, "type", "transactionType");
        String batchNo = body.get("batchNo") != null ? body.get("batchNo").toString() : null;
        String relatedType = getString(body, "relatedType", "relatedOrderType");
        Long relatedId = getLong(body, "relatedId", "relatedOrderId");
        String secondaryUnit = getString(body, "secondaryUnit");
        BigDecimal secondaryQty = getBigDecimal(body, "secondaryQty", "secondaryQuantity");
        inventoryService.stockOut(materialId, warehouseId, qty, type, batchNo, relatedType, relatedId, secondaryUnit, secondaryQty);
        return R.ok();
    }

    /**
     * 锁定库存
     */
    @PostMapping("/lock")
    public R<Void> lockStock(@RequestBody Map<String, Object> body) {
        Long materialId = Long.valueOf(body.get("materialId").toString());
        Long warehouseId = Long.valueOf(body.get("warehouseId").toString());
        BigDecimal qty = new BigDecimal(body.get("qty").toString());
        String type = body.get("type") != null ? body.get("type").toString() : null;
        String relatedType = body.get("relatedType") != null ? body.get("relatedType").toString() : null;
        Long relatedId = body.get("relatedId") != null ? Long.valueOf(body.get("relatedId").toString()) : null;
        String relatedOrderNo = body.get("relatedOrderNo") != null ? body.get("relatedOrderNo").toString() : null;
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;
        inventoryService.lockStock(materialId, warehouseId, qty, type, relatedType, relatedId, relatedOrderNo, reason, null);
        return R.ok();
    }

    /**
     * 释放库存锁定
     */
    @PostMapping("/{reservationId}/unlock")
    public R<Void> unlockStock(@PathVariable Long reservationId) {
        inventoryService.unlockStock(reservationId);
        return R.ok();
    }

    /**
     * 待入库工单列表（成品质检通过，待仓库确认入库）
     */
    @GetMapping("/pending-stock-in")
    public R<List<Map<String, Object>>> getPendingStockIn() {
        return R.ok(productionStockInService.getPendingStockIn());
    }

    /**
     * 确认成品入库
     */
    @PostMapping("/confirm-production-in")
    public R<Void> confirmProductionIn(@RequestBody Map<String, Object> body) {
        Long workOrderId = Long.valueOf(body.get("workOrderId").toString());
        Long warehouseId = Long.valueOf(body.get("warehouseId").toString());
        BigDecimal qty = new BigDecimal(body.get("qty").toString());
        String batchNo = body.get("batchNo") != null ? body.get("batchNo").toString() : null;
        productionStockInService.confirmStockIn(workOrderId, warehouseId, qty, batchNo);
        return R.ok();
    }

    private String getString(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private Long getLong(Map<String, Object> body, String... keys) {
        String value = getString(body, keys);
        return value != null ? Long.valueOf(value) : null;
    }

    private BigDecimal getBigDecimal(Map<String, Object> body, String... keys) {
        String value = getString(body, keys);
        return value != null ? new BigDecimal(value) : null;
    }
}
