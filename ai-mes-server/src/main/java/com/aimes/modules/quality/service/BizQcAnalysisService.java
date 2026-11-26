package com.aimes.modules.quality.service;

import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.quality.entity.BizDefectRecord;
import com.aimes.modules.quality.entity.BizQcTask;
import com.aimes.modules.quality.mapper.BizDefectRecordMapper;
import com.aimes.modules.quality.mapper.BizQcTaskMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizQcAnalysisService {

    private final BizQcTaskMapper qcTaskMapper;
    private final BizDefectRecordMapper defectRecordMapper;
    private final BizMaterialMapper materialMapper;
    private final SysUserMapper userMapper;

    /** 判断质检任务是否为合格（pass 或 completed 且无不合格数） */
    private boolean isPass(BizQcTask task) {
        String r = task.getResult();
        if ("pass".equals(r)) return true;
        if ("completed".equals(r)) return task.getFailQty() == null || task.getFailQty().compareTo(BigDecimal.ZERO) <= 0;
        return false;
    }

    /** 判断质检任务是否为不合格（fail 或 completed 且有不合格数） */
    private boolean isFail(BizQcTask task) {
        String r = task.getResult();
        if ("fail".equals(r)) return true;
        if ("completed".equals(r)) return task.getFailQty() != null && task.getFailQty().compareTo(BigDecimal.ZERO) > 0;
        return false;
    }

    /**
     * 总览统计
     */
    public Map<String, Object> getOverview() {
        Map<String, Object> result = new LinkedHashMap<>();

        List<BizQcTask> allTasks = qcTaskMapper.selectList(new LambdaQueryWrapper<BizQcTask>()
                .eq(BizQcTask::getDeleted, 0));
        result.put("totalTasks", allTasks.size());

        long passCount = allTasks.stream().filter(this::isPass).count();
        result.put("passCount", passCount);

        long failCount = allTasks.stream().filter(this::isFail).count();
        result.put("failCount", failCount);

        long pendingCount = allTasks.stream().filter(t -> "pending".equals(t.getResult())).count();
        result.put("pendingCount", pendingCount);

        long completed = passCount + failCount;
        BigDecimal passRate = completed > 0
                ? BigDecimal.valueOf(passCount * 100).divide(BigDecimal.valueOf(completed), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        result.put("passRate", passRate);

        long defectTotal = defectRecordMapper.selectCount(new LambdaQueryWrapper<BizDefectRecord>()
                .eq(BizDefectRecord::getDeleted, 0));
        result.put("defectTotal", defectTotal);

        long defectPending = defectRecordMapper.selectCount(new LambdaQueryWrapper<BizDefectRecord>()
                .eq(BizDefectRecord::getDeleted, 0)
                .eq(BizDefectRecord::getHandleStatus, "pending"));
        result.put("defectPending", defectPending);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayTasks = allTasks.stream()
                .filter(t -> t.getCreateTime() != null && !t.getCreateTime().isBefore(todayStart))
                .count();
        result.put("todayTasks", todayTasks);

        return result;
    }

    /**
     * 合格率趋势（按天）
     */
    public Map<String, Object> getPassRateTrend(int days, String qcType) {
        LocalDateTime startTime = LocalDate.now().minusDays(days).atStartOfDay();

        LambdaQueryWrapper<BizQcTask> wrapper = new LambdaQueryWrapper<BizQcTask>()
                .eq(BizQcTask::getDeleted, 0)
                .in(BizQcTask::getResult, "pass", "fail", "completed")
                .ge(BizQcTask::getInspectedAt, startTime);
        if (StringUtils.hasText(qcType)) {
            wrapper.eq(BizQcTask::getQcType, qcType);
        }
        wrapper.orderByAsc(BizQcTask::getInspectedAt);

        List<BizQcTask> tasks = qcTaskMapper.selectList(wrapper);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        Map<String, long[]> dayMap = new LinkedHashMap<>();
        for (int i = days; i >= 0; i--) {
            String key = LocalDate.now().minusDays(i).format(fmt);
            dayMap.put(key, new long[]{0, 0});
        }

        for (BizQcTask task : tasks) {
            if (task.getInspectedAt() == null) continue;
            String key = task.getInspectedAt().toLocalDate().format(fmt);
            long[] counts = dayMap.get(key);
            if (counts != null) {
                counts[1]++;
                if (isPass(task)) {
                    counts[0]++;
                }
            }
        }

        List<String> dates = new ArrayList<>(dayMap.keySet());
        List<BigDecimal> rates = new ArrayList<>();
        List<Long> totals = new ArrayList<>();
        for (long[] counts : dayMap.values()) {
            totals.add(counts[1]);
            rates.add(counts[1] > 0
                    ? BigDecimal.valueOf(counts[0] * 100).divide(BigDecimal.valueOf(counts[1]), 1, RoundingMode.HALF_UP)
                    : null);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("passRates", rates);
        result.put("taskCounts", totals);
        return result;
    }

    /**
     * 缺陷分布统计
     */
    public Map<String, Object> getDefectDistribution(int days) {
        LocalDateTime startTime = LocalDate.now().minusDays(days).atStartOfDay();

        List<BizDefectRecord> defects = defectRecordMapper.selectList(
                new LambdaQueryWrapper<BizDefectRecord>()
                        .eq(BizDefectRecord::getDeleted, 0)
                        .ge(BizDefectRecord::getCreateTime, startTime));

        Map<String, Long> byType = defects.stream()
                .filter(d -> d.getDefectType() != null)
                .collect(Collectors.groupingBy(BizDefectRecord::getDefectType, Collectors.counting()));

        Map<String, Long> byLevel = defects.stream()
                .filter(d -> d.getDefectLevel() != null)
                .collect(Collectors.groupingBy(BizDefectRecord::getDefectLevel, Collectors.counting()));

        Map<String, Long> byStatus = defects.stream()
                .filter(d -> d.getHandleStatus() != null)
                .collect(Collectors.groupingBy(BizDefectRecord::getHandleStatus, Collectors.counting()));

        Map<String, Long> byPath = defects.stream()
                .filter(d -> d.getHandlePath() != null)
                .collect(Collectors.groupingBy(BizDefectRecord::getHandlePath, Collectors.counting()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("byType", byType);
        result.put("byLevel", byLevel);
        result.put("byStatus", byStatus);
        result.put("byPath", byPath);
        result.put("total", defects.size());
        return result;
    }

    /**
     * 产品质量排名
     */
    public Map<String, Object> getProductQualityRanking(int days, int top) {
        LocalDateTime startTime = LocalDate.now().minusDays(days).atStartOfDay();

        List<BizQcTask> tasks = qcTaskMapper.selectList(
                new LambdaQueryWrapper<BizQcTask>()
                        .eq(BizQcTask::getDeleted, 0)
                        .in(BizQcTask::getResult, "pass", "fail", "completed")
                        .ge(BizQcTask::getInspectedAt, startTime));

        Map<Long, long[]> productStats = new LinkedHashMap<>();
        for (BizQcTask task : tasks) {
            Long productId = task.getProductId();
            if (productId == null) continue;
            long[] counts = productStats.computeIfAbsent(productId, k -> new long[]{0, 0});
            counts[1]++;
            if (isPass(task)) {
                counts[0]++;
            }
        }

        Set<Long> productIds = productStats.keySet();
        Map<Long, String> nameMap = productIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, BizMaterial::getMaterialName, (a, b) -> a));

        List<Map<String, Object>> ranking = productStats.entrySet().stream()
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("productId", e.getKey());
                    item.put("productName", nameMap.getOrDefault(e.getKey(), "未知产品"));
                    item.put("totalTasks", e.getValue()[1]);
                    item.put("passCount", e.getValue()[0]);
                    item.put("failCount", e.getValue()[1] - e.getValue()[0]);
                    BigDecimal rate = e.getValue()[1] > 0
                            ? BigDecimal.valueOf(e.getValue()[0] * 100).divide(BigDecimal.valueOf(e.getValue()[1]), 1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    item.put("passRate", rate);
                    return item;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("passRate")).compareTo((BigDecimal) a.get("passRate")))
                .limit(top)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ranking", ranking);
        return result;
    }

    /**
     * 检验员绩效统计
     */
    public Map<String, Object> getInspectorStats(int days) {
        LocalDateTime startTime = LocalDate.now().minusDays(days).atStartOfDay();

        List<BizQcTask> tasks = qcTaskMapper.selectList(
                new LambdaQueryWrapper<BizQcTask>()
                        .eq(BizQcTask::getDeleted, 0)
                        .in(BizQcTask::getResult, "pass", "fail", "completed")
                        .isNotNull(BizQcTask::getInspectorId)
                        .ge(BizQcTask::getInspectedAt, startTime));

        Map<Long, long[]> inspectorStats = new LinkedHashMap<>();
        for (BizQcTask task : tasks) {
            Long inspectorId = task.getInspectorId();
            if (inspectorId == null) continue;
            long[] counts = inspectorStats.computeIfAbsent(inspectorId, k -> new long[]{0, 0});
            counts[1]++;
            if (isPass(task)) {
                counts[0]++;
            }
        }

        Set<Long> inspectorIds = inspectorStats.keySet();
        Map<Long, String> nameMap = inspectorIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(inspectorIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));

        List<Map<String, Object>> stats = inspectorStats.entrySet().stream()
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("inspectorId", e.getKey());
                    item.put("inspectorName", nameMap.getOrDefault(e.getKey(), "未知"));
                    item.put("totalTasks", e.getValue()[1]);
                    item.put("passCount", e.getValue()[0]);
                    item.put("failCount", e.getValue()[1] - e.getValue()[0]);
                    BigDecimal rate = e.getValue()[1] > 0
                            ? BigDecimal.valueOf(e.getValue()[0] * 100).divide(BigDecimal.valueOf(e.getValue()[1]), 1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    item.put("passRate", rate);
                    return item;
                })
                .sorted((a, b) -> Long.compare((long) b.get("totalTasks"), (long) a.get("totalTasks")))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stats", stats);
        return result;
    }
}
