package com.cgcpms.revenue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 业主收入确认实体 — 对应 contract_revenue 表。
 * <p>
 * 与 stl_settlement（分包结算）完全独立，处理对业主/甲方的收入确认。
 * 按新收入准则（CAS 14）：履约进度确认收入，与业主结算分离核算。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_revenue")
public class ContractRevenue extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long projectId;

    private Long contractId;

    private String revenueCode;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate revenueDate;

    /** 累计履约进度(%) */
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal progressPercent;

    /** 进度描述（如：主体结构封顶） */
    private String progressDesc;

    /** 本期确认收入（不含税） */
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal revenueAmount;

    /** 销项税额 */
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal revenueTax;

    /** 含税收入 */
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal revenueAmountWithTax;

    /** 本期向业主结算金额 */
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal billedAmount;

    /** 结算税额 */
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal billedTax;

    /** 审批状态: DRAFT / PENDING / APPROVED / REJECTED */
    private String approvalStatus;

    /** 审批通过后生成的 cost_item ID */
    private Long costItemId;

    private Long approvalInstanceId;
    private String formulaVersion;
    private Integer attachmentCount;

    @com.baomidou.mybatisplus.annotation.Version
    private Integer version;
}
