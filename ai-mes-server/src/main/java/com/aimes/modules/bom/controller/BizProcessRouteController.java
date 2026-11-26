package com.aimes.modules.bom.controller;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.bom.entity.BizProcessRoute;
import com.aimes.modules.bom.entity.BizProcessRouteStep;
import com.aimes.modules.bom.service.BizProcessRouteService;
import com.aimes.modules.system.service.SysBizConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/process")
@RequiredArgsConstructor
public class BizProcessRouteController {

    private final BizProcessRouteService processRouteService;
    private final SysBizConfigService bizConfigService;

    @GetMapping("/routes/page")
    public R<PageResult<Map<String, Object>>> getRoutePage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String routeName,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String productNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(processRouteService.getPage(keyword, routeName, productName, productNo, status, pageNum, pageSize));
    }

    @GetMapping("/routes")
    public R<List<Map<String, Object>>> getRoutes(@RequestParam(required = false) Long productId) {
        return R.ok(processRouteService.getRoutesWithProductName(productId));
    }

    @GetMapping("/routes/{id}")
    public R<Map<String, Object>> getRouteDetail(@PathVariable Long id) {
        return R.ok(processRouteService.getRouteDetail(id));
    }

    @GetMapping("/routes/export-active")
    public void exportActiveRoutes(@RequestParam(required = false) String routeName,
                                   @RequestParam(required = false) String productName,
                                   jakarta.servlet.http.HttpServletResponse response) throws Exception {
        List<Map<String, Object>> rows = processRouteService.getActiveRoutesExportRows(routeName, productName);
        List<String> headers = List.of("产品编号", "产品名称", "工艺路线名称", "版本号",
                "工序序号", "工序名称", "标准工时(分)", "计件单价", "班组", "备注");
        List<java.util.function.Function<Map<String, Object>, Object>> ex = List.of(
                m -> m.get("productNo"), m -> m.get("productName"), m -> m.get("routeName"), m -> m.get("versionNo"),
                m -> m.get("stepNo"), m -> m.get("stepName"), m -> m.get("standardTime"),
                m -> m.get("piecePrice"), m -> m.get("teamName"), m -> m.get("remark"));
        com.aimes.common.utils.ExcelUtil.export(response, "工艺路线激活版", headers, rows, ex);
    }

    @PostMapping("/routes")
    public R<Void> createRoute(@RequestBody BizProcessRoute route) {
        if (bizConfigService.getBoolValue("production.report_statistics", false)) {
            if (route.getProductId() == null) {
                throw new BusinessException("员工报工统计已启用，新建工艺路线必须选择产品");
            }
        }
        processRouteService.createRoute(route);
        return R.ok();
    }

    @PutMapping("/routes/{id}")
    public R<Void> updateRoute(@PathVariable Long id, @RequestBody BizProcessRoute route) {
        processRouteService.updateRoute(id, route);
        return R.ok();
    }

    @PostMapping("/steps")
    public R<Void> addStep(@RequestBody BizProcessRouteStep step) {
        processRouteService.addStep(step);
        return R.ok();
    }

    @PutMapping("/steps/{id}")
    public R<Void> updateStep(@PathVariable Long id, @RequestBody BizProcessRouteStep step) {
        processRouteService.updateStep(id, step);
        return R.ok();
    }

    @DeleteMapping("/steps/{id}")
    public R<Void> deleteStep(@PathVariable Long id) {
        processRouteService.deleteStep(id);
        return R.ok();
    }
}
