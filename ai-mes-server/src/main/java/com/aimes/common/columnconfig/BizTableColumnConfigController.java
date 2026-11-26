package com.aimes.common.columnconfig;

import com.aimes.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户表格列配置接口
 * GET  /api/table-column-config/{module}   获取当前用户该模块的列配置
 * POST /api/table-column-config/{module}   保存当前用户该模块的列配置（全量替换）
 */
@RestController
@RequestMapping("/api/table-column-config")
@RequiredArgsConstructor
public class BizTableColumnConfigController {

    private final BizTableColumnConfigService columnConfigService;

    @GetMapping("/{module}")
    public R<List<BizTableColumnConfig>> get(@PathVariable String module) {
        return R.ok(columnConfigService.getUserColumnConfig(module));
    }

    @PostMapping("/{module}")
    public R<Void> save(@PathVariable String module,
                        @RequestBody List<BizTableColumnConfig> columns) {
        columnConfigService.saveUserColumnConfig(module, columns);
        return R.ok();
    }
}
