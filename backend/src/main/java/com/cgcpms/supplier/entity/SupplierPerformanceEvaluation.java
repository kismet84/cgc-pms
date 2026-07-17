package com.cgcpms.supplier.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sp_performance_evaluation")
public class SupplierPerformanceEvaluation extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID) @JsonSerialize(using = ToStringSerializer.class) private Long id;
    @JsonSerialize(using = ToStringSerializer.class) private Long tenantId;
    @JsonSerialize(using = ToStringSerializer.class) private Long projectId;
    @JsonSerialize(using = ToStringSerializer.class) private Long partnerId;
    @JsonSerialize(using = ToStringSerializer.class) private Long contractId;
    @JsonSerialize(using = ToStringSerializer.class) private Long purchaseOrderId;
    private String evaluationCode;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal deliveryScore;
    private BigDecimal qualityScore;
    private BigDecimal serviceScore;
    private BigDecimal commercialScore;
    private BigDecimal totalScore;
    private String grade;
    private Integer onTimeFlag;
    private Integer approvedReceiptCount;
    private Integer unqualifiedReceiptCount;
    private Integer returnCount;
    private Integer finalizedSettlementCount;
    private Integer qualitySafetyFactCount;
    private BigDecimal qualitySafetyAverage;
    private String evaluationComment;
    private Integer recommendBlacklist;
    private String status;
    @JsonSerialize(using = ToStringSerializer.class) private Long confirmedBy;
    private LocalDateTime confirmedAt;
    @Version private Integer version;
}
