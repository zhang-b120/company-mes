package com.aimes.modules.order.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.inventory.service.BizInvHistoryService;
import com.aimes.modules.master.entity.BizCustomer;
import com.aimes.modules.master.mapper.BizCustomerMapper;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 发货统计 — 基于出入库历史 invType=sales_out 的数据，关联销售订单获取客户名称
 */
@RestController
@RequestMapping("/api/order/shipment-statistics")
@RequiredArgsConstructor
public class ShipmentStatisticsController {

    private final BizInvHistoryService historyService;
    private final BizSalesOrderMapper salesOrderMapper;
    private final BizCustomerMapper customerMapper;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String materialNo,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String dateStart,
            @RequestParam(required = false) String dateEnd,
            @RequestParam(required = false) String applicantName,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {

        // 使用已有的 getPage 方法查询 sales_out 且 status=normal 的数据
        PageResult<Map<String, Object>> page = historyService.getPage(
                null, null, null, "sales_out", null, "normal",
                null, materialNo, materialName, null, dateStart, dateEnd,
                pageNum, pageSize);

        // 对结果做客户名称富化 + 按 customerName / applicantName 过滤
        enrichCustomerName(page.getRecords());

        // 过滤 customerName
        if (StringUtils.hasText(customerName)) {
            List<Map<String, Object>> filtered = page.getRecords().stream()
                    .filter(m -> {
                        String cn = String.valueOf(m.getOrDefault("customerName", ""));
                        return cn.contains(customerName);
                    })
                    .collect(Collectors.toList());
            return R.ok(new PageResult<>(filtered, filtered.size(), pageNum, pageSize));
        }

        // 过滤 applicantName
        if (StringUtils.hasText(applicantName)) {
            List<Map<String, Object>> filtered = page.getRecords().stream()
                    .filter(m -> {
                        String an = String.valueOf(m.getOrDefault("applicantName", ""));
                        return an.contains(applicantName);
                    })
                    .collect(Collectors.toList());
            return R.ok(new PageResult<>(filtered, filtered.size(), pageNum, pageSize));
        }

        return R.ok(page);
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) String customerName,
                       @RequestParam(required = false) String materialNo,
                       @RequestParam(required = false) String materialName,
                       @RequestParam(required = false) String dateStart,
                       @RequestParam(required = false) String dateEnd,
                       @RequestParam(required = false) String applicantName) throws IOException {

        PageResult<Map<String, Object>> page = historyService.getPage(
                null, null, null, "sales_out", null, "normal",
                null, materialNo, materialName, null, dateStart, dateEnd,
                1, 10000);

        enrichCustomerName(page.getRecords());

        List<Map<String, Object>> data = page.getRecords();

        // 过滤
        if (StringUtils.hasText(customerName)) {
            data = data.stream()
                    .filter(m -> String.valueOf(m.getOrDefault("customerName", "")).contains(customerName))
                    .collect(Collectors.toList());
        }
        if (StringUtils.hasText(applicantName)) {
            data = data.stream()
                    .filter(m -> String.valueOf(m.getOrDefault("applicantName", "")).contains(applicantName))
                    .collect(Collectors.toList());
        }

        List<String> headers = List.of("历史单号", "关联单据号", "客户名称", "物料编码", "物料名称",
                "规格", "单位", "数量", "辅助单位", "辅助数量", "仓库", "申请人", "操作人", "发货时间");

        List<Function<Map<String, Object>, Object>> extractors = List.of(
                m -> m.get("historyNo"),
                m -> m.get("relatedOrderNo"),
                m -> m.get("customerName"),
                m -> m.get("materialNo"),
                m -> m.get("materialName"),
                m -> m.get("spec"),
                m -> m.get("unit"),
                m -> m.get("quantity"),
                m -> m.get("secondaryUnit"),
                m -> m.get("secondaryQty"),
                m -> m.get("warehouseName"),
                m -> m.get("applicantName"),
                m -> m.get("operatorName"),
                m -> m.get("createTime")
        );

        ExcelUtil.export(response, "发货统计", headers, data, extractors);
    }

    /**
     * 通过 relatedOrderNo 查找销售订单 → 客户ID → 客户名称，写入每行 Map
     */
    private void enrichCustomerName(List<Map<String, Object>> records) {
        if (records == null || records.isEmpty()) return;

        // 收集所有不重复的 relatedOrderNo
        Set<String> orderNos = records.stream()
                .map(m -> String.valueOf(m.getOrDefault("relatedOrderNo", "")))
                .filter(s -> !s.isEmpty() && !"null".equals(s))
                .collect(Collectors.toSet());
        if (orderNos.isEmpty()) return;

        // 批量查询销售订单
        List<BizSalesOrder> orders = salesOrderMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrder>().in(BizSalesOrder::getOrderNo, orderNos));
        Map<String, Long> orderNoToCustomerId = orders.stream()
                .filter(o -> o.getCustomerId() != null)
                .collect(Collectors.toMap(BizSalesOrder::getOrderNo, BizSalesOrder::getCustomerId, (a, b) -> a));

        // 批量查询客户
        Set<Long> customerIds = new HashSet<>(orderNoToCustomerId.values());
        Map<Long, String> customerIdToName = customerIds.isEmpty() ? new HashMap<>() :
                customerMapper.selectBatchIds(customerIds).stream()
                        .collect(Collectors.toMap(BizCustomer::getId, BizCustomer::getCustomerName, (a, b) -> a));

        // 组装 orderNo → customerName
        Map<String, String> orderNoToCustomerName = new HashMap<>();
        orderNoToCustomerId.forEach((orderNo, custId) ->
                orderNoToCustomerName.put(orderNo, customerIdToName.getOrDefault(custId, "")));

        // 写入每行
        records.forEach(m -> {
            String orderNo = String.valueOf(m.getOrDefault("relatedOrderNo", ""));
            m.put("customerName", orderNoToCustomerName.getOrDefault(orderNo, ""));
        });
    }
}
