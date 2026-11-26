package com.aimes.modules.dataimport.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.bom.entity.BizBomItem;
import com.aimes.modules.bom.entity.BizBomVersion;
import com.aimes.modules.bom.entity.BizProcess;
import com.aimes.modules.bom.entity.BizProcessRoute;
import com.aimes.modules.bom.entity.BizProcessRouteStep;
import com.aimes.modules.bom.mapper.BizBomItemMapper;
import com.aimes.modules.bom.mapper.BizBomVersionMapper;
import com.aimes.modules.bom.mapper.BizProcessMapper;
import com.aimes.modules.bom.mapper.BizProcessRouteMapper;
import com.aimes.modules.bom.mapper.BizProcessRouteStepMapper;
import com.aimes.modules.master.entity.BizCustomer;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizMaterialSpec;
import com.aimes.modules.master.entity.BizSupplier;
import com.aimes.modules.master.mapper.BizCustomerMapper;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizMaterialSpecMapper;
import com.aimes.modules.master.mapper.BizSupplierMapper;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.mapper.BizSalesOrderItemMapper;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.purchase.entity.BizPurchaseOrder;
import com.aimes.modules.purchase.entity.BizPurchaseOrderItem;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderItemMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderMapper;
import com.aimes.modules.eam.entity.*;
import com.aimes.modules.eam.mapper.*;
import com.aimes.modules.quality.entity.BizQcScheme;
import com.aimes.modules.quality.entity.BizQcTemplate;
import com.aimes.modules.quality.entity.BizQcTemplateItem;
import com.aimes.modules.quality.mapper.BizQcSchemeMapper;
import com.aimes.modules.quality.mapper.BizQcTemplateItemMapper;
import com.aimes.modules.quality.mapper.BizQcTemplateMapper;
import com.aimes.modules.team.entity.BizTeam;
import com.aimes.modules.team.entity.BizTeamMember;
import com.aimes.modules.team.mapper.BizTeamMapper;
import com.aimes.modules.team.mapper.BizTeamMemberMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.entity.SysWarehouse;
import com.aimes.modules.system.mapper.SysWarehouseMapper;
import com.aimes.modules.system.entity.SysDept;
import com.aimes.modules.system.entity.SysRole;
import com.aimes.modules.system.entity.SysUserRole;
import com.aimes.modules.system.mapper.SysDeptMapper;
import com.aimes.modules.system.mapper.SysRoleMapper;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.system.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 历史数据批量导入服务
 * 支持导入: 物料规格、物料、工序、工艺路线、BOM、销售订单、采购订单、用户、客户、供应商
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataImportService {

    private final BizMaterialMapper materialMapper;
    private final BizMaterialSpecMapper materialSpecMapper;
    private final com.aimes.modules.master.mapper.BizUnitMapper bizUnitMapper;
    private final com.aimes.modules.master.mapper.BizMaterialCategoryMapper materialCategoryMapper;
    private final com.aimes.modules.master.mapper.BizSupplierCategoryMapper supplierCategoryMapper;
    private final org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory;
    private final BizCustomerMapper customerMapper;
    private final BizSupplierMapper supplierMapper;
    private final BizSalesOrderMapper salesOrderMapper;
    private final BizSalesOrderItemMapper salesOrderItemMapper;
    private final BizBomVersionMapper bomVersionMapper;
    private final BizBomItemMapper bomItemMapper;
    private final BizPurchaseOrderMapper purchaseOrderMapper;
    private final BizPurchaseOrderItemMapper purchaseOrderItemMapper;
    private final BizProcessMapper processMapper;
    private final BizProcessRouteMapper processRouteMapper;
    private final BizProcessRouteStepMapper processRouteStepMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysWarehouseMapper sysWarehouseMapper;
    private final BizQcTemplateMapper qcTemplateMapper;
    private final BizQcTemplateItemMapper qcTemplateItemMapper;
    private final BizQcSchemeMapper qcSchemeMapper;
    private final BizTeamMapper teamMapper;
    private final BizTeamMemberMapper teamMemberMapper;
    private final EamEquipmentMapper eamEquipmentMapper;
    private final EamEquipmentStatusMapper eamEquipmentStatusMapper;
    private final EamMaintenanceTemplateMapper eamMaintTemplateMapper;
    private final EamMaintenanceTemplateItemMapper eamMaintTemplateItemMapper;
    private final EamMaintenanceTemplateEquipmentMapper eamMaintTemplateEquipmentMapper;
    private final EamInspectionTemplateMapper eamInspTemplateMapper;
    private final EamInspectionTemplateItemMapper eamInspTemplateItemMapper;
    private final EamInspectionTemplateEquipmentMapper eamInspTemplateEquipmentMapper;
    private final com.aimes.modules.inventory.mapper.BizInventoryMapper bizInventoryMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    // ==================== 表头定义 ====================

    /** 物料规格 - 仅规格名称 */
    public static final List<String> MATERIAL_SPEC_HEADERS = List.of("规格名称", "创建时间");

    /** 物料档案 - 固定列（去掉了原来的"规格"列，规格改为动态表头） */
    public static final List<String> MATERIAL_HEADERS = List.of(
            "物料编号", "物料名称", "物料类型", "物料类别", "主单位",
            "辅助单位", "换算比例(1主单位=N辅助单位)",
            "物料属性(采购/生产/销售)", "物料来源(采购/自制/外协)",
            "标准成本(含税)", "最小库存", "最大库存",
            "采购提前期(天)", "生产提前期(天)", "最小批量", "批量倍数",
            "默认仓库", "默认供应商", "创建时间", "备注");

    /** 物料固定列数量（动态规格列从此位置之后开始） */
    public static final int MATERIAL_FIXED_COLUMN_COUNT = 20;

    public static final List<String> SALES_ORDER_HEADERS = List.of(
            "订单编号", "客户名称", "联系人", "收货地址", "结算方式",
            "交货日期", "业务员", "下单日期", "订单备注",
            "产品编号", "产品名称", "规格型号", "计量单位", "数量", "含税单价", "含税金额", "税率(%)", "行备注");

    public static final List<String> BOM_HEADERS = List.of(
            "产品编号", "产品名称", "版本号", "版本状态(draft/active/deprecated)",
            "层级", "子件物料编号", "子件物料名称", "来源类型(purchase/manufacture)", "数量",
            "损耗率(%)", "排序号", "创建时间");

    public static final List<String> PURCHASE_ORDER_HEADERS = List.of(
            "采购单号", "供应商名称", "联系人", "交货日期", "付款条款",
            "采购员", "下单日期", "采购备注",
            "物料编号", "物料名称", "数量", "含税单价", "含税金额", "税率(%)", "行备注");

    public static final List<String> PROCESS_HEADERS = List.of(
            "工序名称", "标准工时(天)", "计件单价", "绑定类型(个人/班组)",
            "绑定对象名称", "SOP描述", "入半成品库(0/1)", "可外协(0/1)", "创建时间");

    public static final List<String> PROCESS_ROUTE_HEADERS = List.of(
            "路线名称", "产品编号", "产品名称", "版本号", "状态(active/inactive)",
            "工序名称", "工序序号", "工序组",
            "覆盖标准工时(天)", "覆盖计件单价", "质检模板", "创建时间");

    public static final List<String> USER_HEADERS = List.of(
            "用户名", "真实姓名", "手机号",
            "部门", "岗位组", "角色", "入职日期", "创建时间", "备注");

    public static final List<String> CUSTOMER_HEADERS = List.of(
            "客户编号", "客户名称", "联系人", "电话", "邮箱",
            "地址", "结算方式", "信用额度", "税率(%)", "创建时间", "备注");

    public static final List<String> SUPPLIER_HEADERS = List.of(
            "供应商编号", "供应商名称", "供应商类别", "联系人", "电话",
            "地址", "结算方式", "开户行", "银行账号", "税号",
            "发票类型", "加工能力", "创建时间", "备注");

    public static final List<String> TEAM_HEADERS = List.of(
            "班组名称", "车间", "组长姓名",
            "生效日期", "成员姓名", "创建时间", "备注");

    /** 质检体系一页汇总(模板+方案+项目展开成每检验项一行) */
    public static final List<String> QC_TEMPLATE_HEADERS = List.of(
            "模板名称", "适用类型", "适用产品编号", "适用物料编号",
            "抽样方案", "抽样模式", "抽检数量", "抽检比例(%)", "默认模板",
            "检验项目名称", "检验类型", "标准值", "下限值", "上限值",
            "单位", "检验方法", "默认值", "是否必填", "是否关键", "排序号", "创建时间");

    // ==================== 示例数据定义 ====================

    private static final List<String[]> MATERIAL_SPEC_SAMPLES = List.of(
            new String[]{"材质", "2026-01-01 08:00:00"},
            new String[]{"尺寸", "2026-01-01 08:00:00"},
            new String[]{"颜色", "2026-01-01 08:00:00"}
    );

    /** 物料模板示例（固定列 + 动态规格列示例） */
    private List<String> getMaterialTemplateHeaders() {
        // 固定列 + 示例规格列
        List<String> headers = new ArrayList<>(MATERIAL_HEADERS);
        // 加载已有的规格名称作为动态列
        List<BizMaterialSpec> specs = materialSpecMapper.selectList(
                new LambdaQueryWrapper<BizMaterialSpec>()
                        .eq(BizMaterialSpec::getDeleted, 0)
                        .eq(BizMaterialSpec::getStatus, "active")
                        .orderByAsc(BizMaterialSpec::getSortOrder));
        if (specs.isEmpty()) {
            // 没有规格数据时使用默认示例
            headers.add("材质");
            headers.add("尺寸");
            headers.add("颜色");
        } else {
            for (BizMaterialSpec spec : specs) {
                headers.add(spec.getSpecName());
            }
        }
        return headers;
    }

    private static final List<String[]> MATERIAL_SAMPLES = List.of(
            new String[]{"M20260101", "304不锈钢板", "原材料", "金属板材", "kg",
                    "吨", "1000", "采购", "采购",
                    "45.00", "50", "500",
                    "7", "", "", "",
                    "原材料仓", "示例供应商公司", "2026-01-15 08:30:00", "示例物料",
                    "304", "1000x2000x2mm", "银白色"},
            new String[]{"P20260102", "六角螺栓M10", "成品", "标准件", "个",
                    "", "", "生产,销售", "自制",
                    "1.50", "200", "2000",
                    "", "3", "100", "50",
                    "成品仓", "", "2026-01-15 09:00:00", "",
                    "碳钢", "M10x30", ""}
    );

    // 表头共 18 列:订单编号 / 客户名称 / 联系人 / 收货地址 / 结算方式 /
    //              交货日期 / 业务员 / 下单日期 / 订单备注 /
    //              产品编号 / 产品名称 / 规格型号 / 计量单位 / 数量 / 单价 / 金额 / 税率(%) / 行备注
    private static final List<String[]> SALES_ORDER_SAMPLES = List.of(
            new String[]{"SO20260301001", "示例客户公司", "张三", "上海市浦东新区XX路100号", "月结30天",
                    "2026-04-15", "sales01", "2026-03-01 09:00:00", "示例订单",
                    "P20260102", "示例产品A", "材质:NBR 尺寸:Ø15mm", "件",
                    "1000", "1.50", "", "13", "第一批"},
            new String[]{"SO20260301001", "", "", "", "",
                    "", "", "", "",
                    "M20260101", "", "", "",
                    "50", "45.00", "", "13", "同一订单第二行明细"}
    );

    private static final List<String[]> BOM_SAMPLES = List.of(
            // 一级子件（半成品）
            new String[]{"P20260102", "", "V1.0", "active",
                    "1", "M20260101", "", "manufacture", "1",
                    "0", "1", "2026-02-01 10:00:00"},
            // 二级子件（挂在上一行的一级下）
            new String[]{"P20260102", "", "V1.0", "",
                    "2", "M20260104", "", "purchase", "2",
                    "0", "1", ""},
            new String[]{"P20260102", "", "V1.0", "",
                    "2", "M20260105", "", "purchase", "3",
                    "0", "2", ""},
            // 另一个一级子件（直接外购）
            new String[]{"P20260102", "", "V1.0", "",
                    "1", "M20260103", "", "purchase", "1",
                    "0", "2", ""}
    );

    private static final List<String[]> PURCHASE_ORDER_SAMPLES = List.of(
            new String[]{"PO20260201001", "示例供应商公司", "李四", "2026-03-01", "货到付款",
                    "purchase01", "2026-02-01 14:00:00", "示例采购单",
                    "M20260101", "", "200", "42.00", "8400.00", "首批采购"},
            new String[]{"PO20260201001", "", "", "", "",
                    "", "", "",
                    "M20260103", "", "5000", "0.50", "2500.00", "同一采购单第二行"}
    );

    private static final List<String[]> PROCESS_SAMPLES = List.of(
            new String[]{"切割", "1", "5.00", "个人", "张三,李四", "使用激光切割机进行板材切割", "0", "1", "2026-01-10 08:00:00"},
            new String[]{"冲压", "1", "3.00", "班组", "一车间A组", "冷冲压成型", "0", "0", "2026-01-10 08:00:00"},
            new String[]{"表面处理", "2", "8.00", "", "", "电镀/喷涂处理", "1", "1", "2026-01-10 08:00:00"}
    );

    private static final List<String[]> PROCESS_ROUTE_SAMPLES = List.of(
            new String[]{"螺栓生产线", "P20260102", "", "V1.0", "active",
                    "切割", "10", "1", "", "", "", "2026-01-20 09:00:00"},
            new String[]{"螺栓生产线", "", "", "", "",
                    "冲压", "20", "2", "20", "3.50", "", ""},
            new String[]{"螺栓生产线", "", "", "", "",
                    "表面处理", "30", "3", "", "", "成品终检模板", ""}
    );

    private static final List<String[]> USER_SAMPLES = List.of(
            new String[]{"zhangsan", "张三", "13800138001",
                    "生产部", "一车间", "工人", "2025-06-01", "2025-06-01 08:00:00", "示例工人"},
            new String[]{"lisi", "李四", "13800138002",
                    "销售部", "销售一组", "销售", "2025-03-15", "2025-03-15 09:00:00", "示例销售"}
    );

    private static final List<String[]> CUSTOMER_SAMPLES = List.of(
            new String[]{"C20260101", "示例客户公司", "王五", "021-12345678", "wangwu@example.com",
                    "上海市浦东新区XX路100号", "月结30天", "100000", "13",
                    "2026-01-01 08:00:00", "示例客户"},
            new String[]{"", "第二客户公司", "赵六", "010-87654321", "",
                    "北京市朝阳区YY路200号", "预付", "50000", "6",
                    "2026-01-05 10:00:00", "编号留空自动生成"}
    );

    private static final List<String[]> SUPPLIER_SAMPLES = List.of(
            new String[]{"S20260101", "示例供应商公司", "金属板材加工", "孙七", "0512-66778899",
                    "苏州市工业园区ZZ路300号", "月结60天", "工商银行苏州支行", "6222021234567890123", "91320000MA1XXXXX",
                    "增值税专用发票", "金属板材加工", "2026-01-01 08:00:00", "示例供应商"},
            new String[]{"", "第二供应商", "", "周八", "0755-11223344",
                    "深圳市南山区AA路400号", "货到付款", "", "", "",
                    "", "", "2026-01-10 09:00:00", "编号留空自动生成"}
    );

    private static final List<String[]> TEAM_SAMPLES = List.of(
            new String[]{"一车间A组", "一车间", "张三",
                    "2025-06-01", "张三", "2025-06-01 08:00:00", "示例班组-同名多行表示多成员"},
            new String[]{"一车间A组", "", "",
                    "", "李四", "", "同一班组第二个成员"},
            new String[]{"一车间B组", "一车间", "王五",
                    "2025-06-01", "王五", "2025-06-01 08:00:00", ""}
    );

    private static final List<String[]> QC_TEMPLATE_SAMPLES = List.of(
            // 第 1 个模板 - 掩护梁终检(3 个检验项)
            new String[]{"掩护梁终检", "成品检", "M001", "",
                    "抽检", "按比例", "", "10", "是",
                    "外观", "单选", "", "", "", "", "目视检查", "合格", "是", "否", "1",
                    "2026-01-01 08:00:00"},
            new String[]{"掩护梁终检", "", "", "",
                    "", "", "", "", "",
                    "长度", "计量型", "1500", "1495", "1505", "mm", "卷尺测量", "1500", "是", "是", "2", ""},
            new String[]{"掩护梁终检", "", "", "",
                    "", "", "", "", "",
                    "焊缝", "单选", "", "", "", "", "参照图纸", "合格", "是", "是", "3", ""},
            // 第 2 个模板 - 焊丝来料检验(2 个检验项)
            new String[]{"焊丝来料检验", "来料检", "", "M010",
                    "抽检", "固定数量", "5", "", "否",
                    "直径", "计量型", "1.2", "1.18", "1.22", "mm", "千分尺", "1.2", "是", "否", "1",
                    "2026-01-01 08:00:00"},
            new String[]{"焊丝来料检验", "", "", "",
                    "", "", "", "", "",
                    "表面质量", "单选", "", "", "", "", "目视", "合格", "是", "否", "2", ""}
    );

    private static final Set<String> QC_TEMPLATE_REQUIRED = Set.of(
            "模板名称", "抽样方案", "检验项目名称", "检验类型");

    // ==================== 必填列定义 ====================

    private static final Set<String> MATERIAL_SPEC_REQUIRED = Set.of("规格名称");
    private static final Set<String> MATERIAL_REQUIRED = Set.of("物料名称");
    private static final Set<String> SALES_ORDER_REQUIRED = Set.of("客户名称", "产品编号", "数量");
    private static final Set<String> PURCHASE_ORDER_REQUIRED_SET = Set.of("供应商名称", "物料编号", "数量");
    private static final Set<String> BOM_REQUIRED = Set.of("产品编号", "子件物料编号", "数量");
    /** BOM模板中的公式列索引：产品名称(1)、子件物料名称(5) */
    private static final Set<String> PURCHASE_ORDER_REQUIRED = Set.of("供应商名称", "物料编号", "数量");
    private static final Set<String> PROCESS_REQUIRED = Set.of("工序名称");
    private static final Set<String> PROCESS_ROUTE_REQUIRED = Set.of("路线名称", "工序名称", "工序序号");
    private static final Set<String> USER_REQUIRED = Set.of("用户名");
    private static final Set<String> CUSTOMER_REQUIRED = Set.of("客户名称");
    private static final Set<String> SUPPLIER_REQUIRED = Set.of("供应商名称");
    private static final Set<String> TEAM_REQUIRED = Set.of("班组名称");

    // ==================== 模板下载 ====================

    public void exportTemplate(String type, HttpServletResponse response) throws IOException {
        switch (type) {
            case "material-spec" -> ExcelUtil.exportTemplateWithSample(response, "物料规格导入模板",
                    MATERIAL_SPEC_HEADERS, MATERIAL_SPEC_SAMPLES, MATERIAL_SPEC_REQUIRED);
            case "material" -> exportMaterialTemplate(response);
            case "sales-order" -> exportSalesOrderTemplate(response);
            case "bom" -> exportBomTemplate(response);
            case "purchase-order" -> exportPurchaseOrderTemplate(response);
            case "process" -> exportProcessTemplate(response);
            case "process-route" -> exportProcessRouteTemplate(response);
            case "user" -> exportUserTemplate(response);
            case "customer" -> ExcelUtil.exportTemplateWithSample(response, "客户导入模板",
                    CUSTOMER_HEADERS, CUSTOMER_SAMPLES, CUSTOMER_REQUIRED);
            case "supplier" -> exportSupplierTemplate(response);
            case "team" -> exportTeamTemplate(response);
            case "qc-template" -> ExcelUtil.exportTemplateWithSample(response, "质检体系导入模板",
                    QC_TEMPLATE_HEADERS, QC_TEMPLATE_SAMPLES, QC_TEMPLATE_REQUIRED);
            case "inventory" -> exportInventoryTemplate(response);
            default -> throw new BusinessException("不支持的导入类型: " + type);
        }
    }

    /**
     * 导出所有导入模板的"纯净版"(带示例 + 必填标记,但无下拉)打包成 zip
     * 给用户作为临时下载入口,方便手动填写不带下拉约束的模板
     */
    public void exportAllPlainTemplates(HttpServletResponse response) throws IOException {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + java.net.URLEncoder.encode("所有导入模板.zip", "UTF-8"));

        // (文件名, 表头, 示例数据, 必填列)
        List<Object[]> templates = List.of(
                new Object[]{"01_用户导入模板", USER_HEADERS, USER_SAMPLES, Set.of("用户名", "真实姓名")},
                new Object[]{"02_班组导入模板", TEAM_HEADERS, TEAM_SAMPLES, TEAM_REQUIRED},
                new Object[]{"03_客户导入模板", CUSTOMER_HEADERS, CUSTOMER_SAMPLES, CUSTOMER_REQUIRED},
                new Object[]{"04_供应商导入模板", SUPPLIER_HEADERS, SUPPLIER_SAMPLES, SUPPLIER_REQUIRED},
                new Object[]{"05_物料规格导入模板", MATERIAL_SPEC_HEADERS, MATERIAL_SPEC_SAMPLES, MATERIAL_SPEC_REQUIRED},
                new Object[]{"06_物料导入模板", MATERIAL_HEADERS, MATERIAL_SAMPLES, MATERIAL_REQUIRED},
                new Object[]{"07_工序导入模板", PROCESS_HEADERS, PROCESS_SAMPLES, Set.of("工序名称")},
                new Object[]{"08_工艺路线导入模板", PROCESS_ROUTE_HEADERS, PROCESS_ROUTE_SAMPLES, Set.of("路线名称", "工序名称")},
                new Object[]{"09_BOM导入模板", BOM_HEADERS, BOM_SAMPLES, Set.of("产品编号", "子件物料编号", "数量")},
                new Object[]{"10_销售订单导入模板", SALES_ORDER_HEADERS, SALES_ORDER_SAMPLES, Set.of("客户名称", "产品编号", "数量")},
                new Object[]{"11_采购订单导入模板", PURCHASE_ORDER_HEADERS, PURCHASE_ORDER_SAMPLES, Set.of("供应商名称", "物料编号", "数量")},
                new Object[]{"12_质检体系导入模板", QC_TEMPLATE_HEADERS, QC_TEMPLATE_SAMPLES, QC_TEMPLATE_REQUIRED}
        );

        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
            for (Object[] tpl : templates) {
                String fileName = (String) tpl[0];
                @SuppressWarnings("unchecked")
                List<String> headers = (List<String>) tpl[1];
                @SuppressWarnings("unchecked")
                List<String[]> samples = (List<String[]>) tpl[2];
                @SuppressWarnings("unchecked")
                Set<String> required = (Set<String>) tpl[3];

                zos.putNextEntry(new java.util.zip.ZipEntry(fileName + ".xlsx"));
                ExcelUtil.writeTemplateToStream(zos, headers, samples, required);
                zos.closeEntry();
            }
        }
    }

    // ==================== 带下拉校验的模板通用构建 ====================

    /** 下拉数据源定义 */
    private record DropdownSource(String sheetName, String rangeName, String[] values) {}
    /** 下拉校验列定义 */
    private record DropdownColumn(int colIndex, String rangeName) {}

    /**
     * 通用模板导出：支持多个隐藏 Sheet 作为下拉数据源，多列添加下拉校验
     */
    private void exportTemplateWithDropdowns(HttpServletResponse response, String fileName,
                                              List<String> headers, List<String[]> samples, Set<String> required,
                                              List<DropdownSource> sources, List<DropdownColumn> dropdowns) throws IOException {
        org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Sheet1");

        // ---- 样式 ----
        org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);

        org.apache.poi.ss.usermodel.CellStyle reqStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font reqFont = workbook.createFont();
        reqFont.setBold(true);
        reqFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.RED.getIndex());
        reqStyle.setFont(reqFont);
        reqStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        reqStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        reqStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);

        org.apache.poi.ss.usermodel.CellStyle sampleStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font sFont = workbook.createFont();
        sFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_50_PERCENT.getIndex());
        sampleStyle.setFont(sFont);
        sampleStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        sampleStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

        // ---- 写表头 ----
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            String name = headers.get(i);
            boolean isReq = required != null && required.contains(name);
            cell.setCellValue(isReq ? "*" + name : name);
            cell.setCellStyle(isReq ? reqStyle : headerStyle);
            sheet.setColumnWidth(i, 5000);
        }

        // ---- 写示例数据 ----
        for (int r = 0; r < samples.size(); r++) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(r + 1);
            String[] vals = samples.get(r);
            for (int c = 0; c < vals.length && c < headers.size(); c++) {
                org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
                cell.setCellValue(vals[c] != null ? vals[c] : "");
                cell.setCellStyle(sampleStyle);
            }
        }

        // ---- 创建隐藏 Sheet 和命名范围 ----
        for (DropdownSource src : sources) {
            org.apache.poi.ss.usermodel.Sheet hidden = workbook.createSheet(src.sheetName);
            for (int i = 0; i < src.values.length; i++) {
                hidden.createRow(i).createCell(0).setCellValue(src.values[i]);
            }
            workbook.setSheetHidden(workbook.getSheetIndex(hidden), true);

            org.apache.poi.ss.usermodel.Name nr = workbook.createName();
            nr.setNameName(src.rangeName);
            nr.setRefersToFormula(src.sheetName + "!$A$1:$A$" + Math.max(src.values.length, 1));
        }

        // ---- 添加下拉校验 ----
        org.apache.poi.ss.usermodel.DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        for (DropdownColumn dc : dropdowns) {
            org.apache.poi.ss.usermodel.DataValidationConstraint dvc =
                    dvHelper.createFormulaListConstraint(dc.rangeName);
            org.apache.poi.ss.util.CellRangeAddressList range =
                    new org.apache.poi.ss.util.CellRangeAddressList(1, 999, dc.colIndex, dc.colIndex);
            org.apache.poi.ss.usermodel.DataValidation dv = dvHelper.createValidation(dvc, range);
            dv.setShowErrorBox(false);
            dv.setSuppressDropDownArrow(true);
            sheet.addValidationData(dv);
        }

        // ---- 输出 ----
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + java.net.URLEncoder.encode(fileName + ".xlsx", java.nio.charset.StandardCharsets.UTF_8));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /** 获取员工姓名列表（下拉数据源） */
    private String[] getEmployeeNames() {
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getDeleted, 0).eq(SysUser::getStatus, "active")
                        .isNotNull(SysUser::getRealName).ne(SysUser::getRealName, ""))
                .stream().map(SysUser::getRealName).distinct().toArray(String[]::new);
    }

    /** 获取班组名称列表（下拉数据源） */
    /** 获取结算/付款方式列表（从系统配置 settlement_types 读取） */
    private String[] getSettlementTypes() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT config_value FROM sys_config WHERE config_key = 'settlement_types' AND deleted = 0 LIMIT 1");
            if (!rows.isEmpty()) {
                String json = (String) rows.get(0).get("config_value");
                if (json != null && json.startsWith("[")) {
                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                    List<String> list = om.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                    return list.toArray(new String[0]);
                }
            }
        } catch (Exception e) {
            log.warn("读取结算方式配置失败", e);
        }
        return new String[]{"现结", "月结30天", "月结60天", "月结90天"};
    }

    private String[] getTeamNames() {
        return teamMapper.selectList(new LambdaQueryWrapper<BizTeam>()
                        .eq(BizTeam::getDeleted, 0).eq(BizTeam::getStatus, "active"))
                .stream().map(BizTeam::getTeamName).filter(Objects::nonNull).toArray(String[]::new);
    }

    /**
     * 物料模板：默认仓库列(Q=16)下拉选择，动态规格列在固定列之后
     */
    /**
     * 用户模板：部门(D=3)下拉、角色(F=5)下拉（中文名称）
     * 列：A=用户名(0), B=真实姓名(1), C=手机号(2),
     *     D=部门(3), E=岗位组(4), F=角色(5), G=入职日期(6), H=创建时间(7), I=备注(8)
     */
    private void exportUserTemplate(HttpServletResponse response) throws IOException {
        List<SysDept> allDepts = sysDeptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getDeleted, 0));
        // 部门：只列一级部门（parentId=0 的子部门，即生产部、销售部等）
        String[] deptNames = allDepts.stream()
                .filter(d -> d.getParentId() != null && d.getParentId() != 0)
                .filter(d -> {
                    // 判断是否为一级子部门（parent 的 parent 是 0）
                    Long pid = d.getParentId();
                    return allDepts.stream().anyMatch(p -> p.getId().equals(pid) && p.getParentId() != null && p.getParentId() == 0);
                })
                .map(SysDept::getDeptName).filter(Objects::nonNull).toArray(String[]::new);
        // 如果没有二级结构，退化为全部部门
        if (deptNames.length == 0) {
            deptNames = allDepts.stream().map(SysDept::getDeptName).filter(Objects::nonNull).toArray(String[]::new);
        }
        // 岗位组：全部部门名称都列出（用户可选任意层级）
        String[] groupNames = allDepts.stream()
                .map(SysDept::getDeptName).filter(Objects::nonNull).toArray(String[]::new);
        String[] roleNames = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getDeleted, 0)).stream()
                .map(SysRole::getRoleName).filter(Objects::nonNull).toArray(String[]::new);

        exportTemplateWithDropdowns(response, "用户导入模板",
                USER_HEADERS, USER_SAMPLES, USER_REQUIRED,
                List.of(new DropdownSource("部门列表", "DeptNames", deptNames),
                        new DropdownSource("岗位组列表", "GroupNames", groupNames),
                        new DropdownSource("角色列表", "RoleNames", roleNames)),
                List.of(new DropdownColumn(3, "DeptNames"),    // 部门(D=3)
                        new DropdownColumn(4, "GroupNames"),   // 岗位组(E=4)
                        new DropdownColumn(5, "RoleNames")));  // 角色(F=5)
    }

    private void exportMaterialTemplate(HttpServletResponse response) throws IOException {
        List<String> headers = getMaterialTemplateHeaders();
        String[] whNames = sysWarehouseMapper.selectList(new LambdaQueryWrapper<SysWarehouse>()
                        .eq(SysWarehouse::getDeleted, 0))
                .stream().map(SysWarehouse::getWarehouseName).filter(Objects::nonNull).toArray(String[]::new);
        String[] supplierNames = supplierMapper.selectList(new LambdaQueryWrapper<BizSupplier>()
                        .eq(BizSupplier::getDeleted, 0))
                .stream().map(BizSupplier::getSupplierName).filter(Objects::nonNull).toArray(String[]::new);
        // 物料类别下拉:只列叶子节点;没有子节点的父节点也算叶子(自身可被选)
        // 即:类别 X 出现在下拉里 ⇔ 不存在 parent_id = X 的有效子节点
        List<com.aimes.modules.master.entity.BizMaterialCategory> allCats = materialCategoryMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.master.entity.BizMaterialCategory>()
                        .eq(com.aimes.modules.master.entity.BizMaterialCategory::getDeleted, 0));
        Set<Long> parentIds = allCats.stream()
                .map(com.aimes.modules.master.entity.BizMaterialCategory::getParentId)
                .filter(Objects::nonNull).filter(p -> p > 0)
                .collect(Collectors.toSet());
        String[] categoryNames = allCats.stream()
                .filter(c -> !parentIds.contains(c.getId()))
                .map(com.aimes.modules.master.entity.BizMaterialCategory::getCategoryName)
                .filter(Objects::nonNull).toArray(String[]::new);

        exportTemplateWithDropdowns(response, "物料导入模板",
                headers, MATERIAL_SAMPLES, MATERIAL_REQUIRED,
                List.of(new DropdownSource("仓库列表", "WhNames", whNames),
                        new DropdownSource("供应商列表", "SupNames", supplierNames),
                        new DropdownSource("物料类别列表", "CatNames", categoryNames)),
                List.of(new DropdownColumn(3, "CatNames"),      // 物料类别(索引3)
                        new DropdownColumn(16, "WhNames"),      // 默认仓库(索引16)
                        new DropdownColumn(17, "SupNames")));   // 默认供应商(索引17)
    }

    /** 供应商模板:供应商类别列下拉(只显示叶子节点;没有子节点的根节点也算叶子) */
    private void exportSupplierTemplate(HttpServletResponse response) throws IOException {
        List<com.aimes.modules.master.entity.BizSupplierCategory> allCats = supplierCategoryMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.master.entity.BizSupplierCategory>()
                        .eq(com.aimes.modules.master.entity.BizSupplierCategory::getDeleted, 0));
        Set<Long> parentIds = allCats.stream()
                .map(com.aimes.modules.master.entity.BizSupplierCategory::getParentId)
                .filter(Objects::nonNull).filter(p -> p > 0)
                .collect(Collectors.toSet());
        String[] categoryNames = allCats.stream()
                .filter(c -> !parentIds.contains(c.getId()))
                .map(com.aimes.modules.master.entity.BizSupplierCategory::getCategoryName)
                .filter(Objects::nonNull).toArray(String[]::new);

        exportTemplateWithDropdowns(response, "供应商导入模板",
                SUPPLIER_HEADERS, SUPPLIER_SAMPLES, SUPPLIER_REQUIRED,
                List.of(new DropdownSource("供应商类别列表", "SupCatNames", categoryNames)),
                List.of(new DropdownColumn(2, "SupCatNames"))); // 供应商类别(索引2)
    }

    /** 班组模板：组长姓名、成员姓名列下拉 */
    private void exportTeamTemplate(HttpServletResponse response) throws IOException {
        String[] empNames = getEmployeeNames();
        exportTemplateWithDropdowns(response, "班组导入模板",
                TEAM_HEADERS, TEAM_SAMPLES, TEAM_REQUIRED,
                List.of(new DropdownSource("员工列表", "EmpNames", empNames)),
                List.of(new DropdownColumn(2, "EmpNames"),   // 组长姓名(索引2)
                        new DropdownColumn(5, "EmpNames"))); // 成员姓名(索引5)
    }

    /**
     * 工序模板：绑定类型 + 绑定对象级联下拉
     * 原理：创建名为"个人"和"班组"的命名范围，绑定对象列用 INDIRECT($D2) 引用，
     * 当绑定类型选"个人"时自动切换为员工列表，选"班组"时切换为班组列表。
     */
    private void exportProcessTemplate(HttpServletResponse response) throws IOException {
        String[] empNames = getEmployeeNames();
        String[] teamNames = getTeamNames();

        // 先用通用方法构建基础模板（绑定类型列用普通下拉）
        // 但绑定对象列需要特殊处理，所以不走通用方法的 dropdown，手动构建
        org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Sheet1");

        // ---- 样式 ----
        org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font hf = workbook.createFont();
        hf.setBold(true);
        headerStyle.setFont(hf);
        headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);

        org.apache.poi.ss.usermodel.CellStyle reqStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font rf = workbook.createFont();
        rf.setBold(true);
        rf.setColor(org.apache.poi.ss.usermodel.IndexedColors.RED.getIndex());
        reqStyle.setFont(rf);
        reqStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        reqStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        reqStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);

        org.apache.poi.ss.usermodel.CellStyle sampleStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font sf = workbook.createFont();
        sf.setColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_50_PERCENT.getIndex());
        sampleStyle.setFont(sf);
        sampleStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        sampleStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

        // ---- 写表头 ----
        List<String> headers = PROCESS_HEADERS;
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            String name = headers.get(i);
            boolean isReq = PROCESS_REQUIRED.contains(name);
            cell.setCellValue(isReq ? "*" + name : name);
            cell.setCellStyle(isReq ? reqStyle : headerStyle);
            sheet.setColumnWidth(i, 5000);
        }

        // ---- 写示例数据 ----
        for (int r = 0; r < PROCESS_SAMPLES.size(); r++) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(r + 1);
            String[] vals = PROCESS_SAMPLES.get(r);
            for (int c = 0; c < vals.length && c < headers.size(); c++) {
                org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
                cell.setCellValue(vals[c] != null ? vals[c] : "");
                cell.setCellStyle(sampleStyle);
            }
        }

        // ---- 隐藏 Sheet：员工列表 → 命名范围"个人" ----
        org.apache.poi.ss.usermodel.Sheet empSheet = workbook.createSheet("员工列表");
        for (int i = 0; i < empNames.length; i++) {
            empSheet.createRow(i).createCell(0).setCellValue(empNames[i]);
        }
        workbook.setSheetHidden(workbook.getSheetIndex(empSheet), true);
        org.apache.poi.ss.usermodel.Name empRange = workbook.createName();
        empRange.setNameName("个人");
        empRange.setRefersToFormula("员工列表!$A$1:$A$" + Math.max(empNames.length, 1));

        // ---- 隐藏 Sheet：班组列表 → 命名范围"班组" ----
        org.apache.poi.ss.usermodel.Sheet teamSheet = workbook.createSheet("班组列表");
        for (int i = 0; i < teamNames.length; i++) {
            teamSheet.createRow(i).createCell(0).setCellValue(teamNames[i]);
        }
        workbook.setSheetHidden(workbook.getSheetIndex(teamSheet), true);
        org.apache.poi.ss.usermodel.Name teamRange = workbook.createName();
        teamRange.setNameName("班组");
        teamRange.setRefersToFormula("班组列表!$A$1:$A$" + Math.max(teamNames.length, 1));

        // ---- 下拉校验 ----
        org.apache.poi.ss.usermodel.DataValidationHelper dvHelper = sheet.getDataValidationHelper();

        // 绑定类型列(索引3)：固定选项 个人/班组
        org.apache.poi.ss.usermodel.DataValidationConstraint typeConstraint =
                dvHelper.createExplicitListConstraint(new String[]{"个人", "班组"});
        org.apache.poi.ss.util.CellRangeAddressList typeRange =
                new org.apache.poi.ss.util.CellRangeAddressList(1, 999, 3, 3);
        org.apache.poi.ss.usermodel.DataValidation typeDv = dvHelper.createValidation(typeConstraint, typeRange);
        typeDv.setShowErrorBox(false);
        typeDv.setSuppressDropDownArrow(true);
        sheet.addValidationData(typeDv);

        // 绑定对象名称列(索引4)：用 INDIRECT 级联引用，D列的值作为命名范围名称
        // 当 D2="个人" 时 → INDIRECT("个人") → 员工列表
        // 当 D2="班组" 时 → INDIRECT("班组") → 班组列表
        org.apache.poi.ss.usermodel.DataValidationConstraint targetConstraint =
                dvHelper.createFormulaListConstraint("INDIRECT($D2)");
        org.apache.poi.ss.util.CellRangeAddressList targetRange =
                new org.apache.poi.ss.util.CellRangeAddressList(1, 999, 4, 4);
        org.apache.poi.ss.usermodel.DataValidation targetDv = dvHelper.createValidation(targetConstraint, targetRange);
        targetDv.setShowErrorBox(false);
        targetDv.setSuppressDropDownArrow(true);
        sheet.addValidationData(targetDv);

        // ---- 输出 ----
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + java.net.URLEncoder.encode("工序导入模板.xlsx", java.nio.charset.StandardCharsets.UTF_8));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /**
     * 工艺路线模板：
     * - 产品编号列(B)下拉选择，产品名称列(C) VLOOKUP 自动带出
     * - 工序名称列(F)下拉选择，覆盖标准工时(I)和覆盖计件单价(J) VLOOKUP 自动带出
     * - 质检模板列(K)下拉选择，一般只在最后一道工序上填写
     *
     * 列布局：
     * A=路线名称(0), B=产品编号(1), C=产品名称(2), D=版本号(3), E=状态(4),
     * F=工序名称(5), G=工序序号(6), H=工序组(7), I=覆盖标准工时(8), J=覆盖计件单价(9),
     * K=质检模板(10), L=创建时间(11)
     */
    private void exportProcessRouteTemplate(HttpServletResponse response) throws IOException {
        // 加载工序数据
        List<BizProcess> processes = processMapper.selectList(
                new LambdaQueryWrapper<BizProcess>().eq(BizProcess::getDeleted, 0));

        // 加载产品数据（仅成品）
        List<BizMaterial> products = materialMapper.selectList(new LambdaQueryWrapper<BizMaterial>()
                .eq(BizMaterial::getDeleted, 0)
                .eq(BizMaterial::getCategory, "finished"));
        // 加载质检模板
        List<BizQcTemplate> qcTemplates = qcTemplateMapper.selectList(new LambdaQueryWrapper<BizQcTemplate>()
                .eq(BizQcTemplate::getDeleted, 0));

        org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Sheet1");

        // ---- 样式 ----
        org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font hf = workbook.createFont();
        hf.setBold(true);
        headerStyle.setFont(hf);
        headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);

        org.apache.poi.ss.usermodel.CellStyle reqStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font rf = workbook.createFont();
        rf.setBold(true);
        rf.setColor(org.apache.poi.ss.usermodel.IndexedColors.RED.getIndex());
        reqStyle.setFont(rf);
        reqStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        reqStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        reqStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);

        org.apache.poi.ss.usermodel.CellStyle sampleStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font sf = workbook.createFont();
        sf.setColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_50_PERCENT.getIndex());
        sampleStyle.setFont(sf);
        sampleStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        sampleStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

        // 产品名称列：灰色斜体 + 浅灰底色（视觉提示只读，由公式自动填充）
        org.apache.poi.ss.usermodel.CellStyle readonlyStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font roFont = workbook.createFont();
        roFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_40_PERCENT.getIndex());
        roFont.setItalic(true);
        readonlyStyle.setFont(roFont);
        readonlyStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        readonlyStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

        // ---- 写表头 ----
        List<String> headers = PROCESS_ROUTE_HEADERS;
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            String name = headers.get(i);
            boolean isReq = PROCESS_ROUTE_REQUIRED.contains(name);
            cell.setCellValue(isReq ? "*" + name : name);
            cell.setCellStyle(isReq ? reqStyle : headerStyle);
            sheet.setColumnWidth(i, 5000);
        }

        // ---- 写示例数据 ----
        for (int r = 0; r < PROCESS_ROUTE_SAMPLES.size(); r++) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(r + 1);
            String[] vals = PROCESS_ROUTE_SAMPLES.get(r);
            for (int c = 0; c < vals.length && c < headers.size(); c++) {
                org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
                cell.setCellValue(vals[c] != null ? vals[c] : "");
                cell.setCellStyle(sampleStyle);
            }
        }

        // ---- 隐藏 Sheet 1：工序数据（名称 | 标准工时 | 计件单价） ----
        org.apache.poi.ss.usermodel.Sheet procSheet = workbook.createSheet("工序数据");
        for (int i = 0; i < processes.size(); i++) {
            BizProcess p = processes.get(i);
            org.apache.poi.ss.usermodel.Row row = procSheet.createRow(i);
            row.createCell(0).setCellValue(p.getProcessName() != null ? p.getProcessName() : "");
            row.createCell(1).setCellValue(p.getStandardTime() != null ? p.getStandardTime() : 1);
            row.createCell(2).setCellValue(p.getPiecePrice() != null ? p.getPiecePrice().doubleValue() : 0);
        }
        workbook.setSheetHidden(workbook.getSheetIndex(procSheet), true);

        org.apache.poi.ss.usermodel.Name procNamesRange = workbook.createName();
        procNamesRange.setNameName("ProcNames");
        procNamesRange.setRefersToFormula("工序数据!$A$1:$A$" + Math.max(processes.size(), 1));

        org.apache.poi.ss.usermodel.Name procDataRange = workbook.createName();
        procDataRange.setNameName("ProcData");
        procDataRange.setRefersToFormula("工序数据!$A$1:$C$" + Math.max(processes.size(), 1));

        // ---- 隐藏 Sheet 2：产品数据（编号 | 名称） ----
        org.apache.poi.ss.usermodel.Sheet prodSheet = workbook.createSheet("产品数据");
        for (int i = 0; i < products.size(); i++) {
            BizMaterial m = products.get(i);
            org.apache.poi.ss.usermodel.Row row = prodSheet.createRow(i);
            row.createCell(0).setCellValue(m.getMaterialNo() != null ? m.getMaterialNo() : "");
            row.createCell(1).setCellValue(m.getMaterialName() != null ? m.getMaterialName() : "");
        }
        workbook.setSheetHidden(workbook.getSheetIndex(prodSheet), true);

        org.apache.poi.ss.usermodel.Name prodNoRange = workbook.createName();
        prodNoRange.setNameName("ProdNos");
        prodNoRange.setRefersToFormula("产品数据!$A$1:$A$" + Math.max(products.size(), 1));

        org.apache.poi.ss.usermodel.Name prodDataRange = workbook.createName();
        prodDataRange.setNameName("ProdData");
        prodDataRange.setRefersToFormula("产品数据!$A$1:$B$" + Math.max(products.size(), 1));

        // ---- 下拉校验 ----
        org.apache.poi.ss.usermodel.DataValidationHelper dvHelper = sheet.getDataValidationHelper();

        // 产品编号列(索引1=B列)下拉
        org.apache.poi.ss.usermodel.DataValidationConstraint prodConstraint =
                dvHelper.createFormulaListConstraint("ProdNos");
        org.apache.poi.ss.util.CellRangeAddressList prodRange =
                new org.apache.poi.ss.util.CellRangeAddressList(1, 999, 1, 1);
        org.apache.poi.ss.usermodel.DataValidation prodDv = dvHelper.createValidation(prodConstraint, prodRange);
        prodDv.setShowErrorBox(false);
        prodDv.setSuppressDropDownArrow(true);
        sheet.addValidationData(prodDv);

        // 工序名称列(索引5=F列)下拉
        org.apache.poi.ss.usermodel.DataValidationConstraint procConstraint =
                dvHelper.createFormulaListConstraint("ProcNames");
        org.apache.poi.ss.util.CellRangeAddressList procDvRange =
                new org.apache.poi.ss.util.CellRangeAddressList(1, 999, 5, 5);
        org.apache.poi.ss.usermodel.DataValidation procDv = dvHelper.createValidation(procConstraint, procDvRange);
        procDv.setShowErrorBox(false);
        procDv.setSuppressDropDownArrow(true);
        sheet.addValidationData(procDv);

        // ---- 隐藏 Sheet 3：质检模板数据 ----
        org.apache.poi.ss.usermodel.Sheet qcSheet = workbook.createSheet("质检模板数据");
        for (int i = 0; i < qcTemplates.size(); i++) {
            qcSheet.createRow(i).createCell(0).setCellValue(
                    qcTemplates.get(i).getTemplateName() != null ? qcTemplates.get(i).getTemplateName() : "");
        }
        workbook.setSheetHidden(workbook.getSheetIndex(qcSheet), true);
        org.apache.poi.ss.usermodel.Name qcRange = workbook.createName();
        qcRange.setNameName("QcTemplates");
        qcRange.setRefersToFormula("质检模板数据!$A$1:$A$" + Math.max(qcTemplates.size(), 1));

        // 质检模板列(索引10=K列)下拉
        org.apache.poi.ss.usermodel.DataValidation qcDv = dvHelper.createValidation(
                dvHelper.createFormulaListConstraint("QcTemplates"),
                new org.apache.poi.ss.util.CellRangeAddressList(1, 999, 10, 10));
        qcDv.setShowErrorBox(false);
        qcDv.setSuppressDropDownArrow(true);
        sheet.addValidationData(qcDv);

        // ---- 预填 VLOOKUP 公式（第2行到第100行） ----
        for (int rowIdx = 1; rowIdx <= 100; rowIdx++) {
            org.apache.poi.ss.usermodel.Row row = sheet.getRow(rowIdx);
            if (row == null) row = sheet.createRow(rowIdx);
            int n = rowIdx + 1;

            // C列(索引2)：产品名称
            org.apache.poi.ss.usermodel.Cell nameCell = row.getCell(2);
            if (nameCell == null) nameCell = row.createCell(2);
            nameCell.setCellFormula("IF(B" + n + "=\"\",\"\",VLOOKUP(B" + n + ",ProdData,2,FALSE))");
            nameCell.setCellStyle(readonlyStyle);

            // I列(索引8)：覆盖标准工时
            org.apache.poi.ss.usermodel.Cell stdCell = row.getCell(8);
            if (stdCell == null) stdCell = row.createCell(8);
            stdCell.setCellFormula("IF(F" + n + "=\"\",\"\",VLOOKUP(F" + n + ",ProcData,2,FALSE))");

            // J列(索引9)：覆盖计件单价
            org.apache.poi.ss.usermodel.Cell priceCell = row.getCell(9);
            if (priceCell == null) priceCell = row.createCell(9);
            priceCell.setCellFormula("IF(F" + n + "=\"\",\"\",VLOOKUP(F" + n + ",ProcData,3,FALSE))");
        }

        // ---- 输出 ----
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + java.net.URLEncoder.encode("工艺路线导入模板.xlsx", java.nio.charset.StandardCharsets.UTF_8));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /**
     * 销售订单模板：
     * B=客户名称(1)下拉，C=联系人(2)和E=结算方式(4)自动带出
     * G=业务员(6)下拉，J=产品编号(9)下拉，K=产品名称(10)自动带出
     */
    private void exportSalesOrderTemplate(HttpServletResponse response) throws IOException {
        List<BizCustomer> customers = customerMapper.selectList(new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0));
        // 客户数据：名称 | 联系人 | 地址 | 结算方式
        String[][] custData = customers.stream().map(c -> new String[]{
                c.getCustomerName() != null ? c.getCustomerName() : "",
                c.getContactName() != null ? c.getContactName() : "",
                c.getAddress() != null ? c.getAddress() : "",
                c.getSettlementType() != null ? c.getSettlementType() : ""
        }).toArray(String[][]::new);

        String[] empNames = getEmployeeNames();
        List<BizMaterial> products = materialMapper.selectList(new LambdaQueryWrapper<BizMaterial>()
                .eq(BizMaterial::getDeleted, 0).eq(BizMaterial::getCategory, "finished"));

        // 主体VLOOKUP列：联系人C(2)=数据第2列, 收货地址D(3)=数据第3列, 结算方式E(4)=数据第4列
        // 金额N(13) = 数量L(11) * 单价M(12)
        exportOrderTemplate(response, "销售订单导入模板",
                SALES_ORDER_HEADERS, SALES_ORDER_SAMPLES, SALES_ORDER_REQUIRED,
                "客户列表", custData, 1, new int[]{2, 3, 4}, new int[]{2, 3, 4}, 4,
                "SO业务员", empNames, 6,
                products, 9, 10,
                11, 12, 13,
                null, null);
    }

    /**
     * 采购订单模板：
     * B=供应商名称(1)下拉，C=联系人(2)自动带出
     * F=采购员(5)下拉，I=物料编号(8)下拉，J=物料名称(9)自动带出
     */
    private void exportPurchaseOrderTemplate(HttpServletResponse response) throws IOException {
        List<BizSupplier> suppliers = supplierMapper.selectList(new LambdaQueryWrapper<BizSupplier>()
                .eq(BizSupplier::getDeleted, 0));
        // 供应商数据：名称 | 联系人
        String[][] supData = suppliers.stream().map(s -> new String[]{
                s.getSupplierName() != null ? s.getSupplierName() : "",
                s.getContactName() != null ? s.getContactName() : ""
        }).toArray(String[][]::new);

        String[] empNames = getEmployeeNames();
        List<BizMaterial> allMats = materialMapper.selectList(new LambdaQueryWrapper<BizMaterial>()
                .eq(BizMaterial::getDeleted, 0)
                .and(w -> w.ne(BizMaterial::getIsVirtualSemi, 1).or().isNull(BizMaterial::getIsVirtualSemi)));

        // 付款方式从系统配置读取
        String[] paymentTerms = getSettlementTypes();

        // 主体VLOOKUP列：联系人(2)=第2列
        // 付款条款E(4)下拉
        // 金额M(12) = 数量K(10) * 单价L(11)
        exportOrderTemplate(response, "采购订单导入模板",
                PURCHASE_ORDER_HEADERS, PURCHASE_ORDER_SAMPLES, PURCHASE_ORDER_REQUIRED_SET,
                "供应商列表", supData, 1, new int[]{2}, new int[]{2}, 2,
                "PO采购员", empNames, 5,
                allMats, 8, 9,
                10, 11, 12,
                List.of(new DropdownSource("付款方式", "PayTerms", paymentTerms)),
                List.of(new DropdownColumn(4, "PayTerms")));
    }

    /**
     * 订单类模板通用构建
     * @param mainData        主体多列数据（客户/供应商），第1列=名称（下拉），后续列用于VLOOKUP
     * @param mainCol         主体名称在Sheet1中的列索引
     * @param mainFillCols    主体附带字段在Sheet1中的列索引数组（如联系人、结算方式）
     * @param mainDataCols    上述字段对应 mainData 中的列号（VLOOKUP第3参数，从1开始）
     * @param mainTotalCols   mainData 的总列数
     */
    private void exportOrderTemplate(HttpServletResponse response, String fileName,
                                      List<String> headers, List<String[]> samples, Set<String> required,
                                      String mainSheetName, String[][] mainData, int mainCol,
                                      int[] mainFillCols, int[] mainDataCols, int mainTotalCols,
                                      String empSheetName, String[] empNames, int empCol,
                                      List<BizMaterial> materials, int matNoCol, int matNameCol,
                                      int qtyCol, int priceCol, int amountCol,
                                      List<DropdownSource> extraSources, List<DropdownColumn> extraDropdowns) throws IOException {
        org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Sheet1");

        // ---- 样式 ----
        org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font hf = workbook.createFont(); hf.setBold(true); headerStyle.setFont(hf);
        headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);

        org.apache.poi.ss.usermodel.CellStyle reqStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font rf = workbook.createFont(); rf.setBold(true);
        rf.setColor(org.apache.poi.ss.usermodel.IndexedColors.RED.getIndex()); reqStyle.setFont(rf);
        reqStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        reqStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        reqStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);

        org.apache.poi.ss.usermodel.CellStyle sampleStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font sf = workbook.createFont();
        sf.setColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_50_PERCENT.getIndex()); sampleStyle.setFont(sf);
        sampleStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        sampleStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

        org.apache.poi.ss.usermodel.CellStyle roStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font roFont = workbook.createFont();
        roFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_40_PERCENT.getIndex()); roFont.setItalic(true);
        roStyle.setFont(roFont);
        roStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        roStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

        // ---- 表头 ----
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            String name = headers.get(i);
            boolean isReq = required != null && required.contains(name);
            cell.setCellValue(isReq ? "*" + name : name);
            cell.setCellStyle(isReq ? reqStyle : headerStyle);
            sheet.setColumnWidth(i, 5000);
        }

        // ---- 示例数据 ----
        for (int r = 0; r < samples.size(); r++) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(r + 1);
            String[] vals = samples.get(r);
            for (int c = 0; c < vals.length && c < headers.size(); c++) {
                org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
                cell.setCellValue(vals[c] != null ? vals[c] : "");
                cell.setCellStyle(sampleStyle);
            }
        }

        // ---- 隐藏Sheet：主体数据（客户/供应商，含名称+联系人+结算方式等） ----
        org.apache.poi.ss.usermodel.Sheet mainSheet = workbook.createSheet(mainSheetName);
        for (int i = 0; i < mainData.length; i++) {
            org.apache.poi.ss.usermodel.Row row = mainSheet.createRow(i);
            for (int c = 0; c < mainData[i].length; c++) {
                row.createCell(c).setCellValue(mainData[i][c]);
            }
        }
        workbook.setSheetHidden(workbook.getSheetIndex(mainSheet), true);
        // 名称列范围（下拉用）
        org.apache.poi.ss.usermodel.Name mnr = workbook.createName();
        mnr.setNameName("OrderMainNames");
        mnr.setRefersToFormula(mainSheetName + "!$A$1:$A$" + Math.max(mainData.length, 1));
        // 完整数据范围（VLOOKUP用）
        String lastCol = String.valueOf((char) ('A' + mainTotalCols - 1));
        org.apache.poi.ss.usermodel.Name mdr = workbook.createName();
        mdr.setNameName("OrderMainData");
        mdr.setRefersToFormula(mainSheetName + "!$A$1:$" + lastCol + "$" + Math.max(mainData.length, 1));

        // ---- 隐藏Sheet：人员 ----
        org.apache.poi.ss.usermodel.Sheet empSheet = workbook.createSheet(empSheetName);
        for (int i = 0; i < empNames.length; i++) empSheet.createRow(i).createCell(0).setCellValue(empNames[i]);
        workbook.setSheetHidden(workbook.getSheetIndex(empSheet), true);
        org.apache.poi.ss.usermodel.Name enr = workbook.createName();
        enr.setNameName("OrderEmpNames");
        enr.setRefersToFormula(empSheetName + "!$A$1:$A$" + Math.max(empNames.length, 1));

        // ---- 隐藏Sheet：物料/产品数据（编号|名称） ----
        org.apache.poi.ss.usermodel.Sheet matSheet = workbook.createSheet("订单物料数据");
        for (int i = 0; i < materials.size(); i++) {
            BizMaterial m = materials.get(i);
            org.apache.poi.ss.usermodel.Row row = matSheet.createRow(i);
            row.createCell(0).setCellValue(m.getMaterialNo() != null ? m.getMaterialNo() : "");
            row.createCell(1).setCellValue(m.getMaterialName() != null ? m.getMaterialName() : "");
        }
        workbook.setSheetHidden(workbook.getSheetIndex(matSheet), true);
        org.apache.poi.ss.usermodel.Name matNos = workbook.createName();
        matNos.setNameName("OrderMatNos");
        matNos.setRefersToFormula("订单物料数据!$A$1:$A$" + Math.max(materials.size(), 1));
        org.apache.poi.ss.usermodel.Name matData = workbook.createName();
        matData.setNameName("OrderMatData");
        matData.setRefersToFormula("订单物料数据!$A$1:$B$" + Math.max(materials.size(), 1));

        // ---- 下拉校验 ----
        org.apache.poi.ss.usermodel.DataValidationHelper dvh = sheet.getDataValidationHelper();
        addDropdown(sheet, dvh, "OrderMainNames", mainCol);
        addDropdown(sheet, dvh, "OrderEmpNames", empCol);
        addDropdown(sheet, dvh, "OrderMatNos", matNoCol);

        // ---- 额外下拉 ----
        if (extraSources != null) {
            for (DropdownSource src : extraSources) {
                org.apache.poi.ss.usermodel.Sheet hidden = workbook.createSheet(src.sheetName);
                for (int i = 0; i < src.values.length; i++) {
                    hidden.createRow(i).createCell(0).setCellValue(src.values[i]);
                }
                workbook.setSheetHidden(workbook.getSheetIndex(hidden), true);
                org.apache.poi.ss.usermodel.Name nr = workbook.createName();
                nr.setNameName(src.rangeName);
                nr.setRefersToFormula(src.sheetName + "!$A$1:$A$" + Math.max(src.values.length, 1));
            }
        }
        if (extraDropdowns != null) {
            for (DropdownColumn dc : extraDropdowns) {
                addDropdown(sheet, dvh, dc.rangeName, dc.colIndex);
            }
        }

        // ---- VLOOKUP 公式 ----
        String mainColLetter = String.valueOf((char) ('A' + mainCol));
        String matNoColLetter = String.valueOf((char) ('A' + matNoCol));
        for (int rowIdx = 1; rowIdx <= 100; rowIdx++) {
            org.apache.poi.ss.usermodel.Row row = sheet.getRow(rowIdx);
            if (row == null) row = sheet.createRow(rowIdx);
            int n = rowIdx + 1;

            // 主体附带字段（联系人、结算方式等）- 自动带出但可手动修改
            for (int k = 0; k < mainFillCols.length; k++) {
                org.apache.poi.ss.usermodel.Cell cell = row.getCell(mainFillCols[k]);
                if (cell == null) cell = row.createCell(mainFillCols[k]);
                cell.setCellFormula("IF(" + mainColLetter + n + "=\"\",\"\",VLOOKUP(" + mainColLetter + n + ",OrderMainData," + mainDataCols[k] + ",FALSE))");
            }

            // 物料/产品名称
            org.apache.poi.ss.usermodel.Cell nameCell = row.getCell(matNameCol);
            if (nameCell == null) nameCell = row.createCell(matNameCol);
            nameCell.setCellFormula("IF(" + matNoColLetter + n + "=\"\",\"\",VLOOKUP(" + matNoColLetter + n + ",OrderMatData,2,FALSE))");
            nameCell.setCellStyle(roStyle);

            // 金额 = 数量 * 单价
            if (qtyCol >= 0 && priceCol >= 0 && amountCol >= 0) {
                String qtyRef = String.valueOf((char) ('A' + qtyCol)) + n;
                String priceRef = String.valueOf((char) ('A' + priceCol)) + n;
                org.apache.poi.ss.usermodel.Cell amtCell = row.getCell(amountCol);
                if (amtCell == null) amtCell = row.createCell(amountCol);
                amtCell.setCellFormula("IF(OR(" + qtyRef + "=\"\"," + priceRef + "=\"\"),\"\"," + qtyRef + "*" + priceRef + ")");
            }
        }

        // ---- 输出 ----
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + java.net.URLEncoder.encode(fileName + ".xlsx", java.nio.charset.StandardCharsets.UTF_8));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /** 添加下拉校验到指定列 */
    private void addDropdown(org.apache.poi.ss.usermodel.Sheet sheet,
                              org.apache.poi.ss.usermodel.DataValidationHelper dvh,
                              String rangeName, int colIndex) {
        org.apache.poi.ss.usermodel.DataValidation dv = dvh.createValidation(
                dvh.createFormulaListConstraint(rangeName),
                new org.apache.poi.ss.util.CellRangeAddressList(1, 999, colIndex, colIndex));
        dv.setShowErrorBox(false);
        dv.setSuppressDropDownArrow(true);
        sheet.addValidationData(dv);
    }

    /**
     * BOM模板：
     * - 产品编号(A)下拉选择（仅成品），产品名称(B) VLOOKUP 自动带出
     * - 子件物料编号(E)下拉选择（全部物料），子件物料名称(F) VLOOKUP 自动带出
     *
     * 列布局：A=产品编号(0), B=产品名称(1), C=版本号(2), D=版本状态(3),
     *         E=子件物料编号(4), F=子件物料名称(5), G=来源类型(6), H=数量(7),
     *         I=损耗率(8), J=排序号(9), K=创建时间(10)
     */
    private void exportBomTemplate(HttpServletResponse response) throws IOException {
        // 加载成品列表
        List<BizMaterial> products = materialMapper.selectList(new LambdaQueryWrapper<BizMaterial>()
                .eq(BizMaterial::getDeleted, 0)
                .eq(BizMaterial::getCategory, "finished"));
        // 加载全部物料（子件可以是任何物料）
        List<BizMaterial> allMaterials = materialMapper.selectList(new LambdaQueryWrapper<BizMaterial>()
                .eq(BizMaterial::getDeleted, 0)
                .and(w -> w.ne(BizMaterial::getIsVirtualSemi, 1).or().isNull(BizMaterial::getIsVirtualSemi)));

        org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Sheet1");

        // ---- 样式 ----
        org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font hf = workbook.createFont();
        hf.setBold(true);
        headerStyle.setFont(hf);
        headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);

        org.apache.poi.ss.usermodel.CellStyle reqStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font rf = workbook.createFont();
        rf.setBold(true);
        rf.setColor(org.apache.poi.ss.usermodel.IndexedColors.RED.getIndex());
        reqStyle.setFont(rf);
        reqStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        reqStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        reqStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);

        org.apache.poi.ss.usermodel.CellStyle sampleStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font sf = workbook.createFont();
        sf.setColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_50_PERCENT.getIndex());
        sampleStyle.setFont(sf);
        sampleStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        sampleStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

        org.apache.poi.ss.usermodel.CellStyle readonlyStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font roFont = workbook.createFont();
        roFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_40_PERCENT.getIndex());
        roFont.setItalic(true);
        readonlyStyle.setFont(roFont);
        readonlyStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        readonlyStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

        // ---- 写表头 ----
        List<String> headers = BOM_HEADERS;
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            String name = headers.get(i);
            boolean isReq = BOM_REQUIRED.contains(name);
            cell.setCellValue(isReq ? "*" + name : name);
            cell.setCellStyle(isReq ? reqStyle : headerStyle);
            sheet.setColumnWidth(i, 5000);
        }

        // ---- 写示例数据 ----
        for (int r = 0; r < BOM_SAMPLES.size(); r++) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(r + 1);
            String[] vals = BOM_SAMPLES.get(r);
            for (int c = 0; c < vals.length && c < headers.size(); c++) {
                org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
                cell.setCellValue(vals[c] != null ? vals[c] : "");
                cell.setCellStyle(sampleStyle);
            }
        }

        // ---- 隐藏 Sheet 1：成品数据（编号 | 名称） ----
        org.apache.poi.ss.usermodel.Sheet prodSheet = workbook.createSheet("成品数据");
        for (int i = 0; i < products.size(); i++) {
            BizMaterial m = products.get(i);
            org.apache.poi.ss.usermodel.Row row = prodSheet.createRow(i);
            row.createCell(0).setCellValue(m.getMaterialNo() != null ? m.getMaterialNo() : "");
            row.createCell(1).setCellValue(m.getMaterialName() != null ? m.getMaterialName() : "");
        }
        workbook.setSheetHidden(workbook.getSheetIndex(prodSheet), true);

        org.apache.poi.ss.usermodel.Name prodNoRange = workbook.createName();
        prodNoRange.setNameName("BomProdNos");
        prodNoRange.setRefersToFormula("成品数据!$A$1:$A$" + Math.max(products.size(), 1));
        org.apache.poi.ss.usermodel.Name prodDataRange = workbook.createName();
        prodDataRange.setNameName("BomProdData");
        prodDataRange.setRefersToFormula("成品数据!$A$1:$B$" + Math.max(products.size(), 1));

        // ---- 隐藏 Sheet 2：全部物料数据（编号 | 名称） ----
        org.apache.poi.ss.usermodel.Sheet matSheet = workbook.createSheet("物料数据");
        for (int i = 0; i < allMaterials.size(); i++) {
            BizMaterial m = allMaterials.get(i);
            org.apache.poi.ss.usermodel.Row row = matSheet.createRow(i);
            row.createCell(0).setCellValue(m.getMaterialNo() != null ? m.getMaterialNo() : "");
            row.createCell(1).setCellValue(m.getMaterialName() != null ? m.getMaterialName() : "");
        }
        workbook.setSheetHidden(workbook.getSheetIndex(matSheet), true);

        org.apache.poi.ss.usermodel.Name matNoRange = workbook.createName();
        matNoRange.setNameName("BomMatNos");
        matNoRange.setRefersToFormula("物料数据!$A$1:$A$" + Math.max(allMaterials.size(), 1));
        org.apache.poi.ss.usermodel.Name matDataRange = workbook.createName();
        matDataRange.setNameName("BomMatData");
        matDataRange.setRefersToFormula("物料数据!$A$1:$B$" + Math.max(allMaterials.size(), 1));

        // ---- 下拉校验 ----
        org.apache.poi.ss.usermodel.DataValidationHelper dvHelper = sheet.getDataValidationHelper();

        // 产品编号列(A=0)下拉
        org.apache.poi.ss.usermodel.DataValidation prodDv = dvHelper.createValidation(
                dvHelper.createFormulaListConstraint("BomProdNos"),
                new org.apache.poi.ss.util.CellRangeAddressList(1, 999, 0, 0));
        prodDv.setShowErrorBox(false);
        prodDv.setSuppressDropDownArrow(true);
        sheet.addValidationData(prodDv);

        // 子件物料编号列(F=5)下拉
        org.apache.poi.ss.usermodel.DataValidation matDv = dvHelper.createValidation(
                dvHelper.createFormulaListConstraint("BomMatNos"),
                new org.apache.poi.ss.util.CellRangeAddressList(1, 999, 5, 5));
        matDv.setShowErrorBox(false);
        matDv.setSuppressDropDownArrow(true);
        sheet.addValidationData(matDv);

        // 层级列(E=4)下拉：1~5级
        org.apache.poi.ss.usermodel.DataValidation levelDv = dvHelper.createValidation(
                dvHelper.createExplicitListConstraint(new String[]{"1", "2", "3", "4", "5"}),
                new org.apache.poi.ss.util.CellRangeAddressList(1, 999, 4, 4));
        levelDv.setShowErrorBox(false);
        levelDv.setSuppressDropDownArrow(true);
        sheet.addValidationData(levelDv);

        // ---- 预填 VLOOKUP 公式（第2行到第100行） ----
        for (int rowIdx = 1; rowIdx <= 100; rowIdx++) {
            org.apache.poi.ss.usermodel.Row row = sheet.getRow(rowIdx);
            if (row == null) row = sheet.createRow(rowIdx);
            int n = rowIdx + 1;

            // B列(1)：产品名称 =IF(A{n}="","",VLOOKUP(A{n},BomProdData,2,FALSE))
            org.apache.poi.ss.usermodel.Cell prodNameCell = row.getCell(1);
            if (prodNameCell == null) prodNameCell = row.createCell(1);
            prodNameCell.setCellFormula("IF(A" + n + "=\"\",\"\",VLOOKUP(A" + n + ",BomProdData,2,FALSE))");
            prodNameCell.setCellStyle(readonlyStyle);

            // G列(6)：子件物料名称 =IF(F{n}="","",VLOOKUP(F{n},BomMatData,2,FALSE))
            org.apache.poi.ss.usermodel.Cell matNameCell = row.getCell(6);
            if (matNameCell == null) matNameCell = row.createCell(6);
            matNameCell.setCellFormula("IF(F" + n + "=\"\",\"\",VLOOKUP(F" + n + ",BomMatData,2,FALSE))");
            matNameCell.setCellStyle(readonlyStyle);
        }

        // ---- 输出 ----
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + java.net.URLEncoder.encode("BOM导入模板.xlsx", java.nio.charset.StandardCharsets.UTF_8));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    // ==================== 物料规格导入 ====================

    @Transactional
    public Map<String, Object> importMaterialSpecs(MultipartFile file) {
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, MATERIAL_SPEC_HEADERS);
        int success = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 2;
            Map<String, String> r = rows.get(i);
            try {
                String specName = r.get("规格名称");
                if (specName == null || specName.isEmpty()) {
                    errors.add("第" + rowNum + "行: 规格名称不能为空");
                    continue;
                }

                // 检查是否已存在
                Long exists = materialSpecMapper.selectCount(new LambdaQueryWrapper<BizMaterialSpec>()
                        .eq(BizMaterialSpec::getSpecName, specName)
                        .eq(BizMaterialSpec::getDeleted, 0));
                if (exists > 0) {
                    errors.add("第" + rowNum + "行: 规格名称'" + specName + "'已存在，已跳过");
                    continue;
                }

                BizMaterialSpec spec = new BizMaterialSpec();
                spec.setSpecName(specName);
                spec.setSortOrder(i);
                spec.setStatus("active");
                LocalDateTime createDt = parseDatetime(r.get("创建时间"));
                if (createDt != null) spec.setCreateTime(createDt);
                materialSpecMapper.insert(spec);
                success++;
            } catch (Exception e) {
                errors.add("第" + rowNum + "行: " + friendlyError(e));
            }
        }
        return buildResult(success, rows.size(), errors);
    }

    // ==================== 物料导入（支持动态规格列） ====================

    @Transactional
    public Map<String, Object> importMaterials(MultipartFile file) {
        long t0 = System.currentTimeMillis();
        // 使用动态表头解析（固定列之后的列视为规格列）
        ExcelUtil.ParseResult parseResult = ExcelUtil.parseImportDynamic(file, MATERIAL_FIXED_COLUMN_COUNT);
        List<Map<String, String>> rows = parseResult.getRows();
        List<String> dynamicHeaders = parseResult.getDynamicHeaders();

        // 加载仓库映射（名称 -> ID）
        Map<String, Long> warehouseMap = loadWarehouseMap();
        // 加载供应商映射（名称 -> ID）
        Map<String, Long> supplierMap = loadSupplierMap();

        // 一次性加载物料类别:catByName(名称→id)、catByCode(code→id,用于按物料类型兜底)
        List<com.aimes.modules.master.entity.BizMaterialCategory> allCats = materialCategoryMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.master.entity.BizMaterialCategory>()
                        .eq(com.aimes.modules.master.entity.BizMaterialCategory::getDeleted, 0));
        Map<String, Long> catByName = new HashMap<>();
        Map<String, Long> catByNormName = new HashMap<>(); // 去空白后的索引,兜底匹配
        Map<String, Long> catByCode = new HashMap<>();
        for (var c : allCats) {
            if (c.getCategoryName() != null) {
                catByName.put(c.getCategoryName().trim(), c.getId());
                String norm = c.getCategoryName().trim().replace(" ", "").replace("　", "").replaceAll("\\s+", "");
                catByNormName.putIfAbsent(norm, c.getId());
            }
            if (c.getCategoryCode() != null) catByCode.putIfAbsent(c.getCategoryCode(), c.getId());
        }

        // 加载已有的规格名称集合，用于匹配动态表头
        Set<String> validSpecNames = materialSpecMapper.selectList(
                        new LambdaQueryWrapper<BizMaterialSpec>()
                                .eq(BizMaterialSpec::getDeleted, 0)
                                .eq(BizMaterialSpec::getStatus, "active"))
                .stream().map(BizMaterialSpec::getSpecName).collect(Collectors.toSet());

        // 过滤出有效的规格列（必须在物料规格表中存在）
        List<String> matchedSpecHeaders = dynamicHeaders.stream()
                .filter(validSpecNames::contains)
                .collect(Collectors.toList());

        List<String> errors = new ArrayList<>();

        // 一次性加载数据库已有物料:签名集合 + 物料编号集合(用于去重,避免逐行 SELECT)
        List<BizMaterial> existingMaterials = materialMapper.selectList(
                new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0));
        Set<String> existingSignatures = existingMaterials.stream()
                .map(m -> buildMaterialSignature(m.getMaterialName(), m.getSpec(), m.getUnit()))
                .collect(Collectors.toSet());
        Set<String> existingMaterialNos = existingMaterials.stream()
                .map(BizMaterial::getMaterialNo).filter(Objects::nonNull).collect(Collectors.toSet());

        // 本次导入已处理的签名/编号（避免同一批次中重复行导致重复插入）
        Set<String> importedSignatures = new HashSet<>();
        Set<String> importedNos = new HashSet<>();

        // 累积所有通过校验的新物料,循环结束后一次性 saveBatch
        List<BizMaterial> toInsert = new ArrayList<>(rows.size());
        com.fasterxml.jackson.databind.ObjectMapper specOm = new com.fasterxml.jackson.databind.ObjectMapper();

        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 2;
            Map<String, String> r = rows.get(i);
            try {
                String name = r.get("物料名称");
                if (name == null || name.isEmpty()) {
                    errors.add("第" + rowNum + "行: 物料名称不能为空");
                    continue;
                }

                BizMaterial m = new BizMaterial();
                String materialNo = r.get("物料编号");
                m.setMaterialNo(materialNo != null && !materialNo.isEmpty() ? materialNo : CodeUtil.generate("M"));
                m.setMaterialName(name);
                String matType = mapMaterialType(r.get("物料类型"));
                m.setMaterialType(matType);
                m.setCategory(matType); // category 跟 materialType 保持一致
                // 物料类别(具体类目): 优先用模板里填的"物料类别"按名称匹配;
                // 未填 → 按物料类型 code 兜底到顶层类目
                // 填了但没匹配上 → 保留 category_id=null,且报错提示(不再静默回退,避免误以为已归类)
                String catName = r.get("物料类别");
                if (catName != null && !catName.isBlank()) {
                    Long catId = catByName.get(catName.trim());
                    if (catId == null) {
                        String norm = catName.trim().replace(" ", "").replace("　", "").replaceAll("\s+", "");
                        catId = catByNormName.get(norm);
                    }
                    if (catId != null) {
                        m.setCategoryId(catId);
                    } else {
                        errors.add("第" + rowNum + "行: 物料类别'" + catName + "'在 biz_material_category 中未找到(请检查是否已建类别/是否被软删/是否有空白),本行未归类");
                    }
                } else {
                    Long byCode = catByCode.get(matType);
                    if (byCode != null) m.setCategoryId(byCode);
                }
                m.setUnit(r.get("主单位"));

                // 多单位换算
                String auxUnit = r.get("辅助单位");
                String ratioStr = r.get("换算比例(1主单位=N辅助单位)");
                if (auxUnit != null && !auxUnit.isEmpty() && ratioStr != null && !ratioStr.isEmpty()) {
                    double ratio = Double.parseDouble(ratioStr);
                    String unit = m.getUnit() != null ? m.getUnit() : "";
                    m.setUnitConfig(String.format(
                            "{\"baseUnit\":\"%s\",\"conversions\":[{\"unit\":\"%s\",\"ratio\":%.4f}]}",
                            unit, auxUnit, ratio));
                }

                m.setMaterialAttributes(mapMaterialAttributes(r.get("物料属性(采购/生产/销售)")));
                m.setSourceType(mapSourceType(r.get("物料来源(采购/自制/外协)")));
                m.setStandardCost(parseBD(r.get("标准成本(含税)")));
                m.setMinStock(parseBD(r.get("最小库存")));
                m.setMaxStock(parseBD(r.get("最大库存")));
                m.setPurchaseLeadTime(parseInt(r.get("采购提前期(天)")));
                m.setProductionLeadTime(parseInt(r.get("生产提前期(天)")));
                m.setMinOrderQty(parseBD(r.get("最小批量")));
                m.setOrderQtyMultiple(parseBD(r.get("批量倍数")));

                // 默认仓库（按仓库名称匹配ID）
                String whName = r.get("默认仓库");
                if (whName != null && !whName.isEmpty()) {
                    Long whId = warehouseMap.get(whName);
                    if (whId != null) {
                        m.setDefaultWarehouseId(whId);
                    } else {
                        errors.add("第" + rowNum + "行: 仓库'" + whName + "'不存在，已忽略");
                    }
                }

                // 默认供应商（按供应商名称匹配ID）
                String supName = r.get("默认供应商");
                if (supName != null && !supName.isEmpty()) {
                    Long supId = supplierMap.get(supName);
                    if (supId != null) {
                        m.setDefaultSupplierId(supId);
                    } else {
                        errors.add("第" + rowNum + "行: 供应商'" + supName + "'不存在，已忽略");
                    }
                }

                m.setRemark(r.get("备注"));
                m.setStatus("active");

                // 创建时间
                LocalDateTime createDt = parseDatetime(r.get("创建时间"));
                if (createDt != null) m.setCreateTime(createDt);

                // 构建规格：读取动态规格列，只保留有值的规格
                // 单规格（只有1列有数据）：存纯文本，如 "304不锈钢"
                // 多规格（2列以上有数据）：存JSON数组，如 [{"name":"材质","values":["304"]},{"name":"尺寸","values":["M10"]}]
                List<Map<String, Object>> specList = new ArrayList<>();
                for (String specName : matchedSpecHeaders) {
                    String val = r.get(specName);
                    if (val != null && !val.isEmpty()) {
                        Map<String, Object> specItem = new LinkedHashMap<>();
                        specItem.put("name", specName);
                        specItem.put("values", List.of(val));
                        specList.add(specItem);
                    }
                }
                if (specList.size() == 1) {
                    m.setSpec((String) ((List<?>) specList.get(0).get("values")).get(0));
                } else if (specList.size() > 1) {
                    try {
                        m.setSpec(specOm.writeValueAsString(specList));
                    } catch (Exception e) {
                        m.setSpec(specList.toString());
                    }
                }

                boolean hasExplicitNo = materialNo != null && !materialNo.isEmpty();

                // 用户明确填了物料编号 → 按预加载的编号集合判重
                if (hasExplicitNo) {
                    if (existingMaterialNos.contains(materialNo) || importedNos.contains(materialNo)) {
                        errors.add("第" + rowNum + "行: 物料编号'" + materialNo + "'已存在，已跳过");
                        continue;
                    }
                    importedNos.add(materialNo);
                } else {
                    // 未填编号 → 按 名称+规格+单位 签名去重(数据库已有或本次批量已处理)
                    String signature = buildMaterialSignature(m.getMaterialName(), m.getSpec(), m.getUnit());
                    if (existingSignatures.contains(signature) || importedSignatures.contains(signature)) {
                        errors.add("第" + rowNum + "行: 物料'" + name + "'(规格/单位相同)已存在，已跳过");
                        continue;
                    }
                    importedSignatures.add(signature);
                }

                // 累积到批量列表,循环结束后一次性 saveBatch
                toInsert.add(m);
            } catch (Exception e) {
                errors.add("第" + rowNum + "行: " + friendlyError(e));
            }
        }

        // 批量插入:使用 MyBatis BATCH 执行器,大幅减少与 MySQL 的往返
        int success = 0;
        if (!toInsert.isEmpty()) {
            final int BATCH_SIZE = 500;
            try (org.apache.ibatis.session.SqlSession batchSession = sqlSessionFactory.openSession(
                    org.apache.ibatis.session.ExecutorType.BATCH, false)) {
                BizMaterialMapper batchMapper = batchSession.getMapper(BizMaterialMapper.class);
                for (int i = 0; i < toInsert.size(); i++) {
                    batchMapper.insert(toInsert.get(i));
                    if ((i + 1) % BATCH_SIZE == 0) {
                        batchSession.flushStatements();
                    }
                }
                batchSession.flushStatements();
                batchSession.commit();
                success = toInsert.size();
            } catch (Exception e) {
                log.error("批量插入物料失败", e);
                errors.add("批量写入失败: " + friendlyError(e));
            }
        }

        long elapsed = System.currentTimeMillis() - t0;
        log.info("importMaterials 完成: rows={}, inserted={}, errors={}, {}ms",
                rows.size(), success, errors.size(), elapsed);

        Map<String, Object> result = buildResult(success, rows.size(), errors);
        result.put("elapsedMs", elapsed);
        // 返回匹配到的规格列信息
        result.put("matchedSpecs", matchedSpecHeaders);
        if (!dynamicHeaders.isEmpty()) {
            List<String> unmatchedSpecs = dynamicHeaders.stream()
                    .filter(h -> !validSpecNames.contains(h))
                    .collect(Collectors.toList());
            if (!unmatchedSpecs.isEmpty()) {
                result.put("unmatchedSpecs", unmatchedSpecs);
            }
        }
        return result;
    }

    // ==================== 销售订单导入 ====================

    @Transactional
    public Map<String, Object> importSalesOrders(MultipartFile file) {
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, SALES_ORDER_HEADERS);
        int success = 0;
        List<String> errors = new ArrayList<>();

        Map<String, Long> customerMap = loadCustomerMap();
        Map<String, Long> materialMap = loadMaterialMap();
        Map<String, Long> userMap = loadUserMap();

        // 智能分组：有订单编号按编号分，没编号按"客户名称+下单日期+业务员"组合分
        LinkedHashMap<String, List<IndexedRow>> grouped = groupOrderRows(rows,
                "订单编号", "客户名称", "下单日期", "业务员");

        for (Map.Entry<String, List<IndexedRow>> entry : grouped.entrySet()) {
            String groupKey = entry.getKey();
            List<IndexedRow> orderRows = entry.getValue();
            Map<String, String> first = orderRows.get(0).data;
            int firstRowNum = orderRows.get(0).rowNum;
            // groupKey 以 "NO:" 开头表示有订单编号，否则为自动生成
            String orderNo = groupKey.startsWith("NO:") ? groupKey.substring(3) : null;

            try {
                if (orderNo != null) {
                    Long exists = salesOrderMapper.selectCount(new LambdaQueryWrapper<BizSalesOrder>()
                            .eq(BizSalesOrder::getOrderNo, orderNo).eq(BizSalesOrder::getDeleted, 0));
                    if (exists > 0) {
                        errors.add("第" + firstRowNum + "行: 订单编号'" + orderNo + "'已存在，已跳过");
                        continue;
                    }
                }

                String customerName = first.get("客户名称");
                Long customerId = customerMap.get(customerName);
                if (customerName != null && !customerName.isEmpty() && customerId == null) {
                    errors.add("第" + firstRowNum + "行: 客户'" + customerName + "'不存在");
                    continue;
                }

                BizSalesOrder order = new BizSalesOrder();
                order.setOrderNo(orderNo != null ? orderNo : CodeUtil.generate("SO"));
                order.setCustomerId(customerId);
                order.setContactName(first.get("联系人"));
                order.setShippingAddress(first.get("收货地址"));
                order.setSettlementType(first.get("结算方式"));
                order.setDeliveryDate(parseDate(first.get("交货日期")));
                order.setStatus("confirmed");
                order.setRemark(first.get("订单备注"));

                // 业务员
                String salesmanName = first.get("业务员");
                if (salesmanName != null && !salesmanName.isEmpty()) {
                    Long salesmanId = userMap.get(salesmanName);
                    if (salesmanId != null) {
                        order.setSalesmanId(salesmanId);
                    }
                }

                LocalDateTime createDt = parseDatetime(first.get("下单日期"));
                if (createDt != null) order.setCreateTime(createDt);

                // 先校验所有明细行，有任何失败则整条订单不导入
                List<BizSalesOrderItem> validItems = new ArrayList<>();
                BigDecimal totalAmount = BigDecimal.ZERO;
                boolean hasItemError = false;
                for (IndexedRow ir : orderRows) {
                    Map<String, String> r = ir.data;
                    String productNo = r.get("产品编号");
                    String productName = r.get("产品名称");
                    Long productId = null;

                    if (productNo != null && !productNo.isEmpty()) {
                        productId = materialMap.get(productNo);
                        if (productId == null) {
                            errors.add("第" + ir.rowNum + "行: 产品编号'" + productNo + "'不存在");
                            hasItemError = true;
                            continue;
                        }
                    } else if (productName != null && !productName.isEmpty()) {
                        // 产品编号为空 → 按名称自动创建物料
                        BizMaterial newMat = new BizMaterial();
                        newMat.setMaterialNo(CodeUtil.generate("M"));
                        newMat.setMaterialName(productName);
                        newMat.setMaterialType("finished");
                        newMat.setCategory("finished");
                        autoSetCategoryId(newMat, "finished");
                        newMat.setStatus("active");
                        newMat.setMaterialAttributes("production,sales");
                        // 解析规格: "材质:NBR, 尺寸:015mm" → JSON
                        String specStr = r.get("规格型号");
                        if (specStr != null && !specStr.isEmpty()) {
                            newMat.setSpec(parseSpecToJson(specStr));
                        }
                        // 解析单位: "箱 (1箱=100个)" → unit=箱 + unitConfig
                        String unitStr = r.get("计量单位");
                        if (unitStr != null && !unitStr.isEmpty()) {
                            parseAndSetUnit(newMat, unitStr);
                        }
                        // 默认仓库:成品仓
                        SysWarehouse finishedWh = sysWarehouseMapper.selectOne(
                                new LambdaQueryWrapper<SysWarehouse>()
                                        .eq(SysWarehouse::getWarehouseType, "finished")
                                        .eq(SysWarehouse::getDeleted, 0).last("LIMIT 1"));
                        if (finishedWh != null) newMat.setDefaultWarehouseId(finishedWh.getId());
                        materialMapper.insert(newMat);
                        productId = newMat.getId();
                        materialMap.put(newMat.getMaterialNo(), productId);
                    } else {
                        String qty = r.get("数量");
                        if (qty != null && !qty.isEmpty()) {
                            errors.add("第" + ir.rowNum + "行: 产品编号和产品名称均为空");
                            hasItemError = true;
                        }
                        continue;
                    }

                    // 自动给关联物料加上"生产+销售"属性 + 补齐规格/单位
                    BizMaterial mat = materialMapper.selectById(productId);
                    if (mat != null) {
                        String attrs = mat.getMaterialAttributes();
                        boolean changed = false;
                        if (attrs == null || attrs.isEmpty()) { attrs = "production,sales"; changed = true; }
                        else {
                            if (!attrs.contains("production")) { attrs += ",production"; changed = true; }
                            if (!attrs.contains("sales")) { attrs += ",sales"; changed = true; }
                        }
                        if (changed) mat.setMaterialAttributes(attrs);
                        // 补齐规格(如果物料没有规格但 Excel 有)
                        String specStr = r.get("规格型号");
                        if (specStr != null && !specStr.isEmpty() && (mat.getSpec() == null || mat.getSpec().isEmpty())) {
                            mat.setSpec(parseSpecToJson(specStr));
                            changed = true;
                        }
                        // 补齐单位(如果物料没有 unitConfig 但 Excel 有多单位)
                        String unitStr = r.get("计量单位");
                        if (unitStr != null && !unitStr.isEmpty() && mat.getUnitConfig() == null) {
                            parseAndSetUnit(mat, unitStr);
                            changed = true;
                        }
                        if (changed) materialMapper.updateById(mat);
                    }

                    BizSalesOrderItem item = new BizSalesOrderItem();
                    item.setProductId(productId);
                    item.setQuantity(parseBD(r.get("数量")));
                    item.setUnitPrice(parseBD(r.get("含税单价")));
                    item.setTaxRate(parseBD(r.get("税率(%)")));
                    item.setProducedQty(BigDecimal.ZERO);
                    item.setShippedQty(BigDecimal.ZERO);
                    item.setRemark(r.get("行备注"));

                    // 含税总价 = 数量 × 含税单价 (单价是含税口径,直接相乘)
                    BigDecimal amount = parseBD(r.get("含税金额"));
                    if (amount == null && item.getQuantity() != null && item.getUnitPrice() != null) {
                        amount = item.getQuantity().multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
                    }
                    item.setAmount(amount);
                    if (amount != null) totalAmount = totalAmount.add(amount);
                    if (createDt != null) item.setCreateTime(createDt);

                    validItems.add(item);
                }

                if (hasItemError || validItems.isEmpty()) {
                    if (validItems.isEmpty()) {
                        errors.add("第" + firstRowNum + "行: 订单没有有效的产品明细行，整单跳过");
                    } else {
                        errors.add("第" + firstRowNum + "行: 订单存在明细错误，整单跳过");
                    }
                    continue;
                }

                // 校验全部通过，执行插入
                salesOrderMapper.insert(order);
                for (BizSalesOrderItem item : validItems) {
                    item.setOrderId(order.getId());
                    salesOrderItemMapper.insert(item);
                }
                order.setTotalAmount(totalAmount);
                salesOrderMapper.updateById(order);
                success++;
            } catch (Exception e) {
                errors.add("第" + firstRowNum + "行: " + friendlyError(e));
            }
        }
        return buildResult(success, grouped.size(), errors);
    }

    // ==================== BOM导入 ====================

    @Transactional
    public Map<String, Object> importBom(MultipartFile file) {
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, BOM_HEADERS);
        int success = 0;
        List<String> errors = new ArrayList<>();

        Map<String, Long> materialMap = loadMaterialMap();

        LinkedHashMap<String, List<IndexedRow>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> r = rows.get(i);
            String key = r.get("产品编号") + "|" + r.get("版本号");
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(new IndexedRow(i + 2, r));
        }

        for (Map.Entry<String, List<IndexedRow>> entry : grouped.entrySet()) {
            List<IndexedRow> bomRows = entry.getValue();
            Map<String, String> first = bomRows.get(0).data;
            int firstRowNum = bomRows.get(0).rowNum;

            try {
                String productNo = first.get("产品编号");
                Long productId = materialMap.get(productNo);
                if (productId == null) {
                    errors.add("第" + firstRowNum + "行: 产品编号'" + productNo + "'不存在");
                    continue;
                }

                String versionNo = first.get("版本号");
                if (versionNo == null || versionNo.isEmpty()) versionNo = "V1.0";

                String status = first.get("版本状态(draft/active/deprecated)");
                if (status == null || status.isEmpty()) status = "draft";

                Long exists = bomVersionMapper.selectCount(new LambdaQueryWrapper<BizBomVersion>()
                        .eq(BizBomVersion::getProductId, productId)
                        .eq(BizBomVersion::getVersionNo, versionNo)
                        .eq(BizBomVersion::getDeleted, 0));
                if (exists > 0) {
                    errors.add("第" + firstRowNum + "行: 产品'" + productNo + "'的BOM版本'" + versionNo + "'已存在，已跳过");
                    continue;
                }

                BizBomVersion version = new BizBomVersion();
                version.setProductId(productId);
                version.setVersionNo(versionNo);
                version.setStatus(status);

                LocalDateTime createDt = parseDatetime(first.get("创建时间"));
                if (createDt != null) version.setCreateTime(createDt);

                // 先校验所有子件并记录层级，有任何失败则整个BOM版本不导入
                // leveledItems 是 <层级, BizBomItem> 对的列表，保持原始顺序
                List<int[]> levelList = new ArrayList<>();      // {层级, validItems索引}
                List<BizBomItem> validItems = new ArrayList<>();
                boolean hasItemError = false;
                int sortIdx = 0;
                for (IndexedRow ir : bomRows) {
                    Map<String, String> r = ir.data;
                    String matNo = r.get("子件物料编号");
                    if (matNo == null || matNo.isEmpty()) continue;

                    Long matId = materialMap.get(matNo);
                    if (matId == null) {
                        errors.add("第" + ir.rowNum + "行: 物料编号'" + matNo + "'不存在");
                        hasItemError = true;
                        continue;
                    }

                    // 层级：不填=1（顶级）
                    Integer level = parseInt(r.get("层级"));
                    if (level == null || level <= 0) level = 1;

                    BizBomItem item = new BizBomItem();
                    item.setParentItemId(0L); // 临时占位，插入后根据level决定
                    item.setMaterialId(matId);

                    BizMaterial mat = materialMapper.selectById(matId);
                    item.setItemType(mat != null && mat.getCategory() != null ? mat.getCategory() : "raw_material");

                    String sourceType = r.get("来源类型(purchase/manufacture)");
                    // 兼容中英文: 自制/manufacture/MFG → manufacture; 其余 (外购/purchase/空) → purchase
                    String normalized = "purchase";
                    if (sourceType != null && !sourceType.isEmpty()) {
                        String s = sourceType.trim();
                        if ("manufacture".equalsIgnoreCase(s) || "自制".equals(s) || "MFG".equalsIgnoreCase(s)) {
                            normalized = "manufacture";
                        }
                    }
                    item.setSourceType(normalized);
                    if ("manufacture".equals(item.getSourceType())) {
                        item.setProductId(matId);
                    }

                    BigDecimal qty = parseBD(r.get("数量"));
                    if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                        errors.add("第" + ir.rowNum + "行: 数量必填且必须大于 0,子件物料'" + matNo + "'");
                        hasItemError = true;
                        continue;
                    }
                    item.setQuantity(qty);
                    BigDecimal wastage = parseBD(r.get("损耗率(%)"));
                    item.setWastageRate(wastage != null ? wastage : BigDecimal.ZERO);
                    item.setActualQuantity(item.getQuantity().multiply(
                            BigDecimal.ONE.add(item.getWastageRate().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)))
                            .setScale(4, RoundingMode.HALF_UP));

                    Integer sort = parseInt(r.get("排序号"));
                    item.setSortOrder(sort != null ? sort : ++sortIdx);

                    levelList.add(new int[]{level, validItems.size()});
                    validItems.add(item);
                }

                if (hasItemError || validItems.isEmpty()) {
                    errors.add("第" + firstRowNum + "行: BOM'" + productNo + " " + versionNo + "'" +
                            (validItems.isEmpty() ? "没有有效子件，整条跳过" : "存在子件错误，整条跳过"));
                    continue;
                }

                // 校验通过，执行插入（先插父后插子，按层级构建父子关系）
                bomVersionMapper.insert(version);
                // lastIdByLevel[i] 记录当前层级 i 最近插入的 item id，用于后续子级引用
                Map<Integer, Long> lastIdByLevel = new HashMap<>();
                for (int[] pair : levelList) {
                    int level = pair[0];
                    BizBomItem item = validItems.get(pair[1]);
                    item.setBomVersionId(version.getId());
                    // 设置父级：层级1 → parentItemId=0；层级>1 → 上一级的最近id
                    if (level > 1) {
                        Long parentId = lastIdByLevel.get(level - 1);
                        item.setParentItemId(parentId != null ? parentId : 0L);
                    } else {
                        item.setParentItemId(0L);
                    }
                    bomItemMapper.insert(item);
                    lastIdByLevel.put(level, item.getId());
                    // 清除比当前层级更深的缓存（新的同级或父级出现后，更深的子级上下文失效）
                    lastIdByLevel.entrySet().removeIf(e -> e.getKey() > level);
                }
                success++;
            } catch (Exception e) {
                errors.add("第" + firstRowNum + "行: " + friendlyError(e));
            }
        }
        return buildResult(success, grouped.size(), errors);
    }

    // ==================== 采购订单导入 ====================

    @Transactional
    public Map<String, Object> importPurchaseOrders(MultipartFile file) {
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, PURCHASE_ORDER_HEADERS);
        int success = 0;
        List<String> errors = new ArrayList<>();

        Map<String, Long> supplierMap = loadSupplierMap();
        Map<String, Long> materialMap = loadMaterialMap();
        Map<String, Long> userMap = loadUserMap();

        // 智能分组：有采购单号按单号分，没单号按"供应商名称+下单日期+采购员"组合分
        LinkedHashMap<String, List<IndexedRow>> grouped = groupOrderRows(rows,
                "采购单号", "供应商名称", "下单日期", "采购员");

        for (Map.Entry<String, List<IndexedRow>> entry : grouped.entrySet()) {
            String groupKey = entry.getKey();
            List<IndexedRow> poRows = entry.getValue();
            Map<String, String> first = poRows.get(0).data;
            int firstRowNum = poRows.get(0).rowNum;
            String poNo = groupKey.startsWith("NO:") ? groupKey.substring(3) : null;

            try {
                if (poNo != null) {
                    Long exists = purchaseOrderMapper.selectCount(new LambdaQueryWrapper<BizPurchaseOrder>()
                            .eq(BizPurchaseOrder::getPoNo, poNo).eq(BizPurchaseOrder::getDeleted, 0));
                    if (exists > 0) {
                        errors.add("第" + firstRowNum + "行: 采购单号'" + poNo + "'已存在，已跳过");
                        continue;
                    }
                }

                String supplierName = first.get("供应商名称");
                Long supplierId = supplierMap.get(supplierName);
                if (supplierName != null && !supplierName.isEmpty() && supplierId == null) {
                    errors.add("第" + firstRowNum + "行: 供应商'" + supplierName + "'不存在");
                    continue;
                }

                BizPurchaseOrder po = new BizPurchaseOrder();
                po.setPoNo(poNo != null ? poNo : CodeUtil.generate("PO"));
                po.setSupplierId(supplierId);
                po.setContactName(first.get("联系人"));
                po.setDeliveryDate(parseDate(first.get("交货日期")));
                po.setPaymentTerms(first.get("付款条款"));
                po.setStatus("confirmed");
                po.setRemark(first.get("采购备注"));

                // 采购员
                String buyerName = first.get("采购员");
                if (buyerName != null && !buyerName.isEmpty()) {
                    Long buyerId = userMap.get(buyerName);
                    if (buyerId != null) {
                        po.setCreateBy(buyerId);
                    }
                }

                LocalDateTime createDt = parseDatetime(first.get("下单日期"));
                if (createDt != null) po.setCreateTime(createDt);

                // 先校验所有明细行，有任何失败则整条采购单不导入
                List<BizPurchaseOrderItem> validItems = new ArrayList<>();
                BigDecimal totalAmount = BigDecimal.ZERO;
                boolean hasItemError = false;
                for (IndexedRow ir : poRows) {
                    Map<String, String> r = ir.data;
                    String matNo = r.get("物料编号");
                    if (matNo == null || matNo.isEmpty()) {
                        String qty = r.get("数量");
                        if (qty != null && !qty.isEmpty()) {
                            errors.add("第" + ir.rowNum + "行: 物料编号为空");
                            hasItemError = true;
                        }
                        continue;
                    }

                    Long matId = materialMap.get(matNo);
                    if (matId == null) {
                        errors.add("第" + ir.rowNum + "行: 物料编号'" + matNo + "'不存在");
                        hasItemError = true;
                        continue;
                    }

                    BizPurchaseOrderItem item = new BizPurchaseOrderItem();
                    item.setMaterialId(matId);
                    item.setQuantity(parseBD(r.get("数量")));
                    item.setUnitPrice(parseBD(r.get("含税单价")));
                    item.setTaxRate(parseBD(r.get("税率(%)")));
                    item.setInvTaskQty(BigDecimal.ZERO);
                    item.setStockInQty(BigDecimal.ZERO);
                    item.setQualifiedQty(BigDecimal.ZERO);
                    item.setReceiptQty(BigDecimal.ZERO);
                    item.setRemark(r.get("行备注"));

                    // 含税总价 = 数量 × 含税单价 (单价是含税口径,直接相乘)
                    BigDecimal amount = parseBD(r.get("含税金额"));
                    if (amount == null && item.getQuantity() != null && item.getUnitPrice() != null) {
                        amount = item.getQuantity().multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
                    }
                    item.setAmount(amount);
                    if (amount != null) totalAmount = totalAmount.add(amount);
                    if (createDt != null) item.setCreateTime(createDt);

                    validItems.add(item);
                }

                if (hasItemError || validItems.isEmpty()) {
                    if (validItems.isEmpty()) {
                        errors.add("第" + firstRowNum + "行: 采购单没有有效的物料明细行，整单跳过");
                    } else {
                        errors.add("第" + firstRowNum + "行: 采购单存在明细错误，整单跳过");
                    }
                    continue;
                }

                // 校验全部通过，执行插入
                purchaseOrderMapper.insert(po);
                for (BizPurchaseOrderItem item : validItems) {
                    item.setPurchaseOrderId(po.getId());
                    purchaseOrderItemMapper.insert(item);
                }
                po.setTotalAmount(totalAmount);
                purchaseOrderMapper.updateById(po);
                success++;
            } catch (Exception e) {
                errors.add("第" + firstRowNum + "行: " + friendlyError(e));
            }
        }
        return buildResult(success, grouped.size(), errors);
    }

    // ==================== 工序导入 ====================

    @Transactional
    public Map<String, Object> importProcesses(MultipartFile file) {
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, PROCESS_HEADERS);
        int success = 0;
        List<String> errors = new ArrayList<>();

        Map<String, Long> userMap = loadUserMap();
        Map<String, Long> teamMap = loadTeamMap();

        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 2;
            Map<String, String> r = rows.get(i);
            try {
                String name = r.get("工序名称");
                if (name == null || name.isEmpty()) {
                    errors.add("第" + rowNum + "行: 工序名称不能为空");
                    continue;
                }

                Long exists = processMapper.selectCount(new LambdaQueryWrapper<BizProcess>()
                        .eq(BizProcess::getProcessName, name).eq(BizProcess::getDeleted, 0));
                if (exists > 0) {
                    errors.add("第" + rowNum + "行: 工序'" + name + "'已存在，已跳过");
                    continue;
                }

                BizProcess p = new BizProcess();
                p.setProcessName(name);
                Integer stdTime = parseInt(r.get("标准工时(天)"));
                p.setStandardTime(stdTime != null ? stdTime : 1);
                p.setPiecePrice(parseBD(r.get("计件单价")));

                String bindType = parseBindType(r.get("绑定类型(个人/班组)"));
                p.setBindType(bindType);

                // 根据绑定类型解析绑定对象
                String bindNames = r.get("绑定对象名称");
                if (bindNames != null && !bindNames.isEmpty() && !"none".equals(bindType)) {
                    List<Long> bindIdList = new ArrayList<>();
                    for (String bn : bindNames.split("[,，]")) {
                        bn = bn.trim();
                        if (bn.isEmpty()) continue;
                        if ("employee".equals(bindType)) {
                            Long uid = userMap.get(bn);
                            if (uid != null) {
                                bindIdList.add(uid);
                            } else {
                                errors.add("第" + rowNum + "行: 人员'" + bn + "'不存在，已跳过");
                            }
                        } else if ("team".equals(bindType)) {
                            Long tid = teamMap.get(bn);
                            if (tid != null) {
                                bindIdList.add(tid);
                                // 同时设置 teamId 为第一个班组
                                if (p.getTeamId() == null) p.setTeamId(tid);
                            } else {
                                errors.add("第" + rowNum + "行: 班组'" + bn + "'不存在，已跳过");
                            }
                        }
                    }
                    if (!bindIdList.isEmpty()) {
                        p.setBindIds(bindIdList.toString()); // [1,2,3] 格式
                    }
                }

                p.setSopDescription(r.get("SOP描述"));
                p.setIsSemiStock("1".equals(r.get("入半成品库(0/1)")) ? 1 : 0);
                p.setIsOutsourceable("1".equals(r.get("可外协(0/1)")) ? 1 : 0);

                LocalDateTime createDt = parseDatetime(r.get("创建时间"));
                if (createDt != null) p.setCreateTime(createDt);

                processMapper.insert(p);
                success++;
            } catch (Exception e) {
                errors.add("第" + rowNum + "行: " + friendlyError(e));
            }
        }
        return buildResult(success, rows.size(), errors);
    }

    // ==================== 工艺路线导入 ====================

    @Transactional
    public Map<String, Object> importProcessRoutes(MultipartFile file) {
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, PROCESS_ROUTE_HEADERS);
        int success = 0;
        List<String> errors = new ArrayList<>();

        Map<String, Long> materialMap = loadMaterialMap();
        Map<String, Long> processMap = loadProcessMap();
        Map<String, Long> qcTemplateMap = loadQcTemplateMap();

        // 按"路线名称+版本号"组合分组（同名不同版本 = 不同路线）
        LinkedHashMap<String, List<IndexedRow>> grouped = groupRowsComposite(rows, "路线名称", "版本号");

        for (Map.Entry<String, List<IndexedRow>> entry : grouped.entrySet()) {
            List<IndexedRow> routeRows = entry.getValue();
            Map<String, String> first = routeRows.get(0).data;
            int firstRowNum = routeRows.get(0).rowNum;
            String routeName = first.get("路线名称");

            try {
                if (routeName == null || routeName.isEmpty() || routeName.startsWith("__auto_")) {
                    errors.add("第" + firstRowNum + "行: 路线名称不能为空");
                    continue;
                }

                String productNo = first.get("产品编号");
                Long productId = null;
                if (productNo != null && !productNo.isEmpty()) {
                    productId = materialMap.get(productNo);
                    if (productId == null) {
                        errors.add("第" + firstRowNum + "行: 产品编号'" + productNo + "'不存在");
                        continue;
                    }
                }

                String versionNo = first.get("版本号");

                // 检查是否已存在相同的工艺路线（路线名称+产品+版本号）
                LambdaQueryWrapper<BizProcessRoute> dupCheck = new LambdaQueryWrapper<BizProcessRoute>()
                        .eq(BizProcessRoute::getRouteName, routeName)
                        .eq(BizProcessRoute::getDeleted, 0);
                if (productId != null) {
                    dupCheck.eq(BizProcessRoute::getProductId, productId);
                } else {
                    dupCheck.isNull(BizProcessRoute::getProductId);
                }
                if (versionNo != null && !versionNo.isEmpty()) {
                    dupCheck.eq(BizProcessRoute::getVersionNo, versionNo);
                }
                Long routeExists = processRouteMapper.selectCount(dupCheck);
                if (routeExists > 0) {
                    errors.add("第" + firstRowNum + "行: 工艺路线'" + routeName +
                            (versionNo != null && !versionNo.isEmpty() ? " " + versionNo : "") + "'已存在，已跳过");
                    continue;
                }

                BizProcessRoute route = new BizProcessRoute();
                route.setRouteName(routeName);
                route.setProductId(productId);
                route.setVersionNo(versionNo);
                String status = first.get("状态(active/inactive)");
                route.setStatus(status != null && !status.isEmpty() ? status : "active");

                LocalDateTime createDt = parseDatetime(first.get("创建时间"));
                if (createDt != null) route.setCreateTime(createDt);

                // 先校验所有工序步骤，有任何失败则整条路线不导入
                Long collectedQcTemplateId = null;
                List<BizProcessRouteStep> validSteps = new ArrayList<>();
                boolean hasStepError = false;
                for (IndexedRow ir : routeRows) {
                    Map<String, String> r = ir.data;
                    String procName = r.get("工序名称");
                    if (procName == null || procName.isEmpty()) continue;

                    Long processId = processMap.get(procName);
                    if (processId == null) {
                        errors.add("第" + ir.rowNum + "行: 工序'" + procName + "'不存在");
                        hasStepError = true;
                        continue;
                    }

                    BizProcessRouteStep step = new BizProcessRouteStep();
                    step.setProcessId(processId);
                    step.setStepNo(parseInt(r.get("工序序号")));
                    Integer group = parseInt(r.get("工序组"));
                    step.setStepGroup(group != null ? group : 1);
                    step.setStandardTime(parseInt(r.get("覆盖标准工时(天)")));
                    step.setPiecePrice(parseBD(r.get("覆盖计件单价")));

                    String qcTemplateName = r.get("质检模板");
                    if (qcTemplateName != null && !qcTemplateName.isEmpty()) {
                        Long qcTplId = qcTemplateMap.get(qcTemplateName);
                        if (qcTplId != null) {
                            collectedQcTemplateId = qcTplId;
                        } else {
                            errors.add("第" + ir.rowNum + "行: 质检模板'" + qcTemplateName + "'不存在");
                            hasStepError = true;
                        }
                    }
                    validSteps.add(step);
                }

                if (hasStepError || validSteps.isEmpty()) {
                    errors.add("第" + firstRowNum + "行: 工艺路线'" + routeName + "'" +
                            (validSteps.isEmpty() ? "没有有效工序，整条跳过" : "存在工序错误，整条跳过"));
                    continue;
                }

                // 校验通过，执行插入
                processRouteMapper.insert(route);
                for (BizProcessRouteStep step : validSteps) {
                    step.setRouteId(route.getId());
                    processRouteStepMapper.insert(step);
                }
                // 质检模板统一设置到最后一道工序
                if (collectedQcTemplateId != null) {
                    BizProcessRouteStep lastStep = validSteps.get(validSteps.size() - 1);
                    lastStep.setQcTemplateId(collectedQcTemplateId);
                    processRouteStepMapper.updateById(lastStep);
                }

                success++;
            } catch (Exception e) {
                errors.add("第" + firstRowNum + "行: " + friendlyError(e));
            }
        }
        return buildResult(success, grouped.size(), errors);
    }

    // ==================== 用户导入 ====================

    @Transactional
    public Map<String, Object> importUsers(MultipartFile file) {
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, USER_HEADERS);
        int success = 0;
        List<String> errors = new ArrayList<>();

        // 预加载部门映射（名称 -> ID）
        Map<String, Long> deptMap = loadDeptMap();
        // 预加载角色映射（中文名称/编码 -> ID）
        Map<String, Long> roleMap = loadRoleNameMap();

        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 2;
            Map<String, String> r = rows.get(i);
            try {
                String username = r.get("用户名");
                if (username == null || username.isEmpty()) {
                    errors.add("第" + rowNum + "行: 用户名不能为空");
                    continue;
                }

                // 检查是否已存在未删除的同名用户
                Long activeExists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username));
                if (activeExists > 0) {
                    errors.add("第" + rowNum + "行: 用户名'" + username + "'已存在，已跳过");
                    continue;
                }

                // 检查是否有已删除的同名用户（重新入职场景）
                SysUser deletedUser = sysUserMapper.selectDeletedByUsername(username);
                boolean isRestore = deletedUser != null;

                SysUser user = isRestore ? deletedUser : new SysUser();
                user.setUsername(username);
                user.setRealName(r.get("真实姓名"));
                user.setPhone(r.get("手机号"));

                // 部门
                String deptName = r.get("部门");
                if (deptName != null && !deptName.isEmpty()) {
                    Long deptId = deptMap.get(deptName);
                    if (deptId != null) {
                        user.setDeptId(deptId);
                    }
                }

                user.setWorkshop(r.get("岗位组"));
                user.setHireDate(parseDate(r.get("入职日期")));
                user.setRemark(r.get("备注"));
                user.setStatus("active");
                user.setLoginFailCount(0);
                user.setLockUntil(null);

                LocalDateTime createDt = parseDatetime(r.get("创建时间"));

                if (isRestore) {
                    // 恢复已删除用户：先置 deleted=0，再更新字段
                    sysUserMapper.restoreById(user.getId());
                    // 重新入职不重置密码，保留原密码
                    sysUserMapper.updateById(user);
                    // createTime 使用原生SQL更新（@TableField(fill=INSERT) 会阻止 updateById 更新此字段）
                    if (createDt != null) {
                        jdbcTemplate.update("UPDATE sys_user SET create_time = ? WHERE id = ?", createDt, user.getId());
                    }
                } else {
                    // 新建用户：设置默认密码 123456
                    user.setPassword(passwordEncoder.encode("123456"));
                    if (createDt != null) user.setCreateTime(createDt);
                    sysUserMapper.insert(user);
                }

                // 分配角色（支持中文角色名，逗号分隔多角色）
                String roleName = r.get("角色");
                if (roleName != null && !roleName.isEmpty()) {
                    if (isRestore) {
                        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                                .eq(SysUserRole::getUserId, user.getId()));
                    }
                    for (String name : roleName.split("[,，]")) {
                        name = name.trim();
                        Long roleId = roleMap.get(name);
                        if (roleId != null) {
                            SysUserRole ur = new SysUserRole();
                            ur.setUserId(user.getId());
                            ur.setRoleId(roleId);
                            sysUserRoleMapper.insert(ur);
                        } else {
                            errors.add("第" + rowNum + "行: 角色'" + name + "'不存在，已跳过");
                        }
                    }
                }

                success++;
                if (isRestore) {
                    errors.add("第" + rowNum + "行: 用户'" + username + "'为重新入职，已恢复并更新信息");
                }
            } catch (Exception e) {
                errors.add("第" + rowNum + "行: " + friendlyError(e));
            }
        }
        return buildResult(success, rows.size(), errors);
    }

    // ==================== 客户导入 ====================

    @Transactional
    public Map<String, Object> importCustomers(MultipartFile file) {
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, CUSTOMER_HEADERS);
        int success = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 2;
            Map<String, String> r = rows.get(i);
            try {
                String name = r.get("客户名称");
                if (name == null || name.isEmpty()) {
                    errors.add("第" + rowNum + "行: 客户名称不能为空");
                    continue;
                }

                // 检查未删除的同名客户
                Long activeExists = customerMapper.selectCount(new LambdaQueryWrapper<BizCustomer>()
                        .eq(BizCustomer::getCustomerName, name));
                if (activeExists > 0) {
                    errors.add("第" + rowNum + "行: 客户名称'" + name + "'已存在，已跳过");
                    continue;
                }

                String customerNo = r.get("客户编号");
                // 检查未删除的同编号客户
                if (customerNo != null && !customerNo.isEmpty()) {
                    Long noExists = customerMapper.selectCount(new LambdaQueryWrapper<BizCustomer>()
                            .eq(BizCustomer::getCustomerNo, customerNo));
                    if (noExists > 0) {
                        errors.add("第" + rowNum + "行: 客户编号'" + customerNo + "'已存在，已跳过");
                        continue;
                    }
                }

                // 检查已删除的同名客户（恢复场景）
                BizCustomer deletedCust = findDeletedByField("biz_customer", "customer_name", name, BizCustomer.class);
                boolean isRestore = deletedCust != null;

                BizCustomer c = isRestore ? deletedCust : new BizCustomer();
                c.setCustomerNo(customerNo != null && !customerNo.isEmpty() ? customerNo :
                        (isRestore && c.getCustomerNo() != null ? c.getCustomerNo() : CodeUtil.generate("C")));
                c.setCustomerName(name);
                c.setContactName(r.get("联系人"));
                c.setPhone(r.get("电话"));
                c.setEmail(r.get("邮箱"));
                c.setAddress(r.get("地址"));
                c.setSettlementType(r.get("结算方式"));
                c.setCreditLimit(parseBD(r.get("信用额度")));
                c.setTaxRate(parseBD(r.get("税率(%)")));
                c.setRemark(r.get("备注"));
                c.setStatus("active");

                LocalDateTime createDt = parseDatetime(r.get("创建时间"));
                if (createDt != null) c.setCreateTime(createDt);

                if (isRestore) {
                    restoreById("biz_customer", c.getId());
                    customerMapper.updateById(c);
                    if (createDt != null) {
                        jdbcTemplate.update("UPDATE biz_customer SET create_time = ? WHERE id = ?", createDt, c.getId());
                    }
                } else {
                    customerMapper.insert(c);
                }
                success++;
                if (isRestore) {
                    errors.add("第" + rowNum + "行: 客户'" + name + "'已从删除状态恢复并更新");
                }
            } catch (Exception e) {
                errors.add("第" + rowNum + "行: " + friendlyError(e));
            }
        }
        return buildResult(success, rows.size(), errors);
    }

    // ==================== 供应商导入 ====================

    @Transactional
    public Map<String, Object> importSuppliers(MultipartFile file) {
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, SUPPLIER_HEADERS);
        int success = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 2;
            Map<String, String> r = rows.get(i);
            try {
                String name = r.get("供应商名称");
                if (name == null || name.isEmpty()) {
                    errors.add("第" + rowNum + "行: 供应商名称不能为空");
                    continue;
                }

                // 检查未删除的同名供应商
                Long activeExists = supplierMapper.selectCount(new LambdaQueryWrapper<BizSupplier>()
                        .eq(BizSupplier::getSupplierName, name));
                if (activeExists > 0) {
                    errors.add("第" + rowNum + "行: 供应商名称'" + name + "'已存在，已跳过");
                    continue;
                }

                String supplierNo = r.get("供应商编号");
                if (supplierNo != null && !supplierNo.isEmpty()) {
                    Long noExists = supplierMapper.selectCount(new LambdaQueryWrapper<BizSupplier>()
                            .eq(BizSupplier::getSupplierNo, supplierNo));
                    if (noExists > 0) {
                        errors.add("第" + rowNum + "行: 供应商编号'" + supplierNo + "'已存在，已跳过");
                        continue;
                    }
                }

                // 检查已删除的同名供应商（恢复场景）
                BizSupplier deletedSup = findDeletedByField("biz_supplier", "supplier_name", name, BizSupplier.class);
                boolean isRestore = deletedSup != null;

                BizSupplier s = isRestore ? deletedSup : new BizSupplier();
                s.setSupplierNo(supplierNo != null && !supplierNo.isEmpty() ? supplierNo :
                        (isRestore && s.getSupplierNo() != null ? s.getSupplierNo() : CodeUtil.generate("S")));
                s.setSupplierName(name);
                // 供应商类别(按类别名精确匹配 biz_supplier_category)
                String supCatName = r.get("供应商类别");
                if (supCatName != null && !supCatName.isBlank()) {
                    com.aimes.modules.master.entity.BizSupplierCategory sc = supplierCategoryMapper.selectOne(
                            new LambdaQueryWrapper<com.aimes.modules.master.entity.BizSupplierCategory>()
                                    .eq(com.aimes.modules.master.entity.BizSupplierCategory::getCategoryName, supCatName.trim())
                                    .eq(com.aimes.modules.master.entity.BizSupplierCategory::getDeleted, 0)
                                    .last("LIMIT 1"));
                    if (sc != null) {
                        s.setSupplierCategoryId(sc.getId());
                    } else {
                        errors.add("第" + rowNum + "行: 供应商类别'" + supCatName + "'未找到, 已忽略");
                    }
                }
                s.setContactName(r.get("联系人"));
                s.setPhone(r.get("电话"));
                s.setAddress(r.get("地址"));
                s.setSettlementType(r.get("结算方式"));
                s.setBankName(r.get("开户行"));
                s.setBankAccount(r.get("银行账号"));
                s.setTaxNo(r.get("税号"));
                s.setInvoiceType(r.get("发票类型"));
                s.setProcessingAbility(r.get("加工能力"));
                s.setRemark(r.get("备注"));
                s.setStatus("active");

                LocalDateTime createDt = parseDatetime(r.get("创建时间"));
                if (createDt != null) s.setCreateTime(createDt);

                if (isRestore) {
                    restoreById("biz_supplier", s.getId());
                    supplierMapper.updateById(s);
                    if (createDt != null) {
                        jdbcTemplate.update("UPDATE biz_supplier SET create_time = ? WHERE id = ?", createDt, s.getId());
                    }
                } else {
                    supplierMapper.insert(s);
                }
                success++;
                if (isRestore) {
                    errors.add("第" + rowNum + "行: 供应商'" + name + "'已从删除状态恢复并更新");
                }
            } catch (Exception e) {
                errors.add("第" + rowNum + "行: " + friendlyError(e));
            }
        }
        return buildResult(success, rows.size(), errors);
    }

    // ==================== 班组导入（同名多行 = 多成员） ====================

    @Transactional
    public Map<String, Object> importTeams(MultipartFile file) {
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, TEAM_HEADERS);
        int success = 0;
        List<String> errors = new ArrayList<>();

        Map<String, Long> userMap = loadUserMap();

        // 按班组名称分组（同名多行表示同一班组的多个成员）
        LinkedHashMap<String, List<IndexedRow>> grouped = groupRows(rows, "班组名称");

        for (Map.Entry<String, List<IndexedRow>> entry : grouped.entrySet()) {
            String teamName = entry.getKey();
            List<IndexedRow> teamRows = entry.getValue();
            Map<String, String> first = teamRows.get(0).data;
            int firstRowNum = teamRows.get(0).rowNum;

            try {
                if (teamName == null || teamName.isEmpty() || teamName.startsWith("__auto_")) {
                    errors.add("第" + firstRowNum + "行: 班组名称不能为空");
                    continue;
                }

                // 检查班组名称是否重复
                Long exists = teamMapper.selectCount(new LambdaQueryWrapper<BizTeam>()
                        .eq(BizTeam::getTeamName, teamName));
                if (exists > 0) {
                    errors.add("第" + firstRowNum + "行: 班组名称'" + teamName + "'已存在，已跳过");
                    continue;
                }

                BizTeam team = new BizTeam();
                team.setTeamName(teamName);
                team.setWorkshop(first.get("车间"));
                team.setEffectiveDate(parseDate(first.get("生效日期")));
                team.setRemark(first.get("备注"));
                team.setStatus("active");

                // 组长（用姓名匹配）
                String leaderName = first.get("组长姓名");
                if (leaderName != null && !leaderName.isEmpty()) {
                    Long leaderId = userMap.get(leaderName);
                    if (leaderId != null) {
                        team.setLeaderId(leaderId);
                    } else {
                        errors.add("第" + firstRowNum + "行: 组长'" + leaderName + "'不存在，已忽略组长设置");
                    }
                }

                LocalDateTime createDt = parseDatetime(first.get("创建时间"));
                if (createDt != null) team.setCreateTime(createDt);

                teamMapper.insert(team);

                // 先收集所有有效成员ID，再按均分权重插入
                LinkedHashSet<Long> validUserIds = new LinkedHashSet<>();
                for (IndexedRow ir : teamRows) {
                    String memberName = ir.data.get("成员姓名");
                    if (memberName == null || memberName.isEmpty()) continue;
                    memberName = memberName.trim();

                    Long userId = userMap.get(memberName);
                    if (userId == null) {
                        errors.add("第" + ir.rowNum + "行: 成员'" + memberName + "'不存在，已跳过");
                        continue;
                    }
                    validUserIds.add(userId); // LinkedHashSet 自动去重
                }

                // 均分权重：总权重=1.0，每人=1/n，最后一个人吸收余数保证总和=1.0
                int total = validUserIds.size();
                if (total > 0) {
                    BigDecimal one = BigDecimal.ONE;
                    BigDecimal perWeight = one.divide(new BigDecimal(total), 4, RoundingMode.HALF_UP);
                    BigDecimal accumulated = BigDecimal.ZERO;
                    int idx = 0;
                    for (Long userId : validUserIds) {
                        idx++;
                        BizTeamMember member = new BizTeamMember();
                        member.setTeamId(team.getId());
                        member.setUserId(userId);
                        // 最后一个成员承接余数，保证总和精确为1.0
                        BigDecimal weight = (idx == total) ? one.subtract(accumulated) : perWeight;
                        member.setWeight(weight);
                        accumulated = accumulated.add(weight);
                        member.setJoinedAt(createDt != null ? createDt : LocalDateTime.now());
                        teamMemberMapper.insert(member);
                    }
                }

                success++;
            } catch (Exception e) {
                errors.add("第" + firstRowNum + "行: " + friendlyError(e));
            }
        }
        return buildResult(success, grouped.size(), errors);
    }

    // ==================== 质检体系导入(模板+方案+项目 一页汇总) ====================

    @Transactional
    public Map<String, Object> importQcTemplates(MultipartFile file) {
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, QC_TEMPLATE_HEADERS);
        int success = 0;
        List<String> errors = new ArrayList<>();

        Map<String, Long> materialMap = loadMaterialMap();

        // 按 "模板名称" 分组
        LinkedHashMap<String, List<IndexedRow>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> r = rows.get(i);
            String name = r.get("模板名称");
            if (name == null || name.isEmpty()) continue;
            grouped.computeIfAbsent(name.trim(), k -> new ArrayList<>()).add(new IndexedRow(i + 2, r));
        }

        for (Map.Entry<String, List<IndexedRow>> entry : grouped.entrySet()) {
            String templateName = entry.getKey();
            List<IndexedRow> tplRows = entry.getValue();
            Map<String, String> first = tplRows.get(0).data;
            int firstRowNum = tplRows.get(0).rowNum;

            try {
                // 1. 先找或建 质检方案
                Long schemeId = null;
                String schemeTypeCn = first.get("抽样方案");
                if (schemeTypeCn != null && !schemeTypeCn.isEmpty()) {
                    String schemeName = templateName + "-" + schemeTypeCn;
                    BizQcScheme existScheme = qcSchemeMapper.selectOne(
                            new LambdaQueryWrapper<BizQcScheme>()
                                    .eq(BizQcScheme::getSchemeName, schemeName)
                                    .eq(BizQcScheme::getDeleted, 0).last("LIMIT 1"));
                    if (existScheme != null) {
                        schemeId = existScheme.getId();
                    } else {
                        BizQcScheme scheme = new BizQcScheme();
                        scheme.setSchemeName(schemeName);
                        scheme.setSchemeType("全检".equals(schemeTypeCn) ? "full" : "sampling");
                        scheme.setSamplingMode(parseSamplingMode(first.get("抽样模式")));
                        scheme.setSamplingQty(parseInt(first.get("抽检数量")));
                        scheme.setSamplingRatio(parseBD(first.get("抽检比例(%)")));
                        scheme.setRatioRoundUp(1);
                        scheme.setStatus("active");
                        qcSchemeMapper.insert(scheme);
                        schemeId = scheme.getId();
                    }
                }

                // 2. 建 质检模板
                // 检查是否已存在同名模板
                BizQcTemplate existTpl = qcTemplateMapper.selectOne(
                        new LambdaQueryWrapper<BizQcTemplate>()
                                .eq(BizQcTemplate::getTemplateName, templateName)
                                .eq(BizQcTemplate::getDeleted, 0).last("LIMIT 1"));
                if (existTpl != null) {
                    errors.add("第" + firstRowNum + "行: 质检模板'" + templateName + "'已存在,已跳过");
                    continue;
                }

                BizQcTemplate tpl = new BizQcTemplate();
                tpl.setTemplateCode(CodeUtil.generate("QCT")); // 模板编码自动生成,数据库 NOT NULL
                tpl.setTemplateName(templateName);
                tpl.setApplyType(parseApplyType(first.get("适用类型")));
                String productNo = first.get("适用产品编号");
                if (productNo != null && !productNo.isEmpty()) {
                    tpl.setApplyProductId(materialMap.get(productNo));
                }
                String materialNo = first.get("适用物料编号");
                if (materialNo != null && !materialNo.isEmpty()) {
                    tpl.setApplyMaterialId(materialMap.get(materialNo));
                }
                tpl.setSchemeId(schemeId);
                tpl.setIsDefault("是".equals(first.get("默认模板")) ? 1 : 0);
                tpl.setStatus("active");
                LocalDateTime createDt = parseDatetime(first.get("创建时间"));
                if (createDt != null) tpl.setCreateTime(createDt);
                qcTemplateMapper.insert(tpl);

                // 3. 建所有检验项
                int sortIdx = 0;
                for (IndexedRow ir : tplRows) {
                    Map<String, String> r = ir.data;
                    String itemName = r.get("检验项目名称");
                    if (itemName == null || itemName.isEmpty()) continue;

                    BizQcTemplateItem item = new BizQcTemplateItem();
                    item.setTemplateId(tpl.getId());
                    item.setItemName(itemName.trim());
                    item.setItemType(parseItemType(r.get("检验类型")));
                    item.setStandardValue(r.get("标准值"));
                    item.setToleranceMin(r.get("下限值"));
                    item.setToleranceMax(r.get("上限值"));
                    item.setUnit(r.get("单位"));
                    item.setMethod(r.get("检验方法"));
                    item.setDefaultValue(r.get("默认值"));
                    item.setIsRequired("是".equals(r.get("是否必填")) ? 1 : 0);
                    item.setIsCritical("是".equals(r.get("是否关键")) ? 1 : 0);
                    Integer sort = parseInt(r.get("排序号"));
                    item.setSortOrder(sort != null ? sort : ++sortIdx);
                    qcTemplateItemMapper.insert(item);
                }

                success++;
            } catch (Exception e) {
                errors.add("第" + firstRowNum + "行: " + friendlyError(e));
            }
        }
        return buildResult(success, grouped.size(), errors);
    }

    private String parseApplyType(String cn) {
        if (cn == null) return null;
        return switch (cn.trim()) {
            case "来料检" -> "incoming";
            case "过程检" -> "process";
            case "成品检" -> "outgoing";
            case "外协检" -> "outsource";
            default -> cn;
        };
    }

    private String parseSamplingMode(String cn) {
        if (cn == null) return null;
        return switch (cn.trim()) {
            case "首件" -> "first_piece";
            case "末件" -> "last_piece";
            case "固定数量" -> "custom_qty";
            case "按比例" -> "ratio";
            default -> cn;
        };
    }

    private String parseItemType(String cn) {
        if (cn == null) return "quantitative";
        return switch (cn.trim()) {
            case "计量型" -> "quantitative";
            case "文本型" -> "text";
            case "单选" -> "radio";
            default -> cn;
        };
    }

    private String parseBindType(String str) {
        if (str == null || str.isEmpty()) return "none";
        return switch (str.trim()) {
            case "个人" -> "employee";
            case "班组" -> "team";
            default -> str; // 已经是英文值则直接用
        };
    }


    // ==================== 软删除恢复辅助方法 ====================

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * 通过原生SQL查找已软删除的记录（绕过 @TableLogic）
     * @param table 表名
     * @param field 查询字段（数据库列名，如 customer_name）
     * @param value 字段值
     * @param clazz 实体类（仅用于拿ID）
     * @return 找到则返回实体（仅填充id和对应字段），否则返回null
     */
    @SuppressWarnings("unchecked")
    private <T> T findDeletedByField(String table, String field, String value, Class<T> clazz) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM " + table + " WHERE " + field + " = ? AND deleted = 1 LIMIT 1", value);
            if (rows.isEmpty()) return null;
            Map<String, Object> row = rows.get(0);
            // 用 MyBatis-Plus 的 mapper 按 ID 查会被 @TableLogic 过滤，所以手动构建
            // 这里用反射简单处理
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            om.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            // 将下划线列名转为驼峰
            Map<String, Object> camelRow = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : row.entrySet()) {
                camelRow.put(toCamelCase(e.getKey()), e.getValue());
            }
            return om.convertValue(camelRow, clazz);
        } catch (Exception e) {
            log.warn("查找已删除记录失败: table={}, field={}, value={}", table, field, value, e);
            return null;
        }
    }

    /** 恢复已删除记录（直接执行SQL绕过 @TableLogic） */
    private void restoreById(String table, Long id) {
        jdbcTemplate.update("UPDATE " + table + " SET deleted = 0 WHERE id = ?", id);
    }

    /** 下划线转驼峰 */
    private String toCamelCase(String col) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : col.toCharArray()) {
            if (c == '_') { upper = true; }
            else { sb.append(upper ? Character.toUpperCase(c) : c); upper = false; }
        }
        return sb.toString();
    }

    // ==================== 辅助方法 ====================

    private Map<String, Long> loadMaterialMap() {
        return materialMapper.selectList(new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0))
                .stream().filter(m -> m.getMaterialNo() != null)
                .collect(Collectors.toMap(BizMaterial::getMaterialNo, BizMaterial::getId, (a, b) -> a));
    }

    private Map<String, Long> loadCustomerMap() {
        return customerMapper.selectList(new LambdaQueryWrapper<BizCustomer>().eq(BizCustomer::getDeleted, 0))
                .stream().filter(c -> c.getCustomerName() != null)
                .collect(Collectors.toMap(BizCustomer::getCustomerName, BizCustomer::getId, (a, b) -> a));
    }

    private Map<String, Long> loadSupplierMap() {
        return supplierMapper.selectList(new LambdaQueryWrapper<BizSupplier>().eq(BizSupplier::getDeleted, 0))
                .stream().filter(s -> s.getSupplierName() != null)
                .collect(Collectors.toMap(BizSupplier::getSupplierName, BizSupplier::getId, (a, b) -> a));
    }

    private Map<String, Long> loadProcessMap() {
        return processMapper.selectList(new LambdaQueryWrapper<BizProcess>().eq(BizProcess::getDeleted, 0))
                .stream().filter(p -> p.getProcessName() != null)
                .collect(Collectors.toMap(BizProcess::getProcessName, BizProcess::getId, (a, b) -> a));
    }

    /** 加载质检模板映射：模板名称 -> ID */
    private Map<String, Long> loadQcTemplateMap() {
        return qcTemplateMapper.selectList(new LambdaQueryWrapper<BizQcTemplate>()
                        .eq(BizQcTemplate::getDeleted, 0))
                .stream().filter(t -> t.getTemplateName() != null)
                .collect(Collectors.toMap(BizQcTemplate::getTemplateName, BizQcTemplate::getId, (a, b) -> a));
    }

    /** 加载班组映射：班组名称 -> ID */
    private Map<String, Long> loadTeamMap() {
        return teamMapper.selectList(new LambdaQueryWrapper<BizTeam>().eq(BizTeam::getDeleted, 0))
                .stream().filter(t -> t.getTeamName() != null)
                .collect(Collectors.toMap(BizTeam::getTeamName, BizTeam::getId, (a, b) -> a));
    }

    /** 加载用户映射：用户名 -> ID，同时也映射真实姓名 -> ID */
    private Map<String, Long> loadUserMap() {
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0).eq(SysUser::getStatus, "active"));
        Map<String, Long> map = new LinkedHashMap<>();
        for (SysUser u : users) {
            if (u.getUsername() != null) map.put(u.getUsername(), u.getId());
            if (u.getRealName() != null && !u.getRealName().isEmpty()) map.putIfAbsent(u.getRealName(), u.getId());
        }
        return map;
    }

    /** 加载仓库映射：仓库名称 -> ID */
    private Map<String, Long> loadWarehouseMap() {
        return sysWarehouseMapper.selectList(new LambdaQueryWrapper<SysWarehouse>()
                        .eq(SysWarehouse::getDeleted, 0))
                .stream().filter(w -> w.getWarehouseName() != null)
                .collect(Collectors.toMap(SysWarehouse::getWarehouseName, SysWarehouse::getId, (a, b) -> a));
    }

    /** 加载部门映射：部门名称 -> ID */
    private Map<String, Long> loadDeptMap() {
        return sysDeptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                        .eq(SysDept::getDeleted, 0))
                .stream().filter(d -> d.getDeptName() != null)
                .collect(Collectors.toMap(SysDept::getDeptName, SysDept::getId, (a, b) -> a));
    }

    /** 加载角色映射：角色编码 -> ID */
    private Map<String, Long> loadRoleMap() {
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getDeleted, 0))
                .stream().filter(r -> r.getRoleCode() != null)
                .collect(Collectors.toMap(SysRole::getRoleCode, SysRole::getId, (a, b) -> a));
    }

    /** 加载角色映射：中文名称 + 编码都映射到 ID */
    private Map<String, Long> loadRoleNameMap() {
        List<SysRole> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getDeleted, 0));
        Map<String, Long> map = new LinkedHashMap<>();
        for (SysRole r : roles) {
            if (r.getRoleName() != null) map.put(r.getRoleName(), r.getId());
            if (r.getRoleCode() != null) map.putIfAbsent(r.getRoleCode(), r.getId());
        }
        return map;
    }

    /**
     * 按指定列分组，保持插入顺序。
     * 空值行继承上一行的分组 key（同一订单/路线/班组的多行明细只需在首行填写名称）。
     * 如果第一行就为空，则分配 __auto_ 占位符。
     */
    private LinkedHashMap<String, List<IndexedRow>> groupRows(List<Map<String, String>> rows, String key) {
        LinkedHashMap<String, List<IndexedRow>> grouped = new LinkedHashMap<>();
        String lastVal = null;
        for (int i = 0; i < rows.size(); i++) {
            String val = rows.get(i).get(key);
            if (val != null && !val.isEmpty()) {
                lastVal = val; // 非空值：更新当前分组
            } else if (lastVal != null) {
                val = lastVal; // 空值：沿用上一行的分组
            } else {
                val = "__auto_" + i; // 第一行就为空：占位符
            }
            grouped.computeIfAbsent(val, k -> new ArrayList<>()).add(new IndexedRow(i + 2, rows.get(i)));
        }
        return grouped;
    }

    /**
     * 订单智能分组：
     * - 有编号的行：按编号分组（同编号 = 同一订单），key = "NO:编号"
     * - 无编号的行：按"主体名称+日期+人员"组合分组（三者相同 = 同一订单），key = "AUTO:组合"
     * - 无编号且后续行主体名称/日期/人员都为空：继承上一组（同一订单的多行明细）
     */
    private LinkedHashMap<String, List<IndexedRow>> groupOrderRows(
            List<Map<String, String>> rows, String noKey, String nameKey, String dateKey, String personKey) {
        LinkedHashMap<String, List<IndexedRow>> grouped = new LinkedHashMap<>();
        String lastKey = null;
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> r = rows.get(i);
            String no = r.get(noKey);
            String name = r.get(nameKey);
            String date = r.get(dateKey);
            String person = r.get(personKey);

            if (no != null && !no.isEmpty()) {
                // 有编号：按编号分组
                lastKey = "NO:" + no;
            } else if (name != null && !name.isEmpty()) {
                // 无编号但有主体名称：按组合分组
                String d = (date != null ? date : "");
                String p = (person != null ? person : "");
                lastKey = "AUTO:" + name + "|" + d + "|" + p;
            } else if (lastKey != null) {
                // 都为空：继承上一组（同订单多行明细）
            } else {
                lastKey = "AUTO:__solo_" + i;
            }
            grouped.computeIfAbsent(lastKey, k -> new ArrayList<>()).add(new IndexedRow(i + 2, r));
        }
        return grouped;
    }

    /**
     * 按两个字段组合分组（如路线名称+版本号），空值行继承上一行。
     * 新分组判定：主字段（路线名称）变化 或 副字段（版本号）非空时。
     * 两个字段都为空的行继承上一组。
     */
    private LinkedHashMap<String, List<IndexedRow>> groupRowsComposite(
            List<Map<String, String>> rows, String key1, String key2) {
        LinkedHashMap<String, List<IndexedRow>> grouped = new LinkedHashMap<>();
        String lastKey = null;
        String lastV1 = null; // 追踪上一行的主字段值
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> r = rows.get(i);
            String v1 = r.get(key1);
            String v2 = r.get(key2);
            boolean hasV1 = v1 != null && !v1.isEmpty();
            boolean hasV2 = v2 != null && !v2.isEmpty();

            // 主字段变化（不同路线名）→ 新分组
            boolean v1Changed = hasV1 && (lastV1 == null || !v1.equals(lastV1));

            if (hasV2 || v1Changed) {
                // 有版本号 或 路线名称变化 → 新分组
                String name = hasV1 ? v1 : (lastV1 != null ? lastV1 : "__auto_" + i);
                String suffix = hasV2 ? v2 : "";
                lastKey = name + "|" + suffix;
                if (hasV1) lastV1 = v1;
            } else if (lastKey == null) {
                // 第一行两个字段都为空
                lastKey = "__auto_" + i + "|";
            }
            // 两个字段都为空的行继承上一组
            grouped.computeIfAbsent(lastKey, k -> new ArrayList<>()).add(new IndexedRow(i + 2, r));
        }
        return grouped;
    }

    private static Map<String, Object> buildResult(int success, int total, List<String> errors) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("success", success);
        result.put("failed", total - success);
        result.put("errors", errors);
        return result;
    }

    // ==================== 类型转换 ====================

    /**
     * 宽松日期解析器，支持带/不带前导零：
     * 2025-11-01、2025-11-1、2025/11/1、20251101
     */
    private static final DateTimeFormatter LENIENT_DATE = new java.time.format.DateTimeFormatterBuilder()
            .appendPattern("yyyy")
            .optionalStart().appendLiteral('-').optionalEnd()
            .optionalStart().appendLiteral('/').optionalEnd()
            .appendPattern("M")
            .optionalStart().appendLiteral('-').optionalEnd()
            .optionalStart().appendLiteral('/').optionalEnd()
            .appendPattern("d")
            .toFormatter();

    /**
     * 宽松日期时间解析器，支持带/不带前导零：
     * 2025-11-1 9:10、2025-11-01 09:10:00、2025/11/1 9:10 等
     */
    private static final DateTimeFormatter LENIENT_DATETIME = new java.time.format.DateTimeFormatterBuilder()
            .appendPattern("yyyy")
            .optionalStart().appendLiteral('-').optionalEnd()
            .optionalStart().appendLiteral('/').optionalEnd()
            .appendPattern("M")
            .optionalStart().appendLiteral('-').optionalEnd()
            .optionalStart().appendLiteral('/').optionalEnd()
            .appendPattern("d")
            .appendLiteral(' ')
            .appendPattern("H:m")
            .optionalStart().appendLiteral(':').appendPattern("s").optionalEnd()
            .parseDefaulting(java.time.temporal.ChronoField.SECOND_OF_MINUTE, 0)
            .toFormatter();

    private LocalDate parseDate(String str) {
        if (str == null || str.isEmpty()) return null;
        str = str.trim();
        // 先尝试带时间的格式（取日期部分）
        try { return LocalDateTime.parse(str, LENIENT_DATETIME).toLocalDate(); } catch (Exception ignored) {}
        // 再尝试纯日期格式
        try { return LocalDate.parse(str, LENIENT_DATE); } catch (Exception ignored) {}
        return null;
    }

    private LocalDateTime parseDatetime(String str) {
        if (str == null || str.isEmpty()) return null;
        str = str.trim();
        // 先尝试完整日期时间
        try { return LocalDateTime.parse(str, LENIENT_DATETIME); } catch (Exception ignored) {}
        // 再尝试纯日期，补零时
        LocalDate date = parseDate(str);
        return date != null ? date.atStartOfDay() : null;
    }

    private BigDecimal parseBD(String str) {
        if (str == null) return null;
        // Excel 单元格按显示格式返回字符串, 千分位/货币/百分比/全角字符都会让 new BigDecimal 失败,
        // 这里统一清洗: 去掉非数字/小数点/负号字符, 百分号要先识别再除以 100
        String s = str.trim();
        if (s.isEmpty()) return null;
        boolean isPercent = s.endsWith("%") || s.endsWith("％");
        // 去掉常见非数字字符: 千分位逗号(中英)、空格、货币符号、引号、全角数字
        s = s.replace(",", "").replace(",", "")
             .replace(" ", "").replace(" ", "")
             .replace("¥", "").replace("$", "").replace("￥", "")
             .replace("'", "").replace("\"", "")
             .replace("%", "").replace("％", "");
        // 全角数字 → 半角
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '０' && c <= '９') sb.append((char) (c - '０' + '0'));
            else if (c == '．') sb.append('.');
            else if (c == '－' || c == '−') sb.append('-');
            else sb.append(c);
        }
        // 强力清洗: 只保留数字/小数点/负号, 任何看不见的字符 (零宽空格 ​ ﻿, Tab, 全角空格 　 等) 一律剥掉
        s = sb.toString().replaceAll("[^0-9.\\-+]", "");
        if (s.isEmpty() || "-".equals(s) || ".".equals(s) || "+".equals(s)) return null;
        try {
            BigDecimal v = new BigDecimal(s);
            if (isPercent) v = v.divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP);
            return v;
        } catch (NumberFormatException e) { return null; }
    }

    private Integer parseInt(String str) {
        if (str == null) return null;
        BigDecimal bd = parseBD(str);
        if (bd == null) return null;
        try { return bd.intValueExact(); }
        catch (ArithmeticException e) { return bd.intValue(); }
    }

    /** 物料属性：中文逗号分隔 → 英文逗号分隔 */
    private String mapMaterialAttributes(String str) {
        if (str == null || str.isEmpty()) return null;
        String[] parts = str.split("[,，]");
        List<String> result = new ArrayList<>();
        for (String p : parts) {
            String mapped = switch (p.trim()) {
                case "采购" -> "purchase";
                case "生产" -> "production";
                case "销售" -> "sales";
                default -> p.trim();
            };
            result.add(mapped);
        }
        return String.join(",", result);
    }

    /** 物料来源：中文 → 英文 */
    private String mapSourceType(String str) {
        if (str == null || str.isEmpty()) return null;
        return switch (str.trim()) {
            case "采购" -> "purchase";
            case "自制" -> "manufacture";
            case "外协" -> "outsource";
            default -> str.trim();
        };
    }

    private String mapMaterialType(String chinese) {
        if (chinese == null || chinese.isEmpty()) return null;
        return switch (chinese.trim()) {
            case "成品" -> "finished";
            case "半成品" -> "semi_finished";
            case "原材料" -> "raw_material";
            case "辅料" -> "auxiliary";
            case "包材" -> "packaging";
            case "外购件" -> "purchased";
            default -> chinese;
        };
    }

    private String parseOrderStatus(String status) {
        if (status == null || status.isEmpty()) return "confirmed";
        return switch (status.trim()) {
            case "草稿" -> "draft";
            case "待审批" -> "pending_approval";
            case "已确认" -> "confirmed";
            case "生产中" -> "in_production";
            case "部分发货" -> "partial_shipped";
            case "已发货" -> "shipped";
            case "已完成" -> "completed";
            case "已结算" -> "settled";
            case "已作废" -> "voided";
            case "已取消" -> "cancelled";
            default -> status;
        };
    }

    private String parsePOStatus(String status) {
        if (status == null || status.isEmpty()) return "confirmed";
        return switch (status.trim()) {
            case "草稿" -> "draft";
            case "待审批" -> "pending_approval";
            case "已确认" -> "confirmed";
            case "部分收货" -> "partial_received";
            case "已收货" -> "received";
            case "已办结" -> "closed";
            case "已拒绝" -> "rejected";
            default -> status;
        };
    }

    /** 将数据库原始异常转为友好提示 */
    /**
     * 构建物料去重签名：名称+规格+单位的标准化组合
     * 规格会被标准化（去空格、排序JSON数组等），避免因格式差异导致误判
     */
    private String buildMaterialSignature(String name, String spec, String unit) {
        String n = name != null ? name.trim() : "";
        String s = normalizeSpec(spec);
        String u = unit != null ? unit.trim() : "";
        return n + "|" + s + "|" + u;
    }

    /**
     * 标准化规格字符串：
     * - null/空 → 空串
     * - JSON数组格式 → 按 name 排序后重新序列化，避免字段顺序影响签名
     * - 普通字符串 → 去首尾空格
     */
    private String normalizeSpec(String spec) {
        if (spec == null || spec.isBlank()) return "";
        String trimmed = spec.trim();
        if (trimmed.startsWith("[")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                List<Map<String, Object>> list = om.readValue(trimmed,
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
                // 按 name 排序，values 也排序，保证签名稳定
                list.sort(Comparator.comparing(m -> String.valueOf(m.getOrDefault("name", ""))));
                return om.writeValueAsString(list);
            } catch (Exception ignored) {
                return trimmed;
            }
        }
        return trimmed;
    }

    private String friendlyError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "未知错误";
        if (msg.contains("Duplicate entry")) {
            // 提取 Duplicate entry 'xxx' for key 'table.uk_yyy' 中的值
            int start = msg.indexOf("Duplicate entry '");
            int end = msg.indexOf("'", start + 17);
            String dupVal = (start >= 0 && end > start) ? msg.substring(start + 17, end) : "";
            return "'" + dupVal + "'已存在（含已删除的历史记录），请修改后重试";
        }
        if (msg.contains("Data truncation")) return "数据超出字段长度限制";
        if (msg.contains("cannot be null")) return "必填字段为空";
        // 截断过长的技术错误信息
        if (msg.length() > 100) return msg.substring(0, 100) + "...";
        return msg;
    }

    /**
     * 解析规格字符串为 JSON,并同步补齐规格库(biz_material_spec):
     * "材质:NBR, 尺寸:015mm" → [{"name":"材质","value":"NBR"},{"name":"尺寸","value":"015mm"}]
     * "0150×30mm" → "0150×30mm" (普通规格,原样返回)
     */
    private String parseSpecToJson(String specStr) {
        if (specStr == null || specStr.isEmpty()) return specStr;
        // 检测 key:value 格式(至少含一个 "xxx:yyy")
        if (specStr.contains(":") && !specStr.startsWith("[") && !specStr.startsWith("{")) {
            try {
                String[] parts = specStr.split("[,，]");
                List<Map<String, String>> specs = new ArrayList<>();
                for (String part : parts) {
                    String[] kv = part.trim().split("[:：]", 2);
                    if (kv.length == 2) {
                        String specName = kv[0].trim();
                        Map<String, String> m = new LinkedHashMap<>();
                        m.put("name", specName);
                        m.put("value", kv[1].trim());
                        specs.add(m);
                        // 补齐规格库: 如果 specName 不存在则新建
                        ensureSpecExists(specName);
                    }
                }
                if (!specs.isEmpty()) {
                    return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(specs);
                }
            } catch (Exception ignored) {}
        }
        return specStr; // 普通规格原样返回
    }

    /** 确保规格名称存在于 biz_material_spec,不存在则新建 */
    private void ensureSpecExists(String specName) {
        if (specName == null || specName.isEmpty()) return;
        Long exists = materialSpecMapper.selectCount(new LambdaQueryWrapper<BizMaterialSpec>()
                .eq(BizMaterialSpec::getSpecName, specName)
                .eq(BizMaterialSpec::getDeleted, 0));
        if (exists == 0) {
            BizMaterialSpec spec = new BizMaterialSpec();
            spec.setSpecName(specName);
            spec.setStatus("active");
            materialSpecMapper.insert(spec);
        }
    }

    /**
     * 解析计量单位:
     * "箱 (1箱=100个)" → unit=箱 + unitConfig={"baseUnit":"箱","conversions":[{"unit":"个","ratio":100}]}
     * "个" → unit=个 + unitConfig=null
     */
    private void parseAndSetUnit(BizMaterial material, String unitStr) {
        if (unitStr == null || unitStr.isEmpty()) return;
        // 匹配: "主单位 (1主单位=N副单位)" 或 "主单位（1主单位=N副单位）"
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(.+?)\\s*[（(]\\s*1\\s*\\1\\s*=\\s*(\\d+)\\s*(.+?)\\s*[）)]$")
                .matcher(unitStr.trim());
        if (m.matches()) {
            String mainUnit = m.group(1).trim();
            int rate = Integer.parseInt(m.group(2));
            String subUnit = m.group(3).trim();
            material.setUnit(mainUnit);
            material.setUnitConfig(String.format(
                    "{\"baseUnit\":\"%s\",\"conversions\":[{\"unit\":\"%s\",\"ratio\":%d}]}",
                    mainUnit, subUnit, rate));
            ensureUnitExists(mainUnit);
            ensureUnitExists(subUnit);
        } else {
            material.setUnit(unitStr.trim());
            ensureUnitExists(unitStr.trim());
        }
    }

    /** 根据物料类型自动设置 categoryId(匹配 biz_material_category.category_code) */
    private void autoSetCategoryId(BizMaterial material, String materialType) {
        if (materialType == null || materialType.isEmpty()) return;
        com.aimes.modules.master.entity.BizMaterialCategory cat = materialCategoryMapper.selectOne(
                new LambdaQueryWrapper<com.aimes.modules.master.entity.BizMaterialCategory>()
                        .eq(com.aimes.modules.master.entity.BizMaterialCategory::getCategoryCode, materialType)
                        .eq(com.aimes.modules.master.entity.BizMaterialCategory::getDeleted, 0)
                        .last("LIMIT 1"));
        if (cat != null) {
            material.setCategoryId(cat.getId());
        }
    }

    /** 确保单位存在于 biz_unit,不存在则新建 */
    private void ensureUnitExists(String unitName) {
        if (unitName == null || unitName.isEmpty()) return;
        Long exists = bizUnitMapper.selectCount(new LambdaQueryWrapper<com.aimes.modules.master.entity.BizUnit>()
                .eq(com.aimes.modules.master.entity.BizUnit::getUnitName, unitName)
                .eq(com.aimes.modules.master.entity.BizUnit::getDeleted, 0));
        if (exists == 0) {
            com.aimes.modules.master.entity.BizUnit unit = new com.aimes.modules.master.entity.BizUnit();
            unit.setUnitName(unitName);
            unit.setUnitCode(unitName.toUpperCase());
            unit.setStatus("active");
            bizUnitMapper.insert(unit);
        }
    }

    // ==================== 设备管理(EAM) 导入 ====================

    private static final List<String> EQUIPMENT_HEADERS = List.of(
            "设备编号", "设备名称", "型号", "设备类别", "供应商名称",
            "购入日期", "启用日期", "状态", "部门", "安装位置",
            "规格参数", "技术参数", "维护周期(天)", "设备原值", "折旧年限",
            "创建时间", "备注");

    private static final List<String> MAINT_TEMPLATE_HEADERS = List.of(
            "模板名称", "适用设备类别", "周期类型", "周期值",
            "项目分组", "项目名称", "检查方法", "标准", "单位",
            "是否必填", "是否需要拍照", "排序号",
            "关联设备编号", "创建时间", "备注");

    private static final List<String> INSP_TEMPLATE_HEADERS = List.of(
            "模板名称", "适用设备类别", "点检类型", "状态",
            "项目名称", "检查标准", "检查方法",
            "是否必填", "排序号",
            "关联设备编号", "创建时间", "备注");

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importEquipment(org.springframework.web.multipart.MultipartFile file) {
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, EQUIPMENT_HEADERS);
        int success = 0;
        List<String> errors = new java.util.ArrayList<>();

        Map<String, Long> supplierNameToId = supplierMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizSupplier>()
                        .eq(BizSupplier::getDeleted, 0))
                .stream().collect(java.util.stream.Collectors.toMap(BizSupplier::getSupplierName, BizSupplier::getId, (a, b) -> a));
        Map<String, Long> deptNameToId = sysDeptMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysDept>()
                        .eq(SysDept::getDeleted, 0))
                .stream().collect(java.util.stream.Collectors.toMap(SysDept::getDeptName, SysDept::getId, (a, b) -> a));

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> r = rows.get(i);
            try {
                EamEquipment eq = new EamEquipment();
                String eqNo = r.get("设备编号");
                eq.setEquipmentNo(eqNo != null && !eqNo.isEmpty() ? eqNo : CodeUtil.generate("EQ"));
                eq.setEquipmentName(r.get("设备名称"));
                eq.setModel(r.get("型号"));
                eq.setCategory(r.get("设备类别"));
                String supplierName = r.get("供应商名称");
                if (supplierName != null && !supplierName.isEmpty()) eq.setSupplierId(supplierNameToId.get(supplierName));
                eq.setPurchaseDate(parseDate(r.get("购入日期")));
                eq.setEnableDate(parseDate(r.get("启用日期")));
                eq.setStatus(r.get("状态") != null && !r.get("状态").isEmpty() ? r.get("状态") : "NORMAL");
                String deptName = r.get("部门");
                if (deptName != null && !deptName.isEmpty()) eq.setDeptId(deptNameToId.get(deptName));
                eq.setLocation(r.get("安装位置"));
                eq.setSpecification(r.get("规格参数"));
                eq.setParameters(r.get("技术参数"));
                String cycle = r.get("维护周期(天)");
                if (cycle != null && !cycle.isEmpty()) try { eq.setMaintenanceCycle(Integer.parseInt(cycle.trim())); } catch (Exception ignored) {}
                String val = r.get("设备原值");
                if (val != null && !val.isEmpty()) try { eq.setEquipmentValue(new java.math.BigDecimal(val.trim())); } catch (Exception ignored) {}
                String dep = r.get("折旧年限");
                if (dep != null && !dep.isEmpty()) try { eq.setDepreciationYears(Integer.parseInt(dep.trim())); } catch (Exception ignored) {}
                eq.setCreateTime(parseDatetime(r.get("创建时间")));
                eq.setRemark(r.get("备注"));
                eamEquipmentMapper.insert(eq);

                // 自动创建设备运行状态记录
                EamEquipmentStatus status = new EamEquipmentStatus();
                status.setEquipmentId(eq.getId());
                status.setRunningStatus("RUNNING");
                status.setAlarmStatus("NORMAL");
                eamEquipmentStatusMapper.insert(status);
                success++;
            } catch (Exception e) {
                errors.add("第" + (i + 2) + "行: " + friendlyError(e));
            }
        }
        return Map.of("success", success, "errors", errors);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importMaintenanceTemplates(org.springframework.web.multipart.MultipartFile file) {
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, MAINT_TEMPLATE_HEADERS);
        int success = 0;
        List<String> errors = new java.util.ArrayList<>();

        // 设备编号 → ID
        Map<String, Long> eqNoToId = eamEquipmentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EamEquipment>()
                        .eq(EamEquipment::getDeleted, 0))
                .stream().collect(java.util.stream.Collectors.toMap(EamEquipment::getEquipmentNo, EamEquipment::getId, (a, b) -> a));

        // 按模板名称分组
        java.util.LinkedHashMap<String, List<Map<String, String>>> grouped = new java.util.LinkedHashMap<>();
        for (Map<String, String> r : rows) {
            String name = r.get("模板名称");
            if (name == null || name.isEmpty()) continue;
            grouped.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(r);
        }

        for (var entry : grouped.entrySet()) {
            try {
                List<Map<String, String>> items = entry.getValue();
                Map<String, String> first = items.get(0);

                EamMaintenanceTemplate tpl = new EamMaintenanceTemplate();
                tpl.setTemplateName(entry.getKey());
                tpl.setEquipmentType(first.get("适用设备类别"));
                tpl.setCycleType(first.get("周期类型"));
                String cv = first.get("周期值");
                if (cv != null && !cv.isEmpty()) try { tpl.setCycleValue(Integer.parseInt(cv.trim())); } catch (Exception ignored) {}
                tpl.setRemark(first.get("备注"));
                tpl.setCreateTime(parseDatetime(first.get("创建时间")));
                eamMaintTemplateMapper.insert(tpl);

                // 模板项
                int sortNo = 0;
                for (Map<String, String> item : items) {
                    sortNo++;
                    EamMaintenanceTemplateItem ti = new EamMaintenanceTemplateItem();
                    ti.setTemplateId(tpl.getId());
                    ti.setItemGroup(item.get("项目分组"));
                    ti.setItemName(item.get("项目名称"));
                    ti.setMethod(item.get("检查方法"));
                    ti.setStandard(item.get("标准"));
                    ti.setUom(item.get("单位"));
                    String req = item.get("是否必填");
                    ti.setRequired("1".equals(req) ? 1 : 0);
                    String photo = item.get("是否需要拍照");
                    ti.setNeedPhoto("1".equals(photo) ? 1 : 0);
                    String sn = item.get("排序号");
                    ti.setSortNo(sn != null && !sn.isEmpty() ? Integer.parseInt(sn.trim()) : sortNo);
                    eamMaintTemplateItemMapper.insert(ti);
                }

                // 关联设备
                String eqNos = first.get("关联设备编号");
                if (eqNos != null && !eqNos.isEmpty()) {
                    for (String no : eqNos.split("[,，]")) {
                        Long eqId = eqNoToId.get(no.trim());
                        if (eqId != null) {
                            EamMaintenanceTemplateEquipment rel = new EamMaintenanceTemplateEquipment();
                            rel.setTemplateId(tpl.getId());
                            rel.setEquipmentId(eqId);
                            eamMaintTemplateEquipmentMapper.insert(rel);
                        }
                    }
                }
                success++;
            } catch (Exception e) {
                errors.add("模板[" + entry.getKey() + "]: " + friendlyError(e));
            }
        }
        return Map.of("success", success, "errors", errors);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importInspectionTemplates(org.springframework.web.multipart.MultipartFile file) {
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, INSP_TEMPLATE_HEADERS);
        int success = 0;
        List<String> errors = new java.util.ArrayList<>();

        Map<String, Long> eqNoToId = eamEquipmentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EamEquipment>()
                        .eq(EamEquipment::getDeleted, 0))
                .stream().collect(java.util.stream.Collectors.toMap(EamEquipment::getEquipmentNo, EamEquipment::getId, (a, b) -> a));

        java.util.LinkedHashMap<String, List<Map<String, String>>> grouped = new java.util.LinkedHashMap<>();
        for (Map<String, String> r : rows) {
            String name = r.get("模板名称");
            if (name == null || name.isEmpty()) continue;
            grouped.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(r);
        }

        for (var entry : grouped.entrySet()) {
            try {
                List<Map<String, String>> items = entry.getValue();
                Map<String, String> first = items.get(0);

                EamInspectionTemplate tpl = new EamInspectionTemplate();
                tpl.setTemplateName(entry.getKey());
                tpl.setEquipmentType(first.get("适用设备类别"));
                String typeStr = first.get("点检类型");
                tpl.setInspectionType(typeStr != null && !typeStr.isEmpty() ? Integer.parseInt(typeStr.trim()) : 0);
                tpl.setStatus(first.get("状态") != null && !first.get("状态").isEmpty() ? first.get("状态") : "active");
                tpl.setRemark(first.get("备注"));
                tpl.setCreateTime(parseDatetime(first.get("创建时间")));
                eamInspTemplateMapper.insert(tpl);

                int sortNo = 0;
                for (Map<String, String> item : items) {
                    sortNo++;
                    EamInspectionTemplateItem ti = new EamInspectionTemplateItem();
                    ti.setTemplateId(tpl.getId());
                    ti.setItemName(item.get("项目名称"));
                    ti.setStandard(item.get("检查标准"));
                    ti.setMethod(item.get("检查方法"));
                    String req = item.get("是否必填");
                    ti.setIsRequired("1".equals(req) ? 1 : 0);
                    String sn = item.get("排序号");
                    ti.setSortOrder(sn != null && !sn.isEmpty() ? Integer.parseInt(sn.trim()) : sortNo);
                    eamInspTemplateItemMapper.insert(ti);
                }

                String eqNos = first.get("关联设备编号");
                if (eqNos != null && !eqNos.isEmpty()) {
                    for (String no : eqNos.split("[,，]")) {
                        Long eqId = eqNoToId.get(no.trim());
                        if (eqId != null) {
                            EamInspectionTemplateEquipment rel = new EamInspectionTemplateEquipment();
                            rel.setTemplateId(tpl.getId());
                            rel.setEquipmentId(eqId);
                            eamInspTemplateEquipmentMapper.insert(rel);
                        }
                    }
                }
                success++;
            } catch (Exception e) {
                errors.add("模板[" + entry.getKey() + "]: " + friendlyError(e));
            }
        }
        return Map.of("success", success, "errors", errors);
    }

    // ==================== 库存台账导入 ====================

    public static final List<String> INVENTORY_HEADERS = List.of(
            "物料编号", "物料名称", "仓库名称", "在库数量", "可用数量", "锁定数量");

    private static final List<String[]> INVENTORY_SAMPLES = List.of(
            new String[]{"M20260101", "(选择物料编号后自动带出)", "成品库", "100", "80", "20"},
            new String[]{"M20260102", "(选择物料编号后自动带出)", "原料库", "500", "500", "0"}
    );

    private static final Set<String> INVENTORY_REQUIRED = Set.of("物料编号", "仓库名称", "在库数量");

    /**
     * 库存台账模板：物料编号 + 仓库名称 下拉，物料名称 通过 VLOOKUP 自动带出
     */
    private void exportInventoryTemplate(HttpServletResponse response) throws IOException {
        List<BizMaterial> materials = materialMapper.selectList(new LambdaQueryWrapper<BizMaterial>()
                .and(w -> w.isNull(BizMaterial::getIsVirtualSemi).or().ne(BizMaterial::getIsVirtualSemi, 1)));
        String[] matNos = materials.stream().map(BizMaterial::getMaterialNo).filter(Objects::nonNull).toArray(String[]::new);
        String[] whNames = sysWarehouseMapper.selectList(new LambdaQueryWrapper<SysWarehouse>()
                        .eq(SysWarehouse::getDeleted, 0))
                .stream().map(SysWarehouse::getWarehouseName).filter(Objects::nonNull).toArray(String[]::new);

        // 物料编号→物料名称 映射表（写到隐藏 sheet，供 VLOOKUP 用）
        org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet main = wb.createSheet("Sheet1");

        org.apache.poi.ss.usermodel.CellStyle headerStyle = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font hf = wb.createFont(); hf.setBold(true); headerStyle.setFont(hf);
        headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);

        org.apache.poi.ss.usermodel.CellStyle reqStyle = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font rf = wb.createFont(); rf.setBold(true);
        rf.setColor(org.apache.poi.ss.usermodel.IndexedColors.RED.getIndex());
        reqStyle.setFont(rf);
        reqStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        reqStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        reqStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);

        // 表头（必填列前缀 "*" 与通用导入解析器一致，见 ExcelUtil.stripRequired）
        org.apache.poi.ss.usermodel.Row headerRow = main.createRow(0);
        for (int i = 0; i < INVENTORY_HEADERS.size(); i++) {
            org.apache.poi.ss.usermodel.Cell c = headerRow.createCell(i);
            String h = INVENTORY_HEADERS.get(i);
            boolean req = INVENTORY_REQUIRED.contains(h);
            c.setCellValue(req ? "*" + h : h);
            c.setCellStyle(req ? reqStyle : headerStyle);
            main.setColumnWidth(i, 4500);
        }

        // 示例行 + VLOOKUP 公式（物料名称列 B 从隐藏 sheet 查）
        org.apache.poi.ss.usermodel.CellStyle sampleStyle = wb.createCellStyle();
        sampleStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_TURQUOISE.getIndex());
        sampleStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        for (int r = 0; r < INVENTORY_SAMPLES.size(); r++) {
            org.apache.poi.ss.usermodel.Row row = main.createRow(r + 1);
            String[] sample = INVENTORY_SAMPLES.get(r);
            for (int c = 0; c < sample.length; c++) {
                org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
                cell.setCellStyle(sampleStyle);
                if (c == 1) {
                    cell.setCellFormula("IFERROR(VLOOKUP(A" + (r + 2) + ",物料映射!A:B,2,FALSE),\"\")");
                } else {
                    cell.setCellValue(sample[c]);
                }
            }
        }
        // 非示例行也预填 VLOOKUP 公式（到第 200 行）
        for (int r = INVENTORY_SAMPLES.size() + 1; r <= 200; r++) {
            org.apache.poi.ss.usermodel.Row row = main.createRow(r);
            org.apache.poi.ss.usermodel.Cell cell = row.createCell(1);
            cell.setCellFormula("IFERROR(VLOOKUP(A" + (r + 1) + ",物料映射!A:B,2,FALSE),\"\")");
        }

        // 隐藏 sheet：物料映射（物料编号→物料名称）
        org.apache.poi.ss.usermodel.Sheet mapSheet = wb.createSheet("物料映射");
        for (int i = 0; i < materials.size(); i++) {
            BizMaterial m = materials.get(i);
            if (m.getMaterialNo() == null) continue;
            org.apache.poi.ss.usermodel.Row row = mapSheet.createRow(i);
            row.createCell(0).setCellValue(m.getMaterialNo());
            row.createCell(1).setCellValue(m.getMaterialName() != null ? m.getMaterialName() : "");
        }
        wb.setSheetHidden(wb.getSheetIndex(mapSheet), true);

        // 下拉：物料编号(A)、仓库名称(C)。用命名范围+隐藏 sheet 承载数据
        org.apache.poi.ss.usermodel.Sheet srcSheet = wb.createSheet("数据源");
        wb.setSheetHidden(wb.getSheetIndex(srcSheet), true);
        writeListToColumn(srcSheet, 0, matNos);
        writeListToColumn(srcSheet, 1, whNames);
        defineName(wb, "MatNos", "数据源", 0, matNos.length);
        defineName(wb, "WhNames2", "数据源", 1, whNames.length);
        addDropdown(wb, main, 0, "MatNos");  // 物料编号列
        addDropdown(wb, main, 2, "WhNames2"); // 仓库名称列

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + java.net.URLEncoder.encode("库存台账导入模板.xlsx", "UTF-8"));
        wb.write(response.getOutputStream());
        wb.close();
    }

    private static void writeListToColumn(org.apache.poi.ss.usermodel.Sheet sheet, int col, String[] values) {
        for (int i = 0; i < values.length; i++) {
            org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
            if (row == null) row = sheet.createRow(i);
            row.createCell(col).setCellValue(values[i] != null ? values[i] : "");
        }
    }

    private static void defineName(org.apache.poi.xssf.usermodel.XSSFWorkbook wb, String name, String sheet, int col, int rows) {
        if (rows <= 0) rows = 1;
        org.apache.poi.ss.usermodel.Name n = wb.createName();
        n.setNameName(name);
        String colLetter = org.apache.poi.ss.util.CellReference.convertNumToColString(col);
        n.setRefersToFormula("'" + sheet + "'!$" + colLetter + "$1:$" + colLetter + "$" + rows);
    }

    private static void addDropdown(org.apache.poi.xssf.usermodel.XSSFWorkbook wb, org.apache.poi.ss.usermodel.Sheet sheet, int colIdx, String rangeName) {
        org.apache.poi.ss.usermodel.DataValidationHelper dvh = sheet.getDataValidationHelper();
        org.apache.poi.ss.usermodel.DataValidationConstraint con = dvh.createFormulaListConstraint(rangeName);
        org.apache.poi.ss.util.CellRangeAddressList range = new org.apache.poi.ss.util.CellRangeAddressList(1, 500, colIdx, colIdx);
        org.apache.poi.ss.usermodel.DataValidation dv = dvh.createValidation(con, range);
        dv.setShowErrorBox(true);
        sheet.addValidationData(dv);
    }

    @Transactional
    public Map<String, Object> importInventory(org.springframework.web.multipart.MultipartFile file) {
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, INVENTORY_HEADERS);
        int success = 0;
        List<String> errors = new ArrayList<>();

        // 预加载物料编号和仓库名称索引
        Map<String, BizMaterial> matByNo = materialMapper.selectList(new LambdaQueryWrapper<>())
                .stream().filter(m -> m.getMaterialNo() != null)
                .collect(Collectors.toMap(BizMaterial::getMaterialNo, m -> m, (a, b) -> a));
        Map<String, SysWarehouse> whByName = sysWarehouseMapper.selectList(new LambdaQueryWrapper<>())
                .stream().filter(w -> w.getWarehouseName() != null)
                .collect(Collectors.toMap(SysWarehouse::getWarehouseName, w -> w, (a, b) -> a));

        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 2;
            Map<String, String> r = rows.get(i);
            try {
                String matNo = r.get("物料编号");
                String whName = r.get("仓库名称");
                if (matNo == null || matNo.isEmpty() || whName == null || whName.isEmpty()) {
                    errors.add("第" + rowNum + "行: 物料编号或仓库名称为空");
                    continue;
                }
                BizMaterial mat = matByNo.get(matNo.trim());
                if (mat == null) {
                    errors.add("第" + rowNum + "行: 物料编号'" + matNo + "'未找到");
                    continue;
                }
                SysWarehouse wh = whByName.get(whName.trim());
                if (wh == null) {
                    errors.add("第" + rowNum + "行: 仓库'" + whName + "'未找到");
                    continue;
                }

                BigDecimal total = parseBD(r.get("在库数量"));
                if (total == null) {
                    errors.add("第" + rowNum + "行: 在库数量为空");
                    continue;
                }
                BigDecimal locked = parseBD(r.get("锁定数量"));
                if (locked == null) locked = BigDecimal.ZERO;
                BigDecimal avail = parseBD(r.get("可用数量"));
                if (avail == null) avail = total.subtract(locked);

                // 根据物料 unitConfig 自动折算辅助单位数量
                String secUnit = com.aimes.common.utils.UnitConversionUtil.getSecondaryUnit(mat.getUnitConfig());
                BigDecimal secTotal = com.aimes.common.utils.UnitConversionUtil.toSecondary(total, mat.getUnitConfig());
                BigDecimal secAvail = com.aimes.common.utils.UnitConversionUtil.toSecondary(avail, mat.getUnitConfig());
                BigDecimal secLocked = com.aimes.common.utils.UnitConversionUtil.toSecondary(locked, mat.getUnitConfig());

                // 已存在则更新，否则新增
                com.aimes.modules.inventory.entity.BizInventory existing = bizInventoryMapper.selectOne(
                        new LambdaQueryWrapper<com.aimes.modules.inventory.entity.BizInventory>()
                                .eq(com.aimes.modules.inventory.entity.BizInventory::getMaterialId, mat.getId())
                                .eq(com.aimes.modules.inventory.entity.BizInventory::getWarehouseId, wh.getId())
                                .last("LIMIT 1"));
                if (existing != null) {
                    existing.setTotalQty(total);
                    existing.setAvailableQty(avail);
                    existing.setLockedQty(locked);
                    if (secUnit != null) existing.setSecondaryUnit(secUnit);
                    if (secTotal != null) existing.setSecondaryTotalQty(secTotal);
                    if (secAvail != null) existing.setSecondaryAvailableQty(secAvail);
                    if (secLocked != null) existing.setSecondaryLockedQty(secLocked);
                    bizInventoryMapper.updateById(existing);
                } else {
                    com.aimes.modules.inventory.entity.BizInventory inv =
                            new com.aimes.modules.inventory.entity.BizInventory();
                    inv.setMaterialId(mat.getId());
                    inv.setWarehouseId(wh.getId());
                    inv.setTotalQty(total);
                    inv.setAvailableQty(avail);
                    inv.setLockedQty(locked);
                    inv.setSecondaryUnit(secUnit);
                    inv.setSecondaryTotalQty(secTotal);
                    inv.setSecondaryAvailableQty(secAvail);
                    inv.setSecondaryLockedQty(secLocked);
                    bizInventoryMapper.insert(inv);
                }
                success++;
            } catch (Exception e) {
                errors.add("第" + rowNum + "行: " + friendlyError(e));
            }
        }
        return buildResult(success, rows.size(), errors);
    }

    /** 带行号的行数据 */
    private record IndexedRow(int rowNum, Map<String, String> data) {}
}
