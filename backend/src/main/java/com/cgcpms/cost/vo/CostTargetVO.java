package com.cgcpms.cost.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CostTargetVO implements Serializable {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    private String versionNo;
    private String versionName;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal totalTargetAmount;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal totalBidCostAmount;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal totalResponsibilityAmount;
    private Integer isActive;
    private String approvalStatus;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveDate;

    private String status;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approvalInstanceId;
    private Integer version;
    private String remark;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;
}
