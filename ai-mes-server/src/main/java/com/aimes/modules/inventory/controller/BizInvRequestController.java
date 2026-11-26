package com.aimes.modules.inventory.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.inventory.service.BizInvRequestService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/requests")
@RequiredArgsConstructor
public class BizInvRequestController {

    private final BizInvRequestService requestService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> getPage(
            @RequestParam(required = false) String requestNo,
            @RequestParam(required = false) String invType,
            @RequestParam(required = false) String invTypeGroup,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String relatedOrderNo,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String dateStart,
            @RequestParam(required = false) String dateEnd,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(requestService.getPage(requestNo, invType, invTypeGroup, status, relatedOrderNo,
                keyword, customerName, customerId, dateStart, dateEnd, pageNum, pageSize));
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String requestNo,
            @RequestParam(required = false) String invType,
            @RequestParam(required = false) String invTypeGroup,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String relatedOrderNo,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String dateStart,
            @RequestParam(required = false) String dateEnd,
            HttpServletResponse response) throws IOException {
        requestService.export(requestNo, invType, invTypeGroup, status, relatedOrderNo,
                customerName, customerId, dateStart, dateEnd, response);
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> getDetail(@PathVariable Long id) {
        return R.ok(requestService.getDetail(id));
    }

    @GetMapping("/by-no")
    public R<Map<String, Object>> getDetailByNo(@RequestParam String requestNo) {
        return R.ok(requestService.getDetailByNo(requestNo));
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        requestService.create(body);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requestService.update(id, body);
        return R.ok();
    }

    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id) {
        requestService.approve(id);
        return R.ok();
    }

    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;
        requestService.reject(id, remark);
        return R.ok();
    }
}
