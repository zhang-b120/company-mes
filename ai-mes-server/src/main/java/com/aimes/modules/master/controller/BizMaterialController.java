package com.aimes.modules.master.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.dataimport.service.DataImportService;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.service.BizMaterialService;
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
@RequestMapping("/api/master/materials")
@RequiredArgsConstructor
public class BizMaterialController {

    private final BizMaterialService materialService;
    private final DataImportService dataImportService;

    @GetMapping("/page")
    public R<PageResult<BizMaterial>> page(
            @RequestParam(required = false) String materialNo,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String spec,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String materialTypes,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String materialAttribute,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        // 前端传 materialTypes（逗号分隔），兼容 category 单值
        String effectiveCategory = materialTypes != null ? materialTypes : category;
        return R.ok(materialService.getPage(materialNo, materialName, spec, effectiveCategory, status, categoryId, materialAttribute, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<BizMaterial> getById(@PathVariable Long id) {
        BizMaterial m = materialService.getById(id);
        if (m != null) materialService.enrichDisplayNames(m);
        return R.ok(m);
    }

    @PostMapping
    public R<Long> create(@RequestBody BizMaterial entity) {
        materialService.create(entity);
        return R.ok(entity.getId());
    }

    @PostMapping("/batch")
    public R<List<Long>> batchCreate(@RequestBody List<BizMaterial> list) {
        return R.ok(materialService.batchCreate(list));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizMaterial entity) {
        materialService.update(id, entity);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return R.ok();
    }

    @GetMapping("/select")
    public R<List<Map<String, Object>>> selectList(@RequestParam(required = false) String attribute) {
        return R.ok(materialService.getSelectList(attribute));
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        List<BizMaterial> list = materialService.listWithEnrichment();
        List<String> headers = Arrays.asList("物料编号", "物料名称", "规格", "分类", "单位", "最小库存", "最大库存", "安全库存", "保质期(天)", "物料属性", "默认供应商", "状态", "备注");
        List<Function<BizMaterial, Object>> extractors = Arrays.asList(
                BizMaterial::getMaterialNo, BizMaterial::getMaterialName, BizMaterial::getSpec,
                m -> mapCategory(m.getCategory()), BizMaterial::getUnit, BizMaterial::getMinStock,
                BizMaterial::getMaxStock, BizMaterial::getSafetyStock,
                BizMaterial::getShelfLifeDays, m -> mapAttributes(m.getMaterialAttributes()),
                BizMaterial::getDefaultSupplierName,
                m -> mapStatus(m.getStatus()), BizMaterial::getRemark);
        ExcelUtil.export(response, "物料列表", headers, list, extractors);
    }

    private static String mapCategory(String c) {
        if (c == null) return "";
        return switch (c) {
            case "raw_material" -> "原材料";
            case "semi_finished" -> "半成品";
            case "finished" -> "成品";
            case "auxiliary" -> "辅料";
            case "packaging" -> "包材";
            case "purchased" -> "外购件";
            default -> c;
        };
    }

    private static String mapAttributes(String attrs) {
        if (attrs == null || attrs.isEmpty()) return "";
        String[] parts = attrs.split(",");
        List<String> result = new java.util.ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            result.add(switch (t) {
                case "purchase" -> "采购";
                case "production" -> "生产";
                case "sales" -> "销售";
                default -> t;
            });
        }
        return String.join(",", result);
    }

    private static String mapStatus(String s) {
        if (s == null) return "";
        return switch (s) {
            case "active" -> "启用";
            case "inactive" -> "停用";
            default -> s;
        };
    }

    /** 导入物料（复用数据导入模块的逻辑，支持动态规格列、仓库下拉等） */
    @PostMapping("/import")
    public R<Map<String, Object>> importMaterials(@RequestParam("file") MultipartFile file) {
        return R.ok(dataImportService.importMaterials(file));
    }

    /** 下载导入模板（复用数据导入模块的模板，含示例数据、必填标星、仓库下拉、动态规格列） */
    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        dataImportService.exportTemplate("material", response);
    }
}
