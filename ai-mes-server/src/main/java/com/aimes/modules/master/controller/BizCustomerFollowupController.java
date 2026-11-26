package com.aimes.modules.master.controller;

import com.aimes.common.result.R;
import com.aimes.modules.master.entity.BizCustomerFollowup;
import com.aimes.modules.master.service.BizCustomerFollowupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/master/customers/{customerId}/followups")
@RequiredArgsConstructor
public class BizCustomerFollowupController {

    private final BizCustomerFollowupService followupService;

    @GetMapping
    public R<List<BizCustomerFollowup>> list(@PathVariable Long customerId) {
        return R.ok(followupService.listByCustomer(customerId));
    }

    @PostMapping
    public R<Void> create(@PathVariable Long customerId, @RequestBody BizCustomerFollowup followup) {
        followupService.create(customerId, followup);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long customerId, @PathVariable Long id) {
        followupService.delete(id);
        return R.ok();
    }
}
