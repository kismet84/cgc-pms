package com.cgcpms.site.vo;

import lombok.Data;

@Data
public class SiteDailyDeliveryVO {
    private String receiptItemId;
    private String receiptId;
    private String receiptCode;
    private String partnerName;
    private String materialId;
    private String materialName;
    private String actualQuantity;
    private String qualifiedQuantity;
}
