package com.cgcpms.cashbook.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CashJournalQuery {
    private long pageNo = 1;
    private long pageSize = 20;
    private Long accountId;
    private String direction;
    private String status;
    private String sourceType;
    private Long sourceId;
    private Long projectId;
    private Long contractId;
    private Boolean hasAttachment;
    private LocalDate businessDateStart;
    private LocalDate businessDateEnd;
    private String keyword;
}
