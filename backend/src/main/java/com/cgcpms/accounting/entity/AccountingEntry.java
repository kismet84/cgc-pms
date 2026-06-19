package com.cgcpms.accounting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
import java.util.ArrayList;
import java.util.List;

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

    /** DRAFT / POSTED / REVERSED */
    private String entryStatus;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal totalDebit;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal totalCredit;

    /** 分录行（仅内存传递，不映射数据库列） */
    @TableField(exist = false)
    private List<AccountingEntryLine> lines = new ArrayList<>();
}
