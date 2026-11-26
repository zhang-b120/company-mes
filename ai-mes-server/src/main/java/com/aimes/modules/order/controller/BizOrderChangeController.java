package com.aimes.modules.order.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.order.entity.BizOrderChange;
import com.aimes.modules.order.service.BizOrderChangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order/changes")
@RequiredArgsConstructor
public class BizOrderChangeController {

    private final BizOrderChangeService orderChangeService;

    @GetMapping("/page")
    public R<PageResult<BizOrderChange>> page(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(orderChangeService.getPage(orderId, status, pageNum, pageSize));
    }

    @GetMapping
    public R<List<BizOrderChange>> list(@RequestParam Long orderId) {
        return R.ok(orderChangeService.listByOrderId(orderId));
    }

    @GetMapping("/{id}")
    public R<BizOrderChange> detail(@PathVariable Long id) {
        return R.ok(orderChangeService.getById(id));
    }

    /** 创建变更 (申请变更): 填类型+原因 → 拍快照 → status=draft, 不启动审批流 */
    @PostMapping
    public R<Map<String, Object>> create(@RequestBody BizOrderChange change) {
        Long changeId = orderChangeService.create(change);
        Map<String, Object> data = new HashMap<>();
        data.put("changeId", changeId);
        return R.ok(data);
    }

    /** 草稿保存 (允许多次): payload = {"order":{...}, "items":[...]} */
    @PutMapping("/{id}/draft")
    public R<Void> saveDraft(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        orderChangeService.saveDraft(id, payload);
        return R.ok();
    }

    /** 提交审批: status draft→pending, 启动通用审批流 */
    @PutMapping("/{id}/submit")
    public R<Map<String, Object>> submit(@PathVariable Long id) {
        Long instanceId = orderChangeService.submitChange(id);
        Map<String, Object> data = new HashMap<>();
        data.put("instanceId", instanceId);
        data.put("approvalEnabled", instanceId != null);
        return R.ok(data);
    }

    /** 放弃草稿 */
    @DeleteMapping("/{id}/draft")
    public R<Void> discardDraft(@PathVariable Long id) {
        orderChangeService.discardDraft(id);
        return R.ok();
    }

    /** 审批 diff 视图: 返回 original + proposed JSON */
    @GetMapping("/{id}/diff")
    public R<Map<String, Object>> diff(@PathVariable Long id) {
        return R.ok(orderChangeService.getDiff(id));
    }

    /** 兼容: 直接 approve/reject (订单详情页变更记录 tab 上的按钮) */
    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String remark = body != null ? body.get("remark") : null;
        orderChangeService.approve(id, remark);
        return R.ok();
    }

    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String remark = body != null ? body.get("remark") : null;
        orderChangeService.reject(id, remark);
        return R.ok();
    }
}
