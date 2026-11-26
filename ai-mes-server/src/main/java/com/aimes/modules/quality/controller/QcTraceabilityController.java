package com.aimes.modules.quality.controller;

import com.aimes.common.result.R;
import com.aimes.modules.quality.service.BizQcTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/quality/traceability")
@RequiredArgsConstructor
public class QcTraceabilityController {

    private final BizQcTaskService qcTaskService;

    @GetMapping
    public R<Map<String, Object>> traceability(
            @RequestParam(required = false) String workOrderNo,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String batchNo) {
        return R.ok(qcTaskService.getTraceabilityByNo(workOrderNo, productName, batchNo));
    }
}
