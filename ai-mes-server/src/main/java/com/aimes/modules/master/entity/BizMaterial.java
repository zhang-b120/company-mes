package com.aimes.modules.master.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonGetter;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "biz_material", autoResultMap = true)
public class BizMaterial extends BaseEntity {
    private String materialNo;
    private String materialName;
    private String spec;
    private String category;
    private Long categoryId;
    private String unit;
    private String unitConfig;
    private BigDecimal safetyStock;
    private BigDecimal maxStock;
    private BigDecimal minStock;
    /** 标准成本(含税) */
    private BigDecimal standardCost;
    private Long defaultWarehouseId;
    private Long defaultSupplierId;
    private Integer shelfLifeDays;
    private Boolean allowStockLock;
    private String materialAttributes;
    private String status;
    private String remark;

    /** 是否虚拟半成品(外协工序自动生成) 0=否 1=是 */
    private Integer isVirtualSemi;

    // MRP相关字段
    private String sourceType;
    private Integer purchaseLeadTime;
    private Integer productionLeadTime;
    private BigDecimal minOrderQty;
    private BigDecimal orderQtyMultiple;
    private Boolean mrpEnabled;

    /** 图纸编号（成品类型使用） */
    private String drawingNo;

    /** 负责人用户ID */
    private Long responsibleUserId;

    private String imageUrl;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> attachmentUrls;

    /** 前端使用 materialType 字段名，映射为 category 的别名 */
    @JsonGetter("materialType")
    public String getMaterialType() {
        return this.category;
    }

    @com.fasterxml.jackson.annotation.JsonSetter("materialType")
    public void setMaterialType(String materialType) {
        this.category = materialType;
    }

    /** 瞬态:是否已配置工艺路线(列表页用) */
    @TableField(exist = false)
    private Boolean hasProcess;

    /** 瞬态:默认供应商名称(列表页展示用) */
    @TableField(exist = false)
    private String defaultSupplierName;

    /** 瞬态:物料类别名称(关联 biz_material_category.category_name) */
    @TableField(exist = false)
    private String categoryName;
}
