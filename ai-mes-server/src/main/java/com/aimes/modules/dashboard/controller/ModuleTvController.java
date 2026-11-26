package com.aimes.modules.dashboard.controller;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.R;
import com.aimes.modules.dashboard.service.EamTvService;
import com.aimes.modules.dashboard.service.ModuleTvService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 各业务模块独立大屏聚合接口(免登录,统一管理)
 * GET /api/module-tv/{module}
 *   module: production | sales | inventory | purchase | quality
 *         | finance | outsource | mrp | workreport | eam
 */
@RestController
@RequestMapping("/api/module-tv")
@RequiredArgsConstructor
public class ModuleTvController {

    private final ModuleTvService service;
    /** EAM 数据聚合复用既有 service(细节多,避免重写) */
    private final EamTvService eamTvService;

    @GetMapping("/{module}")
    public R<Map<String, Object>> getModule(@PathVariable String module) {
        return R.ok(switch (module) {
            case "production" -> service.getProduction();
            case "sales" -> service.getSales();
            case "inventory" -> service.getInventory();
            case "purchase" -> service.getPurchase();
            case "quality" -> service.getQuality();
            case "finance" -> service.getFinance();
            case "outsource" -> service.getOutsource();
            case "mrp" -> service.getMrp();
            case "workreport" -> service.getWorkReport();
            case "eam" -> eamTvService.getAll();
            default -> throw new BusinessException("不支持的模块: " + module);
        });
    }
}
