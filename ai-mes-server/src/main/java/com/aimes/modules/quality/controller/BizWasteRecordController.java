package com.aimes.modules.quality.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.quality.entity.BizWasteRecord;
import com.aimes.modules.quality.service.BizWasteRecordService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api/quality/waste-records")
@RequiredArgsConstructor
public class BizWasteRecordController {

    private final BizWasteRecordService wasteRecordService;

    @GetMapping("/page")
    public R<PageResult<BizWasteRecord>> page(
            @RequestParam(required = false) String wasteName,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String workOrderNo,
            @RequestParam(required = false) String defectType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateEnd,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(wasteRecordService.getPage(wasteName, operatorName, productName, workOrderNo, defectType, dateStart, dateEnd, pageNum, pageSize));
    }

    @PostMapping
    public R<Void> create(@RequestBody BizWasteRecord record) {
        wasteRecordService.create(record);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizWasteRecord record) {
        wasteRecordService.update(id, record);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        wasteRecordService.delete(id);
        return R.ok();
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> stats(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateEnd) {
        return R.ok(wasteRecordService.getStats(dateStart, dateEnd));
    }

    @GetMapping("/export")
    public void exportRecords(
            @RequestParam(required = false) String wasteName,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String workOrderNo,
            @RequestParam(required = false) String defectType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateEnd,
            HttpServletResponse response) throws Exception {
        var page = wasteRecordService.getPage(wasteName, operatorName, productName, workOrderNo,
                defectType, dateStart, dateEnd, 1, 100000);
        List<String> headers = List.of("记录日期", "废料名称", "数量", "单位", "缺陷类型",
                "废料原因", "操作人", "关联产品", "关联工单号", "备注");
        List<Function<BizWasteRecord, Object>> ex = List.of(
                BizWasteRecord::getRecordDate,
                BizWasteRecord::getWasteName,
                BizWasteRecord::getQuantity,
                BizWasteRecord::getUnit,
                BizWasteRecord::getDefectType,
                BizWasteRecord::getReason,
                BizWasteRecord::getOperatorName,
                BizWasteRecord::getProductName,
                BizWasteRecord::getWorkOrderNo,
                BizWasteRecord::getRemark);
        ExcelUtil.export(response, "废料统计", headers, page.getRecords(), ex);
    }
}
