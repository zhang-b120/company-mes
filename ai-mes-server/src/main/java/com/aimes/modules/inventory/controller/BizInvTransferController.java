package com.aimes.modules.inventory.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.inventory.service.BizInvTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory/transfer")
@RequiredArgsConstructor
public class BizInvTransferController {

    private final BizInvTransferService transferService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(@RequestParam Map<String, Object> params) {
        return R.ok(transferService.getPage(params));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(transferService.getDetail(id));
    }

    @GetMapping("/by-no")
    public R<Map<String, Object>> detailByNo(@RequestParam String transferNo) {
        return R.ok(transferService.getDetailByNo(transferNo));
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> params) {
        transferService.create(params);
        return R.ok();
    }

    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String remark = body != null ? body.get("remark") : null;
        transferService.approve(id, remark);
        return R.ok();
    }

    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        transferService.reject(id, body.get("remark"));
        return R.ok();
    }
}
