package com.aimes.modules.dataimport.service.simulation;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.quality.entity.BizDefectRecord;
import com.aimes.modules.quality.entity.BizQcDisposal;
import com.aimes.modules.quality.entity.BizQcTask;
import com.aimes.modules.quality.mapper.BizDefectRecordMapper;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.aimes.modules.dataimport.service.simulation.SimTimeHelper.*;

/**
 * ⑤ 质检处理+缺陷记录生成器
 * 读取 result=fail 的 BizQcTask → 生成 BizQcDisposal + BizDefectRecord
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class QcDisposalGenerator {

    private final BizQcTaskMapper qcTaskMapper;
    private final BizQcDisposalMapper qcDisposalMapper;
    private final BizDefectRecordMapper defectRecordMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizWoProcessTaskMapper processTaskMapper;
    private final BizMaterialMapper materialMapper;
    private final SysUserMapper userMapper;

    public static final List<String> DISPOSAL_HEADERS = List.of(
            "处理编号", "质检任务编号(关联)", "工单编号", "工序名称", "物料编号",
            "数量", "处理类型", "处理原因", "状态", "处理人", "处理时间", "创建时间", "创建人");

    public static final List<String> DEFECT_HEADERS = List.of(
            "缺陷编号", "来源类型", "产品编号", "工单编号", "数量",
            "缺陷类型", "缺陷原因", "缺陷等级", "处理状态", "处理结果",
            "处理人", "处理时间", "创建时间", "创建人");

    private List<List<Object>> cachedDisposalRows;
    private List<List<Object>> cachedDefectRows;

    public Map<String, Object> generate(SimConfig cfg) {
        Set<Long> allIds = new java.util.HashSet<>();
        allIds.addAll(cfg.getCreatorIds());
        allIds.addAll(cfg.getHandlerIds());
        Map<Long, String> userNames = loadUserNames(new java.util.ArrayList<>(allIds));

        // 加载 result=fail 的质检任务
        List<BizQcTask> failTasks = qcTaskMapper.selectList(
                new LambdaQueryWrapper<BizQcTask>()
                        .eq(BizQcTask::getDeleted, 0)
                        .eq(BizQcTask::getResult, "fail"));

        // 加载工单映射
        Map<Long, BizWorkOrder> woMap = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>().eq(BizWorkOrder::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizWorkOrder::getId, w -> w, (a, b) -> a));

        // 加载工序任务映射
        Map<Long, BizWoProcessTask> processTaskMap = processTaskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>().eq(BizWoProcessTask::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizWoProcessTask::getId, t -> t, (a, b) -> a));

        // 加载物料映射 id → materialNo
        Map<Long, String> materialNoMap = materialMapper.selectList(
                new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizMaterial::getId, BizMaterial::getMaterialNo, (a, b) -> a));

        cachedDisposalRows = new ArrayList<>();
        cachedDefectRows = new ArrayList<>();

        for (BizQcTask qcTask : failTasks) {
            BigDecimal failQty = qcTask.getFailQty() != null ? qcTask.getFailQty() : BigDecimal.ONE;
            if (failQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            // 根据 scrapRate 决定处理类型
            boolean isScrap = Math.random() < cfg.getScrapRate().doubleValue();
            String disposalType = isScrap ? "scrap" : "rework";
            String disposalReason = isScrap ? "不合格品报废" : "返修处理";

            // 处理时间 = 检验时间后 laterTime
            LocalDateTime baseTime = qcTask.getInspectedAt() != null ? qcTask.getInspectedAt() : qcTask.getCreateTime();
            if (baseTime == null) baseTime = LocalDateTime.now();
            LocalDateTime processedAt = laterTime(baseTime, cfg);
            // 处理时间为 null 或在截止日之后 → 该处置还没发生,跳过
            if (processedAt == null || cfg.isAfterCutoff(processedAt)) continue;

            Long processedById = cfg.pickHandler();
            String processedByName = userNames.getOrDefault(processedById, processedById != null ? String.valueOf(processedById) : "");

            Long creatorId = cfg.pickCreator();
            String creatorName = userNames.getOrDefault(creatorId, creatorId != null ? String.valueOf(creatorId) : "");

            // 获取工单编号
            BizWorkOrder wo = qcTask.getWorkOrderId() != null ? woMap.get(qcTask.getWorkOrderId()) : null;
            String woNo = wo != null ? wo.getWoNo() : "";

            // 获取工序名称
            BizWoProcessTask processTask = qcTask.getTaskId() != null ? processTaskMap.get(qcTask.getTaskId()) : null;
            String stepName = processTask != null && processTask.getStepName() != null ? processTask.getStepName() : "";

            // 获取物料编号
            String materialNo = qcTask.getMaterialId() != null ? materialNoMap.getOrDefault(qcTask.getMaterialId(), "") : "";
            String productNo = qcTask.getProductId() != null ? materialNoMap.getOrDefault(qcTask.getProductId(), "") : "";

            String disposalNo = CodeUtil.generate("QCD");
            String defectNo = CodeUtil.generate("DF");

            // 质检处理记录
            cachedDisposalRows.add(List.of(
                    disposalNo, qcTask.getTaskNo(), woNo, stepName, materialNo,
                    failQty.toPlainString(), disposalType, disposalReason,
                    "processed", processedByName,
                    fmtDateTime(processedAt), fmtDateTime(processedAt), creatorName));

            // 缺陷记录
            String handleResult = isScrap ? "报废处理" : "返修处理";
            cachedDefectRows.add(List.of(
                    defectNo, "qc", productNo, woNo,
                    failQty.toPlainString(),
                    "quality_issue", disposalReason, "minor",
                    "handled", handleResult,
                    processedByName, fmtDateTime(processedAt),
                    fmtDateTime(processedAt), creatorName));
        }

        return Map.of("tables", Map.of("qc_disposal", cachedDisposalRows.size(), "defect_record", cachedDefectRows.size()),
                "totalRows", cachedDisposalRows.size() + cachedDefectRows.size());
    }

    public void exportZip(HttpServletResponse response) throws IOException {
        if (cachedDisposalRows == null) throw new BusinessException("请先执行生成");

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + java.net.URLEncoder.encode("05_质检处理.zip", "UTF-8"));

        try (var zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
            zos.putNextEntry(new java.util.zip.ZipEntry("05_质检处理_qc_disposal.xlsx"));
            SimTimeHelper.writeExcelToStream(zos, DISPOSAL_HEADERS, cachedDisposalRows);
            zos.closeEntry();

            zos.putNextEntry(new java.util.zip.ZipEntry("05_质检处理_defect_record.xlsx"));
            SimTimeHelper.writeExcelToStream(zos, DEFECT_HEADERS, cachedDefectRows);
            zos.closeEntry();
        }
    }

    @Transactional
    public Map<String, Object> importData(MultipartFile disposalFile, MultipartFile defectFile) {
        if (disposalFile == null || disposalFile.isEmpty()) throw new BusinessException("请上传质检处理文件");
        List<Map<String, String>> disposalRows = ExcelUtil.parseImport(disposalFile, DISPOSAL_HEADERS);
        int disposalSuccess = 0;
        int defectSuccess = 0;
        List<String> errors = new ArrayList<>();

        Map<String, Long> userNameToId = loadUserNameToIdMap();

        // taskNo → qcTask 映射
        Map<String, BizQcTask> taskNoMap = qcTaskMapper.selectList(
                new LambdaQueryWrapper<BizQcTask>().eq(BizQcTask::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizQcTask::getTaskNo, t -> t, (a, b) -> a));

        Map<String, Long> woNoToId = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>().eq(BizWorkOrder::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizWorkOrder::getWoNo, BizWorkOrder::getId, (a, b) -> a));

        Map<String, Long> materialNoToId = materialMapper.selectList(
                new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizMaterial::getMaterialNo, BizMaterial::getId, (a, b) -> a));

        Map<String, Long> disposalNoToId = new HashMap<>();
        for (int i = 0; i < disposalRows.size(); i++) {
            Map<String, String> r = disposalRows.get(i);
            try {
                String taskNo = r.get("质检任务编号(关联)");
                BizQcTask qcTask = taskNoMap.get(taskNo);
                if (qcTask == null) { errors.add("第" + (i + 2) + "行(disposal): 质检任务不存在 " + taskNo); continue; }

                BizQcDisposal disposal = new BizQcDisposal();
                disposal.setDisposalNo(r.get("处理编号"));
                disposal.setQcTaskId(qcTask.getId());
                disposal.setWorkOrderId(qcTask.getWorkOrderId());
                disposal.setProcessTaskId(qcTask.getTaskId());
                disposal.setMaterialId(materialNoToId.get(r.get("物料编号")));
                disposal.setStepName(r.get("工序名称"));
                disposal.setQuantity(parseBD(r.get("数量")));
                disposal.setDisposalType(r.get("处理类型"));
                disposal.setDisposalReason(r.get("处理原因"));
                disposal.setStatus(r.get("状态"));
                disposal.setProcessedBy(userNameToId.get(r.get("处理人")));
                disposal.setProcessedAt(parseDatetime(r.get("处理时间")));
                disposal.setCreateTime(parseDatetime(r.get("创建时间")));
                disposal.setCreateBy(userNameToId.get(r.get("创建人")));
                disposal.setUpdateTime(disposal.getCreateTime());
                disposal.setUpdateBy(disposal.getCreateBy());
                qcDisposalMapper.insert(disposal);
                disposalNoToId.put(r.get("处理编号"), disposal.getId());

                // 回写 qcTask.disposalStatus
                if (disposal.getQcTaskId() != null) {
                    com.aimes.modules.quality.entity.BizQcTask qt = qcTaskMapper.selectById(disposal.getQcTaskId());
                    if (qt != null) {
                        qt.setDisposalStatus("disposed");
                        qcTaskMapper.updateById(qt);
                    }
                }

                disposalSuccess++;
            } catch (Exception e) {
                errors.add("第" + (i + 2) + "行(disposal): " + e.getMessage());
            }
        }

        // 导入缺陷记录
        if (defectFile != null && !defectFile.isEmpty()) {
            List<Map<String, String>> defectRows = ExcelUtil.parseImport(defectFile, DEFECT_HEADERS);
            for (int i = 0; i < defectRows.size(); i++) {
                Map<String, String> r = defectRows.get(i);
                try {
                    BizDefectRecord defect = new BizDefectRecord();
                    defect.setDefectNo(r.get("缺陷编号"));
                    defect.setSourceType(r.get("来源类型"));
                    defect.setProductId(materialNoToId.get(r.get("产品编号")));
                    defect.setWorkOrderId(woNoToId.get(r.get("工单编号")));
                    defect.setQuantity(parseBD(r.get("数量")));
                    defect.setDefectType(r.get("缺陷类型"));
                    defect.setDefectReason(r.get("缺陷原因"));
                    defect.setDefectLevel(r.get("缺陷等级"));
                    defect.setHandleStatus(r.get("处理状态"));
                    defect.setHandleResult(r.get("处理结果"));
                    defect.setHandlerId(userNameToId.get(r.get("处理人")));
                    defect.setHandledAt(parseDatetime(r.get("处理时间")));
                    defect.setCreateTime(parseDatetime(r.get("创建时间")));
                    defect.setCreateBy(userNameToId.get(r.get("创建人")));
                    defect.setUpdateTime(defect.getCreateTime());
                    defect.setUpdateBy(defect.getCreateBy());
                    defectRecordMapper.insert(defect);
                    defectSuccess++;
                } catch (Exception e) {
                    errors.add("第" + (i + 2) + "行(defect): " + e.getMessage());
                }
            }
        }

        return Map.of("disposalSuccess", disposalSuccess, "defectSuccess", defectSuccess, "errors", errors);
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
