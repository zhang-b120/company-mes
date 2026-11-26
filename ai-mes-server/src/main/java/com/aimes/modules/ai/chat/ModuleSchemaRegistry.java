package com.aimes.modules.ai.chat;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.aimes.modules.ai.chat.ModuleSchema.FieldDef;

/**
 * 模块字段注册表 — 集中管理所有业务模块的字段结构
 * AI根据注册的字段定义来理解每个模块应该提取哪些数据
 */
@Component
public class ModuleSchemaRegistry {

    private final Map<String, ModuleSchema> schemas = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        // ===== 主数据 =====
        register("customer", "客户", false, List.of(
                field("customerName", "客户名称", "string", true, null),
                field("customerNo", "客户编号", "string", false, "系统自动生成，可留空"),
                field("contactName", "联系人", "string", false, null),
                field("phone", "联系电话", "string", false, null),
                field("email", "邮箱", "string", false, null),
                field("address", "地址", "string", false, null),
                field("creditLimit", "信用额度", "number", false, null),
                field("remark", "备注", "text", false, null)
        ));

        register("supplier", "供应商", false, List.of(
                field("supplierName", "供应商名称", "string", true, null),
                field("supplierNo", "供应商编号", "string", false, "系统自动生成"),
                field("contactName", "联系人", "string", false, null),
                field("phone", "联系电话", "string", false, null),
                field("email", "邮箱", "string", false, null),
                field("address", "地址", "string", false, null),
                field("businessScope", "经营范围", "text", false, null),
                field("remark", "备注", "text", false, null)
        ));

        register("material", "物料", true, List.of(
                field("materialName", "物料名称", "string", true, null),
                field("materialNo", "物料编号", "string", false, "系统自动生成"),
                field("spec", "规格型号", "string", false, null),
                field("materialType", "物料类型", "select", true, "可选: product成品/semi_product半成品/raw_material原材料/auxiliary辅料/package包材/outsource外购件"),
                field("unit", "单位", "string", true, "如: 件/kg/m/套/个"),
                field("categoryId", "所属类别", "select", false, "从已有物料类别中匹配"),
                field("safetyStock", "安全库存", "number", false, null),
                field("remark", "备注", "text", false, null)
        ));

        register("product", "产品", true, List.of(
                field("productName", "产品名称", "string", true, null),
                field("productNo", "产品编号", "string", false, "系统自动生成"),
                field("spec", "规格型号", "string", false, null),
                field("unit", "单位", "string", true, null),
                field("category", "类别", "string", false, null),
                field("productionMode", "生产模式", "select", false, "MTO按单/MTS备库/MIXED混合"),
                field("remark", "备注", "text", false, null)
        ));

        register("process", "工序", true, List.of(
                field("processName", "工序名称", "string", true, null),
                field("processNo", "工序编号", "string", false, "系统自动生成"),
                field("processType", "工序类型", "select", false, "如: machining机加工/assembly装配/inspection检验/packaging包装/surface表面处理"),
                field("standardTime", "标准工时(分钟)", "number", false, null),
                field("piecePrice", "计件单价(元)", "number", false, null),
                field("equipmentReq", "设备需求", "string", false, null),
                field("remark", "备注", "text", false, null)
        ));

        // ===== 订单 =====
        register("salesOrder", "销售订单", false, List.of(
                field("customerId", "客户", "select", true, "从已有客户列表匹配"),
                field("contactName", "联系人", "string", false, null),
                field("contactPhone", "联系电话", "string", false, null),
                field("deliveryAddress", "收货地址", "string", false, null),
                field("remark", "备注", "text", false, null),
                field("items", "产品明细", "array", true, "数组，每项含: productId/productName产品, quantity数量, unitPrice单价, deliveryDate交货日期, remark备注")
        ));

        // ===== 生产 =====
        register("workOrder", "生产工单", false, List.of(
                field("orderId", "关联销售订单", "select", false, "从已有订单匹配"),
                field("productId", "产品", "select", true, "从已有产品匹配"),
                field("planQty", "计划数量", "number", true, null),
                field("deliveryDate", "交货日期", "date", true, null),
                field("priority", "优先级", "number", false, "1-5，5最高"),
                field("remark", "备注", "text", false, null)
        ));

        // ===== 采购 =====
        register("purchaseRequest", "采购申请", true, List.of(
                field("materialId", "物料", "select", true, "从已有物料匹配"),
                field("quantity", "申请数量", "number", true, null),
                field("suggestedSupplierId", "建议供应商", "select", false, "从已有供应商匹配"),
                field("expectedDate", "期望到货日", "date", false, null),
                field("remark", "备注", "text", false, null)
        ));

        register("purchaseOrder", "采购订单", false, List.of(
                field("supplierId", "供应商", "select", true, "从已有供应商匹配"),
                field("deliveryDate", "交货日期", "date", false, null),
                field("remark", "备注", "text", false, null),
                field("items", "采购明细", "array", true, "数组，每项含: materialId物料, quantity数量, unitPrice单价")
        ));

        register("purchaseReceipt", "采购签收", false, List.of(
                field("purchaseOrderId", "采购订单", "select", true, "从待签收的采购订单匹配"),
                field("receivedQty", "收货数量", "number", true, null),
                field("defectQty", "不良数量", "number", false, null),
                field("batchNo", "批次号", "string", false, null),
                field("warehouseId", "入库仓库", "select", false, null),
                field("remark", "备注", "text", false, null)
        ));

