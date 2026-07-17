package com.cgcpms.accounting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("accounting_entry")
public class AccountingEntry extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private String entryCode;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate entryDate;

    private String entryType;

    private String sourceType;

    private Long sourceId;

    private Long projectId;
    private Long contractId;
    private Long payApplicationId;
    private Long payRecordId;
    private Long collectionRecordId;

    /** DRAFT / POSTED / REVERSED */
    private String entryStatus;

    /** PENDING / APPROVED / REJECTED */
    private String reviewStatus;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private Long postedBy;
    private Long periodId;
    private Integer adjustmentFlag;
    private Long originalEntryId;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal totalDebit;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal totalCredit;

    private LocalDateTime postedAt;
    private LocalDateTime reversedAt;
    private Long reversedEntryId;

    @Version
    private Integer version;

    /** 分录行（仅内存传递，不映射数据库列） */
    @TableField(exist = false)
    private List<AccountingEntryLine> lines = new ArrayList<>();
}
