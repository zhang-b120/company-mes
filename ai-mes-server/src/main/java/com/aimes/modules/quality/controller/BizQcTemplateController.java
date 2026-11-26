package com.aimes.modules.quality.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.quality.entity.BizQcTemplate;
import com.aimes.modules.quality.service.BizQcTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quality/templates")
@RequiredArgsConstructor
public class BizQcTemplateController {

    private final BizQcTemplateService templateService;

    @GetMapping("/select")
    public R<List<Map<String, Object>>> select() {
        return R.ok(templateService.getSelectList());
    }

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String templateName,
            @RequestParam(required = false) String templateCode,
            @RequestParam(required = false) String applyType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(templateService.getPage(templateName, templateCode, applyType, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(templateService.getDetail(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        templateService.createWithItems(body);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        templateService.updateWithItems(id, body);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return R.ok();
    }
}
