package com.aimes.modules.master.controller;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.bom.entity.BizProcess;
import com.aimes.modules.bom.entity.BizProcessRoute;
import com.aimes.modules.bom.entity.BizProcessRouteStep;
import com.aimes.modules.bom.mapper.BizProcessMapper;
import com.aimes.modules.bom.mapper.BizProcessRouteMapper;
import com.aimes.modules.bom.mapper.BizProcessRouteStepMapper;
import com.aimes.modules.system.service.SysBizConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工序库控制器
 * 管理可复用的工序定义（基于 biz_process 表）
 */
@RestController
@RequestMapping("/api/master/processes")
@RequiredArgsConstructor
public class BizProcessController {

    private final BizProcessMapper processMapper;
    private final BizProcessRouteStepMapper routeStepMapper;
    private final BizProcessRouteMapper routeMapper;
    private final SysBizConfigService bizConfigService;
    private final com.aimes.modules.dataimport.service.DataImportService dataImportService;

    @GetMapping("/page")
    public R<PageResult<BizProcess>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        LambdaQueryWrapper<BizProcess> wrapper = new LambdaQueryWrapper<BizProcess>()
                .like(StringUtils.hasText(keyword), BizProcess::getProcessName, keyword)
                .orderByDesc(BizProcess::getCreateTime);
        Page<BizProcess> page = processMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    @GetMapping("/select")
    public R<List<Map<String, Object>>> select() {
        List<BizProcess> processes = processMapper.selectList(
                new LambdaQueryWrapper<BizProcess>()
                        .orderByAsc(BizProcess::getProcessName));
        List<Map<String, Object>> options = processes.stream().map(p -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", p.getId());
            map.put("name", p.getProcessName());
            map.put("piecePrice", p.getPiecePrice());
            map.put("standardTime", p.getStandardTime());
            map.put("teamId", p.getTeamId());
            map.put("isSemiStock", p.getIsSemiStock());
            map.put("isOutsourceable", p.getIsOutsourceable());
            map.put("bindType", p.getBindType());
            map.put("bindIds", p.getBindIds());
            return map;
        }).collect(Collectors.toList());
        return R.ok(options);
    }

    @PostMapping
    public R<Void> create(@RequestBody BizProcess process) {
        if (bizConfigService.getBoolValue("production.report_statistics", false)) {
            if (process.getPiecePrice() == null) {
                throw new BusinessException("员工报工统计已启用，计件单价为必填项");
            }
        }
        processMapper.insert(process);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizProcess process) {
        if (bizConfigService.getBoolValue("production.report_statistics", false)) {
            if (process.getPiecePrice() == null) {
                throw new BusinessException("员工报工统计已启用，计件单价为必填项");
            }
        }
        process.setId(id);
        processMapper.updateById(process);
        return R.ok();
    }

    @GetMapping("/{id}/references")
    public R<List<Map<String, Object>>> references(@PathVariable Long id) {
        // 查找引用此工序的所有工艺路线工序关联
        List<BizProcessRouteStep> refs = routeStepMapper.selectList(
                new LambdaQueryWrapper<BizProcessRouteStep>()
                        .eq(BizProcessRouteStep::getProcessId, id));
        // 批量带出工艺路线名称(避免 N+1)
        java.util.Set<Long> routeIds = refs.stream()
                .map(BizProcessRouteStep::getRouteId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, String> routeNameMap = routeIds.isEmpty() ? Map.of()
                : routeMapper.selectBatchIds(routeIds).stream()
                        .collect(Collectors.toMap(BizProcessRoute::getId,
                                r -> r.getRouteName() != null ? r.getRouteName() : "",
                                (a, b) -> a));
        List<Map<String, Object>> result = refs.stream().map(rs -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", rs.getId());
            map.put("routeId", rs.getRouteId());
            map.put("routeName", rs.getRouteId() != null ? routeNameMap.getOrDefault(rs.getRouteId(), "") : "");
            map.put("stepNo", rs.getStepNo());
            map.put("processId", rs.getProcessId());
            return map;
        }).collect(Collectors.toList());
        return R.ok(result);
    }

    @GetMapping("/export")
    public R<List<BizProcess>> export() {
        return R.ok(processMapper.selectList(
                new LambdaQueryWrapper<BizProcess>().orderByAsc(BizProcess::getProcessName)));
    }

    @PostMapping("/import")
    public R<Map<String, Object>> importProcesses(@org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return R.ok(dataImportService.importProcesses(file));
    }

    @GetMapping("/import-template")
    public void importTemplate(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        dataImportService.exportTemplate("process", response);
    }
}
