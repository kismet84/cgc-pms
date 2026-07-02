package com.cgcpms.settlement.vo;

import lombok.Data;

@Data
public class SettlementAttachmentVO {
    private String id;
    private String originalName;
    private Long fileSize;
    private String fileType;
    private String uploadedBy;
    private String uploadedAt;
}
