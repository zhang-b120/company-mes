package com.aimes.modules.eam.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.eam.entity.EamMaintenancePlan;
import com.aimes.modules.eam.entity.EamMaintenanceTask;
import com.aimes.modules.eam.entity.EamMaintenanceTaskDetail;
import com.aimes.modules.eam.entity.EamMaintenanceTemplate;
import com.aimes.modules.eam.service.EamMaintenancePlanService;
import com.aimes.modules.eam.service.EamMaintenanceTaskService;
import com.aimes.modules.eam.service.EamMaintenanceTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/eam/maintenance")
@RequiredArgsConstructor
public class EamMaintenanceController {

    private final EamMaintenanceTemplateService templateService;
    private final EamMaintenancePlanService planService;
    private final EamMaintenanceTaskService taskService;

    // ========== 保养模板 F3 ==========

    @GetMapping("/templates/page")
    public R<PageResult<EamMaintenanceTemplate>> templatePage(@RequestParam(required = false) String templateName,
                                                                @RequestParam(required = false) String equipmentType,
                                                                @RequestParam(defaultValue = "1") int pageNum,
                                                                @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(templateService.getPage(templateName, equipmentType, pageNum, pageSize));
    }

    @GetMapping("/templates/list")
    public R<List<EamMaintenanceTemplate>> templateList() {
        return R.ok(templateService.list());
    }

    @GetMapping("/templates/{id}")
    public R<Map<String, Object>> templateDetail(@PathVariable Long id) {
        return R.ok(templateService.getDetail(id));
    }

    @PostMapping("/templates")
    public R<Void> createTemplate(@RequestBody Map<String, Object> body) {
        templateService.createWithItems(body);
        return R.ok();
    }

    @PutMapping("/templates/{id}")
    public R<Void> updateTemplate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        templateService.updateWithItems(id, body);
        return R.ok();
    }

    @DeleteMapping("/templates/{id}")
    public R<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return R.ok();
    }

    // ========== 保养计划 F4 ==========

    @GetMapping("/plans/page")
    public R<PageResult<EamMaintenancePlan>> planPage(@RequestParam(required = false) String planName,
                                                        @RequestParam(required = false) Integer maintenanceType,
                                                        @RequestParam(required = false) Integer planStatus,
                                                        @RequestParam(defaultValue = "1") int pageNum,
                                                        @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(planService.getPage(planName, maintenanceType, planStatus, pageNum, pageSize));
    }

    @GetMapping("/plans/{id}")
    public R<EamMaintenancePlan> planDetail(@PathVariable Long id) {
        return R.ok(planService.getById(id));
    }

    @PostMapping("/plans")
    public R<Void> createPlan(@RequestBody EamMaintenancePlan plan) {
        planService.create(plan);
        return R.ok();
    }

    @PutMapping("/plans/{id}")
    public R<Void> updatePlan(@PathVariable Long id, @RequestBody EamMaintenancePlan plan) {
        planService.update(id, plan);
        return R.ok();
    }

    @DeleteMapping("/plans/{id}")
    public R<Void> deletePlan(@PathVariable Long id) {
        planService.delete(id);
        return R.ok();
    }

    @PostMapping("/plans/{id}/publish")
    public R<Void> publishPlan(@PathVariable Long id) {
        planService.publish(id);
        return R.ok();
    }

    @PostMapping("/plans/{id}/pause")
    public R<Void> pausePlan(@PathVariable Long id) {
        planService.pause(id);
        return R.ok();
    }

    @PostMapping("/plans/{id}/cancel")
    public R<Void> cancelPlan(@PathVariable Long id) {
        planService.cancel(id);
        return R.ok();
    }

    @PutMapping("/plans/{id}/tasks")
    public R<Map<String, Object>> assignTasks(@PathVariable Long id) {
        List<String> taskCodes = planService.assignTasks(id);
        return R.ok(Map.of("generatedTaskCount", taskCodes.size(), "taskCodes", taskCodes));
    }

    // ========== 保养任务 F5 ==========

    @GetMapping("/tasks/page")
    public R<PageResult<Map<String, Object>>> taskPage(@RequestParam(required = false) String taskCode,
                                                        @RequestParam(required = false) Long planId,
                                                        @RequestParam(required = false) Long equipmentId,
                                                        @RequestParam(required = false) Integer taskStatus,
                                                        @RequestParam(defaultValue = "1") int pageNum,
                                                        @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(taskService.getPage(taskCode, planId, equipmentId, taskStatus, pageNum, pageSize));
    }

    @GetMapping("/tasks/{id}")
    public R<Map<String, Object>> taskDetail(@PathVariable Long id) {
        return R.ok(taskService.getDetail(id));
    }

    @PostMapping("/tasks/{id}/start")
    public R<Void> startTask(@PathVariable Long id) {
        taskService.start(id);
        return R.ok();
    }

    @PostMapping("/tasks/{id}/pause")
    public R<Void> pauseTask(@PathVariable Long id) {
        taskService.pause(id);
        return R.ok();
    }

    @PostMapping("/tasks/{id}/cancel")
    public R<Void> cancelTask(@PathVariable Long id) {
        taskService.cancel(id);
        return R.ok();
    }

    @PostMapping("/tasks/{id}/complete")
    public R<Void> completeTask(@PathVariable Long id, @RequestBody(required = false) EamMaintenanceTask payload) {
        taskService.complete(id, payload);
        return R.ok();
    }

    @PutMapping("/tasks/details/{detailId}")
    public R<Void> updateDetail(@PathVariable Long detailId, @RequestBody EamMaintenanceTaskDetail payload) {
        taskService.updateDetail(detailId, payload);
        return R.ok();
    }

    @GetMapping("/tasks/statistics")
    public R<Map<String, Object>> taskStatistics() {
        return R.ok(taskService.getStatistics());
    }
}
