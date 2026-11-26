package com.aimes.modules.purchase.controller;

import com.aimes.common.result.R;
import com.aimes.modules.purchase.service.PurchaseAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase/analysis")
@RequiredArgsConstructor
public class PurchaseAnalysisController {

    private final PurchaseAnalysisService analysisService;

    /**
     * 供应商维度分析
     */
    @GetMapping("/supplier")
    public R<List<Map<String, Object>>> bySupplier(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEnd,
            @RequestParam(required = false) Long supplierId) {
        return R.ok(analysisService.analyzeBySupplier(
                dateStart != null ? dateStart.atStartOfDay() : null,
                dateEnd != null ? dateEnd.atTime(23, 59, 59) : null, supplierId));
    }

    /**
     * 物料维度分析
     */
    @GetMapping("/material")
    public R<List<Map<String, Object>>> byMaterial(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEnd,
            @RequestParam(required = false) Long materialId) {
        return R.ok(analysisService.analyzeByMaterial(
                dateStart != null ? dateStart.atStartOfDay() : null,
                dateEnd != null ? dateEnd.atTime(23, 59, 59) : null, materialId));
    }

    /**
     * Excel 导出
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "supplier") String dimension,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEnd,
            @RequestParam(required = false) Long filterId) throws IOException {
        byte[] bytes = analysisService.exportExcel(dimension,
                dateStart != null ? dateStart.atStartOfDay() : null,
                dateEnd != null ? dateEnd.atTime(23, 59, 59) : null, filterId);
        String filename = "supplier".equals(dimension) ? "采购分析-供应商维度.xlsx" : "采购分析-物料维度.xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20"))
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
