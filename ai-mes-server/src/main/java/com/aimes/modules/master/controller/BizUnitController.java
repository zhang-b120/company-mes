package com.aimes.modules.master.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.master.entity.BizUnit;
import com.aimes.modules.master.service.BizUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/master/units")
@RequiredArgsConstructor
public class BizUnitController {

    private final BizUnitService unitService;

    @GetMapping("/page")
    public R<PageResult<BizUnit>> page(
            @RequestParam(required = false) String unitName,
            @RequestParam(required = false) String unitCode,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(unitService.getPage(unitName, unitCode, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<BizUnit> getById(@PathVariable Long id) {
        return R.ok(unitService.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody BizUnit entity) {
        unitService.create(entity);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizUnit entity) {
        unitService.update(id, entity);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        unitService.delete(id);
        return R.ok();
    }

    @GetMapping("/select")
    public R<List<Map<String, Object>>> selectList() {
        return R.ok(unitService.getSelectList());
    }
}
