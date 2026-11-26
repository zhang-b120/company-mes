package com.aimes.modules.dataimport.service.simulation;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.mrp.entity.BizProductionPlan;
import com.aimes.modules.mrp.entity.BizProductionPlanItem;
import com.aimes.modules.mrp.mapper.BizProductionPlanItemMapper;
import com.aimes.modules.mrp.mapper.BizProductionPlanMapper;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.mapper.BizSalesOrderItemMapper;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.aimes.modules.dataimport.service.simulation.SimTimeHelper.*;

/**
 * ① 生产计划生成器
 * 读取销售订单 → 生成 BizProductionPlan + BizProductionPlanItem
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PlanGenerator {

    private final BizSalesOrderMapper salesOrderMapper;
    private final BizSalesOrderItemMapper salesOrderItemMapper;
    private final SalesOrderStatusHelper salesOrderStatusHelper;
    private final BizMaterialMapper materialMapper;
    private final BizProductionPlanMapper planMapper;
    private final BizProductionPlanItemMapper planItemMapper;
    private final com.aimes.modules.mrp.mapper.BizProductionPlanOrderRelMapper planOrderRelMapper;
    private final SysUserMapper userMapper;

    // ===== Excel 表头 =====
    public static final List<String> PLAN_HEADERS = List.of(
            "计划编号", "计划名称", "计划开始日期", "计划结束日期", "状态", "创建方式", "创建时间", "创建人");

    public static final List<String> PLAN_ITEM_HEADERS = List.of(
            "计划编号(关联)", "产品编号", "产品名称", "计划数量", "已下达数量", "已完成数量",
            "计划开始日", "计划结束日", "优先级", "来源类型", "状态",
            "关联订单号", "关联订单行产品编号", "分配数量",
            "创建时间", "创建人");

    // ===== 内存缓存 =====
    private List<List<Object>> cachedPlanRows;
    private List<List<Object>> cachedItemRows;

    // ===== 生成 =====

    public Map<String, Object> generate(SimConfig cfg) {
        // 加载用户姓名映射
        Map<Long, String> userNames = loadUserNames(cfg.getCreatorIds());

        // 查询非草稿/已取消的销售订单
        List<BizSalesOrder> orders = salesOrderMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrder>().eq(BizSalesOrder::getDeleted, 0)
                        .notIn(BizSalesOrder::getStatus, List.of("draft", "pending_approval", "cancelled", "voided")));

        // 加载物料名称映射
        Map<Long, BizMaterial> matMap = materialMapper.selectList(
                new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        cachedPlanRows = new ArrayList<>();
        cachedItemRows = new ArrayList<>();

        for (BizSalesOrder order : orders) {
            // 整单外协：按配置比例跳过部分订单（这些订单由⑤整单外协模块处理）
            if (cfg.isFullOutsource()) {
                continue; // 不生成计划，留给整单外协
            }

            List<BizSalesOrderItem> items = salesOrderItemMapper.selectList(
                    new LambdaQueryWrapper<BizSalesOrderItem>()
                            .eq(BizSalesOrderItem::getOrderId, order.getId())
                            .eq(BizSalesOrderItem::getDeleted, 0));
            if (items.isEmpty()) continue;

            // 时间计算
            LocalDate orderDate = order.getCreateTime() != null ? order.getCreateTime().toLocalDate() : LocalDate.now().minusDays(30);
            LocalDate delivery = order.getDeliveryDate() != null ? order.getDeliveryDate() : orderDate.plusDays(21);

            // 计划开始 = 订单创建时间 + 随机[delayDaysMin, delayDaysMax]个工作日
            int startDelay = cfg.randomDelayDays();
            LocalDate planStart = addWorkingDays(orderDate, startDelay, cfg);
            planStart = snapToWorkingDay(planStart, cfg);
            // 截止日过滤：计划开始日（=计划创建日）在截止日之后 → 该计划尚未制定，跳过
            if (cfg.isAfterCutoff(planStart)) continue;

            // 计划结束 = 订单交期 - 随机[advanceDaysMin, advanceDaysMax]个工作日
            int endAdvance = cfg.randomAdvanceDays();
            LocalDate planEnd = addWorkingDays(delivery, -endAdvance, cfg);
            if (planEnd == null || planEnd.isBefore(planStart)) planEnd = delivery;
            planEnd = snapToWorkingDay(planEnd, cfg);
            if (planEnd.isBefore(planStart)) planEnd = addWorkingDays(planStart, 7, cfg);

            // 计划创建时间 = 计划开始日期当天
            LocalDateTime planTime = randomTime(planStart, cfg);
            if (planTime == null) continue;
            Long creatorId = cfg.pickCreator();
            String creatorName = userNames.getOrDefault(creatorId, String.valueOf(creatorId));

            String planNo = CodeUtil.generate("PP");
            String planName = order.getOrderNo() + " 生产计划";

            cachedPlanRows.add(List.of(
                    planNo, planName, fmtDate(planStart), fmtDate(planEnd),
                    "confirmed", "manual", fmtDateTime(planTime), creatorName));

            // 生成行项（每个订单行项 → 一个计划行项，同时记录订单关联信息）
            for (BizSalesOrderItem oi : items) {
                if (oi.getProductId() == null || oi.getQuantity() == null) continue;
                BizMaterial product = matMap.get(oi.getProductId());
                String productNo = product != null ? product.getMaterialNo() : "";
                String productName = product != null ? product.getMaterialName() : "";
                BigDecimal qty = oi.getQuantity();

                cachedItemRows.add(List.of(
                        planNo, productNo, productName,
                        qty.toPlainString(), "0", "0",  // planQty, releasedQty=0(②回写), completedQty=0(③回写)
                        fmtDate(planStart), fmtDate(planEnd),
                        "1", "manual", "pending",  // pending(②③回写后变completed)
                        order.getOrderNo(), productNo, qty.toPlainString(),
                        fmtDateTime(planTime), creatorName));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tables", Map.of("plan", cachedPlanRows.size(), "plan_item", cachedItemRows.size()));
        result.put("totalRows", cachedPlanRows.size() + cachedItemRows.size());
        return result;
    }

    // ===== 导出 =====

    /**
     * 导出ZIP（两个xlsx文件）
     */
    public void exportZip(HttpServletResponse response) throws IOException {
        if (cachedPlanRows == null || cachedItemRows == null)
            throw new BusinessException("请先执行生成");

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + java.net.URLEncoder.encode("01_生产计划.zip", "UTF-8"));

        try (var zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
            zos.putNextEntry(new java.util.zip.ZipEntry("01_生产计划_plan.xlsx"));
            SimTimeHelper.writeExcelToStream(zos, PLAN_HEADERS, cachedPlanRows);
            zos.closeEntry();

            zos.putNextEntry(new java.util.zip.ZipEntry("01_生产计划_plan_item.xlsx"));
            SimTimeHelper.writeExcelToStream(zos, PLAN_ITEM_HEADERS, cachedItemRows);
            zos.closeEntry();
        }
    }

    // ===== 导入 =====

    @Transactional
    public Map<String, Object> importData(MultipartFile planFile, MultipartFile planItemFile) {
        int success = 0;
        List<String> errors = new ArrayList<>();

        // 1. 解析plan.xlsx
        List<Map<String, String>> planRows = ExcelUtil.parseImport(planFile, PLAN_HEADERS);

        // 2. 构建用户名→ID映射
        Map<String, Long> userNameToId = loadUserNameToIdMap();

        // 3. 逐行导入plan
        Map<String, Long> planNoToId = new LinkedHashMap<>();
        for (int i = 0; i < planRows.size(); i++) {
            Map<String, String> r = planRows.get(i);
            try {
                BizProductionPlan plan = new BizProductionPlan();
                plan.setPlanNo(r.get("计划编号"));
                plan.setPlanName(r.get("计划名称"));
                plan.setPlanDateFrom(parseDate(r.get("计划开始日期")));
                plan.setPlanDateTo(parseDate(r.get("计划结束日期")));
                plan.setStatus(r.get("状态"));
                plan.setCreateType(r.get("创建方式"));
                plan.setCreateTime(parseDatetime(r.get("创建时间")));
                plan.setCreateBy(userNameToId.get(r.get("创建人")));
                plan.setUpdateTime(plan.getCreateTime());
                plan.setUpdateBy(plan.getCreateBy());
                planMapper.insert(plan);
                planNoToId.put(plan.getPlanNo(), plan.getId());
                success++;
            } catch (Exception e) {
                errors.add("plan第" + (i + 2) + "行: " + e.getMessage());
            }
        }

        // 4. 解析并导入plan_item.xlsx + 创建订单关联
        if (planItemFile != null && !planItemFile.isEmpty()) {
            List<Map<String, String>> itemRows = ExcelUtil.parseImport(planItemFile, PLAN_ITEM_HEADERS);
            Map<String, Long> matNoToId = loadMaterialNoToIdMap();

            // 构建订单号→订单ID映射、订单号+产品编号→订单行项ID映射
            Map<String, Long> orderNoToId = salesOrderMapper.selectList(
                    new LambdaQueryWrapper<BizSalesOrder>().eq(BizSalesOrder::getDeleted, 0))
                    .stream().collect(Collectors.toMap(BizSalesOrder::getOrderNo, BizSalesOrder::getId, (a, b) -> a));
            Map<String, Long> orderItemKey = new HashMap<>();
            for (Map.Entry<String, Long> oe : orderNoToId.entrySet()) {
                salesOrderItemMapper.selectList(new LambdaQueryWrapper<BizSalesOrderItem>()
                        .eq(BizSalesOrderItem::getOrderId, oe.getValue())
                        .eq(BizSalesOrderItem::getDeleted, 0))
                        .forEach(oi -> {
                            BizMaterial m = oi.getProductId() != null ? materialMapper.selectById(oi.getProductId()) : null;
                            String pNo = m != null ? m.getMaterialNo() : "";
                            orderItemKey.put(oe.getKey() + "_" + pNo, oi.getId());
                        });
            }

            for (int i = 0; i < itemRows.size(); i++) {
                Map<String, String> r = itemRows.get(i);
                try {
                    String planNo = r.get("计划编号(关联)");
                    Long planId = planNoToId.get(planNo);
                    if (planId == null) {
                        errors.add("plan_item第" + (i + 2) + "行: 找不到计划编号 " + planNo);
                        continue;
                    }

                    String productNo = r.get("产品编号");
                    Long productId = matNoToId.get(productNo);
                    if (productId == null) {
                        errors.add("plan_item第" + (i + 2) + "行: 找不到产品编号 " + productNo);
                        continue;
                    }

                    BizProductionPlanItem item = new BizProductionPlanItem();
                    item.setPlanId(planId);
                    item.setProductId(productId);
                    item.setPlanQty(parseBD(r.get("计划数量")));
                    item.setReleasedQty(parseBD(r.get("已下达数量")));
                    item.setCompletedQty(parseBD(r.get("已完成数量")));
                    item.setPlanStartDate(parseDate(r.get("计划开始日")));
                    item.setPlanEndDate(parseDate(r.get("计划结束日")));
                    item.setPriority(parseInt(r.get("优先级")));
                    item.setSourceType(r.get("来源类型"));
                    item.setStatus(r.get("状态"));
                    item.setCreateTime(parseDatetime(r.get("创建时间")));
                    item.setCreateBy(userNameToId.get(r.get("创建人")));
                    item.setUpdateTime(item.getCreateTime());
                    item.setUpdateBy(item.getCreateBy());
                    planItemMapper.insert(item);
                    success++;

                    // 5. 创建 biz_production_plan_order_rel 关联记录
                    String relOrderNo = r.get("关联订单号");
                    String relProductNo = r.get("关联订单行产品编号");
                    if (relOrderNo != null && !relOrderNo.isEmpty()) {
                        Long orderId = orderNoToId.get(relOrderNo);
                        Long orderItemId = orderItemKey.get(relOrderNo + "_" + relProductNo);
                        if (orderId != null) {
                            com.aimes.modules.mrp.entity.BizProductionPlanOrderRel rel =
                                    new com.aimes.modules.mrp.entity.BizProductionPlanOrderRel();
                            rel.setPlanItemId(item.getId());
                            rel.setOrderId(orderId);
                            rel.setOrderItemId(orderItemId != null ? orderItemId : 0L);
                            rel.setAllocatedQty(parseBD(r.get("分配数量")));
                            rel.setCreateTime(item.getCreateTime());
                            rel.setCreateBy(item.getCreateBy());
                            rel.setUpdateTime(item.getCreateTime());
                            rel.setUpdateBy(item.getCreateBy());
                            planOrderRelMapper.insert(rel);

                            // 更新销售订单状态为 in_production
                            salesOrderStatusHelper.advanceToInProduction(orderId);
                        } else {
                            errors.add("plan_item第" + (i + 2) + "行: 关联订单号'" + relOrderNo + "'不存在");
                        }
                    }
                } catch (Exception e) {
                    errors.add("plan_item第" + (i + 2) + "行: " + e.getMessage());
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("errors", errors);
        return result;
    }

    // ===== 辅助方法 =====

    private Map<Long, String> loadUserNames(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getId, ids))
                .stream().collect(Collectors.toMap(SysUser::getId,
                        u -> u.getRealName() != null ? u.getRealName() : u.getUsername(), (a, b) -> a));
    }

    private Map<String, Long> loadUserNameToIdMap() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeleted, 0))
                .stream().collect(Collectors.toMap(
                        u -> u.getRealName() != null ? u.getRealName() : u.getUsername(),
                        SysUser::getId, (a, b) -> a));
    }

    private Map<String, Long> loadMaterialNoToIdMap() {
        return materialMapper.selectList(new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0))
                .stream().filter(m -> m.getMaterialNo() != null)
                .collect(Collectors.toMap(BizMaterial::getMaterialNo, BizMaterial::getId, (a, b) -> a));
    }

    private static LocalDate parseDate(String str) {
        if (str == null || str.isEmpty()) return null;
        try { return LocalDate.parse(str.trim(), DateTimeFormatter.ISO_LOCAL_DATE); }
        catch (Exception e) { return LocalDate.parse(str.trim(), DateTimeFormatter.ofPattern("yyyy/MM/dd")); }
    }

    private static LocalDateTime parseDatetime(String str) {
        if (str == null || str.isEmpty()) return null;
        try { return LocalDateTime.parse(str.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
        catch (Exception e) {
            LocalDate d = parseDate(str);
            return d != null ? d.atStartOfDay() : null;
        }
    }

    private static BigDecimal parseBD(String str) {
        if (str == null || str.isEmpty()) return null;
        try { return new BigDecimal(str.trim()); } catch (Exception e) { return null; }
    }

    private static Integer parseInt(String str) {
        if (str == null || str.isEmpty()) return null;
        try { return Integer.parseInt(str.trim().split("\\.")[0]); } catch (Exception e) { return null; }
    }
}
