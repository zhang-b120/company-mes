package com.aimes.modules.quality.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.quality.service.BizDefectRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/quality/defects")
@RequiredArgsConstructor
public class BizDefectRecordController {

    private final BizDefectRecordService defectRecordService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String defectNo,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String handleStatus,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String defectType,
            @RequestParam(required = false) String defectLevel,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        String effectiveStatus = handleStatus != null ? handleStatus : status;
        return R.ok(defectRecordService.getPage(defectNo, batchNo, effectiveStatus, defectType, defectLevel, pageNum, pageSize));
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        return R.ok(defectRecordService.getStats());
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        defectRecordService.create(body);
        return R.ok();
    }

    @PutMapping("/{id}/handle")
    public R<Void> handle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        defectRecordService.handle(id, body);
        return R.ok();
    }
}
