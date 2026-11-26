package com.aimes.modules.inventory.controller;

import com.aimes.common.result.R;
import com.aimes.modules.inventory.entity.BizQrTemplate;
import com.aimes.modules.inventory.service.BizQrTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/qr-templates")
@RequiredArgsConstructor
public class BizQrTemplateController {

    private final BizQrTemplateService qrTemplateService;

    @GetMapping("/list")
    public R<List<BizQrTemplate>> list(@RequestParam(required = false) String keyword) {
        return R.ok(qrTemplateService.list(keyword));
    }

    @GetMapping("/{id}")
    public R<BizQrTemplate> detail(@PathVariable Long id) {
        return R.ok(qrTemplateService.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody BizQrTemplate template) {
        qrTemplateService.create(template);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizQrTemplate template) {
        qrTemplateService.update(id, template);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        qrTemplateService.delete(id);
        return R.ok();
    }

    @PutMapping("/{id}/set-default")
    public R<Void> setDefault(@PathVariable Long id) {
        qrTemplateService.setDefault(id);
        return R.ok();
    }
}
