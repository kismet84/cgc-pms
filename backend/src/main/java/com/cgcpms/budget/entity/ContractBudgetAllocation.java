package com.cgcpms.budget.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_budget_allocation")
public class ContractBudgetAllocation extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long projectId;
    @NotNull
    private Long contractId;
    @NotNull
    private Long budgetLineId;
    @NotNull
    @Positive
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal allocatedAmount;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal reservedAmount;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal consumedAmount;
    @Version
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer version;
}
