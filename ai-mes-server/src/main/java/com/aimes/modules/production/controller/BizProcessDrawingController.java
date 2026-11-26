package com.aimes.modules.production.controller;

import com.aimes.common.result.R;
import com.aimes.modules.production.entity.BizProcessDrawing;
import com.aimes.modules.production.service.BizProcessDrawingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/production/drawings")
@RequiredArgsConstructor
public class BizProcessDrawingController {

    private final BizProcessDrawingService service;

    /** 排产场景：按计划工序ID查询图纸 */
    @GetMapping("/by-plan-step")
    public R<List<BizProcessDrawing>> listByPlanStep(@RequestParam Long planItemStepId) {
        return R.ok(service.listByPlanItemStep(planItemStepId));
    }

    /** 工单场景：按工单工序任务ID查询图纸（含所有版本） */
    @GetMapping("/by-task")
    public R<List<BizProcessDrawing>> listByTask(@RequestParam Long processTaskId) {
        return R.ok(service.listByProcessTask(processTaskId));
    }

    /** 计划工序是否可编辑（false 表示已派发工单，禁止编辑） */
    @GetMapping("/plan-step-editable")
    public R<Boolean> planStepEditable(@RequestParam Long planItemStepId) {
        return R.ok(service.planItemStepEditable(planItemStepId));
    }

    /** 上传图纸（按 payload 中 planItemStepId / processTaskId 自动分流） */
    @PostMapping
    public R<BizProcessDrawing> upload(@RequestBody BizProcessDrawing payload) {
        return R.ok(service.upload(payload));
    }

    /** 删除图纸 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.deleteDrawing(id);
        return R.ok();
    }

    /**
     * 工单图纸摘要：传 processTaskIds (逗号分隔)，
     * 返回 { taskId → { latest, total, hasNewVersion } } 用于工序行渲染图纸状态及"图纸变更"角标
     */
    @GetMapping("/wo-summary")
    public R<Map<Long, Map<String, Object>>> woSummary(@RequestParam String processTaskIds) {
        List<Long> ids = Arrays.stream(processTaskIds.split(","))
                .filter(s -> !s.isBlank())
                .map(s -> Long.valueOf(s.trim()))
                .toList();
        return R.ok(service.summaryByTasks(ids));
    }
}
