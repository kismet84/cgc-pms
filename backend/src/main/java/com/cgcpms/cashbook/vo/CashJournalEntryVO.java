package com.cgcpms.cashbook.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.cgcpms.cashbook.entity.CashJournalChangeLog;
import com.cgcpms.file.vo.SysFileVO;

@Data
public class CashJournalEntryVO {
    private String id;
    private String entryNo;
    private String accountId;
    private String accountName;
    private String accountType;
    private String direction;
    private String amount;
    private String runningBalance;
    private LocalDate businessDate;
    private String counterpartyName;
    private String summary;
    private String projectId;
    private String contractId;
    private String sourceType;
    private String sourceId;
    private String status;
    private LocalDateTime closureDueAt;
    private String archivedBy;
    private LocalDateTime archivedAt;
    private String reverseOfEntryId;
    private String reversalEntryId;
    private Integer version;
    private LocalDateTime createdAt;
    private long attachmentCount;
    private List<SysFileVO> attachments = new ArrayList<>();
    private List<CashJournalChangeLog> changeLogs = new ArrayList<>();
}
