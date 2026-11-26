package com.aimes.modules.inventory.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.inventory.service.BizInvSnapshotService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api/inventory/snapshots")
@RequiredArgsConstructor
public class BizInvSnapshotController {

    private final BizInvSnapshotService snapshotService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> getPage(
            @RequestParam(required = false) String dateStart,
            @RequestParam(required = false) String dateEnd,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(snapshotService.getPage(dateStart, dateEnd, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> getDetail(@PathVariable Long id) {
        return R.ok(snapshotService.getDetail(id));
    }

    /**
     * 按日期查询快照明细，date为空时返回最新快照
     */
    @GetMapping("/by-date")
    public R<Map<String, Object>> getByDate(@RequestParam(required = false) String date) {
        return R.ok(snapshotService.getByDate(date));
    }

    /**
     * 获取所有可用的快照日期
     */
    @GetMapping("/dates")
    public R<List<String>> getAvailableDates() {
        return R.ok(snapshotService.getAvailableDates());
    }

    /**
     * 手动触发快照（测试用）
     */
    @PostMapping("/generate")
    public R<Void> generate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        snapshotService.createSnapshot(date != null ? date : LocalDate.now());
        return R.ok();
    }

    /**
     * 导出指定日期的历史库存台账
     */
    @GetMapping("/export")
    @SuppressWarnings("unchecked")
    public void export(@RequestParam(required = false) String date, HttpServletResponse response) throws IOException {
        Map<String, Object> data = snapshotService.getByDate(date);
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.getOrDefault("items", List.of());
        String snapshotDate = data.get("snapshotDate") != null ? data.get("snapshotDate").toString() : "-";

        List<String> headers = List.of("物料编号", "物料名称", "规格型号", "单位", "类别", "仓库", "库位", "在库数量");
        List<Function<Map<String, Object>, Object>> extractors = List.of(
                r -> r.get("materialNo"),
                r -> r.get("materialName"),
                r -> r.get("spec"),
                r -> r.get("unit"),
                r -> mapCategory((String) r.get("category")),
                r -> r.get("warehouseName"),
                r -> r.get("locationName"),
                r -> r.get("totalQty")
        );
        ExcelUtil.export(response, "历史库存台账_" + snapshotDate, headers, items, extractors);
    }

    private String mapCategory(String c) {
        if (c == null) return "";
        return switch (c) {
            case "finished" -> "成品";
            case "semi_finished" -> "半成品";
            case "raw_material" -> "原材料";
            case "auxiliary" -> "辅料";
            case "packaging" -> "包材";
            case "purchased" -> "外购件";
            default -> c;
        };
    }
}
