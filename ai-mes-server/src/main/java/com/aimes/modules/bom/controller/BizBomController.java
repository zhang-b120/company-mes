package com.aimes.modules.bom.controller;

import com.aimes.common.result.R;
import com.aimes.modules.bom.entity.BizBomItem;
import com.aimes.modules.bom.entity.BizBomSubstitute;
import com.aimes.modules.bom.entity.BizBomVersion;
import com.aimes.modules.bom.service.BizBomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bom")
@RequiredArgsConstructor
public class BizBomController {

    private final BizBomService bomService;

    @GetMapping("/material-select")
    public R<List<Map<String, Object>>> getMaterialSelect() {
        return R.ok(bomService.getMaterialSelectWithBom());
    }

    /** 新 UI: 产品列表(只显示有 BOM 的成品),点进去看版本 */
    @GetMapping("/products")
    public R<List<Map<String, Object>>> listBomProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String productNo,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String spec) {
        return R.ok(bomService.listBomProducts(keyword, productNo, productName, spec));
    }

    @GetMapping("/versions")
    public R<List<Map<String, Object>>> getVersions(@RequestParam Long productId) {
        return R.ok(bomService.getVersionsByProduct(productId));
    }

    @GetMapping("/versions/export-active")
    public void exportActiveVersion(@RequestParam Long productId,
                                    jakarta.servlet.http.HttpServletResponse response) throws Exception {
        List<Map<String, Object>> rows = bomService.getActiveVersionExportRows(productId);
        exportBomRows(response, "BOM激活版本", rows);
    }

    @GetMapping("/versions/export-active-all")
    public void exportActiveVersionsAll(@RequestParam(required = false) String productNo,
                                        @RequestParam(required = false) String productName,
                                        @RequestParam(required = false) String spec,
                                        jakarta.servlet.http.HttpServletResponse response) throws Exception {
        List<Map<String, Object>> rows = bomService.getActiveVersionsExportRowsAll(productNo, productName, spec);
        exportBomRows(response, "BOM激活版本汇总", rows);
    }

    private void exportBomRows(jakarta.servlet.http.HttpServletResponse response, String title,
                               List<Map<String, Object>> rows) throws Exception {
        // 与 BOM 导入模板字段对齐(去掉了版本状态)
        List<String> headers = List.of(
                "产品编号", "产品名称", "版本号",
                "层级", "子件物料编号", "子件物料名称", "来源类型(purchase/manufacture)", "数量",
                "损耗率(%)", "排序号", "创建时间");
        List<java.util.function.Function<Map<String, Object>, Object>> ex = List.of(
                m -> m.get("productNo"), m -> m.get("productName"), m -> m.get("versionNo"),
                m -> m.get("level"),
                m -> m.get("materialNo"), m -> m.get("materialName"), m -> m.get("sourceType"),
                m -> m.get("quantity"), m -> m.get("wastageRate"),
                m -> m.get("sortOrder"), m -> m.get("createTime"));
        com.aimes.common.utils.ExcelUtil.export(response, title, headers, rows, ex);
    }

    @GetMapping("/versions/{id}")
    public R<Map<String, Object>> getVersionDetail(@PathVariable Long id) {
        return R.ok(bomService.getVersionDetail(id));
    }

    @PostMapping("/versions")
    public R<Void> createVersion(@RequestBody BizBomVersion version) {
        bomService.createVersion(version);
        return R.ok();
    }

    /**
     * 申请激活 BOM 版本 — 触发审批流
     * 返回审批实例 ID(前端可据此跳转审批详情)
     */
    @PutMapping("/versions/{id}/activate")
    public R<Long> activateVersion(@PathVariable Long id) {
        return R.ok(bomService.requestActivateVersion(id));
    }

    @PutMapping("/versions/{id}/deprecate")
    public R<Void> deprecateVersion(@PathVariable Long id) {
        bomService.deprecateVersion(id);
        return R.ok();
    }

    @PostMapping("/versions/{id}/copy")
    public R<Void> copyVersion(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String newVersionNo = body != null && body.get("versionNo") != null ? body.get("versionNo").toString() : null;
        bomService.copyVersion(id, newVersionNo);
        return R.ok();
    }

    /** 将某 BOM 版本(必须 active)复制给另一个产品，新版本状态=draft */
    @PostMapping("/versions/{id}/copy-to-product")
    public R<Void> copyVersionToProduct(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long targetProductId = body.get("targetProductId") != null
                ? Long.valueOf(body.get("targetProductId").toString()) : null;
        bomService.copyVersionToProduct(id, targetProductId);
        return R.ok();
    }

    @DeleteMapping("/versions/{id}")
    public R<Void> deleteVersion(@PathVariable Long id) {
        bomService.deleteVersion(id);
        return R.ok();
    }

    @PostMapping("/items")
    public R<Void> addItem(@RequestBody BizBomItem item) {
        bomService.addItem(item);
        return R.ok();
    }

    @PutMapping("/items/{id}")
    public R<Void> updateItem(@PathVariable Long id, @RequestBody BizBomItem item) {
        bomService.updateItem(id, item);
        return R.ok();
    }

    @DeleteMapping("/items/{id}")
    public R<Void> deleteItem(@PathVariable Long id) {
        bomService.deleteItem(id);
        return R.ok();
    }

    @PostMapping("/versions/{id}/calculate-cost")
    public R<BigDecimal> calculateCost(@PathVariable Long id) {
        return R.ok(bomService.calculateCost(id));
    }

    @GetMapping("/versions/{id}/expand")
    public R<List<Map<String, Object>>> expandBom(@PathVariable Long id) {
        return R.ok(bomService.expandToLeafMaterials(id));
    }

    // ===== 替代料 =====

    @GetMapping("/substitutes")
    public R<List<Map<String, Object>>> getSubstitutes(@RequestParam Long bomItemId) {
        return R.ok(bomService.getSubstitutes(bomItemId));
    }

    @PostMapping("/substitutes")
    public R<Void> addSubstitute(@RequestBody BizBomSubstitute substitute) {
        bomService.addSubstitute(substitute);
        return R.ok();
    }

    @PutMapping("/substitutes/{id}")
    public R<Void> updateSubstitute(@PathVariable Long id, @RequestBody BizBomSubstitute substitute) {
        bomService.updateSubstitute(id, substitute);
        return R.ok();
    }

    @DeleteMapping("/substitutes/{id}")
    public R<Void> deleteSubstitute(@PathVariable Long id) {
        bomService.deleteSubstitute(id);
        return R.ok();
    }
}
