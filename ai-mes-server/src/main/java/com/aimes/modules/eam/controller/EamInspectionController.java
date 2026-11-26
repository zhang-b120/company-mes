package com.aimes.modules.eam.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.eam.entity.*;
import com.aimes.modules.eam.service.EamInspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/eam/inspection")
@RequiredArgsConstructor
public class EamInspectionController {

    private final EamInspectionService service;

    // ========== 点检项目库 ==========

    @GetMapping("/items/page")
    public R<PageResult<EamInspectionItem>> itemPage(@RequestParam(required = false) String itemName,
                                                      @RequestParam(required = false) String itemType,
                                                      @RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.itemPage(itemName, itemType, pageNum, pageSize));
    }

    @GetMapping("/items/list")
    public R<List<EamInspectionItem>> itemList() { return R.ok(service.itemList()); }

    @PostMapping("/items")
    public R<Void> createItem(@RequestBody EamInspectionItem item) { service.createItem(item); return R.ok(); }

    @PutMapping("/items/{id}")
    public R<Void> updateItem(@PathVariable Long id, @RequestBody EamInspectionItem item) { service.updateItem(id, item); return R.ok(); }

    @DeleteMapping("/items/{id}")
    public R<Void> deleteItem(@PathVariable Long id) { service.deleteItem(id); return R.ok(); }

    // ========== 点检模板 ==========

    @GetMapping("/templates/page")
    public R<PageResult<EamInspectionTemplate>> templatePage(@RequestParam(required = false) String templateName,
                                                               @RequestParam(required = false) Integer inspectionType,
                                                               @RequestParam(defaultValue = "1") int pageNum,
                                                               @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.templatePage(templateName, inspectionType, pageNum, pageSize));
    }

    @GetMapping("/templates/{id}")
    public R<Map<String, Object>> templateDetail(@PathVariable Long id) { return R.ok(service.templateDetail(id)); }

    @PostMapping("/templates")
    public R<Void> createTemplate(@RequestBody Map<String, Object> body) { service.createTemplate(body); return R.ok(); }

    @PutMapping("/templates/{id}")
    public R<Void> updateTemplate(@PathVariable Long id, @RequestBody Map<String, Object> body) { service.updateTemplate(id, body); return R.ok(); }

    @DeleteMapping("/templates/{id}")
    public R<Void> deleteTemplate(@PathVariable Long id) { service.deleteTemplate(id); return R.ok(); }

    // ========== 点检计划 ==========

    @GetMapping("/plans/page")
    public R<PageResult<EamInspectionPlan>> planPage(@RequestParam(required = false) String planName,
                                                       @RequestParam(required = false) Integer planStatus,
                                                       @RequestParam(defaultValue = "1") int pageNum,
                                                       @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.planPage(planName, planStatus, pageNum, pageSize));
    }

    @PostMapping("/plans")
    public R<Void> createPlan(@RequestBody EamInspectionPlan plan) { service.createPlan(plan); return R.ok(); }

    @PutMapping("/plans/{id}")
    public R<Void> updatePlan(@PathVariable Long id, @RequestBody EamInspectionPlan plan) { service.updatePlan(id, plan); return R.ok(); }

    @DeleteMapping("/plans/{id}")
    public R<Void> deletePlan(@PathVariable Long id) { service.deletePlan(id); return R.ok(); }

    @PostMapping("/plans/{id}/publish")
    public R<Void> publishPlan(@PathVariable Long id) { service.publishPlan(id); return R.ok(); }

    @PostMapping("/plans/{id}/cancel")
    public R<Void> cancelPlan(@PathVariable Long id) { service.cancelPlan(id); return R.ok(); }

    @PutMapping("/plans/{id}/tasks")
    public R<Map<String, Object>> assignTasks(@PathVariable Long id) {
        List<String> taskCodes = service.assignPlanTasks(id);
        return R.ok(Map.of("generatedTaskCount", taskCodes.size(), "taskCodes", taskCodes));
    }

    // ========== 点检任务 ==========

    @GetMapping("/tasks/page")
    public R<PageResult<Map<String, Object>>> taskPage(@RequestParam(required = false) String taskCode,
                                                        @RequestParam(required = false) Long planId,
                                                        @RequestParam(required = false) Long equipmentId,
                                                        @RequestParam(required = false) Integer taskStatus,
                                                        @RequestParam(defaultValue = "1") int pageNum,
                                                        @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.taskPage(taskCode, planId, equipmentId, taskStatus, pageNum, pageSize));
    }

    @GetMapping("/tasks/{id}")
    public R<Map<String, Object>> taskDetail(@PathVariable Long id) { return R.ok(service.taskDetail(id)); }

    @PostMapping("/tasks/{id}/start")
    public R<Void> startTask(@PathVariable Long id) { service.startTask(id); return R.ok(); }

    @PostMapping("/tasks/{id}/cancel")
    public R<Void> cancelTask(@PathVariable Long id) { service.cancelTask(id); return R.ok(); }

    @PostMapping("/tasks/{id}/complete")
    public R<Map<String, Object>> completeTask(@PathVariable Long id, @RequestBody(required = false) EamInspectionTask payload) {
        return R.ok(service.completeTask(id, payload));
    }

    @PutMapping("/tasks/details/{detailId}")
    public R<Void> updateTaskDetail(@PathVariable Long detailId, @RequestBody EamInspectionTaskDetail payload) {
        service.updateTaskDetail(detailId, payload); return R.ok();
    }
}
