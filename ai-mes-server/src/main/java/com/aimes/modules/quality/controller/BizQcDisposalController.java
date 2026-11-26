package com.aimes.modules.quality.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.quality.service.BizQcDisposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/quality/disposals")
@RequiredArgsConstructor
public class BizQcDisposalController {

    private final BizQcDisposalService disposalService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String disposalNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String disposalType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(disposalService.getPage(disposalNo, status, disposalType, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(disposalService.getDetail(id));
    }

    @PutMapping("/{id}/process")
    public R<Void> process(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        disposalService.processDisposal(id, body);
        return R.ok();
    }
}
