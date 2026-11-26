package com.aimes.modules.dataimport.service.simulation;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.production.entity.BizReworkOrder;
import com.aimes.modules.production.entity.BizReworkOrderStep;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizReworkOrderMapper;
import com.aimes.modules.production.mapper.BizReworkOrderStepMapper;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.quality.entity.BizQcDisposal;
import com.aimes.modules.quality.entity.BizQcTask;
import com.aimes.modules.quality.mapper.BizQcDisposalMapper;
import com.aimes.modules.quality.mapper.BizQcTaskMapper;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.aimes.modules.dataimport.service.simulation.SimTimeHelper.*;

/**
 * ⑧ 返修工单生成器
 * 读取 disposalType=rework 的 BizQcDisposal → 生成 BizReworkOrder + BizReworkOrderStep
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReworkGenerator {

    private final BizQcDisposalMapper qcDisposalMapper;
    private final BizQcTaskMapper qcTaskMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizWoProcessTaskMapper processTaskMapper;
    private final BizMaterialMapper materialMapper;
    private final BizReworkOrderMapper reworkOrderMapper;
    private final BizReworkOrderStepMapper reworkOrderStepMapper;
    private final SysUserMapper userMapper;

    public static final List<String> ORDER_HEADERS = List.of(
            "返修编号", "质检任务编号(关联)", "处理单号(关联)", "工单编号(关联)",
            "工序名称", "产品编号", "数量", "状态", "创建时间", "创建人");

    public static final List<String> STEP_HEADERS = List.of(
            "返修编号(关联)", "工序名称", "指派人", "排序号", "状态",
            "完成时间", "创建时间", "创建人");

    private List<List<Object>> cachedOrderRows;
    private List<List<Object>> cachedStepRows;

    public Map<String, Object> generate(SimConfig cfg) {
        Set<Long> allIds = new java.util.HashSet<>();
        allIds.addAll(cfg.getCreatorIds());
        allIds.addAll(cfg.getAssigneeIds());
        Map<Long, String> userNames = loadUserNames(new java.util.ArrayList<>(allIds));

        // 加载 disposalType=rework 的处理单
        List<BizQcDisposal> disposals = qcDisposalMapper.selectList(
                new LambdaQueryWrapper<BizQcDisposal>()
                        .eq(BizQcDisposal::getDeleted, 0)
                        .eq(BizQcDisposal::getDisposalType, "rework"));

        // 质检任务映射
        Map<Long, BizQcTask> qcTaskMap = qcTaskMapper.selectList(
                new LambdaQueryWrapper<BizQcTask>().eq(BizQcTask::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizQcTask::getId, t -> t, (a, b) -> a));

        // 工单映射
        Map<Long, BizWorkOrder> woMap = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>().eq(BizWorkOrder::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizWorkOrder::getId, w -> w, (a, b) -> a));

        // 产品/物料映射
        Map<Long, BizMaterial> materialMap = materialMapper.selectList(
                new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        cachedOrderRows = new ArrayList<>();
        cachedStepRows = new ArrayList<>();

        for (BizQcDisposal disposal : disposals) {
            // 质检任务编号
            String qcTaskNo = "";
            BizQcTask qcTask = disposal.getQcTaskId() != null ? qcTaskMap.get(disposal.getQcTaskId()) : null;
            if (qcTask != null) qcTaskNo = qcTask.getTaskNo();

            // 工单编号
            String woNo = "";
            BizWorkOrder wo = disposal.getWorkOrderId() != null ? woMap.get(disposal.getWorkOrderId()) : null;
            if (wo != null) woNo = wo.getWoNo();

            // 工序名称
            String stepName = disposal.getStepName() != null ? disposal.getStepName() : "";

            // 产品编号
            String productNo = "";
            Long productId = wo != null ? wo.getProductId() : null;
            if (productId != null) {
                BizMaterial mat = materialMap.get(productId);
                if (mat != null) productNo = mat.getMaterialNo();
            }

            BigDecimal qty = disposal.getQuantity() != null ? disposal.getQuantity() : BigDecimal.ONE;

            // 创建时间 = 质检处理时间后 +1 工作日
            LocalDate baseDate;
            if (disposal.getProcessedAt() != null) {
                baseDate = addWorkingDays(disposal.getProcessedAt().toLocalDate(), 1, cfg);
            } else if (disposal.getCreateTime() != null) {
                baseDate = addWorkingDays(disposal.getCreateTime().toLocalDate(), 1, cfg);
            } else {
                baseDate = LocalDate.now();
            }
            baseDate = snapToWorkingDay(baseDate, cfg);
            // 截止日过滤：返修创建日在截止日之后 → 该返修单还没创建，跳过
            if (cfg.isAfterCutoff(baseDate)) continue;
            LocalDateTime createTime = randomTime(baseDate, cfg);
            if (createTime == null) continue;

            // 完成时间 = 创建时间后 2~3 个工作日
            int completeDays = ThreadLocalRandom.current().nextInt(2, 4);
            LocalDate completeDate = addWorkingDays(baseDate, completeDays, cfg);
            // 完成日超过截止日 → 用截止日代替（返修单已创建但未完成）
            if (cfg.isAfterCutoff(completeDate)) completeDate = cfg.getCutoffDate();
            LocalDateTime completeTime = randomTime(completeDate, cfg);
            if (completeTime == null) continue;

            Long creatorId = cfg.pickCreator();
            String creatorName = userNames.getOrDefault(creatorId, creatorId != null ? String.valueOf(creatorId) : "");

            Long assigneeId = cfg.pickAssignee();
            String assigneeName = userNames.getOrDefault(assigneeId, assigneeId != null ? String.valueOf(assigneeId) : "");

            String reworkNo = CodeUtil.generate("RW");

            // 返修工单行
            cachedOrderRows.add(List.of(
                    reworkNo, qcTaskNo, disposal.getDisposalNo() != null ? disposal.getDisposalNo() : "",
                    woNo, stepName, productNo, qty.toPlainString(),
                    "completed", fmtDateTime(createTime), creatorName));

            // 返修步骤行: 只生成一个步骤
            cachedStepRows.add(List.of(
                    reworkNo, "返修", assigneeName, "1",
                    "completed", fmtDateTime(completeTime),
                    fmtDateTime(createTime), creatorName));
        }

        Map<String, Object> tables = new LinkedHashMap<>();
        tables.put("rework_order", cachedOrderRows.size());
        tables.put("rework_step", cachedStepRows.size());
        int total = cachedOrderRows.size() + cachedStepRows.size();
        return Map.of("tables", tables, "totalRows", total);
    }

    public void exportZip(HttpServletResponse response) throws IOException {
        if (cachedOrderRows == null) throw new BusinessException("请先执行生成");

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + java.net.URLEncoder.encode("08_返修工单.zip", "UTF-8"));

        try (var zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
            zos.putNextEntry(new java.util.zip.ZipEntry("08_返修工单_rework_order.xlsx"));
            SimTimeHelper.writeExcelToStream(zos, ORDER_HEADERS, cachedOrderRows);
            zos.closeEntry();

            zos.putNextEntry(new java.util.zip.ZipEntry("08_返修工单_rework_step.xlsx"));
            SimTimeHelper.writeExcelToStream(zos, STEP_HEADERS, cachedStepRows);
            zos.closeEntry();
        }
    }

    @Transactional
    public Map<String, Object> importData(MultipartFile orderFile, MultipartFile stepFile) {
        int success = 0;
        List<String> errors = new ArrayList<>();

        Map<String, Long> userNameToId = loadUserNameToIdMap();

        // 质检任务编号→ID
        Map<String, Long> qcTaskNoToId = qcTaskMapper.selectList(
                new LambdaQueryWrapper<BizQcTask>().eq(BizQcTask::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizQcTask::getTaskNo, BizQcTask::getId, (a, b) -> a));

        // 处理单号→entity
        Map<String, BizQcDisposal> disposalNoToEntity = qcDisposalMapper.selectList(
                new LambdaQueryWrapper<BizQcDisposal>().eq(BizQcDisposal::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizQcDisposal::getDisposalNo, d -> d, (a, b) -> a));

        // 工单编号→ID
        Map<String, Long> woNoToId = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>().eq(BizWorkOrder::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizWorkOrder::getWoNo, BizWorkOrder::getId, (a, b) -> a));

        // 产品编号→ID
        Map<String, Long> materialNoToId = materialMapper.selectList(
                new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizMaterial::getMaterialNo, BizMaterial::getId, (a, b) -> a));

        // 构建 woNo+stepName → taskId
        Map<String, Long> taskKeyToId = new HashMap<>();
        for (Map.Entry<String, Long> e : woNoToId.entrySet()) {
            processTaskMapper.selectList(new LambdaQueryWrapper<BizWoProcessTask>()
                    .eq(BizWoProcessTask::getWorkOrderId, e.getValue())
                    .eq(BizWoProcessTask::getDeleted, 0))
                    .forEach(t -> taskKeyToId.put(e.getKey() + "_" + t.getStepName(), t.getId()));
        }

        // 1. 导入返修工单
        Map<String, Long> reworkNoToId = new HashMap<>();
        if (orderFile != null && !orderFile.isEmpty()) {
            List<Map<String, String>> rows = ExcelUtil.parseImport(orderFile, ORDER_HEADERS);
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> r = rows.get(i);
                try {
                    String woNo = r.get("工单编号(关联)");
                    Long woId = woNoToId.get(woNo);

                    String qcTaskNoStr = r.get("质检任务编号(关联)");
                    Long qcTaskId = qcTaskNoToId.get(qcTaskNoStr);

                    String disposalNo = r.get("处理单号(关联)");
                    BizQcDisposal disposal = disposalNoToEntity.get(disposalNo);
                    Long disposalId = disposal != null ? disposal.getId() : null;

                    String stepName = r.get("工序名称");
                    Long processTaskId = woNo != null ? taskKeyToId.get(woNo + "_" + stepName) : null;
                    Long productId = materialNoToId.get(r.get("产品编号"));

                    BizReworkOrder rework = new BizReworkOrder();
                    rework.setReworkNo(r.get("返修编号"));
                    rework.setQcTaskId(qcTaskId);
                    rework.setQcDisposalId(disposalId);
                    rework.setWorkOrderId(woId);
                    rework.setProcessTaskId(processTaskId);
                    rework.setProductId(productId);
                    rework.setQuantity(parseBD(r.get("数量")));
                    rework.setStatus(r.get("状态"));
                    rework.setCreateTime(parseDatetime(r.get("创建时间")));
                    rework.setCreateBy(userNameToId.get(r.get("创建人")));
                    rework.setUpdateTime(rework.getCreateTime());
                    rework.setUpdateBy(rework.getCreateBy());
                    reworkOrderMapper.insert(rework);
                    reworkNoToId.put(rework.getReworkNo(), rework.getId());

                    // 回写 qcDisposal.reworkOrderId
                    if (rework.getQcDisposalId() != null) {
                        com.aimes.modules.quality.entity.BizQcDisposal disp = qcDisposalMapper.selectById(rework.getQcDisposalId());
                        if (disp != null) {
                            disp.setReworkOrderId(rework.getId());
                            qcDisposalMapper.updateById(disp);
                        }
                    }

                    success++;
                } catch (Exception e) {
                    errors.add("返修工单第" + (i + 2) + "行: " + e.getMessage());
                }
            }
        }

        // 2. 导入返修步骤
        if (stepFile != null && !stepFile.isEmpty()) {
            List<Map<String, String>> rows = ExcelUtil.parseImport(stepFile, STEP_HEADERS);
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> r = rows.get(i);
                try {
                    String reworkNo = r.get("返修编号(关联)");
                    Long reworkOrderId = reworkNoToId.get(reworkNo);
                    if (reworkOrderId == null) { errors.add("返修步骤第" + (i + 2) + "行: 返修工单不存在 " + reworkNo); continue; }

                    BizReworkOrderStep step = new BizReworkOrderStep();
                    step.setReworkOrderId(reworkOrderId);
                    step.setStepName(r.get("工序名称"));
                    step.setAssignedUserId(userNameToId.get(r.get("指派人")));
                    String sortOrderStr = r.get("排序号");
                    step.setSortOrder(sortOrderStr != null && !sortOrderStr.isEmpty() ? Integer.parseInt(sortOrderStr.trim()) : 1);
                    step.setStatus(r.get("状态"));
                    step.setCompletedAt(parseDatetime(r.get("完成时间")));
                    step.setCreateTime(parseDatetime(r.get("创建时间")));
                    step.setCreateBy(userNameToId.get(r.get("创建人")));
                    step.setUpdateTime(step.getCreateTime());
                    step.setUpdateBy(step.getCreateBy());
                    reworkOrderStepMapper.insert(step);
                    success++;
                } catch (Exception e) {
                    errors.add("返修步骤第" + (i + 2) + "行: " + e.getMessage());
                }
            }
        }

        return Map.of("success", success, "errors", errors);
    }

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

    private static BigDecimal parseBD(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return new BigDecimal(s.trim()); } catch (Exception e) { return null; }
    }

    private static LocalDateTime parseDatetime(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return LocalDateTime.parse(s.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
        catch (Exception e) { return null; }
    }
}
