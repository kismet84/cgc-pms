package com.cgcpms.subcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sub_measure_item")
public class SubMeasureItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long measureId;

    private Long contractItemId;

    private String itemName;

    private String unit;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal contractQuantity;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal currentQuantity;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal cumulativeQuantity;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal unitPrice;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal amount;
}
