package com.aimes.modules.system.controller;

import com.aimes.common.result.R;
import com.aimes.modules.system.entity.BizPrintTemplate;
import com.aimes.modules.system.service.BizPrintTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/print-templates")
@RequiredArgsConstructor
public class BizPrintTemplateController {

    private final BizPrintTemplateService templateService;

    /** 列表: 可按 bizType 过滤 */
    @GetMapping
    public R<List<BizPrintTemplate>> list(@RequestParam(required = false) String bizType) {
        return R.ok(templateService.listByBizType(bizType));
    }

    /** 取某业务类型当前 active 模板 (业务页面打印时用) */
    @GetMapping("/active")
    public R<BizPrintTemplate> getActive(@RequestParam String bizType) {
        return R.ok(templateService.getActive(bizType));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public R<BizPrintTemplate> detail(@PathVariable Long id) {
        return R.ok(templateService.getById(id));
    }

    /** 新增 */
    @PostMapping
    public R<Long> create(@RequestBody BizPrintTemplate template) {
        return R.ok(templateService.create(template));
    }

    /** 更新 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizPrintTemplate template) {
        templateService.update(id, template);
        return R.ok();
    }

    /** 激活 (同 bizType 下其他模板自动失活) */
    @PutMapping("/{id}/activate")
    public R<Void> activate(@PathVariable Long id) {
        templateService.activate(id);
        return R.ok();
    }

    /** 删除 (软删) */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return R.ok();
    }
}
