package com.aimes.modules.master.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.master.entity.BizMaterialSpec;
import com.aimes.modules.master.mapper.BizMaterialSpecMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/master/material-specs")
@RequiredArgsConstructor
public class BizMaterialSpecController {

    private final BizMaterialSpecMapper specMapper;

    /** 分页查询 */
    @GetMapping("/page")
    public R<PageResult<BizMaterialSpec>> getPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        LambdaQueryWrapper<BizMaterialSpec> wrapper = new LambdaQueryWrapper<BizMaterialSpec>()
                .eq(BizMaterialSpec::getDeleted, 0);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(BizMaterialSpec::getSpecName, keyword);
        }
        wrapper.orderByAsc(BizMaterialSpec::getSortOrder).orderByDesc(BizMaterialSpec::getId);
        Page<BizMaterialSpec> page = specMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    /** 下拉选择列表（全部启用的） */
    @GetMapping("/select")
    public R<List<BizMaterialSpec>> getSelectList() {
        List<BizMaterialSpec> list = specMapper.selectList(new LambdaQueryWrapper<BizMaterialSpec>()
                .eq(BizMaterialSpec::getDeleted, 0)
                .eq(BizMaterialSpec::getStatus, "active")
                .orderByAsc(BizMaterialSpec::getSortOrder));
        return R.ok(list);
    }

    /** 新增 */
    @PostMapping
    public R<Long> create(@RequestBody BizMaterialSpec spec) {
        if (spec.getSpecName() == null || spec.getSpecName().isEmpty()) {
            return R.fail("规格名称不能为空");
        }
        // 检查重复
        Long exists = specMapper.selectCount(new LambdaQueryWrapper<BizMaterialSpec>()
                .eq(BizMaterialSpec::getSpecName, spec.getSpecName())
                .eq(BizMaterialSpec::getDeleted, 0));
        if (exists > 0) {
            return R.fail("规格名称'" + spec.getSpecName() + "'已存在");
        }
        if (spec.getStatus() == null) spec.setStatus("active");
        if (spec.getSortOrder() == null) spec.setSortOrder(0);
        specMapper.insert(spec);
        return R.ok(spec.getId());
    }

    /** 更新 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizMaterialSpec spec) {
        BizMaterialSpec existing = specMapper.selectById(id);
        if (existing == null) return R.fail("记录不存在");
        // 检查名称重复（排除自身）
        if (spec.getSpecName() != null && !spec.getSpecName().isEmpty()) {
            Long exists = specMapper.selectCount(new LambdaQueryWrapper<BizMaterialSpec>()
                    .eq(BizMaterialSpec::getSpecName, spec.getSpecName())
                    .ne(BizMaterialSpec::getId, id)
                    .eq(BizMaterialSpec::getDeleted, 0));
            if (exists > 0) {
                return R.fail("规格名称'" + spec.getSpecName() + "'已存在");
            }
            existing.setSpecName(spec.getSpecName());
        }
        if (spec.getSortOrder() != null) existing.setSortOrder(spec.getSortOrder());
        if (spec.getStatus() != null) existing.setStatus(spec.getStatus());
        specMapper.updateById(existing);
        return R.ok();
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        specMapper.deleteById(id);
        return R.ok();
    }

    /** 导出 */
    @GetMapping("/export")
    public void export(HttpServletResponse response) throws java.io.IOException {
        List<BizMaterialSpec> list = specMapper.selectList(new LambdaQueryWrapper<BizMaterialSpec>()
                .eq(BizMaterialSpec::getDeleted, 0)
                .orderByAsc(BizMaterialSpec::getSortOrder));
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        ExcelUtil.export(response, "规格库",
                List.of("规格名称", "排序号", "状态", "创建时间"),
                list,
                List.of(BizMaterialSpec::getSpecName,
                        s -> s.getSortOrder() != null ? s.getSortOrder() : 0,
                        s -> "active".equals(s.getStatus()) ? "启用" : "禁用",
                        s -> s.getCreateTime() != null ? s.getCreateTime().format(fmt) : ""));
    }

    /** 导入 */
    @PostMapping("/import")
    public R<Map<String, Object>> importSpecs(@RequestParam("file") MultipartFile file) {
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, List.of("规格名称", "创建时间"));
        int success = 0;
        List<String> errors = new java.util.ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 2;
            String name = rows.get(i).get("规格名称");
            if (name == null || name.isEmpty()) {
                errors.add("第" + rowNum + "行: 规格名称不能为空");
                continue;
            }
            Long exists = specMapper.selectCount(new LambdaQueryWrapper<BizMaterialSpec>()
                    .eq(BizMaterialSpec::getSpecName, name)
                    .eq(BizMaterialSpec::getDeleted, 0));
            if (exists > 0) {
                errors.add("第" + rowNum + "行: '" + name + "'已存在，已跳过");
                continue;
            }
            BizMaterialSpec spec = new BizMaterialSpec();
            spec.setSpecName(name);
            spec.setSortOrder(i);
            spec.setStatus("active");
            String ctStr = rows.get(i).get("创建时间");
            if (ctStr != null && !ctStr.isEmpty()) {
                try {
                    spec.setCreateTime(java.time.LocalDateTime.parse(ctStr.trim(),
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                } catch (Exception ignored) {
                    try {
                        spec.setCreateTime(java.time.LocalDate.parse(ctStr.trim()).atStartOfDay());
                    } catch (Exception ignored2) {}
                }
            }
            specMapper.insert(spec);
            success++;
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("success", success);
        result.put("failed", rows.size() - success);
        result.put("errors", errors);
        return R.ok(result);
    }

    /** 导入模板 */
    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws java.io.IOException {
        ExcelUtil.exportTemplateWithSample(response, "规格库导入模板",
                List.of("规格名称", "创建时间"),
                List.of(
                        new String[]{"材质", "2026-01-01 08:00:00"},
                        new String[]{"尺寸", "2026-01-01 08:00:00"},
                        new String[]{"颜色", "2026-01-01 08:00:00"}
                ),
                java.util.Set.of("规格名称"));
    }
}
