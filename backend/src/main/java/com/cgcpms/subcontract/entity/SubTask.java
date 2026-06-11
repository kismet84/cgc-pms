package com.cgcpms.subcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sub_task")
public class SubTask extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    @NotNull
    private Long projectId;

    private Long contractId;

    private Long partnerId;

    private String taskCode;

    @NotBlank
    private String taskName;

    private String workArea;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate plannedStartDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate plannedEndDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate actualStartDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate actualEndDate;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal progressPercent;

    private String status;
}
