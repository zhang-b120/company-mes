package com.aimes.modules.production.controller;

import com.aimes.common.result.R;
import com.aimes.common.result.PageResult;
import com.aimes.modules.production.entity.BizScheduling;
import com.aimes.modules.production.service.BizSchedulingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/production/scheduling")
@RequiredArgsConstructor
public class BizSchedulingController {

    private final BizSchedulingService schedulingService;

    @GetMapping
    public R<List<Map<String, Object>>> list(
            @RequestParam(required = false) Long workOrderId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return R.ok(schedulingService.getScheduleListEnriched(workOrderId, status, startDate, endDate));
    }

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) Long workOrderId,
            @RequestParam(required = false) Long workerId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(schedulingService.getPage(workOrderId, workerId, teamId, status, startDate, endDate, pageNum, pageSize));
    }

    @PostMapping
    public R<Void> schedule(@RequestBody BizScheduling scheduling) {
        schedulingService.schedule(scheduling);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizScheduling scheduling) {
        schedulingService.update(id, scheduling);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        schedulingService.delete(id);
        return R.ok();
    }
}
