package com.aimes.modules.master.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.master.entity.BizProduct;
import com.aimes.modules.master.service.BizProductService;
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
@RequestMapping("/api/master/products")
@RequiredArgsConstructor
public class BizProductController {

    private final BizProductService productService;

    @GetMapping("/page")
    public R<PageResult<BizProduct>> page(
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String productNo,
            @RequestParam(required = false) String spec,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(productService.getPage(productName, productNo, spec, category, status, categoryId, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<BizProduct> getById(@PathVariable Long id) {
        return R.ok(productService.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody BizProduct entity) {
        productService.create(entity);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizProduct entity) {
        productService.update(id, entity);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return R.ok();
    }

    @GetMapping("/select")
    public R<List<Map<String, Object>>> selectList(@RequestParam(required = false) String attribute) {
        return R.ok(productService.getSelectList(attribute));
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        List<BizProduct> list = productService.list();
        List<String> headers = Arrays.asList("产品编号", "产品名称", "规格", "单位", "分类", "生产模式", "目标库存", "标准成本", "有BOM", "有工艺", "状态", "备注");
        List<Function<BizProduct, Object>> extractors = Arrays.asList(
                BizProduct::getProductNo, BizProduct::getProductName, BizProduct::getSpec,
                BizProduct::getUnit, BizProduct::getCategory, BizProduct::getProductionMode,
                BizProduct::getTargetStock, BizProduct::getStandardCost,
                BizProduct::getHasBom, BizProduct::getHasProcess,
                BizProduct::getStatus, BizProduct::getRemark);
        ExcelUtil.export(response, "产品列表", headers, list, extractors);
    }

    @PostMapping("/import")
    public R<String> importProducts(@RequestParam("file") MultipartFile file) {
        List<String> headers = Arrays.asList("产品编号", "产品名称", "规格", "单位", "分类", "生产模式", "目标库存", "标准成本", "备注");
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, headers);
        int count = 0;
        for (Map<String, String> row : rows) {
            BizProduct p = new BizProduct();
            p.setProductNo(row.get("产品编号"));
            p.setProductName(row.get("产品名称"));
            p.setSpec(row.get("规格"));
            p.setUnit(row.get("单位"));
            p.setCategory(row.get("分类"));
            p.setProductionMode(row.get("生产模式"));
            String stock = row.get("目标库存");
            if (stock != null && !stock.isEmpty()) p.setTargetStock(new BigDecimal(stock));
            String cost = row.get("标准成本");
            if (cost != null && !cost.isEmpty()) p.setStandardCost(new BigDecimal(cost));
            p.setRemark(row.get("备注"));
            p.setStatus("active");
            p.setHasBom(false);
            p.setHasProcess(false);
            productService.save(p);
            count++;
        }
        return R.ok("成功导入" + count + "条产品数据");
    }

    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        List<String> headers = Arrays.asList("产品编号", "产品名称", "规格", "单位", "分类", "生产模式", "目标库存", "标准成本", "备注");
        ExcelUtil.exportTemplate(response, "产品导入模板", headers);
    }
}
