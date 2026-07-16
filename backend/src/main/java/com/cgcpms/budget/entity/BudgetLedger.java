package com.cgcpms.budget.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("budget_ledger")
public class BudgetLedger {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;
    private Long tenantId;
    private Long budgetId;
    private Long budgetLineId;
    private Long projectId;
    private String businessType;
    private Long businessId;
    private String entryType;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal amount;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal reservedBalance;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal consumedBalance;
    private String idempotencyKey;
    private Long createdBy;
    private LocalDateTime createdAt;
    private String remark;
}
