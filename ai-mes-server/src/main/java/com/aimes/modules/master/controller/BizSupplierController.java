package com.aimes.modules.master.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.master.entity.BizSupplier;
import com.aimes.modules.master.mapper.BizSupplierCategoryMapper;
import com.aimes.modules.master.service.BizSupplierService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api/master/suppliers")
@RequiredArgsConstructor
public class BizSupplierController {

    private final BizSupplierService supplierService;
    private final BizSupplierCategoryMapper supplierCategoryMapper;

    @GetMapping("/page")
    public R<PageResult<BizSupplier>> page(
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String supplierNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierType,
            @RequestParam(required = false) Long supplierCategoryId,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String settlementType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(supplierService.getPage(supplierName, supplierNo, status, supplierType, supplierCategoryId, contactName, phone, settlementType, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<BizSupplier> getById(@PathVariable Long id) {
        return R.ok(supplierService.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody BizSupplier entity) {
        supplierService.create(entity);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizSupplier entity) {
        supplierService.update(id, entity);
        return R.ok();
    }

    @PutMapping("/{id}/score")
    public R<Void> updateScore(@PathVariable Long id, @RequestBody Map<String, BigDecimal> body) {
        supplierService.updateScore(id, body.get("score"));
        return R.ok();
    }

    @GetMapping("/select")
    public R<List<Map<String, Object>>> selectList() {
        return R.ok(supplierService.getSelectList());
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        List<BizSupplier> list = supplierService.list();
        List<String> headers = Arrays.asList("供应商编号", "供应商名称", "供应商类别", "联系人", "电话", "地址", "结算方式", "开户银行", "银行账号", "税号", "状态", "备注");
        // 供应商类别名称映射（按 ID 转名称）
        java.util.Map<Long, String> categoryNameMap = new java.util.HashMap<>();
        try {
            java.util.List<com.aimes.modules.master.entity.BizSupplierCategory> cats =
                    supplierCategoryMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.aimes.modules.master.entity.BizSupplierCategory>()
                            .eq(com.aimes.modules.master.entity.BizSupplierCategory::getDeleted, 0));
            cats.forEach(c -> categoryNameMap.put(c.getId(), c.getCategoryName()));
        } catch (Exception ignore) {}
        List<Function<BizSupplier, Object>> extractors = Arrays.asList(
                BizSupplier::getSupplierNo, BizSupplier::getSupplierName,
                s -> s.getSupplierCategoryId() != null ? categoryNameMap.getOrDefault(s.getSupplierCategoryId(), "") : "",
                BizSupplier::getContactName, BizSupplier::getPhone, BizSupplier::getAddress,
                BizSupplier::getSettlementType, BizSupplier::getBankName, BizSupplier::getBankAccount,
                BizSupplier::getTaxNo, BizSupplier::getStatus, BizSupplier::getRemark);
        ExcelUtil.export(response, "供应商列表", headers, list, extractors);
    }

    @PostMapping("/import")
    public R<String> importSuppliers(@RequestParam("file") MultipartFile file) {
        List<String> headers = Arrays.asList("供应商编号", "供应商名称", "供应商类别", "联系人", "电话", "地址", "结算方式", "开户银行", "银行账号", "税号", "备注");
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, headers);
        int count = 0;
        for (Map<String, String> row : rows) {
            BizSupplier s = new BizSupplier();
            s.setSupplierNo(row.get("供应商编号"));
            s.setSupplierName(row.get("供应商名称"));
            // 供应商类别(按类别名精确匹配 biz_supplier_category)
            String supCatName = row.get("供应商类别");
            if (supCatName != null && !supCatName.isBlank()) {
                com.aimes.modules.master.entity.BizSupplierCategory sc = supplierCategoryMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.aimes.modules.master.entity.BizSupplierCategory>()
                                .eq(com.aimes.modules.master.entity.BizSupplierCategory::getCategoryName, supCatName.trim())
                                .eq(com.aimes.modules.master.entity.BizSupplierCategory::getDeleted, 0)
                                .last("LIMIT 1"));
                if (sc != null) s.setSupplierCategoryId(sc.getId());
            }
            s.setContactName(row.get("联系人"));
            s.setPhone(row.get("电话"));
            s.setAddress(row.get("地址"));
            s.setSettlementType(row.get("结算方式"));
            s.setBankName(row.get("开户银行"));
            s.setBankAccount(row.get("银行账号"));
            s.setTaxNo(row.get("税号"));
            s.setRemark(row.get("备注"));
            s.setStatus("active");
            supplierService.save(s);
            count++;
        }
        return R.ok("成功导入" + count + "条供应商数据");
    }

    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        List<String> headers = Arrays.asList("供应商编号", "供应商名称", "供应商类别", "联系人", "电话", "地址", "结算方式", "开户银行", "银行账号", "税号", "备注");
        Map<String, List<String>> dropdowns = new java.util.HashMap<>();
        try {
            List<com.aimes.modules.master.entity.BizSupplierCategory> cats =
                    supplierCategoryMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.aimes.modules.master.entity.BizSupplierCategory>()
                            .eq(com.aimes.modules.master.entity.BizSupplierCategory::getDeleted, 0));
            List<String> names = cats.stream()
                    .map(com.aimes.modules.master.entity.BizSupplierCategory::getCategoryName)
                    .filter(n -> n != null && !n.isBlank())
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
            if (!names.isEmpty()) dropdowns.put("供应商类别", names);
        } catch (Exception ignore) {}
        ExcelUtil.exportTemplate(response, "供应商导入模板", headers, dropdowns);
    }
}
