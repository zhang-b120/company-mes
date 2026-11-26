package com.aimes.modules.bom.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.modules.bom.entity.BizProcess;
import com.aimes.modules.bom.entity.BizProcessRoute;
import com.aimes.modules.bom.entity.BizProcessRouteStep;
import com.aimes.modules.bom.mapper.BizProcessMapper;
import com.aimes.modules.bom.mapper.BizProcessRouteMapper;
import com.aimes.modules.bom.mapper.BizProcessRouteStepMapper;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.quality.entity.BizQcTemplate;
import com.aimes.modules.quality.mapper.BizQcTemplateMapper;
import com.aimes.modules.system.service.SysBizConfigService;
import com.aimes.modules.team.entity.BizTeam;
import com.aimes.modules.team.mapper.BizTeamMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizProcessRouteService extends ServiceImpl<BizProcessRouteMapper, BizProcessRoute> {

    private final BizProcessMapper processMapper;
    private final BizProcessRouteStepMapper routeStepMapper;
    private final BizMaterialMapper materialMapper;
    private final BizQcTemplateMapper qcTemplateMapper;
    private final BizTeamMapper teamMapper;

    @Autowired @Lazy
    private SysBizConfigService bizConfigService;

    // ===== 值解析工具方法：覆盖值 > 工序库默认值 =====

    private static <T> T resolve(T override, T defaultValue) {
        return override != null ? override : defaultValue;
    }

    /**
     * 分页查询工艺路线（支持关键词搜索和状态筛选）
     */
    public PageResult<Map<String, Object>> getPage(String keyword, String status, int pageNum, int pageSize) {
        return getPage(keyword, null, null, null, status, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getPage(String keyword, String routeName, String productName, String status, int pageNum, int pageSize) {
        return getPage(keyword, routeName, productName, null, status, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getPage(String keyword, String routeName, String productName, String productNo, String status, int pageNum, int pageSize) {
        // 按产品名称/编号筛选：先查物料表匹配的ID
        Set<Long> productIdFilter = null;
        boolean needProductFilter = StringUtils.hasText(productName) || StringUtils.hasText(productNo);
        if (needProductFilter) {
            LambdaQueryWrapper<BizMaterial> matWrapper = new LambdaQueryWrapper<BizMaterial>()
                    .like(StringUtils.hasText(productName), BizMaterial::getMaterialName, productName)
                    .like(StringUtils.hasText(productNo), BizMaterial::getMaterialNo, productNo);
            List<Long> matchedIds = materialMapper.selectList(matWrapper)
                    .stream().map(BizMaterial::getId).collect(java.util.stream.Collectors.toList());
            if (matchedIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            }
            productIdFilter = new java.util.HashSet<>(matchedIds);
        }

        LambdaQueryWrapper<BizProcessRoute> wrapper = new LambdaQueryWrapper<BizProcessRoute>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(BizProcessRoute::getRouteName, keyword))
                .like(StringUtils.hasText(routeName), BizProcessRoute::getRouteName, routeName)
                .eq(StringUtils.hasText(status), BizProcessRoute::getStatus, status)
                .orderByDesc(BizProcessRoute::getCreateTime);
        if (productIdFilter != null) {
            wrapper.in(BizProcessRoute::getProductId, productIdFilter);
        }

        Page<BizProcessRoute> page = page(new Page<>(pageNum, pageSize), wrapper);

        // 批量加载产品编号 + 名称
        List<BizProcessRoute> records = page.getRecords();
        Set<Long> productIds = records.stream().map(BizProcessRoute::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizMaterial> productMap = productIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        List<Map<String, Object>> list = records.stream().map(route -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", route.getId());
            map.put("routeName", route.getRouteName());
            map.put("productId", route.getProductId());
            BizMaterial mat = route.getProductId() != null ? productMap.get(route.getProductId()) : null;
            map.put("productNo", mat != null ? mat.getMaterialNo() : "");
            map.put("productName", mat != null ? mat.getMaterialName() : "");
            map.put("version", route.getVersionNo());
            map.put("status", route.getStatus());
            map.put("remark", route.getRemark());
            map.put("createTime", route.getCreateTime());

            // 工序步骤数
            long stepCount = routeStepMapper.selectCount(
                    new LambdaQueryWrapper<BizProcessRouteStep>().eq(BizProcessRouteStep::getRouteId, route.getId()));
            map.put("stepCount", stepCount);

            return map;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 根据产品查询工艺路线列表
     */
    public List<BizProcessRoute> getByProduct(Long productId) {
        return list(new LambdaQueryWrapper<BizProcessRoute>()
                .eq(BizProcessRoute::getProductId, productId)
                .orderByDesc(BizProcessRoute::getCreateTime));
    }

    /**
     * 查询工艺路线列表（含产品名称）
     * productId 为空时返回所有路线，否则返回指定产品的路线
     */
    public List<Map<String, Object>> getRoutesWithProductName(Long productId) {
        LambdaQueryWrapper<BizProcessRoute> wrapper = new LambdaQueryWrapper<BizProcessRoute>()
                .eq(productId != null, BizProcessRoute::getProductId, productId)
                .orderByDesc(BizProcessRoute::getCreateTime);
        List<BizProcessRoute> routes = list(wrapper);

        // 批量解析产品名称
        Set<Long> pIds = routes.stream().map(BizProcessRoute::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> productNameMap = pIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(pIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, BizMaterial::getMaterialName, (a, b) -> a));

        return routes.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("routeName", r.getRouteName());
            map.put("productId", r.getProductId());
            map.put("productName", r.getProductId() != null ? productNameMap.getOrDefault(r.getProductId(), "") : "");
            map.put("versionNo", r.getVersionNo());
            map.put("status", r.getStatus());
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 工艺路线详情（含工序步骤列表，值已解析：覆盖值 > 工序库默认值）
     */
    /**
     * 导出: 所有 active 状态工艺路线的工序明细(平铺)
     * 列: 产品编号, 产品名称, 工艺路线名称, 版本号, 工序序号, 工序名称, 标准工时, 计件单价, 班组, 备注
     */
    public List<Map<String, Object>> getActiveRoutesExportRows(String routeName, String productName) {
        List<BizProcessRoute> routes = list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizProcessRoute>()
                .eq(BizProcessRoute::getStatus, "active")
                .like(StringUtils.hasText(routeName), BizProcessRoute::getRouteName, routeName)
                .orderByAsc(BizProcessRoute::getProductId));
        if (routes.isEmpty()) return List.of();

        Set<Long> productIds = routes.stream().map(BizProcessRoute::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, com.aimes.modules.master.entity.BizMaterial> matMap = productIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(com.aimes.modules.master.entity.BizMaterial::getId, m -> m, (a, b) -> a));

        // 产品名筛选(可选)
        if (StringUtils.hasText(productName)) {
            String kw = productName.trim();
            Set<Long> matchProdIds = matMap.values().stream()
                    .filter(m -> m.getMaterialName() != null && m.getMaterialName().contains(kw))
                    .map(com.aimes.modules.master.entity.BizMaterial::getId).collect(Collectors.toSet());
            routes = routes.stream().filter(r -> r.getProductId() != null && matchProdIds.contains(r.getProductId()))
                    .collect(Collectors.toList());
            if (routes.isEmpty()) return List.of();
        }

        List<Long> routeIds = routes.stream().map(BizProcessRoute::getId).collect(Collectors.toList());
        List<BizProcessRouteStep> allSteps = routeStepMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizProcessRouteStep>()
                        .in(BizProcessRouteStep::getRouteId, routeIds)
                        .orderByAsc(BizProcessRouteStep::getRouteId)
                        .orderByAsc(BizProcessRouteStep::getStepNo));
        Map<Long, List<BizProcessRouteStep>> stepsByRoute = allSteps.stream()
                .collect(Collectors.groupingBy(BizProcessRouteStep::getRouteId, java.util.LinkedHashMap::new, Collectors.toList()));

        Set<Long> processIds = allSteps.stream().map(BizProcessRouteStep::getProcessId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizProcess> processMap = processIds.isEmpty() ? Map.of() :
                processMapper.selectBatchIds(processIds).stream()
                        .collect(Collectors.toMap(BizProcess::getId, p -> p, (a, b) -> a));
        Set<Long> teamIdSet = new java.util.HashSet<>();
        allSteps.forEach(s -> { if (s.getTeamId() != null) teamIdSet.add(s.getTeamId()); });
        processMap.values().forEach(p -> { if (p.getTeamId() != null) teamIdSet.add(p.getTeamId()); });
        Map<Long, String> teamNameMap = teamIdSet.isEmpty() ? Map.of() :
                teamMapper.selectBatchIds(teamIdSet).stream()
                        .collect(Collectors.toMap(BizTeam::getId, BizTeam::getTeamName, (a, b) -> a));

        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (BizProcessRoute route : routes) {
            com.aimes.modules.master.entity.BizMaterial mat = route.getProductId() != null ? matMap.get(route.getProductId()) : null;
            for (BizProcessRouteStep rs : stepsByRoute.getOrDefault(route.getId(), List.of())) {
                BizProcess proc = processMap.get(rs.getProcessId());
                Long teamId = rs.getTeamId() != null ? rs.getTeamId() : (proc != null ? proc.getTeamId() : null);
                Map<String, Object> r = new java.util.LinkedHashMap<>();
                r.put("productNo", mat != null ? mat.getMaterialNo() : "");
                r.put("productName", mat != null ? mat.getMaterialName() : "");
                r.put("routeName", route.getRouteName());
                r.put("versionNo", route.getVersionNo());
                r.put("stepNo", rs.getStepNo());
                r.put("stepName", proc != null ? proc.getProcessName() : null);
                r.put("standardTime", rs.getStandardTime() != null ? rs.getStandardTime() : (proc != null ? proc.getStandardTime() : null));
                r.put("piecePrice", rs.getPiecePrice() != null ? rs.getPiecePrice() : (proc != null ? proc.getPiecePrice() : null));
                r.put("teamName", teamId != null ? teamNameMap.getOrDefault(teamId, "") : "");
                r.put("remark", "");
                rows.add(r);
            }
        }
        return rows;
    }

    public Map<String, Object> getRouteDetail(Long routeId) {
        BizProcessRoute route = getById(routeId);
        if (route == null) {
            throw new BusinessException("工艺路线不存在");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", route.getId());
        detail.put("routeName", route.getRouteName());
        detail.put("productId", route.getProductId());
        detail.put("versionNo", route.getVersionNo());
        detail.put("status", route.getStatus());
        detail.put("remark", route.getRemark());
        detail.put("createBy", route.getCreateBy());
        detail.put("createTime", route.getCreateTime());

        // 查询关联表
        List<BizProcessRouteStep> routeSteps = routeStepMapper.selectList(
                new LambdaQueryWrapper<BizProcessRouteStep>()
                        .eq(BizProcessRouteStep::getRouteId, routeId)
                        .orderByAsc(BizProcessRouteStep::getStepNo));

        // 批量加载工序库
        Set<Long> processIds = routeSteps.stream()
                .map(BizProcessRouteStep::getProcessId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizProcess> processMap = processIds.isEmpty() ? Map.of() :
                processMapper.selectBatchIds(processIds).stream()
                        .collect(Collectors.toMap(BizProcess::getId, p -> p, (a, b) -> a));

        // 收集解析后的 qcTemplateId 和 teamId，用于批量查名称
        Set<Long> templateIds = new LinkedHashSet<>();
        Set<Long> allTeamIds = new LinkedHashSet<>();
        for (BizProcessRouteStep rs : routeSteps) {
            BizProcess proc = processMap.get(rs.getProcessId());
            Long resolvedQcTemplateId = resolve(rs.getQcTemplateId(), proc != null ? proc.getQcTemplateId() : null);
            Long resolvedTeamId = resolve(rs.getTeamId(), proc != null ? proc.getTeamId() : null);
            if (resolvedQcTemplateId != null) templateIds.add(resolvedQcTemplateId);
            if (resolvedTeamId != null) allTeamIds.add(resolvedTeamId);
        }

        Map<Long, String> templateNameMap = templateIds.isEmpty() ? Map.of() :
                qcTemplateMapper.selectBatchIds(templateIds).stream()
                        .collect(Collectors.toMap(BizQcTemplate::getId, BizQcTemplate::getTemplateName, (a, b) -> a));
        Map<Long, String> teamNameMap = allTeamIds.isEmpty() ? Map.of() :
                teamMapper.selectBatchIds(allTeamIds).stream()
                        .collect(Collectors.toMap(BizTeam::getId, BizTeam::getTeamName, (a, b) -> a));

        // 构建响应（值已解析）
        List<Map<String, Object>> stepList = routeSteps.stream().map(rs -> {
            BizProcess proc = processMap.get(rs.getProcessId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getId());
            m.put("routeId", rs.getRouteId());
            m.put("processId", rs.getProcessId());
            m.put("processName", proc != null ? proc.getProcessName() : "");
            m.put("stepName", proc != null ? proc.getProcessName() : ""); // 兼容别名
            m.put("stepNo", rs.getStepNo());
            m.put("stepGroup", rs.getStepGroup());

            Integer resolvedStandardTime = resolve(rs.getStandardTime(), proc != null ? proc.getStandardTime() : null);
            BigDecimal resolvedPiecePrice = resolve(rs.getPiecePrice(), proc != null ? proc.getPiecePrice() : null);
            Long resolvedTeamId = resolve(rs.getTeamId(), proc != null ? proc.getTeamId() : null);
            String resolvedSop = resolve(rs.getSopDescription(), proc != null ? proc.getSopDescription() : null);
            Integer resolvedSemiStock = resolve(rs.getIsSemiStock(), proc != null ? proc.getIsSemiStock() : null);
            Integer resolvedOutsourceable = resolve(rs.getIsOutsourceable(), proc != null ? proc.getIsOutsourceable() : null);
            Long resolvedQcTemplateId = resolve(rs.getQcTemplateId(), proc != null ? proc.getQcTemplateId() : null);

            m.put("standardTime", resolvedStandardTime);
            m.put("piecePrice", resolvedPiecePrice);
            m.put("teamId", resolvedTeamId);
            m.put("teamName", resolvedTeamId != null ? teamNameMap.get(resolvedTeamId) : null);
            m.put("sopDescription", resolvedSop);
            m.put("isSemiStock", resolvedSemiStock);
            m.put("isOutsourceable", resolvedOutsourceable);
            if(Objects.nonNull(resolvedQcTemplateId)){
                m.put("qcTemplateId", resolvedQcTemplateId);
                m.put("qcTemplateName", templateNameMap.getOrDefault(resolvedQcTemplateId, null));
            }
            return m;
        }).collect(Collectors.toList());
        detail.put("steps", stepList);

        return detail;
    }

    /**
     * 创建工艺路线（含工序步骤）
     */
    @Transactional
    public void createRoute(BizProcessRoute route) {
        if (route.getStatus() == null) {
            route.setStatus("active");
        }
        save(route);
        saveRouteSteps(route.getId(), route.getSteps());
    }

    /**
     * 更新工艺路线（含工序步骤：先删后建）
     */
    @Transactional
    public void updateRoute(Long id, BizProcessRoute route) {
        BizProcessRoute existing = getById(id);
        if (existing == null) {
            throw new BusinessException("工艺路线不存在");
        }
        route.setId(id);
        updateById(route);
        // 更新工序步骤：先删除旧的，再插入新的
        if (route.getSteps() != null) {
            routeStepMapper.delete(new LambdaQueryWrapper<BizProcessRouteStep>()
                    .eq(BizProcessRouteStep::getRouteId, id));
            saveRouteSteps(id, route.getSteps());
        }
    }

    /**
     * 添加工序步骤
     */
    @Transactional
    public void addStep(BizProcessRouteStep step) {
        BizProcessRoute route = getById(step.getRouteId());
        if (route == null) {
            throw new BusinessException("工艺路线不存在");
        }
        routeStepMapper.insert(step);
    }

    /**
     * 更新工序步骤
     */
    @Transactional
    public void updateStep(Long id, BizProcessRouteStep step) {
        BizProcessRouteStep existing = routeStepMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("工序步骤不存在");
        }
        step.setId(id);
        routeStepMapper.updateById(step);
    }

    /**
     * 删除工序步骤
     */
    @Transactional
    public void deleteStep(Long id) {
        BizProcessRouteStep existing = routeStepMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("工序步骤不存在");
        }
        routeStepMapper.deleteById(id);
    }

    // ===== 内部方法 =====

    private void saveRouteSteps(Long routeId, List<BizProcessRouteStep> steps) {
        if (steps == null || steps.isEmpty()) return;
        boolean reportStatistics = false;
        try {
            reportStatistics = bizConfigService.getBoolValue("production.report_statistics", false);
        } catch (Exception ignored) {}
        int stepNo = 10;
        int defaultGroup = 1;
        for (BizProcessRouteStep step : steps) {
            // processId 为空时，按 processName 查找或自动创建工序库条目
            if (step.getProcessId() == null && StringUtils.hasText(step.getProcessName())) {
                BizProcess existing = processMapper.selectOne(
                        new LambdaQueryWrapper<BizProcess>()
                                .eq(BizProcess::getProcessName, step.getProcessName())
                                .last("LIMIT 1"));
                if (existing != null) {
                    step.setProcessId(existing.getId());
                } else {
                    BizProcess newProc = new BizProcess();
                    newProc.setProcessName(step.getProcessName());
                    newProc.setStandardTime(step.getStandardTime());
                    newProc.setPiecePrice(step.getPiecePrice());
                    processMapper.insert(newProc);
                    step.setProcessId(newProc.getId());
                }
            }
            if (step.getProcessId() == null) {
                throw new BusinessException("工序 [" + step.getProcessName() + "] 未关联工序库，请选择或新建工序");
            }
            // 业务配置校验：报工统计启用时，计件单价必填
            if (reportStatistics && step.getPiecePrice() == null) {
                if (step.getProcessId() != null) {
                    BizProcess proc = processMapper.selectById(step.getProcessId());
                    if (proc == null || proc.getPiecePrice() == null) {
                        throw new BusinessException("员工报工统计已启用，工序[" + (step.getProcessName() != null ? step.getProcessName() : step.getProcessId()) + "]的计件单价为必填项");
                    }
                }
            }
            step.setId(null);
            step.setRouteId(routeId);
            step.setStepNo(stepNo);
            if (step.getStepGroup() == null) {
                step.setStepGroup(defaultGroup);
                defaultGroup++;
            }
            stepNo += 10;
            routeStepMapper.insert(step);
        }
    }
}
