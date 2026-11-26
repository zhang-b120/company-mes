package com.aimes.modules.eam.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "eam_equipment", autoResultMap = true)
public class EamEquipment extends BaseEntity {
    private String equipmentNo;
    private String equipmentName;
    private String model;
    private String category;
    private Long supplierId;
    private LocalDate purchaseDate;
    private LocalDate enableDate;
    /** NORMAL/MAINTENANCE/RETIRED */
    private String status;
    private Long deptId;
    private String location;
    private String specification;
    private String parameters;
    private String imageUrl;
    /** 附件列表 JSON: [{name, url}] */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> attachmentUrls;
    /** 维护周期(天) */
    private Integer maintenanceCycle;
    private LocalDate nextMaintenanceDate;
    private BigDecimal equipmentValue;
    private Integer depreciationYears;
    private String remark;
}
