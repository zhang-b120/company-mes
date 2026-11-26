package com.aimes.modules.purchase.service;

import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizSupplier;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizSupplierMapper;
import com.aimes.modules.purchase.entity.BizPurchaseOrder;
import com.aimes.modules.purchase.entity.BizPurchaseOrderItem;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderItemMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseAnalysisService {

    private final BizPurchaseOrderMapper orderMapper;
    private final BizPurchaseOrderItemMapper orderItemMapper;
    private final BizSupplierMapper supplierMapper;
    private final BizMaterialMapper materialMapper;

    private static final List<String> RECEIVED_STATUSES = List.of("confirmed", "closed");

    /**
     * 按供应商维度分析
     */
    public List<Map<String, Object>> analyzeBySupplier(LocalDateTime dateStart, LocalDateTime dateEnd, Long supplierId) {
        List<BizPurchaseOrder> orders = getOrders(dateStart, dateEnd, supplierId);
        if (orders.isEmpty()) return Collections.emptyList();

        List<Long> orderIds = orders.stream().map(BizPurchaseOrder::getId).collect(Collectors.toList());
        List<BizPurchaseOrderItem> allItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .in(BizPurchaseOrderItem::getPurchaseOrderId, orderIds));

        // 供应商名称 Map
        Set<Long> sids = orders.stream().map(BizPurchaseOrder::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> sNameMap = sids.isEmpty() ? Map.of() :
                supplierMapper.selectBatchIds(sids).stream()
                        .collect(Collectors.toMap(BizSupplier::getId, BizSupplier::getSupplierName, (a, b) -> a));

        // 物料信息 Map
        Set<Long> mids = allItems.stream().map(BizPurchaseOrderItem::getMaterialId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizMaterial> materialMap = mids.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(mids).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        // 按供应商分组订单
        Map<Long, List<BizPurchaseOrder>> ordersBySup = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getSupplierId() != null ? o.getSupplierId() : 0L));

        List<Map<String, Object>> result = new ArrayList<>();
        ordersBySup.forEach((sid, supOrders) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("supplierId", sid);
            row.put("supplierName", sNameMap.getOrDefault(sid, "未知供应商"));
            row.put("orderCount", supOrders.size());

            // 该供应商的所有采购明细
            Set<Long> supOrderIds = supOrders.stream().map(BizPurchaseOrder::getId).collect(Collectors.toSet());
            List<BizPurchaseOrderItem> supItems = allItems.stream()
                    .filter(item -> supOrderIds.contains(item.getPurchaseOrderId()))
                    .collect(Collectors.toList());

            BigDecimal totalAmount = supItems.stream()
                    .map(i -> i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalQty = supItems.stream()
                    .map(i -> i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avgUnitPrice = totalQty.compareTo(BigDecimal.ZERO) > 0
                    ? totalAmount.divide(totalQty, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            row.put("totalAmount", totalAmount);
            row.put("totalQty", totalQty);
            row.put("avgUnitPrice", avgUnitPrice);

            // 按物料汇总明细
            Map<Long, List<BizPurchaseOrderItem>> itemsByMaterial = supItems.stream()
                    .collect(Collectors.groupingBy(item -> item.getMaterialId() != null ? item.getMaterialId() : 0L));

            row.put("itemCount", itemsByMaterial.size());

            List<Map<String, Object>> materialDetails = new ArrayList<>();
            itemsByMaterial.forEach((mid, mitems) -> {
                BizMaterial mat = materialMap.get(mid);
                Map<String, Object> md = new LinkedHashMap<>();
                md.put("materialId", mid);
                md.put("materialName", mat != null ? mat.getMaterialName() : "");
                md.put("spec", mat != null ? mat.getSpec() : "");
                md.put("unit", mat != null ? mat.getUnit() : "");
                BigDecimal qty = mitems.stream().map(i -> i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal amt = mitems.stream().map(i -> i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal avgPrice = qty.compareTo(BigDecimal.ZERO) > 0 ? amt.divide(qty, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                md.put("totalQty", qty);
                md.put("totalAmount", amt);
                md.put("avgUnitPrice", avgPrice);
                materialDetails.add(md);
            });
            row.put("materialDetails", materialDetails);
            result.add(row);
        });

        result.sort((a, b) -> ((BigDecimal) b.get("totalAmount")).compareTo((BigDecimal) a.get("totalAmount")));
        return result;
    }

    /**
     * 按物料维度分析
     */
    public List<Map<String, Object>> analyzeByMaterial(LocalDateTime dateStart, LocalDateTime dateEnd, Long materialId) {
        List<BizPurchaseOrder> orders = getOrders(dateStart, dateEnd, null);
        if (orders.isEmpty()) return Collections.emptyList();

        List<Long> orderIds = orders.stream().map(BizPurchaseOrder::getId).collect(Collectors.toList());
        LambdaQueryWrapper<BizPurchaseOrderItem> itemWrapper = new LambdaQueryWrapper<BizPurchaseOrderItem>()
                .in(BizPurchaseOrderItem::getPurchaseOrderId, orderIds);
        if (materialId != null) {
            itemWrapper.eq(BizPurchaseOrderItem::getMaterialId, materialId);
        }
        List<BizPurchaseOrderItem> allItems = orderItemMapper.selectList(itemWrapper);
        if (allItems.isEmpty()) return Collections.emptyList();

        Map<Long, BizPurchaseOrder> orderMap = orders.stream()
                .collect(Collectors.toMap(BizPurchaseOrder::getId, o -> o, (a, b) -> a));

        // 物料信息 Map
        Set<Long> mids = allItems.stream().map(BizPurchaseOrderItem::getMaterialId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizMaterial> materialInfoMap = mids.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(mids).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        // 供应商名称 Map
        Set<Long> sids = orders.stream().map(BizPurchaseOrder::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> sNameMap = sids.isEmpty() ? Map.of() :
                supplierMapper.selectBatchIds(sids).stream()
                        .collect(Collectors.toMap(BizSupplier::getId, BizSupplier::getSupplierName, (a, b) -> a));

        // 按物料分组
        Map<Long, List<BizPurchaseOrderItem>> itemsByMaterial = allItems.stream()
                .collect(Collectors.groupingBy(item -> item.getMaterialId() != null ? item.getMaterialId() : 0L));

        List<Map<String, Object>> result = new ArrayList<>();
        itemsByMaterial.forEach((mid, mitems) -> {
            BizMaterial mat = materialInfoMap.get(mid);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialId", mid);
            row.put("materialName", mat != null ? mat.getMaterialName() : "");
            row.put("spec", mat != null ? mat.getSpec() : "");
            row.put("unit", mat != null ? mat.getUnit() : "");

            BigDecimal totalQty = mitems.stream().map(i -> i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalAmt = mitems.stream().map(i -> i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avgPrice = totalQty.compareTo(BigDecimal.ZERO) > 0 ? totalAmt.divide(totalQty, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            row.put("totalQty", totalQty);
            row.put("totalAmount", totalAmt);
            row.put("avgUnitPrice", avgPrice);

            // 按供应商汇总来源
            Map<Long, List<BizPurchaseOrderItem>> itemsBySup = mitems.stream().collect(Collectors.groupingBy(item -> {
                BizPurchaseOrder o = orderMap.get(item.getPurchaseOrderId());
                return o != null && o.getSupplierId() != null ? o.getSupplierId() : 0L;
            }));

            row.put("supplierCount", itemsBySup.size());
            // 总订单数（同一订单在不同物料中可能重复，这里按 purchaseOrderId 去重）
            long orderCount = mitems.stream().map(BizPurchaseOrderItem::getPurchaseOrderId).filter(Objects::nonNull).distinct().count();
            row.put("orderCount", orderCount);

            List<Map<String, Object>> supplierDetails = new ArrayList<>();
            itemsBySup.forEach((sid, sitems) -> {
                Map<String, Object> sd = new LinkedHashMap<>();
                sd.put("supplierId", sid);
                sd.put("supplierName", sNameMap.getOrDefault(sid, "未知供应商"));
                long sdOrderCount = sitems.stream().map(BizPurchaseOrderItem::getPurchaseOrderId).filter(Objects::nonNull).distinct().count();
                sd.put("orderCount", sdOrderCount);
                BigDecimal sdQty = sitems.stream().map(i -> i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal sdAmt = sitems.stream().map(i -> i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal sdAvg = sdQty.compareTo(BigDecimal.ZERO) > 0 ? sdAmt.divide(sdQty, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                sd.put("totalQty", sdQty);
                sd.put("avgUnitPrice", sdAvg);
                sd.put("totalAmount", sdAmt);
                supplierDetails.add(sd);
            });
            row.put("supplierDetails", supplierDetails);
            result.add(row);
        });

        result.sort((a, b) -> ((BigDecimal) b.get("totalAmount")).compareTo((BigDecimal) a.get("totalAmount")));
        return result;
    }

    /**
     * 导出 Excel
     */
    public byte[] exportExcel(String dimension, LocalDateTime dateStart, LocalDateTime dateEnd, Long filterId) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            if ("supplier".equals(dimension)) {
                List<Map<String, Object>> data = analyzeBySupplier(dateStart, dateEnd, filterId);
                Sheet sheet = wb.createSheet("供应商维度分析");
                createSupplierSheet(sheet, data);
            } else {
                List<Map<String, Object>> data = analyzeByMaterial(dateStart, dateEnd, filterId);
                Sheet sheet = wb.createSheet("物料维度分析");
                createMaterialSheet(sheet, data);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void createSupplierSheet(Sheet sheet, List<Map<String, Object>> data) {
        Row header = sheet.createRow(0);
        String[] cols = {"供应商名称", "订单数", "品种数", "采购总数量", "综合均价", "采购总金额"};
        for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);
        int rowIdx = 1;
        for (Map<String, Object> row : data) {
            Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(String.valueOf(row.get("supplierName")));
            r.createCell(1).setCellValue(((Number) row.get("orderCount")).doubleValue());
            r.createCell(2).setCellValue(((Number) row.get("itemCount")).doubleValue());
            r.createCell(3).setCellValue(((BigDecimal) row.get("totalQty")).doubleValue());
            r.createCell(4).setCellValue(((BigDecimal) row.get("avgUnitPrice")).doubleValue());
            r.createCell(5).setCellValue(((BigDecimal) row.get("totalAmount")).doubleValue());
        }
    }

    private void createMaterialSheet(Sheet sheet, List<Map<String, Object>> data) {
        Row header = sheet.createRow(0);
        String[] cols = {"物料名称", "规格", "单位", "供应商数", "订单数", "采购总数量", "综合均价", "采购总金额"};
        for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);
        int rowIdx = 1;
        for (Map<String, Object> row : data) {
            Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(String.valueOf(row.getOrDefault("materialName", "")));
            r.createCell(1).setCellValue(String.valueOf(row.getOrDefault("spec", "")));
            r.createCell(2).setCellValue(String.valueOf(row.getOrDefault("unit", "")));
            r.createCell(3).setCellValue(((Number) row.get("supplierCount")).doubleValue());
            r.createCell(4).setCellValue(((Number) row.get("orderCount")).doubleValue());
            r.createCell(5).setCellValue(((BigDecimal) row.get("totalQty")).doubleValue());
            r.createCell(6).setCellValue(((BigDecimal) row.get("avgUnitPrice")).doubleValue());
            r.createCell(7).setCellValue(((BigDecimal) row.get("totalAmount")).doubleValue());
        }
    }

    private List<BizPurchaseOrder> getOrders(LocalDateTime dateStart, LocalDateTime dateEnd, Long supplierId) {
        LambdaQueryWrapper<BizPurchaseOrder> wrapper = new LambdaQueryWrapper<BizPurchaseOrder>()
                .in(BizPurchaseOrder::getStatus, RECEIVED_STATUSES)
                .eq(supplierId != null, BizPurchaseOrder::getSupplierId, supplierId)
                .ge(dateStart != null, BizPurchaseOrder::getApprovedAt, dateStart)
                .le(dateEnd != null, BizPurchaseOrder::getApprovedAt, dateEnd);
        return orderMapper.selectList(wrapper);
    }
}
