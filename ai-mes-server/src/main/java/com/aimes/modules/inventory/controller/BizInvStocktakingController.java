package com.aimes.modules.inventory.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.inventory.service.BizInvStocktakingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory/stocktaking")
@RequiredArgsConstructor
public class BizInvStocktakingController {

    private final BizInvStocktakingService stocktakingService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(@RequestParam Map<String, Object> params) {
        return R.ok(stocktakingService.getPage(params));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(stocktakingService.getDetail(id));
    }

    @GetMapping("/by-no")
    public R<Map<String, Object>> detailByNo(@RequestParam String stocktakingNo) {
        return R.ok(stocktakingService.getDetailByNo(stocktakingNo));
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> params) {
        stocktakingService.create(params);
        return R.ok();
    }

    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String remark = body != null ? body.get("remark") : null;
        stocktakingService.approve(id, remark);
        return R.ok();
    }

    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        stocktakingService.reject(id, body.get("remark"));
        return R.ok();
    }
}
