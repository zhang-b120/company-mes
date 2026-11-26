package com.aimes.modules.dataimport.controller;

import com.aimes.common.result.R;
import com.aimes.modules.dataimport.service.DataImportService;
import com.aimes.modules.dataimport.service.DataSimulationService;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 历史数据批量导入控制器
 * 支持导入: 物料规格、物料、工序、工艺路线、BOM、销售订单、采购订单、用户、客户、供应商
 */
@RestController
@RequestMapping("/api/data-import")
@RequiredArgsConstructor
public class DataImportController {

    private final DataImportService dataImportService;
    private final DataSimulationService dataSimulationService;
    private final SysUserMapper sysUserMapper;

    /**
     * 下载导入模板
     * @param type 类型: material-spec/material/sales-order/bom/purchase-order/process/process-route/user/customer/supplier
     */
    @GetMapping("/{type}/template")
    public void downloadTemplate(@PathVariable String type, HttpServletResponse response) throws IOException {
        dataImportService.exportTemplate(type, response);
    }

    /**
     * 临时入口:下载所有导入模板的"纯净版"(无下拉/无示例)打包 zip
     */
    @GetMapping("/plain-templates")
    public void downloadAllPlainTemplates(HttpServletResponse response) throws IOException {
        dataImportService.exportAllPlainTemplates(response);
    }

    /**
     * 导入数据
     * @param type 类型: material-spec/material/sales-order/bom/purchase-order/process/process-route/user/customer/supplier
     */
    @PostMapping("/{type}/import")
    public R<Map<String, Object>> importData(@PathVariable String type,
                                              @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = switch (type) {
            case "material-spec" -> dataImportService.importMaterialSpecs(file);
            case "material" -> dataImportService.importMaterials(file);
            case "sales-order" -> dataImportService.importSalesOrders(file);
            case "bom" -> dataImportService.importBom(file);
            case "purchase-order" -> dataImportService.importPurchaseOrders(file);
            case "process" -> dataImportService.importProcesses(file);
            case "process-route" -> dataImportService.importProcessRoutes(file);
            case "user" -> dataImportService.importUsers(file);
            case "customer" -> dataImportService.importCustomers(file);
            case "supplier" -> dataImportService.importSuppliers(file);
            case "team" -> dataImportService.importTeams(file);
            case "qc-template" -> dataImportService.importQcTemplates(file);
            case "equipment" -> dataImportService.importEquipment(file);
            case "maintenance-template" -> dataImportService.importMaintenanceTemplates(file);
            case "inspection-template" -> dataImportService.importInspectionTemplates(file);
            case "inventory" -> dataImportService.importInventory(file);
            default -> throw new com.aimes.common.exception.BusinessException("不支持的导入类型: " + type);
        };
        return R.ok(result);
    }

    /**
     * 获取可选操作人员列表（用于数据生成配置）
     */
    @GetMapping("/users")
    public R<List<Map<String, Object>>> getUserOptions() {
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0).eq(SysUser::getStatus, "active")
                .select(SysUser::getId, SysUser::getRealName, SysUser::getUsername));
        List<Map<String, Object>> list = users.stream().map(u -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("label", u.getRealName() != null ? u.getRealName() : u.getUsername());
            return m;
        }).toList();
        return R.ok(list);
    }

    /**
     * 执行数据模拟生成（根据已导入的订单自动生成工单、报工、库存等数据）
     */
    @PostMapping("/simulate")
    public R<Map<String, Object>> simulate(@RequestBody Map<String, Object> config) {
        String module = (String) config.get("module");
        if (module == null || module.isEmpty()) {
            throw new com.aimes.common.exception.BusinessException("缺少必需的模块参数(module)");
        }
        return R.ok(dataSimulationService.generate(module, config));
    }
}
