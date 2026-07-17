package com.cgcpms.expense.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("expense_application")
public class ExpenseApplication extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;
    @NotNull(message = "项目不能为空")
    private Long projectId;
    @NotNull(message = "合同不能为空")
    private Long contractId;
    @NotNull(message = "费用分类科目不能为空")
    private Long costSubjectId;
    @NotNull(message = "预算科目不能为空")
    private Long budgetLineId;
    @NotNull(message = "付款对象不能为空")
    private Long payeePartnerId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String expenseCode;
    @NotBlank(message = "费用分类不能为空")
    private String expenseCategory;
    @NotNull(message = "费用日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expenseDate;
    @NotNull(message = "费用金额不能为空")
    @Positive(message = "费用金额必须大于0")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal amount;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal convertedAmount;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal paidAmount;
    @NotBlank(message = "费用说明不能为空")
    private String description;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String approvalStatus;
    @Version
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer version;
}
