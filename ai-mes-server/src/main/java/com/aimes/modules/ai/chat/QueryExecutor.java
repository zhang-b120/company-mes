package com.aimes.modules.ai.chat;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.*;

/**
 * 查询执行器 — 根据模块和AI生成的查询条件执行数据库查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryExecutor {

    private final ApplicationContext applicationContext;

    /** 模块→Mapper Bean名称映射 */
    private static final Map<String, String> MODULE_MAPPER_MAP = Map.ofEntries(
            Map.entry("customer", "bizCustomerMapper"),
            Map.entry("supplier", "bizSupplierMapper"),
            Map.entry("material", "bizMaterialMapper"),
            Map.entry("product", "bizProductMapper"),
            Map.entry("salesOrder", "bizSalesOrderMapper"),
            Map.entry("workOrder", "bizWorkOrderMapper"),
            Map.entry("purchaseRequest", "bizPurchaseRequestMapper"),
            Map.entry("purchaseOrder", "bizPurchaseOrderMapper"),
            Map.entry("inventory", "bizInventoryMapper"),
            Map.entry("outsourceOrder", "bizOutsourceOrderMapper"),
            Map.entry("qcTask", "bizQcTaskMapper"),
            Map.entry("defect", "bizDefectRecordMapper"),
            Map.entry("receivable", "bizReceivableMapper"),
            Map.entry("payable", "bizPayableMapper"),
            Map.entry("team", "bizTeamMapper"),
            Map.entry("user", "sysUserMapper"),
            Map.entry("notification", "sysNotificationMapper")
    );

    /** 模块→搜索字段映射（keyword搜索哪些列） */
    private static final Map<String, List<String>> MODULE_SEARCH_FIELDS = Map.ofEntries(
            Map.entry("customer", List.of("customer_name", "contact_name", "phone")),
            Map.entry("supplier", List.of("supplier_name", "contact_name", "phone")),
            Map.entry("material", List.of("material_name", "material_no", "spec")),
            Map.entry("product", List.of("product_name", "product_no", "spec")),
            Map.entry("salesOrder", List.of("order_no", "customer_name")),
            Map.entry("workOrder", List.of("wo_no", "product_name")),
            Map.entry("inventory", List.of("material_name")),
            Map.entry("team", List.of("team_name")),
            Map.entry("purchaseRequest", List.of("request_no")),
            Map.entry("purchaseOrder", List.of("order_no", "supplier_name")),
            Map.entry("outsourceOrder", List.of("order_no", "supplier_name")),
            Map.entry("qcTask", List.of("task_no")),
            Map.entry("defect", List.of("defect_no", "defect_type")),
            Map.entry("receivable", List.of("customer_name")),
            Map.entry("payable", List.of("supplier_name")),
            Map.entry("user", List.of("real_name", "username", "phone")),
            Map.entry("notification", List.of("title", "content"))
    );

    /**
     * 执行查询
     *
     * @param module 模块标识
     * @param queryParams AI生成的查询条件 {keyword, status, dateRange, ...}
     * @return 查询结果列表(最多20条)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Map<String, Object>> execute(String module, Map<String, Object> queryParams) {
        String mapperName = MODULE_MAPPER_MAP.get(module);
        if (mapperName == null) {
            log.warn("未注册查询的模块: {}", module);
            return List.of();
        }

        try {
            BaseMapper mapper = (BaseMapper) applicationContext.getBean(mapperName);
            QueryWrapper wrapper = new QueryWrapper();
            wrapper.eq("deleted", 0);

            // keyword模糊搜索
            String keyword = getStr(queryParams, "keyword");
            if (keyword != null) {
                List<String> searchFields = MODULE_SEARCH_FIELDS.getOrDefault(module, List.of());
                if (!searchFields.isEmpty()) {
                    // 构建 (field1 LIKE '%kw%' OR field2 LIKE '%kw%' OR ...)
                    StringBuilder sql = new StringBuilder("(");
                    for (int i = 0; i < searchFields.size(); i++) {
                        if (i > 0) sql.append(" OR ");
                        sql.append(searchFields.get(i)).append(" LIKE CONCAT('%',{").append(i).append("},'%')");
                    }
                    sql.append(")");
                    Object[] params = new Object[searchFields.size()];
                    java.util.Arrays.fill(params, keyword);
                    wrapper.apply(sql.toString(), params);
                }
            }

            // status精确匹配
            String status = getStr(queryParams, "status");
            if (status != null) {
                wrapper.eq("status", status);
            }

            // 日期范围筛选
            String dateFrom = getStr(queryParams, "dateFrom");
            String dateTo = getStr(queryParams, "dateTo");
            if (dateFrom != null) {
                wrapper.ge("create_time", dateFrom);
            }
            if (dateTo != null) {
                wrapper.le("create_time", dateTo + " 23:59:59");
            }

            // 交期范围筛选
            String deliveryFrom = getStr(queryParams, "deliveryFrom");
            String deliveryTo = getStr(queryParams, "deliveryTo");
            if (deliveryFrom != null) {
                wrapper.ge("delivery_date", deliveryFrom);
            }
            if (deliveryTo != null) {
                wrapper.le("delivery_date", deliveryTo);
            }

            // 排序
            wrapper.orderByDesc("create_time");

            // 分页查询（支持自定义数量，默认最多20条）
            int limit = 20;
            Object limitVal = queryParams.get("limit");
            if (limitVal != null) {
                try {
                    limit = Math.min(50, Integer.parseInt(limitVal.toString()));
                } catch (NumberFormatException ignored) {}
            }
            Page page = new Page(1, limit);
            page = (Page) mapper.selectPage(page, wrapper);

            // 转为Map列表
            List<Map<String, Object>> records = new ArrayList<>();
            for (Object entity : page.getRecords()) {
                records.add(entityToMap(entity));
            }
            return records;
        } catch (Exception e) {
            log.error("查询执行失败: module={}", module, e);
            return List.of();
        }
    }

    private String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null && !val.toString().isBlank() ? val.toString() : null;
    }

    private Map<String, Object> entityToMap(Object entity) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            om.findAndRegisterModules();
            String json = om.writeValueAsString(entity);
            return om.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("error", "转换失败");
        }
    }
}
