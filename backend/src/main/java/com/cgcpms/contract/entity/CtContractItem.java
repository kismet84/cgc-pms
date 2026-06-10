package com.cgcpms.contract.entity;

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
@TableName("ct_contract_item")
public class CtContractItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long contractId;

    private String itemCode;

    private String itemName;

    private String itemSpec;

    private String unit;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal quantity;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal unitPrice;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal amount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal taxRate;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal taxAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal amountWithoutTax;

    private Integer sortOrder;
}