        // ===== 外协 =====
        register("outsourceRequest", "外协申请", false, List.of(
                field("workOrderId", "关联工单", "select", false, null),
                field("processName", "工序", "string", true, null),
                field("quantity", "数量", "number", true, null),
                field("supplierId", "加工商", "select", false, "从已有供应商匹配"),
                field("remark", "备注", "text", false, null)
        ));

        register("outsourceOrder", "外协加工单", false, List.of(
                field("supplierId", "加工商", "select", true, "从已有供应商匹配"),
                field("productId", "产品", "select", true, null),
                field("quantity", "数量", "number", true, null),
                field("unitPrice", "单价", "number", false, null),
                field("deliveryDate", "交期", "date", false, null),
                field("remark", "备注", "text", false, null)
        ));

        // ===== 库存 =====
        register("stockIn", "入库", false, List.of(
                field("materialId", "物料", "select", true, "从已有物料匹配"),
                field("warehouseId", "仓库", "select", true, null),
                field("quantity", "数量", "number", true, null),
                field("batchNo", "批次号", "string", false, null),
                field("sourceNo", "来源单号", "string", false, "关联的采购单/工单编号"),
                field("remark", "备注", "text", false, null)
        ));

        register("stockOut", "出库", false, List.of(
                field("materialId", "物料", "select", true, "从已有物料匹配"),
                field("warehouseId", "仓库", "select", true, null),
                field("quantity", "数量", "number", true, null),
                field("workOrderId", "领料工单", "select", false, null),
                field("remark", "备注", "text", false, null)
        ));

        // ===== BOM =====
        register("bomVersion", "BOM版本", false, List.of(
                field("productId", "产品", "select", true, "从已有产品匹配"),
                field("versionNo", "版本号", "string", false, null),
                field("items", "物料清单", "array", true, "数组，每项含: materialId物料, quantity用量, unit单位, lossRate损耗率")
        ));

        register("processRoute", "工艺路线", false, List.of(
                field("productId", "产品", "select", true, "从已有产品匹配"),
                field("steps", "工序步骤", "array", true, "数组，每项含: stepNo序号, processId工序(从工序库匹配), standardTime标准工时(分钟)")
        ));

        // ===== 质量 =====
        register("qcTemplate", "检验模板", false, List.of(
                field("templateName", "模板名称", "string", true, null),
                field("inspectionType", "检验类型", "select", true, "incoming来料检/process过程检/final成品检/outsource外协检"),
                field("items", "检验项", "array", true, "数组，每项含: itemName项名, standardValue标准值, upperLimit上限, lowerLimit下限, isCritical是否关键项")
        ));

        register("defect", "不良品记录", false, List.of(
                field("materialId", "物料/产品", "select", true, null),
                field("batchNo", "批次号", "string", false, null),
                field("defectQty", "不良数量", "number", true, null),
                field("defectType", "不良类型", "string", true, null),
                field("description", "不良描述", "text", true, null)
        ));

        // ===== 财务 =====
        register("receivable", "应收账款", false, List.of(
                field("customerId", "客户", "select", true, "从已有客户匹配"),
                field("orderId", "关联订单", "select", false, null),
                field("totalAmount", "应收金额", "number", true, null),
                field("dueDate", "到期日", "date", true, null),
                field("remark", "备注", "text", false, null)
        ));

        register("payable", "应付账款", false, List.of(
                field("supplierId", "供应商", "select", true, "从已有供应商匹配"),
                field("purchaseOrderId", "关联采购单", "select", false, null),
                field("totalAmount", "应付金额", "number", true, null),
                field("dueDate", "到期日", "date", true, null),
                field("remark", "备注", "text", false, null)
        ));

        register("wage", "工资记录", true, List.of(
                field("userId", "员工", "select", true, "从已有员工匹配"),
                field("month", "月份", "string", true, "格式: yyyy-MM"),
                field("pieceWage", "计件工资", "number", false, null),
                field("overtimeWage", "加班工资", "number", false, null),
                field("deduction", "扣款", "number", false, null),
                field("bonus", "奖金", "number", false, null)
        ));

        // ===== 系统 =====
        register("user", "用户", true, List.of(
                field("username", "用户名", "string", true, null),
                field("realName", "姓名", "string", true, null),
                field("phone", "手机号", "string", false, null),
                field("email", "邮箱", "string", false, null),
                field("roleId", "角色", "select", false, "从已有角色匹配"),
                field("workshop", "所属车间", "string", false, null)
        ));

        register("team", "班组", false, List.of(
                field("teamName", "班组名称", "string", true, null),
                field("workshop", "所属车间", "string", false, null),
                field("leaderId", "组长", "select", false, "从已有员工匹配"),
                field("remark", "备注", "text", false, null)
        ));
    }

    private void register(String module, String name, boolean supportsBatch, List<FieldDef> fields) {
        schemas.put(module, new ModuleSchema(module, name, fields, supportsBatch));
    }

    private FieldDef field(String name, String label, String type, boolean required, String hint) {
        return new FieldDef(name, label, type, required, hint);
    }

    public ModuleSchema getSchema(String module) {
        return schemas.get(module);
    }

    public String getSchemaPrompt(String module) {
        ModuleSchema schema = schemas.get(module);
        return schema != null ? schema.toPromptDescription() : "未知模块: " + module;
    }

    public Set<String> getAllModules() {
        return schemas.keySet();
    }

    public boolean hasModule(String module) {
        return schemas.containsKey(module);
    }
}
